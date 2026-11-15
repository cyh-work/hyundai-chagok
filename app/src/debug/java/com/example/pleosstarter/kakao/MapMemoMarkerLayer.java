package com.example.pleosstarter.kakao;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;

import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.common.util.FloatPoint;
import com.kakaomobility.knsdk.common.util.DoublePoint;
import com.kakaomobility.knsdk.common.util.IntPoint;
import com.kakaomobility.knsdk.map.knmapview.KNMapView;
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapMarker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 저장 기록의 지도 마커·짧은 탭 적중 판정·긴 누름 이동을 관리한다. */
final class MapMemoMarkerLayer {
    private final KNMapView map;
    private final List<KNMapMarker> markers = new ArrayList<>();
    private final Map<Integer, MapRecordMarker> records = new HashMap<>();
    private final Bitmap favoriteIcon = MapMarkerIcons.record(true);
    private final Bitmap memoIcon = MapMarkerIcons.record(false);
    private final Bitmap draggedFavoriteIcon = draggedIcon(favoriteIcon);
    private final Bitmap draggedMemoIcon = draggedIcon(memoIcon);
    private final float touchSlop;
    private final float memoTouchRadius;
    private boolean active;
    private boolean closed;
    private KNMapMarker draggedMarker;
    private MapRecordMarker draggedRecord;
    private FloatPoint originalCoordinate;
    private MapRecordDragAnchor dragAnchor;

    /** 저장 마커의 터치 범위를 구성하고 선택은 앱 입력 계층에 맡긴다. */
    MapMemoMarkerLayer(Context context, KNMapView map) {
        this.map = map;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        memoTouchRadius = memoIcon.getWidth() / 2.0f
                + 8 * context.getResources().getDisplayMetrics().density;
    }

    /** 원·별 모두 실제 저장 DTO에 연결하고 SDK의 중복 탭 판정은 사용하지 않는다. */
    void replace(List<MapRecordMarker> updated) {
        if (closed) {
            return;
        }
        cancelDrag();
        for (KNMapMarker marker : markers) {
            map.removeMarker(marker);
        }
        markers.clear();
        records.clear();
        for (MapRecordMarker record : updated) {
            FloatPoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(
                    record.longitude(), record.latitude()).toFloatPoint();
            KNMapMarker marker = new KNMapMarker(coordinate);
            marker.setIcon(record.favorite() ? favoriteIcon : memoIcon);
            marker.setPixelOffset(new IntPoint(0, 30));
            marker.setVisible(true);
            marker.setUseSingleTapped(false);
            marker.setUseDoubleTapped(false);
            marker.setUseLongPressed(false);
            marker.setPriority(record.favorite() ? 20 : 30);
            markers.add(marker);
            records.put(marker.getId(), record);
        }
        map.addMarkers(markers);
        Log.i("PlaceMap", "RECORD_MARKERS count=" + markers.size());
    }

    /** 짧은 탭의 현재 투영에서 글자가 없는 기존 별까지 실제 저장 기록을 찾는다. */
    Optional<MapRecordMarker> findRecordAt(float x, float y) {
        if (closed || !active || draggedMarker != null || !insideMap(x, y)) {
            return Optional.empty();
        }
        KNMapMarker selected = findMarkerAt(x, y, false);
        if (selected == null) {
            return Optional.empty();
        }
        return Optional.of(records.get(selected.getId()));
    }

    /** 현재 투영에서 가장 가까운 이동 가능한 저장 마커를 잡는다. */
    boolean beginDrag(float x, float y) {
        if (closed || !active || !insideMap(x, y)) {
            return false;
        }
        cancelDrag();
        KNMapMarker selected = findMarkerAt(x, y, true);
        if (selected == null) {
            return false;
        }

        FloatPoint screen = map.katecToScreen(selected.getCoordinate());
        dragAnchor = new MapRecordDragAnchor(screen.getX(), screen.getY(), x, y, touchSlop);
        draggedMarker = selected;
        draggedRecord = records.get(selected.getId());
        FloatPoint coordinate = selected.getCoordinate();
        originalCoordinate = new FloatPoint(coordinate.getX(), coordinate.getY());

        // 선택 표시는 중심을 유지한 채 테두리만 넓히며 저장 좌표에는 영향을 주지 않는다.
        Bitmap icon = draggedRecord.favorite() ? draggedFavoriteIcon : draggedMemoIcon;
        selected.setIcon(icon);
        selected.setPixelOffset(new IntPoint(0, icon.getHeight() / 2));
        selected.setPriority(200);
        selected.setUseSingleTapped(false);
        selected.setUseDoubleTapped(false);
        selected.setVisibleCalloutBubble(false);
        map.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        Log.i("PlaceMap", "RECORD_DRAG code=START favorite=" + draggedRecord.favorite());
        return true;
    }

    /** 손가락 간격을 보존한 화면 기준점을 투영하며 디스크와 저장 DTO는 바꾸지 않는다. */
    void previewDrag(float x, float y) {
        if (draggedMarker == null) {
            return;
        }
        FloatPoint coordinate = dragCoordinate(x, y);
        draggedMarker.setCoordinate(coordinate);
    }

    /** 화면 표시는 항상 원위치로 복원하고 실제 UP 이동만 저장 계층에 반환한다. */
    Optional<MapRecordMove> endDrag(float x, float y, boolean cancelled) {
        if (draggedMarker == null) {
            return Optional.empty();
        }
        try {
            if (cancelled || !dragAnchor.hasMoved(x, y)) {
                Log.i("PlaceMap", "RECORD_DRAG code=" + (cancelled ? "CANCEL" : "UNCHANGED"));
                return Optional.empty();
            }
            FloatPoint coordinate = dragCoordinate(x, y);
            DoublePoint target = KNSDK.INSTANCE.convertKATECToWGS84(
                    Math.round(coordinate.getX()), Math.round(coordinate.getY()));
            return Optional.of(new MapRecordMove(draggedRecord, target.getY(), target.getX()));
        } finally {
            cancelDrag();
        }
    }

    /** 취소·화면 이탈·마커 교체 시 미리보기만 폐기하고 저장된 좌표로 되돌린다. */
    private void cancelDrag() {
        if (draggedMarker == null) {
            return;
        }
        draggedMarker.setCoordinate(originalCoordinate);
        Bitmap icon = draggedRecord.favorite() ? favoriteIcon : memoIcon;
        draggedMarker.setIcon(icon);
        draggedMarker.setPixelOffset(new IntPoint(0, icon.getHeight() / 2));
        draggedMarker.setPriority(draggedRecord.favorite() ? 20 : 30);
        draggedMarker.setUseSingleTapped(false);
        draggedMarker.setUseDoubleTapped(false);
        draggedMarker = null;
        draggedRecord = null;
        originalCoordinate = null;
        dragAnchor = null;
    }

    /** 투영할 기준점이 화면 밖이거나 유효하지 않으면 임의의 다른 좌표에 저장하지 않는다. */
    private FloatPoint dragCoordinate(float x, float y) {
        float anchorX = dragAnchor.anchorX(x);
        float anchorY = dragAnchor.anchorY(y);
        if (!insideMap(x, y) || !insideMap(anchorX, anchorY)) {
            throw new IllegalArgumentException("지도 안으로 기록을 옮겨 주세요.");
        }
        FloatPoint coordinate = map.screenToKatec(new FloatPoint(anchorX, anchorY));
        if (coordinate == null || !Float.isFinite(coordinate.getX()) || !Float.isFinite(coordinate.getY())) {
            throw new IllegalArgumentException("기록을 옮길 위치를 확인하지 못했어요.");
        }
        return coordinate;
    }

    /** 저장 가능한 지도 영역 안의 유한 화면 좌표만 허용한다. */
    private boolean insideMap(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y)
                && x >= 0 && y >= 0 && x < map.getWidth() && y < map.getHeight();
    }

    /** 실제 아이콘 중심과 현재 투영을 사용하며 겹친 경우 가까운 위쪽 마커를 선택한다. */
    private KNMapMarker findMarkerAt(float x, float y, boolean movableOnly) {
        KNMapMarker nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (KNMapMarker marker : markers) {
            MapRecordMarker record = records.get(marker.getId());
            if (movableOnly && !record.movable() || !marker.isVisible()) {
                continue;
            }
            FloatPoint projected = map.katecToScreen(marker.getCoordinate());
            Bitmap icon = marker.getIcon();
            IntPoint offset = marker.getPixelOffset();
            // SDK의 하단 중앙 기준 아이콘을 pixelOffset(0, 높이/2)로 중앙 정렬했다.
            float centerX = projected.getX() + offset.getX();
            float centerY = projected.getY() + offset.getY() - icon.getHeight() / 2.0f;
            double distance = Math.hypot(centerX - x, centerY - y);
            if (distance <= memoTouchRadius && (distance < nearestDistance
                    || distance == nearestDistance && nearest != null
                    && marker.getPriority() > nearest.getPriority())) {
                nearest = marker;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** 잡은 마커를 구분하는 청록색 테두리를 원래 아이콘 바깥에 추가한다. */
    private static Bitmap draggedIcon(Bitmap original) {
        int padding = 8;
        Bitmap highlighted = Bitmap.createBitmap(original.getWidth() + padding * 2,
                original.getHeight() + padding * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(highlighted);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFF008D95);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3.0f);
        canvas.drawCircle(highlighted.getWidth() / 2.0f, highlighted.getHeight() / 2.0f,
                highlighted.getWidth() / 2.0f - 3, paint);
        canvas.drawBitmap(original, padding, padding, null);
        return highlighted;
    }

    /** 비활성 화면에서는 마커 선택을 막고 진행 중인 이동만 취소한다. */
    void setActive(boolean active) {
        if (!active) {
            cancelDrag();
        }
        this.active = active;
    }

    /** 지도 종료 시 미리보기와 등록한 저장 마커 참조를 정리한다. */
    void close() {
        cancelDrag();
        closed = true;
        for (KNMapMarker marker : markers) {
            map.removeMarker(marker);
        }
        markers.clear();
        records.clear();
    }
}
