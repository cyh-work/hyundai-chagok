package com.example.pleosstarter.kakao;

import android.util.Log;

import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.common.util.FloatPoint;
import com.kakaomobility.knsdk.common.util.IntPoint;
import com.kakaomobility.knsdk.map.knmapview.KNMapView;
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapMarker;

/** 안내 대상 장소 하나를 저장 기록과 분리해 실제 좌표에 핀으로 표시한다. */
final class MapSelectedPlaceLayer {
    private static final int PIN_BOTTOM_PADDING_PIXELS = 2;
    private static final int SELECTED_PLACE_PRIORITY = 100;
    private final KNMapView map;
    private KNMapMarker marker;
    private boolean closed;

    /** 기존 지도에 표시만 추가하며 저장 마커의 입력 리스너는 변경하지 않는다. */
    MapSelectedPlaceLayer(KNMapView map) {
        this.map = map;
    }

    /** 새 선택을 준비한 뒤 이전 핀을 교체하고 터치 입력은 기존 지도에 맡긴다. */
    void show(KakaoPlaceSelection selection) {
        if (closed) {
            return;
        }

        FloatPoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(
                selection.getLongitude(), selection.getLatitude()).toFloatPoint();
        KNMapMarker replacement = new KNMapMarker(coordinate);
        replacement.setIcon(MapMarkerIcons.selectedPlace(selection.getName()));
        replacement.setPixelOffset(new IntPoint(0, PIN_BOTTOM_PADDING_PIXELS));
        replacement.setVisible(true);
        replacement.setUseSingleTapped(false);
        replacement.setUseDoubleTapped(false);
        replacement.setUseLongPressed(false);
        replacement.setPriority(SELECTED_PLACE_PRIORITY);

        clear();
        marker = replacement;
        map.addMarker(marker);
        Log.i("PlaceMap", "SELECTED_PLACE_PIN code=SHOWN");
    }

    /** 다른 마커와 카메라 상태를 보존하면서 선택 핀 하나만 제거한다. */
    void clear() {
        if (marker == null) {
            return;
        }

        map.removeMarker(marker);
        marker = null;
        Log.i("PlaceMap", "SELECTED_PLACE_PIN code=CLEARED");
    }

    /** 지도 해제 전에 선택 핀을 제거하고 이후 표시 요청을 무시한다. */
    void close() {
        clear();
        closed = true;
    }
}
