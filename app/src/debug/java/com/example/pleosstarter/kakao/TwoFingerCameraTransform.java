package com.example.pleosstarter.kakao;

/** 두 손가락의 중심·간격·각도 변화로 지도 이동·배율·방향을 계산한다. */
final class TwoFingerCameraTransform {
    private static final float MINIMUM_ZOOM = 0.16f;
    private static final float MAXIMUM_ZOOM = 5000.0f;
    private static final float MINIMUM_SPAN = 8.0f;
    private final float initialSpan;
    private final float initialAngle;
    private final float initialZoom;
    private final float initialBearing;

    /** 최초 두 포인터와 카메라 배율·방향을 보관하고 유효하지 않은 입력을 거부한다. */
    TwoFingerCameraTransform(float firstX, float firstY, float secondX, float secondY,
            float zoom, float bearing) {
        requireFinite(firstX, firstY, secondX, secondY, zoom, bearing);
        initialSpan = distance(firstX, firstY, secondX, secondY);
        if (initialSpan < MINIMUM_SPAN || zoom <= 0) {
            throw new IllegalArgumentException("두 손가락 간격과 지도 배율을 확인해 주세요.");
        }
        initialAngle = angle(firstX, firstY, secondX, secondY);
        initialZoom = zoom;
        initialBearing = bearing;
    }

    /** 현재 두 포인터에서 중심점과 제한된 배율·회전값을 반환한다. */
    Change update(float firstX, float firstY, float secondX, float secondY) {
        requireFinite(firstX, firstY, secondX, secondY);
        float span = Math.max(MINIMUM_SPAN, distance(firstX, firstY, secondX, secondY));
        float zoom = Math.max(MINIMUM_ZOOM, Math.min(MAXIMUM_ZOOM, initialZoom * initialSpan / span));
        float rotation = normalize(angle(firstX, firstY, secondX, secondY) - initialAngle);
        return new Change((firstX + secondX) / 2, (firstY + secondY) / 2,
                zoom, normalize(initialBearing - rotation));
    }

    /** 수치가 아닌 화면 좌표가 카메라 명령으로 전파되지 않게 한다. */
    private static void requireFinite(float... components) {
        for (float component : components) {
            if (!Float.isFinite(component)) {
                throw new IllegalArgumentException("유효한 지도 입력이 필요합니다.");
            }
        }
    }

    /** 두 손가락 사이 거리를 화면 단위로 반환한다. */
    private static float distance(float firstX, float firstY, float secondX, float secondY) {
        return (float) Math.hypot(secondX - firstX, secondY - firstY);
    }

    /** 화면 좌표 기준의 손가락 방향을 도 단위로 반환한다. */
    private static float angle(float firstX, float firstY, float secondX, float secondY) {
        return (float) Math.toDegrees(Math.atan2(secondY - firstY, secondX - firstX));
    }

    /** 반대 방향 경계를 통과해도 같은 방향을 나타내는 각도로 정규화한다. */
    private static float normalize(float degrees) {
        return (degrees % 360 + 540) % 360 - 180;
    }

    /** 지도 SDK에 적용할 화면 중심·배율·방향을 담는다. */
    record Change(float centerX, float centerY, float zoom, float bearing) {
    }
}
