import android.content.Context;
import android.os.Handler;
import ai.pleos.playground.stt.SpeechToText;
import com.example.pleosstarter.speech.PleosSpeechClient;

/** 변경 전 실제 클라이언트의 비동기 stop 누락을 재현하는 최소 검사다. */
public final class SpeechCancellationProbe {
    /** 시작 직후 취소와 지연 stop의 순서를 실제 클라이언트에 적용한다. */
    public static void main(String[] arguments) {
        Handler.reset();
        SpeechToText.INSTANCES.clear();
        PleosSpeechClient client = new PleosSpeechClient(new Context());
        client.start(text -> {
            throw new AssertionError("Canceled session returned text");
        }, message -> {
            throw new AssertionError("Canceled session returned user error");
        });
        Handler.drain();
        SpeechToText sdk = SpeechToText.INSTANCES.get(0);
        client.cancel();
        sdk.completeStop();
        Handler.drain();
        if (sdk.recording) {
            throw new AssertionError("Microphone remains active: release ran before asynchronous stop");
        }
        if (!sdk.released || Handler.pendingCount() != 0) {
            throw new AssertionError("Ended session resources were not fully released");
        }
        System.out.println("PASS cancellation waits for asynchronous stop acknowledgement");
    }
}
