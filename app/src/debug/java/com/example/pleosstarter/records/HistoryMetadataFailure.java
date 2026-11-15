package com.example.pleosstarter.records;

/** 본 기록 저장과 별개인 히스토리 시각 복원·저장 실패를 구분한다. */
public final class HistoryMetadataFailure extends RuntimeException {
    /** 시각 메타데이터의 읽기 실패와 쓰기 실패를 구분한다. */
    public enum Code {
        RESTORE_FAILED,
        WRITE_FAILED
    }

    private final Code code;

    /** 본 기록 성공을 취소하지 않는 별도 오류를 구성한다. */
    public HistoryMetadataFailure(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /** 시각 관련 진단을 본 저장 오류와 구분할 코드를 반환한다. */
    public Code getCode() {
        return code;
    }
}
