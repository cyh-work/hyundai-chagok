package com.example.pleosstarter.location;

import java.util.Objects;

/** 인증정보나 SDK 내부 예외를 노출하지 않는 위치 조회 실패 결과다. */
public final class LocationFailure {
    private final LocationError code;
    private final String message;

    /** 오류 코드와 사용자 안내를 저장한다. */
    public LocationFailure(LocationError code, String message) {
        this.code = Objects.requireNonNull(code, "code");
        this.message = Objects.requireNonNull(message, "message");
    }

    /** 실패 원인 코드를 반환한다. */
    public LocationError getCode() {
        return code;
    }

    /** 내부 상세를 제외한 사용자 안내를 반환한다. */
    public String getMessage() {
        return message;
    }
}
