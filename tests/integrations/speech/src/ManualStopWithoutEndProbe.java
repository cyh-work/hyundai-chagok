import android.content.Context;
import android.os.Handler;
import ai.pleos.playground.stt.SpeechToText;
import com.example.pleosstarter.speech.PleosSpeechClient;

import java.util.ArrayList;
import java.util.List;

/** 실제 수동 stop에서 Ended가 오지 않아 정상 재시도가 막히는 회귀를 재현한다. */
public final class ManualStopWithoutEndProbe {
    /** Ready 이후 수동 stop이 실제 녹음을 끝내도 재시도가 이어지는지 검사한다. */
    public static void main(String[] arguments) {
        Handler.reset();
        SpeechToText.INSTANCES.clear();
        List<String> errors = new ArrayList<>();
        PleosSpeechClient client = new PleosSpeechClient(new Context());
        client.start(text -> {
            throw new AssertionError("Canceled request returned text");
        }, errors::add);
        Handler.drain();
        SpeechToText sdk = SpeechToText.INSTANCES.get(0);
        sdk.listener.onReady();
        Handler.drain();
        client.cancel();
        client.start(text -> {
            throw new AssertionError("Unexpected text");
        }, errors::add);
        sdk.completeStop(false);
        Handler.advance(1_000);
        if (SpeechToText.INSTANCES.size() != 2 || !errors.isEmpty()) {
            throw new AssertionError("Successful manual stop without Ended blocks retry");
        }
        client.cancel();
        SpeechToText.INSTANCES.get(1).completeStop();
        Handler.drain();
        if (Handler.pendingCount() != 0) {
            throw new AssertionError("Shutdown leaked scheduled tasks");
        }
        System.out.println("PASS manual stop without Ended permits serialized retry after settle");
    }
}
