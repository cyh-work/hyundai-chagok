package com.example.pleosstarter.notes;

/** 위치 기록의 입력·복원·저장 실패를 호출 화면에 구분해 전달한다. */
public final class LocationNotesException extends RuntimeException {
    private final Code code;

    /** 사용자에게 안내 가능한 실패 종류를 정의한다. */
    public enum Code {
        INVALID_INPUT,
        RESTORE_FAILED,
        WRITE_FAILED
    }

    /** 내부 원인 없이 검증 실패와 표시 가능한 설명을 전달한다. */
    public LocationNotesException(Code code, String message) {
        super(message);
        this.code = code;
    }

    /** 내부 원인은 진단용으로 유지하고 화면 설명과 분리한다. */
    public LocationNotesException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 호출 화면이 오류 처리 흐름을 구분할 수 있도록 코드를 반환한다. */
    public Code getCode() {
        return code;
    }
}
