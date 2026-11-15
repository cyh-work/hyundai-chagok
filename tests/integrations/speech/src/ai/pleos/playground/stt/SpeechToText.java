package ai.pleos.playground.stt;

import android.content.Context;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.listener.OnInitializeListener;
import ai.pleos.playground.stt.listener.ResultListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 실제 SDK의 비동기 stop과 즉시 연결 해제 경쟁만 재현하는 외부 경계 대역이다. */
public final class SpeechToText {
    public static final List<SpeechToText> INSTANCES = new ArrayList<>();
    public ResultListener listener;
    public boolean recording;
    public boolean released;
    public int stopCalls;
    public int releases;
    public boolean stopThrows;
    private boolean stopQueued;

    /** SDK 인스턴스를 기록해 세션 직렬화를 검증한다. */
    public SpeechToText(Context context, Mode mode) {
        INSTANCES.add(this);
    }

    /** 연결 성공 콜백만 반환하며 실제 클라이언트가 이를 메인 큐로 전달한다. */
    public void initialize(OnInitializeListener callback) {
        callback.onSuccess();
    }

    /** 언어 설정은 이 생명주기 검사에서 부작용이 없다. */
    public void setLocale(Locale locale) {
    }

    /** 인식 결과 수신자를 보관한다. */
    public void addListener(ResultListener callback) {
        listener = callback;
    }

    /** 등록된 동일한 수신자를 해제한다. */
    public void removeListener(ResultListener callback) {
        if (listener == callback) {
            listener = null;
        }
    }

    /** 인식이 시작된 상태를 만든다. */
    public void request() {
        recording = true;
    }

    /** 실제 SDK처럼 원격 stop은 나중에 실행하도록 예약만 한다. */
    public void stop() {
        stopCalls++;
        if (stopThrows) {
            throw new IllegalStateException("Synthetic stop invocation failure");
        }
        stopQueued = true;
    }

    /** SDK 참조 해제가 비동기 stop보다 먼저면 stop을 전달할 수 없게 된다. */
    public void release() {
        released = true;
        releases++;
    }

    /** 예약된 stop이 연결된 서비스에 도달했을 때만 마이크 종료 콜백을 보낸다. */
    public void completeStop() {
        completeStop(true);
    }

    /** 실제 에뮬레이터처럼 녹음은 끝나지만 Ended 이벤트가 없는 수동 stop도 재현한다. */
    public void completeStop(boolean sendEnded) {
        if (!stopQueued || released) {
            return;
        }
        stopQueued = false;
        recording = false;
        if (sendEnded && listener != null) {
            listener.onEndedRecognition();
        }
    }
}
