package com.example.pleosstarter.map;

/** 인식한 도형과 선택 장소를 전달하며 인식 실패 시 장소는 null이다. */
public record MapGesture(Type type, MapPlace place) {
    /** 지도 입력의 의미를 구분한다. */
    public enum Type {
        STAR,
        CIRCLE,
        LINE,
        UNRECOGNIZED
    }
}
