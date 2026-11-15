package com.example.pleosstarter.kakao;

/** 지도 보기 버튼이 선택하는 평면·입체 시점의 카메라 기울기를 정의한다. */
public enum MapPerspective {
    TWO_DIMENSIONAL(0.0f),
    THREE_DIMENSIONAL(50.0f);

    private final float tiltDegrees;

    /** SDK가 지원하는 기울기를 각 보기 모드에 연결한다. */
    MapPerspective(float tiltDegrees) {
        this.tiltDegrees = tiltDegrees;
    }

    /** 지도 카메라에 적용할 기울기를 도 단위로 반환한다. */
    public float getTiltDegrees() {
        return tiltDegrees;
    }
}
