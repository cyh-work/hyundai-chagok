package com.example.pleosstarter.records;

/** 메모 화면이 음성 SDK와 무관하게 인식 시작·취소·종료를 요청하는 계약이다. */
public interface MemoSpeechInput {
    /** 확정한 텍스트 또는 인식 실패 하나를 메인 스레드에서 전달한다. */
    interface Callback {
        /** 사용자가 검토할 확정 인식 문장을 전달한다. */
        void onText(String text);

        /** 직접 입력과 재시도를 유지하도록 실제 인식 실패를 전달한다. */
        void onFailure(MapRecordFailure failure);
    }

    /** 새 인식을 시작하며 결과가 저장되었다는 의미를 갖지 않는다. */
    void start(Callback callback);

    /** 진행 중인 인식을 결과 없이 취소하며 이후 재시작할 수 있다. */
    void cancel();

    /** 인식 자원을 종료하며 이 인스턴스는 다시 시작하지 않는다. */
    void close();
}
