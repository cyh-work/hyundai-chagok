package com.example.pleosstarter.kakao;

/** 잡은 손가락과 지도 기준점의 간격을 보존하고 미세한 흔들림은 저장에서 제외한다. */
final class MapRecordDragAnchor {
    private final float pointerStartX;
    private final float pointerStartY;
    private final float offsetX;
    private final float offsetY;
    private final float touchSlop;

    /** 현재 지도 투영 기준점과 실제 누른 위치의 차이를 한 번만 보관한다. */
    MapRecordDragAnchor(float anchorX, float anchorY, float pointerX, float pointerY, float touchSlop) {
        if (!Float.isFinite(anchorX) || !Float.isFinite(anchorY) || !Float.isFinite(pointerX)
                || !Float.isFinite(pointerY) || !Float.isFinite(touchSlop) || touchSlop < 0) {
            throw new IllegalArgumentException("기록을 옮길 위치를 확인해 주세요.");
        }
        pointerStartX = pointerX;
        pointerStartY = pointerY;
        offsetX = pointerX - anchorX;
        offsetY = pointerY - anchorY;
        this.touchSlop = touchSlop;
    }

    /** 포인터를 아이콘 중앙에 강제로 맞추지 않고 원래 수평 간격을 유지한다. */
    float anchorX(float pointerX) {
        return pointerX - offsetX;
    }

    /** 화면 위로 들어 올린 표시와 무관하게 저장 기준점의 수직 간격을 유지한다. */
    float anchorY(float pointerY) {
        return pointerY - offsetY;
    }

    /** 처음 잡은 곳으로 돌아온 경우와 길게 누르기 중 작은 흔들림은 쓰기를 만들지 않는다. */
    boolean hasMoved(float pointerX, float pointerY) {
        return Math.hypot(pointerX - pointerStartX, pointerY - pointerStartY) > touchSlop;
    }
}
