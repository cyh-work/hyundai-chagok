package com.example.pleosstarter.stores;

/** 인증 원문과 응답 본문을 제외한 안전한 조회 실패 정보를 보관한다. */
public final class StoreSearchFailure {
    private final StoreSearchError code;
    private final String message;
    private final int httpStatus;

    /** HTTP 응답이 없으면 상태값 0으로 실패를 구성한다. */
    public StoreSearchFailure(StoreSearchError code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    /** 기계적으로 구분할 오류 종류를 반환한다. */
    public StoreSearchError getCode() {
        return code;
    }

    /** 화면에 표시해도 되는 안내를 반환한다. */
    public String getMessage() {
        return message;
    }

    /** 외부 호출에서 실제 수신한 HTTP 상태를 반환한다. */
    public int getHttpStatus() {
        return httpStatus;
    }
}
