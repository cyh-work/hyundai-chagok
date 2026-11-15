package com.example.pleosstarter.kakao;

import com.kakaomobility.knsdk.common.util.FloatPoint;
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCameraUpdate;
import com.kakaomobility.knsdk.map.knmapview.KNMapView;

/** 터치 시작 시점의 지도 투영과 추적 여부를 복사해 도형의 기준 위치를 보존한다. */
final class MapCameraSnapshot {
    private final float x;
    private final float y;
    private final float anchorX;
    private final float anchorY;
    private final float zoom;
    private final float tilt;
    private final float bearing;
    private final boolean following;

    /** SDK가 변경하는 좌표 객체 대신 값만 복사한다. */
    MapCameraSnapshot(KNMapView map, boolean following) {
        x = map.getCoordinate().getX();
        y = map.getCoordinate().getY();
        anchorX = map.getAnchor().getX();
        anchorY = map.getAnchor().getY();
        zoom = map.getZoom();
        tilt = map.getTilt();
        bearing = map.getBearing();
        this.following = following;
    }

    /** 시작 시점과 같은 투영을 요청하는 새 카메라 명령을 만든다. */
    KNMapCameraUpdate cameraUpdate() {
        return KNMapCameraUpdate.Creator.targetTo(new FloatPoint(x, y))
                .anchorTo(new FloatPoint(anchorX, 1.0f - anchorY)).zoomTo(zoom)
                .tiltTo(tilt).bearingTo(bearing);
    }

    /** 렌더러가 요청한 카메라로 돌아왔는지 확인한다. */
    boolean matches(KNMapView map) {
        return Math.hypot(map.getCoordinate().getX() - x, map.getCoordinate().getY() - y) < 1.0
                && Math.abs(map.getAnchor().getX() - anchorX) < 0.0001f
                && Math.abs(map.getAnchor().getY() - anchorY) < 0.0001f
                && Math.abs(map.getZoom() - zoom) < 0.001f
                && Math.abs(map.getTilt() - tilt) < 0.1f
                && Math.abs(map.getBearing() - bearing) < 0.1f;
    }

    /** 터치 전 추적 상태를 반환한다. */
    boolean wasFollowing() {
        return following;
    }
}
