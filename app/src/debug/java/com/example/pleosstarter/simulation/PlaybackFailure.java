package com.example.pleosstarter.simulation;

/** 경로 준비 실패 종류와 화면에 안전하게 표시할 설명을 보관한다. */
public final class PlaybackFailure {
    /** 외부 경로 조회 실패와 사용할 수 없는 좌표를 구분한다. */
    public enum Code {
        ROUTE_REQUEST_FAILED,
        ROUTE_UNAVAILABLE,
        ROUTE_TIMEOUT
    }

    private final Code code;
    private final String message;

    /** 인증정보나 SDK 내부 메시지 없이 실패 결과를 구성한다. */
    public PlaybackFailure(Code code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 호출자가 실패 흐름을 구분할 수 있는 코드를 반환한다. */
    public Code getCode() {
        return code;
    }

    /** 화면에 표시할 안전한 설명을 반환한다. */
    public String getMessage() {
        return message;
    }
}
