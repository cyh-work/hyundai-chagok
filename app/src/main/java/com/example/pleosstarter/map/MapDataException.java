package com.example.pleosstarter.map;

/** 번들 지도를 읽거나 검증하지 못한 오류를 화면 경계에 전달한다. */
final class MapDataException extends Exception {
    /** 내부 원인은 로그에서 확인하고 화면에는 고정 안내를 표시하도록 전달한다. */
    MapDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
