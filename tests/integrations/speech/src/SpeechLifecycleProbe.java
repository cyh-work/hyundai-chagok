import android.content.Context;
import android.os.Handler;
import android.util.Log;
import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.listener.ResultListener;
import com.example.pleosstarter.speech.PleosSpeechClient;

import java.util.ArrayList;
import java.util.List;

/** 실제 클라이언트의 취소·완료·오류·종료 경계를 지연 SDK와 가상 시간으로 검사한다. */
public final class SpeechLifecycleProbe {
    private static int checks;

    /** 외부 인식 대역으로 생명주기 경쟁과 타이머 정리를 순서대로 실행한다. */
    public static void main(String[] arguments) {
        cancelBeforeInitializeCallback();
        cancelAfterRequestAndLateStart();
        serializeRetryAndRejectOldEvents();
        cancelQueuedRetry();
        closeDuringShutdown();
        normalFinalResult();
        endedBeforeFinalResult();
        endedWithoutFinalResult();
        recognitionError();
        recognitionTimeout();
        shutdownDeadline();
        delayedReadyBeforeQuietSettle();
        repeatedReadyDoesNotWaitForever();
        synchronousStopFailure();
        check(Log.MESSAGES.stream().noneMatch(message -> message.contains("synthetic transcript")),
                "transcript is not logged");
        System.out.println("PASS " + checks + " lifecycle checks using real PleosSpeechClient and delayed SDK boundary");
    }

    /** 연결 완료의 지연 콜백보다 빠른 취소가 마이크 요청으로 이어지지 않는지 확인한다. */
    private static void cancelBeforeInitializeCallback() {
        Fixture fixture = new Fixture(false);
        fixture.client.cancel();
        Handler.drain();
        check(fixture.sdk().released && !fixture.sdk().recording, "pre-request cancel releases without recording");
        fixture.assertNoResults();
        clean();
    }

    /** 늦은 준비·시작이 stop을 재요청하고 늦은 텍스트는 무시되는지 확인한다. */
    private static void cancelAfterRequestAndLateStart() {
        Fixture fixture = new Fixture(true);
        ResultListener old = fixture.sdk().listener;
        fixture.client.cancel();
        check(!fixture.sdk().released && fixture.sdk().stopCalls == 1, "cancel retains connection for stop");
        old.onReady();
        old.onStartedRecognition();
        old.onUpdated("synthetic transcript", true);
        Handler.drain();
        check(fixture.sdk().stopCalls == 3, "late ready and start request stop again");
        fixture.sdk().completeStop();
        Handler.drain();
        check(!fixture.sdk().recording && fixture.sdk().releases == 1, "acknowledged stop releases exactly once");
        old.onEndedRecognition();
        Handler.drain();
        check(fixture.sdk().releases == 1, "duplicate end cannot release twice");
        fixture.assertNoResults();
        clean();
    }

    /** 재시도는 이전 마이크 종료 이후에 시작하고 이전 이벤트는 새 요청을 멈추지 않는다. */
    private static void serializeRetryAndRejectOldEvents() {
        Fixture fixture = new Fixture(true);
        SpeechToText previous = fixture.sdk();
        ResultListener old = previous.listener;
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        check(SpeechToText.INSTANCES.size() == 1, "retry waits for old microphone shutdown");
        previous.completeStop();
        Handler.drain();
        check(SpeechToText.INSTANCES.size() == 2 && fixture.sdk().recording, "retry starts after acknowledgement");
        int oldStopCalls = previous.stopCalls;
        old.onReady();
        old.onStartedRecognition();
        old.onUpdated("synthetic transcript", true);
        Handler.drain();
        check(previous.stopCalls == oldStopCalls && fixture.sdk().stopCalls == 0,
                "old events cannot stop new session");
        fixture.client.cancel();
        fixture.sdk().completeStop();
        Handler.drain();
        fixture.assertNoResults();
        clean();
    }

    /** 재시도 대기 중 다시 취소하면 새 요청을 생성하지 않는다. */
    private static void cancelQueuedRetry() {
        Fixture fixture = new Fixture(true);
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        fixture.client.cancel();
        fixture.sdk().completeStop();
        Handler.drain();
        check(SpeechToText.INSTANCES.size() == 1, "cancel clears queued retry");
        fixture.assertNoResults();
        clean();
    }

    /** 화면 종료도 비동기 stop을 기다리며 대기 요청과 이후 시작을 차단한다. */
    private static void closeDuringShutdown() {
        Fixture fixture = new Fixture(true);
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        fixture.client.close();
        check(!fixture.sdk().released, "close does not disconnect before stop");
        fixture.sdk().completeStop();
        Handler.drain();
        check(SpeechToText.INSTANCES.size() == 1 && fixture.sdk().released, "close removes pending retry");
        fixture.client.start(fixture.results::add, fixture.errors::add);
        check(fixture.errors.size() == 1 && fixture.results.isEmpty(), "closed client rejects start");
        clean();
    }

    /** 확정 결과를 한 번 전달하되 마이크 종료 확인 이전에 서비스는 해제하지 않는다. */
    private static void normalFinalResult() {
        Fixture fixture = new Fixture(true);
        fixture.sdk().listener.onUpdated("synthetic transcript", true);
        Handler.drain();
        check(fixture.results.size() == 1 && !fixture.sdk().released, "final result delivered while stop awaits acknowledgement");
        fixture.sdk().completeStop();
        Handler.drain();
        check(fixture.sdk().released && !fixture.sdk().recording, "final result shutdown confirmed");
        clean();
    }

    /** 종료 뒤 지연된 최종 문장도 기존 유예시간 안에서 한 번 전달한다. */
    private static void endedBeforeFinalResult() {
        Fixture fixture = new Fixture(true);
        ResultListener callback = fixture.sdk().listener;
        fixture.sdk().recording = false;
        callback.onEndedRecognition();
        Handler.drain();
        Handler.advance(500);
        callback.onUpdated("synthetic transcript", true);
        Handler.drain();
        check(fixture.results.size() == 1 && fixture.sdk().released, "late final result after ended is retained");
        check(fixture.sdk().stopCalls == 0, "already ended session does not stop a later stream");
        clean();
    }

    /** 인식 종료 뒤 결과가 없으면 유예시간 후 오류와 정리가 한 번 발생한다. */
    private static void endedWithoutFinalResult() {
        Fixture fixture = new Fixture(true);
        fixture.sdk().recording = false;
        fixture.sdk().listener.onEndedRecognition();
        Handler.advance(750);
        check(fixture.errors.size() == 1 && fixture.sdk().released, "empty ended session fails once");
        clean();
    }

    /** SDK 오류도 stop과 연결 해제를 같은 순서로 처리한다. */
    private static void recognitionError() {
        Fixture fixture = new Fixture(true);
        fixture.sdk().listener.onError(ErrorType.MIC_ERROR);
        Handler.drain();
        check(fixture.errors.size() == 1 && !fixture.sdk().released, "error requests asynchronous shutdown");
        fixture.sdk().completeStop();
        Handler.drain();
        check(fixture.sdk().released, "error shutdown completes on ended");
        clean();
    }

    /** 인식 제한시간도 마이크 중지 확인을 기다리고 결과 타이머를 제거한다. */
    private static void recognitionTimeout() {
        Fixture fixture = new Fixture(true);
        Handler.advance(20_000);
        check(fixture.errors.size() == 1 && !fixture.sdk().released, "recognition timeout requests stop");
        fixture.sdk().completeStop();
        Handler.drain();
        check(fixture.sdk().released, "recognition timeout cleans up after acknowledgement");
        clean();
    }

    /** 준비 이벤트가 없는 수동 stop은 한정 시간 뒤 연결만 해제하고 콜백 부재를 오류로 만들지 않는다. */
    private static void shutdownDeadline() {
        Fixture fixture = new Fixture(true);
        SpeechToText previous = fixture.sdk();
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        previous.completeStop(false);
        Handler.advance(4_000);
        check(previous.stopCalls == 8, "bounded preparation stop retries");
        check(previous.released && previous.releases == 1, "no-ready connection released once at bound");
        check(fixture.errors.isEmpty() && SpeechToText.INSTANCES.size() == 2,
                "missing manual-stop callback does not block retry");
        check(Log.MESSAGES.stream().anyMatch(message -> message.contains("reason=PREPARE_WAIT_LIMIT")),
                "preparation limit is not reported as microphone confirmation");
        fixture.client.cancel();
        fixture.sdk().completeStop();
        Handler.drain();
        clean();
    }

    /** 준비에 오래 걸리는 취소는 첫 1초에 해제하지 않고 Ready·Started 뒤 정리 유예를 확보한다. */
    private static void delayedReadyBeforeQuietSettle() {
        Fixture fixture = new Fixture(true);
        SpeechToText previous = fixture.sdk();
        ResultListener callback = previous.listener;
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        Handler.advance(1_310);
        check(!previous.released && SpeechToText.INSTANCES.size() == 1,
                "no premature one-second release before delayed ready");
        callback.onReady();
        Handler.drain();
        previous.completeStop(false);
        Handler.advance(900);
        callback.onStartedRecognition();
        Handler.drain();
        Handler.advance(100);
        check(!previous.released, "late started resets quiet settle");
        Handler.advance(900);
        check(previous.released && SpeechToText.INSTANCES.size() == 2 && fixture.errors.isEmpty(),
                "retry begins after ready and started settle without Ended");
        check(Log.MESSAGES.stream().anyMatch(message -> message.contains("reason=STOP_SETTLED_WITHOUT_END")),
                "quiet settle explicitly records absent Ended callback");
        fixture.client.cancel();
        fixture.sdk().completeStop();
        Handler.drain();
        clean();

        Fixture nearLimit = new Fixture(true);
        nearLimit.client.cancel();
        Handler.advance(3_900);
        nearLimit.sdk().listener.onReady();
        Handler.drain();
        nearLimit.sdk().completeStop(false);
        Handler.advance(100);
        check(!nearLimit.sdk().released, "ready near preparation limit receives full settle window");
        Handler.advance(900);
        check(nearLimit.sdk().released, "near-limit ready settles without permanent wait");
        clean();
    }

    /** 반복되는 준비 이벤트가 유예를 계속 미뤄도 단계별 상한에서 정리를 끝낸다. */
    private static void repeatedReadyDoesNotWaitForever() {
        Fixture fixture = new Fixture(true);
        ResultListener callback = fixture.sdk().listener;
        callback.onReady();
        Handler.drain();
        fixture.client.cancel();
        fixture.sdk().completeStop(false);
        for (int iteration = 0; iteration < 7; iteration++) {
            Handler.advance(500);
            callback.onReady();
            Handler.drain();
        }
        Handler.advance(500);
        check(fixture.sdk().released, "repeated ready stops at fixed cleanup bound");
        check(Log.MESSAGES.stream().anyMatch(message -> message.contains("reason=STOP_WAIT_LIMIT")),
                "cleanup bound is logged distinctly");
        clean();
    }

    /** 실제 SDK stop 호출 자체가 예외를 내면 대기 재시도에 오류를 전달한다. */
    private static void synchronousStopFailure() {
        Fixture fixture = new Fixture(true);
        fixture.sdk().listener.onReady();
        Handler.drain();
        fixture.sdk().stopThrows = true;
        fixture.client.cancel();
        fixture.client.start(fixture.results::add, fixture.errors::add);
        Handler.advance(1_000);
        check(fixture.errors.size() == 1 && SpeechToText.INSTANCES.size() == 1,
                "stop invocation failure rejects queued retry");
        clean();
    }

    /** 정상·실패 종료 뒤 지연 작업이 남지 않는지 확인한다. */
    private static void clean() {
        check(Handler.pendingCount() == 0, "no scheduled tasks leaked");
    }

    /** 검증 문구에는 원문을 포함하지 않는다. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    /** 시나리오별 실제 클라이언트와 결과 수신자를 제공한다. */
    private static final class Fixture {
        private final List<String> results = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();
        private final PleosSpeechClient client;

        /** 새 클라이언트의 시작만 실행하고 필요하면 연결 완료 큐를 처리한다. */
        private Fixture(boolean initialize) {
            Handler.reset();
            SpeechToText.INSTANCES.clear();
            client = new PleosSpeechClient(new Context());
            client.start(results::add, errors::add);
            if (initialize) {
                Handler.drain();
            }
        }

        /** 가장 최근 생성한 외부 SDK 대역을 반환한다. */
        private SpeechToText sdk() {
            return SpeechToText.INSTANCES.get(SpeechToText.INSTANCES.size() - 1);
        }

        /** 취소한 요청이 성공이나 사용자 오류를 뒤늦게 전달하지 않는지 확인한다. */
        private void assertNoResults() {
            check(results.isEmpty() && errors.isEmpty(), "canceled results are suppressed");
        }
    }
}
