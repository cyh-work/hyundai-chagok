package com.example.pleosstarter.records;

/** 위치 기록의 입력·복원·저장·음성 실패를 화면에 구분해 전달한다. */
public final class MapRecordFailure extends RuntimeException {
    /** 기록 흐름에서 사용자에게 안내할 실패 종류다. */
    public enum Code {
        INVALID_INPUT,
        RESTORE_FAILED,
        WRITE_FAILED,
        LIMIT_REACHED,
        SPEECH_FAILED
    }

    private final Code code;

    /** 내부 원문 없이 사용자 안내와 오류 종류를 보관한다. */
    public MapRecordFailure(Code code, String message) {
        super(message);
        this.code = code;
    }

    /** 진단 원인을 표시 가능한 설명과 분리해 보관한다. */
    public MapRecordFailure(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 호출자가 오류 흐름을 선택할 수 있도록 종류를 반환한다. */
    public Code getCode() {
        return code;
    }
}
