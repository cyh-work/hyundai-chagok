package com.example.pleosstarter.map;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import com.example.pleosstarter.notes.LocationNote;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 서울역 주변 번들 지도를 표시하고 지도 위 제스처를 호스트 화면으로 전달한다. */
public final class SeoulMapView extends View {
    private static final int INK = Color.rgb(27, 48, 70);
    private static final int BLUE = Color.rgb(40, 99, 222);
    private static final int AMBER = Color.rgb(229, 155, 29);
    private static final int GREEN = Color.rgb(24, 132, 105);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path strokePath = new Path();
    private final List<StrokePoint> stroke = new ArrayList<>();
    private final List<MapPlace> visiblePlaces = new ArrayList<>();
    private final GestureClassifier classifier = new GeometricGestureClassifier();
    private final MapSelectionResolver selectionResolver = new MapSelectionResolver();
    private Consumer<MapGesture> gestureListener;
    private Predicate<MapPlace> favoriteLookup = place -> false;
    private MapData map;
    private MapData pangyoMap;
    private MapData seoulMap;
    private List<LocationNote> locationNotes = List.of();
    private MapData.Coordinate vehiclePosition;
    private MapPlace selectedPlace;
    private MapGesture.Type lastType = MapGesture.Type.UNRECOGNIZED;
    private boolean drawing;

    /** 코드에서 지도를 생성할 때 공통 초기화로 연결한다. */
    public SeoulMapView(Context context) {
        this(context, null);
    }

    /** XML 레이아웃에서 지도를 생성하고 번들 자료를 읽는다. */
    public SeoulMapView(Context context, AttributeSet attributes) {
        this(context, attributes, 0);
    }

    /** 스타일을 지정한 View 생성과 지도 읽기 실패의 표시 경계를 구성한다. */
    public SeoulMapView(Context context, AttributeSet attributes, int style) {
        super(context, attributes, style);
        setClickable(true);
        setFocusable(true);
        setContentDescription("서울역 주변 지도. 장소 위에 별, 원, 선을 그려 선택할 수 있습니다.");
        try {
            MapAssetReader reader = new MapAssetReader(context.getAssets());
            seoulMap = reader.read("seoul-station-map.json");
            map = seoulMap;
            pangyoMap = reader.read("pangyo-map.json");
        } catch (MapDataException exception) {
            Log.e("SeoulMapView", "서울역 지도 자료를 불러오지 못했습니다.", exception);
        }
    }

    /** Activity가 처리할 인식 결과 수신자를 연결한다. */
    public void setGestureListener(Consumer<MapGesture> listener) {
        gestureListener = listener;
    }

    /** 외부 저장소의 현재 즐겨찾기 조회 함수를 연결하고 표시를 갱신한다. */
    public void setFavoriteLookup(Predicate<MapPlace> lookup) {
        if (lookup == null) {
            favoriteLookup = place -> false;
        } else {
            favoriteLookup = lookup;
        }
        invalidate();
    }

    /** 차량 위치를 포함하는 자료로 전환하고 파란 점을 표시한다. */
    public boolean setVehiclePosition(double latitude, double longitude) {
        MapData nearbyMap = null;
        if (pangyoMap != null && pangyoMap.contains(latitude, longitude)) {
            nearbyMap = pangyoMap;
        } else if (seoulMap != null && seoulMap.contains(latitude, longitude)) {
            nearbyMap = seoulMap;
        }
        if (nearbyMap == null) {
            clearVehiclePosition();
            return false;
        }
        if (nearbyMap != map) {
            map = nearbyMap;
            cancelStroke();
            selectedPlace = null;
        }
        vehiclePosition = new MapData.Coordinate(longitude, latitude);
        invalidate();
        return true;
    }

    /** 현재 지도에 표시할 권역이 판교인지 확인한다. */
    public boolean isPangyoMap() {
        return map != null && map == pangyoMap;
    }

    /** 화면에 표시할 사용자 위치 기록의 최신 스냅샷을 받는다. */
    public void setLocationNotes(List<LocationNote> notes) {
        locationNotes = notes;
        invalidate();
    }

    /** 위치를 다시 확인할 수 없을 때 이전 위치 표시를 지운다. */
    public void clearVehiclePosition() {
        vehiclePosition = null;
        invalidate();
    }

    /** 배경, 실제 도로, 장소 및 현재 스트로크를 순서대로 그린다. */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(238, 242, 236));
        if (map == null) {
            drawText(canvas, "지도 자료를 불러오지 못했습니다", dp(28), dp(48), dp(18), INK, false);
            return;
        }

        MapProjection projection = projection();
        drawRoads(canvas, projection, true);
        drawRoads(canvas, projection, false);
        chooseVisiblePlaces(projection);
        drawPlaces(canvas, projection);
        drawLocationNotes(canvas, projection);
        drawVehiclePosition(canvas, projection);

        if (!stroke.isEmpty()) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(gestureColor());
            paint.setAlpha(drawing ? 230 : 170);
            canvas.drawPath(strokePath, paint);
            paint.setAlpha(255);
        }
    }

    /** 현재 지도 안에 저장한 지점을 주황색 핀으로 표시한다. */
    private void drawLocationNotes(Canvas canvas, MapProjection projection) {
        paint.setStyle(Paint.Style.FILL);
        for (LocationNote note : locationNotes) {
            if (!map.contains(note.latitude(), note.longitude())) {
                continue;
            }
            float x = projection.x(note.longitude());
            float y = projection.y(note.latitude());
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(11), paint);
            paint.setColor(AMBER);
            canvas.drawCircle(x, y, dp(8), paint);
        }
    }

    /** 실제 차량 좌표를 장소 표시와 구별되는 파란색 점으로 그린다. */
    private void drawVehiclePosition(Canvas canvas, MapProjection projection) {
        if (vehiclePosition == null) {
            return;
        }
        float x = projection.x(vehiclePosition.longitude());
        float y = projection.y(vehiclePosition.latitude());
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(48, 15, 108, 244));
        canvas.drawCircle(x, y, dp(25), paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, dp(12), paint);
        paint.setColor(Color.rgb(15, 108, 244));
        canvas.drawCircle(x, y, dp(9), paint);
    }

    /** 도로 외곽과 안쪽을 나눠 교차로가 이어진 지도를 표시한다. */
    private void drawRoads(Canvas canvas, MapProjection projection, boolean outline) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (MapData.Road road : map.roads()) {
            float width = roadWidth(road.kind());
            paint.setStrokeWidth(width + (outline ? dp(2) : 0));
            paint.setColor(outline ? Color.rgb(210, 218, 208) : roadColor(road.kind()));
            Path path = new Path();
            MapData.Coordinate first = road.points().get(0);
            path.moveTo(projection.x(first.longitude()), projection.y(first.latitude()));
            for (int index = 1; index < road.points().size(); index++) {
                MapData.Coordinate point = road.points().get(index);
                path.lineTo(projection.x(point.longitude()), projection.y(point.latitude()));
            }
            canvas.drawPath(path, paint);
        }
    }

    /** 도로 종류에 맞는 가독성 중심의 화면 폭을 반환한다. */
    private float roadWidth(String kind) {
        return switch (kind) {
            case "motorway", "trunk", "primary" -> dp(12);
            case "secondary", "tertiary" -> dp(8);
            case "footway", "path", "steps", "pedestrian", "cycleway" -> dp(2);
            default -> dp(4);
        };
    }

    /** 주요 도로와 보행 경로를 구분하는 채움색을 반환한다. */
    private int roadColor(String kind) {
        return switch (kind) {
            case "motorway", "trunk", "primary" -> Color.rgb(255, 237, 188);
            case "footway", "path", "steps", "pedestrian", "cycleway" -> Color.rgb(214, 225, 205);
            default -> Color.WHITE;
        };
    }

    /** 대표 장소를 먼저 배치한 뒤 겹치지 않는 장소만 추가한다. */
    private void chooseVisiblePlaces(MapProjection projection) {
        visiblePlaces.clear();
        List<MapPlace> ordered = new ArrayList<>(map.places());
        ordered.sort((first, second) -> Integer.compare(priority(second), priority(first)));
        List<RectF> labels = new ArrayList<>();
        paint.setTextSize(dp(13));
        for (MapPlace place : ordered) {
            float x = projection.x(place.longitude());
            float y = projection.y(place.latitude());
            if (x < dp(12) || x > getWidth() - dp(12) || y < dp(12) || y > getHeight() - dp(32)) {
                continue;
            }
            float textWidth = Math.min(paint.measureText(place.name()), getWidth() - dp(50));
            float textX = Math.max(dp(12), Math.min(x - textWidth / 2, getWidth() - textWidth - dp(12)));
            RectF label = new RectF(textX - dp(7), y - dp(10), textX + textWidth + dp(7), y + dp(30));
            boolean overlaps = false;
            for (RectF existing : labels) {
                if (RectF.intersects(label, existing)) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) {
                visiblePlaces.add(place);
                labels.add(label);
            }
            if (visiblePlaces.size() >= 22) {
                break;
            }
        }
    }

    /** 선택·즐겨찾기와 서울역 주변 대표 장소를 우선 배치한다. */
    private int priority(MapPlace place) {
        if (place.equals(selectedPlace)) {
            return 100;
        }
        if (favoriteLookup.test(place)) {
            return 90;
        }
        if (place.name().equals("서울역 KTX")) {
            return 80;
        }
        if (place.name().contains("문화역서울") || place.name().contains("손기정")
                || place.name().contains("청파어린이") || place.name().contains("만리동광장")
                || place.name().contains("롯데마트")) {
            return 70;
        }
        if (place.category().contains("카페") || place.category().contains("주차")) {
            return 20;
        }
        return 0;
    }

    /** 라벨 배경과 색상으로 장소 선택 및 즐겨찾기 상태를 구분한다. */
    private void drawPlaces(Canvas canvas, MapProjection projection) {
        for (MapPlace place : visiblePlaces) {
            float x = projection.x(place.longitude());
            float y = projection.y(place.latitude());
            boolean favorite = favoriteLookup.test(place);
            boolean selected = place.equals(selectedPlace);
            int color = favorite ? AMBER : BLUE;
            if (selected) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(35, 40, 99, 222));
                canvas.drawCircle(x, y, dp(23), paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(9), paint);
            paint.setColor(color);
            canvas.drawCircle(x, y, dp(6), paint);
            if (favorite) {
                drawText(canvas, "★", x - dp(6), y + dp(5), dp(16), AMBER, true);
            }
            paint.setTextSize(dp(13));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL));
            float textWidth = paint.measureText(place.name());
            float textX = Math.max(dp(12), Math.min(x - textWidth / 2, getWidth() - textWidth - dp(12)));
            paint.setColor(Color.argb(235, 255, 255, 255));
            canvas.drawRoundRect(textX - dp(5), y + dp(10), textX + textWidth + dp(5), y + dp(31), dp(5), dp(5), paint);
            drawText(canvas, place.name(), textX, y + dp(25), dp(13), INK, selected);
        }
        if (selectedPlace != null && !visiblePlaces.contains(selectedPlace)) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GREEN);
            canvas.drawCircle(projection.x(selectedPlace.longitude()), projection.y(selectedPlace.latitude()), dp(9), paint);
        }
    }

    /** 스타일 초기화를 함께 수행해 텍스트를 일관되게 표시한다. */
    private void drawText(Canvas canvas, String text, float x, float y, float size, int color, boolean bold) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextSize(size);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        canvas.drawText(text, x, y, paint);
    }

    /** 한 손가락 입력을 기록하고 끝난 스트로크만 인식한다. */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (map == null) {
            return false;
        }
        if (event.getPointerCount() > 1) {
            cancelStroke();
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                stroke.clear();
                strokePath.reset();
                strokePath.moveTo(event.getX(), event.getY());
                stroke.add(new StrokePoint(event.getX(), event.getY()));
                drawing = true;
                lastType = MapGesture.Type.UNRECOGNIZED;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (drawing) {
                    for (int index = 0; index < event.getHistorySize(); index++) {
                        appendPoint(event.getHistoricalX(index), event.getHistoricalY(index));
                    }
                    appendPoint(event.getX(), event.getY());
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (drawing) {
                    appendPoint(event.getX(), event.getY());
                    finishStroke();
                    performClick();
                }
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelStroke();
                return true;
            default:
                return true;
        }
    }

    /** 접근성 클릭 이벤트를 Android 기본 처리로 전달한다. */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** 중복 좌표와 지나친 입력 점을 제한하면서 스트로크를 추가한다. */
    private void appendPoint(float x, float y) {
        StrokePoint previous = stroke.get(stroke.size() - 1);
        if (Math.hypot(x - previous.x(), y - previous.y()) < dp(1) || stroke.size() >= 2000) {
            return;
        }
        stroke.add(new StrokePoint(x, y));
        strokePath.lineTo(x, y);
    }

    /** 별·원은 경계 중심, 선은 끝점에서 장소를 선택한 뒤 결과를 전달한다. */
    private void finishStroke() {
        drawing = false;
        lastType = classifier.classify(stroke, dp(28));
        MapPlace place = null;
        if (lastType != MapGesture.Type.UNRECOGNIZED) {
            float left = Float.MAX_VALUE;
            float right = -Float.MAX_VALUE;
            float top = Float.MAX_VALUE;
            float bottom = -Float.MAX_VALUE;
            for (StrokePoint point : stroke) {
                left = Math.min(left, point.x());
                right = Math.max(right, point.x());
                top = Math.min(top, point.y());
                bottom = Math.max(bottom, point.y());
            }
            float selectionX = (left + right) / 2;
            float selectionY = (top + bottom) / 2;
            if (lastType == MapGesture.Type.LINE) {
                StrokePoint endpoint = stroke.get(stroke.size() - 1);
                selectionX = endpoint.x();
                selectionY = endpoint.y();
            }
            MapProjection projection = projection();
            if (lastType == MapGesture.Type.LINE) {
                place = selectionResolver.resolve(lastType, selectionX, selectionY,
                        visiblePlaces, map, projection, dp(66));
            } else {
                double latitude = projection.latitude(selectionY);
                double longitude = projection.longitude(selectionX);
                if (map.contains(latitude, longitude)) {
                    place = new MapPlace("", "", latitude, longitude);
                }
            }
            if (place == null) {
                lastType = MapGesture.Type.UNRECOGNIZED;
            } else if (lastType == MapGesture.Type.LINE) {
                selectedPlace = place;
            } else {
                selectedPlace = null;
            }
        }
        invalidate();
        if (gestureListener != null) {
            gestureListener.accept(new MapGesture(lastType, place));
        }
    }

    /** 다중 터치와 취소 이벤트는 인식이나 장소 변경 없이 정리한다. */
    private void cancelStroke() {
        drawing = false;
        stroke.clear();
        strokePath.reset();
        getParent().requestDisallowInterceptTouchEvent(false);
        invalidate();
    }

    /** 진행 중 입력과 최종 인식 유형의 선 색을 반환한다. */
    private int gestureColor() {
        if (drawing) {
            return BLUE;
        }
        return switch (lastType) {
            case STAR -> AMBER;
            case CIRCLE -> BLUE;
            case LINE -> GREEN;
            case UNRECOGNIZED -> Color.rgb(173, 73, 73);
        };
    }

    /** 현재 View 크기에서 위경도와 픽셀을 연결한다. */
    private MapProjection projection() {
        return new MapProjection(map, getWidth(), getHeight(), dp(28));
    }

    /** 화면 밀도와 무관한 지도 표시 크기를 픽셀로 바꾼다. */
    private float dp(float size) {
        return size * getResources().getDisplayMetrics().density;
    }
}
