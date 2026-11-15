package com.example.pleosstarter.records;

import com.example.pleosstarter.speech.PleosSpeechClient;

import java.util.Objects;

/** 기존 Pleos 온디바이스 인식기를 메모 입력 계약으로 연결한다. */
public final class PleosMemoSpeechInput implements MemoSpeechInput {
    private final PleosSpeechClient speechClient;

    /** 여러 메모 대화상자가 공유하며 소유 화면이 최종 종료할 인식 클라이언트를 주입한다. */
    public PleosMemoSpeechInput(PleosSpeechClient speechClient) {
        this.speechClient = Objects.requireNonNull(speechClient, "음성 인식 클라이언트가 필요합니다.");
    }

    /** 확정 텍스트와 기존 클라이언트의 안전한 오류 안내만 전달한다. */
    @Override
    public void start(Callback callback) {
        Objects.requireNonNull(callback, "음성 인식 결과 수신자가 필요합니다.");
        speechClient.start(callback::onText, message -> callback.onFailure(
                new MapRecordFailure(MapRecordFailure.Code.SPEECH_FAILED, message)));
    }

    /** 현재 인식만 취소하고 다시 입력할 수 있도록 유지한다. */
    @Override
    public void cancel() {
        speechClient.cancel();
    }

    /** 소유 화면이 종료될 때 음성 서비스와 마이크 사용을 끝낸다. */
    @Override
    public void close() {
        speechClient.close();
    }
}
