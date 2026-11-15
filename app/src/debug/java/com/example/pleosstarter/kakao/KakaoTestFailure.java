package com.example.pleosstarter.kakao;

/** 인증 원문이나 내부 예외 대신 화면에 전달할 실패 정보만 보관한다. */
public final class KakaoTestFailure {
    private final KakaoTestError code;
    private final String message;

    /** 정해진 오류 코드와 사용자 안내를 함께 구성한다. */
    public KakaoTestFailure(KakaoTestError code, String message) {
        this.code = code;
        this.message = message;
    }

    /** 검증 로그와 상태 판정에 사용할 오류 코드를 반환한다. */
    public KakaoTestError getCode() {
        return code;
    }

    /** 인증정보가 없는 사용자 안내를 반환한다. */
    public String getMessage() {
        return message;
    }
}
