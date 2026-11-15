package com.example.pleosstarter.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Locale;
import java.util.function.Consumer;

import ai.pleos.playground.stt.SpeechToText;
import ai.pleos.playground.stt.constant.ErrorType;
import ai.pleos.playground.stt.constant.Mode;
import ai.pleos.playground.stt.listener.OnInitializeListener;
import ai.pleos.playground.stt.listener.ResultListener;

/** 차량 마이크의 한 문장을 Pleos 온디바이스 음성 인식으로 변환한다. */
public final class PleosSpeechClient {
    private static final String TAG = "PleosSpeech";
    private static final long SESSION_TIMEOUT_MS = 20_000L;
    private static final long FINAL_RESULT_GRACE_MS = 750L;
    private static final long STOP_RETRY_MILLIS = 500L;
    private static final long STOP_SETTLE_MILLIS = 1_000L;
    private static final long STOP_STAGE_TIMEOUT_MILLIS = 4_000L;
    private static final String NO_SPEECH = "저장할 음성을 인식하지 못했습니다.";
    private static final String SESSION_TIMEOUT = "음성 인식 시간이 초과됐습니다.";
    private static final String SERVICE_ERROR = "Pleos 음성 인식에 연결할 수 없습니다.";
    private static final String STOP_ERROR = "음성 입력 중지 요청을 전달하지 못했습니다. 잠시 후 다시 시도해 주세요.";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private RecognitionSession active;
    private PendingRecognition pending;
    private boolean closed;
    private int nextSessionId;

    /** 차량 음성 서비스 연결에 사용할 애플리케이션 컨텍스트를 보관한다. */
    public PleosSpeechClient(Context context) {
        this.context = context.getApplicationContext();
    }

    /** 음성 인식을 시작하고 확정 텍스트 또는 오류를 메인 스레드에 한 번 전달한다. */
    public void start(Consumer<String> onText, Consumer<String> onError) {
        runOnMain(() -> {
            if (closed) {
                onError.accept(SERVICE_ERROR);
                return;
            }
            if (active != null) {
                if (active.stopping && pending == null) {
                    pending = new PendingRecognition(onText, onError);
                } else {
                    onError.accept("이미 음성 인식 중입니다.");
                }
                return;
            }

            startSession(onText, onError);
        });
    }

    /** 화면을 떠날 때 진행 중인 인식을 결과 없이 취소한다. 다시 시작할 수 있다. */
    public void cancel() {
        runOnMain(() -> {
            pending = null;
            if (active != null) {
                active.beginRelease();
            }
        });
    }

    /** 화면이 파괴될 때 인식과 서비스 연결을 종료한다. */
    public void close() {
        runOnMain(() -> {
            closed = true;
            pending = null;
            if (active != null) {
                active.beginRelease();
            }
        });
    }

    /** 이전 세션의 정리가 끝난 상태에서만 새 SDK 요청을 생성한다. */
    private void startSession(Consumer<String> onText, Consumer<String> onError) {
        RecognitionSession session = new RecognitionSession(onText, onError, ++nextSessionId);
        active = session;
        session.initialize();
    }

    /** 종료 콜백 또는 준비 후 유예가 끝나면 재시도하며 실제 stop 호출 실패만 오류로 전달한다. */
    private void onSessionReleased(RecognitionSession session, boolean stopRequestAccepted) {
        if (active != session) {
            return;
        }
        active = null;
        PendingRecognition next = pending;
        pending = null;
        if (next == null || closed) {
            return;
        }
        if (stopRequestAccepted) {
            startSession(next.onText, next.onError);
        } else {
            next.onError.accept(STOP_ERROR);
        }
    }

    /** 녹음 종료 확정과 콜백 없는 SDK 연결 정리의 근거를 구분한다. */
    private enum ReleaseReason {
        NEVER_REQUESTED,
        ENDED_CALLBACK,
        STOP_SETTLED_WITHOUT_END,
        PREPARE_WAIT_LIMIT,
        STOP_WAIT_LIMIT
    }

    /** 비동기 종료 중 들어온 한 번의 재시도만 보관한다. */
    private static final class PendingRecognition {
        private final Consumer<String> onText;
        private final Consumer<String> onError;

        /** 종료된 이전 요청과 분리한 다음 요청의 결과 수신자를 보관한다. */
        private PendingRecognition(Consumer<String> onText, Consumer<String> onError) {
            this.onText = onText;
            this.onError = onError;
        }
    }

    /** 호출자가 메인 스레드에 있을 때 즉시 실행하고, 다른 경우 메인으로 전달한다. */
    private void runOnMain(Runnable operation) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            operation.run();
        } else {
            mainHandler.post(operation);
        }
    }

    /** 하나의 음성 요청에 속한 서비스, 콜백, 종료 상태를 관리한다. */
    private final class RecognitionSession implements OnInitializeListener, ResultListener {
        private final Consumer<String> onText;
        private final Consumer<String> onError;
        private final int sessionId;
        private final Runnable timeout = () -> fail(SESSION_TIMEOUT);
        private final Runnable emptyResult = () -> fail(NO_SPEECH);
        private final Runnable retryStop = this::retryStop;
        private final Runnable stopDeadline = this::onStopDeadline;
        private final Runnable settledRelease = this::onStopSettled;
        private SpeechToText sdk;
        private boolean listenerAdded;
        private boolean microphoneRequested;
        private boolean recognitionEnded;
        private boolean recognitionReady;
        private boolean stopRequestAccepted;
        private boolean stopping;
        private boolean released;

        /** 이 요청의 최종 인식 결과 또는 오류 수신자를 보관한다. */
        private RecognitionSession(Consumer<String> onText, Consumer<String> onError, int sessionId) {
            this.onText = onText;
            this.onError = onError;
            this.sessionId = sessionId;
        }

        /** 계정 등록 없이 사용하는 온디바이스 서비스를 연결한다. */
        private void initialize() {
            mainHandler.postDelayed(timeout, SESSION_TIMEOUT_MS);
            try {
                sdk = new SpeechToText(context, Mode.ON_DEVICE);
                sdk.initialize(this);
            } catch (RuntimeException exception) {
                Log.w(TAG, "initialize failed: " + exception.getClass().getSimpleName());
                fail(SERVICE_ERROR);
            }
        }

        /** 서비스 연결이 끝나면 결과 리스너를 등록하고 한국어 인식을 시작한다. */
        @Override
        public void onSuccess() {
            mainHandler.post(() -> {
                if (active != this || microphoneRequested || stopping || released) {
                    return;
                }

                try {
                    sdk.setLocale(Locale.KOREA);
                    sdk.addListener(this);
                    listenerAdded = true;
                    microphoneRequested = true;
                    Log.i(TAG, "SESSION_REQUEST id=" + sessionId);
                    sdk.request();
                } catch (RuntimeException exception) {
                    Log.w(TAG, "request failed: " + exception.getClass().getSimpleName());
                    fail(SERVICE_ERROR);
                }
            });
        }

        /** 연결 실패를 인식 요청의 종료 오류로 전달한다. */
        @Override
        public void onFailure(ErrorType error) {
            onError(error);
        }

        /** 확정된 비어 있지 않은 결과만 저장 화면에 전달한다. */
        @Override
        public void onUpdated(String transcript, boolean completed) {
            if (!completed || transcript == null) {
                return;
            }

            mainHandler.post(() -> {
                if (active != this || stopping || released) {
                    return;
                }

                String text = transcript.trim();
                if (text.isEmpty()) {
                    fail(NO_SPEECH);
                    return;
                }
                recognitionReady = true;
                beginRelease();
                onText.accept(text);
            });
        }

        /** 인식 종료 후 도착할 수 있는 확정 결과를 짧게 기다린다. */
        @Override
        public void onEndedRecognition() {
            mainHandler.post(() -> {
                if (released) {
                    return;
                }
                recognitionEnded = true;
                Log.i(TAG, "SESSION_ENDED id=" + sessionId);
                if (stopping) {
                    finishRelease(ReleaseReason.ENDED_CALLBACK);
                } else if (active == this) {
                    mainHandler.removeCallbacks(emptyResult);
                    mainHandler.postDelayed(emptyResult, FINAL_RESULT_GRACE_MS);
                }
            });
        }

        /** SDK 오류 코드만 기록하고, 원문 정보 없이 사용자 안내를 전달한다. */
        @Override
        public void onError(ErrorType error) {
            mainHandler.post(() -> {
                if (active != this || released) {
                    return;
                }
                Log.w(TAG, "recognition error: " + error);
                if (!stopping) {
                    fail(errorMessage(error));
                }
            });
        }

        /** 취소 뒤 늦게 준비된 인식도 연결을 유지한 상태에서 다시 중지한다. */
        @Override
        public void onReady() {
            handleReadyOrStarted("READY");
        }

        /** 취소와 경합하여 시작된 마이크는 결과 없이 다시 중지한다. */
        @Override
        public void onStartedRecognition() {
            handleReadyOrStarted("STARTED");
        }

        /** 발화 구간 이벤트는 이 메모의 저장 입력으로 사용하지 않는다. */
        @Override
        public void onUpdatedEpdData(long startOffset, long endOffset) {
        }

        /** 입력 음량 이벤트는 이 메모의 저장 입력으로 사용하지 않는다. */
        @Override
        public void onUpdatedRms(double rms) {
        }

        /** 결과를 받지 못한 세션을 닫고 해당 오류를 한 번 전달한다. */
        private void fail(String message) {
            if (active != this || stopping || released) {
                return;
            }
            beginRelease();
            onError.accept(message);
        }

        /** 결과를 차단하되 준비 전 취소는 준비 이벤트를 기다리고 준비 후에는 정리 유예를 둔다. */
        private void beginRelease() {
            if (stopping || released) {
                return;
            }
            mainHandler.removeCallbacks(timeout);
            mainHandler.removeCallbacks(emptyResult);
            if (!microphoneRequested || recognitionEnded || sdk == null) {
                finishRelease(recognitionEnded ? ReleaseReason.ENDED_CALLBACK : ReleaseReason.NEVER_REQUESTED);
                return;
            }

            stopping = true;
            Log.i(TAG, "SESSION_STOP_REQUESTED id=" + sessionId);
            requestStop();
            mainHandler.postDelayed(retryStop, STOP_RETRY_MILLIS);
            mainHandler.postDelayed(stopDeadline, STOP_STAGE_TIMEOUT_MILLIS);
            if (recognitionReady) {
                scheduleSettledRelease();
            }
        }

        /** 취소와 준비 이벤트의 경합을 직렬 처리하고 원문 없이 상태만 기록한다. */
        private void handleReadyOrStarted(String phase) {
            mainHandler.post(() -> {
                if (released) {
                    return;
                }
                boolean firstReady = !recognitionReady;
                recognitionReady = true;
                Log.i(TAG, "SESSION_" + phase + " id=" + sessionId + " stopping=" + stopping);
                if (stopping) {
                    requestStop();
                    if (firstReady) {
                        // 준비 대기와 준비 후 정리 상한을 분리해 늦은 준비 직후 연결을 끊지 않는다.
                        mainHandler.removeCallbacks(stopDeadline);
                        mainHandler.postDelayed(stopDeadline, STOP_STAGE_TIMEOUT_MILLIS);
                    }
                    scheduleSettledRelease();
                }
            });
        }

        /** 비동기 SDK 요청은 호출 반환만으로 종료되었다고 판정하지 않는다. */
        private void requestStop() {
            if (!released && sdk != null) {
                try {
                    sdk.stop();
                    stopRequestAccepted = true;
                } catch (RuntimeException exception) {
                    Log.w(TAG, "stop failed: " + exception.getClass().getSimpleName());
                }
            }
        }

        /** 준비 중 먼저 처리된 stop을 보완하되 유예와 단계별 상한을 연장하지 않는다. */
        private void retryStop() {
            if (!stopping || released) {
                return;
            }
            requestStop();
            mainHandler.postDelayed(retryStop, STOP_RETRY_MILLIS);
        }

        /** 수동 stop에서 Ended가 생략될 수 있으므로 준비 후 짧은 유예를 별도로 예약한다. */
        private void scheduleSettledRelease() {
            mainHandler.removeCallbacks(settledRelease);
            mainHandler.postDelayed(settledRelease, STOP_SETTLE_MILLIS);
        }

        /** SDK 비동기 stop에 유예를 준 뒤 연결만 해제하며 마이크 종료를 확정했다고 기록하지 않는다. */
        private void onStopSettled() {
            if (!stopping || released) {
                return;
            }
            finishRelease(ReleaseReason.STOP_SETTLED_WITHOUT_END);
        }

        /** 준비나 정리 이벤트가 계속 오지 않아도 제한시간 후 연결을 해제해 무한 대기를 막는다. */
        private void onStopDeadline() {
            if (!stopping || released) {
                return;
            }
            ReleaseReason reason = recognitionReady ? ReleaseReason.STOP_WAIT_LIMIT
                    : ReleaseReason.PREPARE_WAIT_LIMIT;
            finishRelease(reason);
        }

        /** 이벤트 또는 유예·상한 근거를 구분해 연결을 해제하고 대기 요청을 이어 간다. */
        private void finishRelease(ReleaseReason reason) {
            if (released) {
                return;
            }
            released = true;
            mainHandler.removeCallbacks(timeout);
            mainHandler.removeCallbacks(emptyResult);
            mainHandler.removeCallbacks(retryStop);
            mainHandler.removeCallbacks(stopDeadline);
            mainHandler.removeCallbacks(settledRelease);

            // stop의 비동기 작업이 끝나기 전 release하면 SDK 내부 서비스 참조가 사라진다.
            if (sdk != null) {
                if (listenerAdded) {
                    cleanup("removeListener", () -> sdk.removeListener(this));
                }
                cleanup("release", sdk::release);
                sdk = null;
            }
            Log.i(TAG, "SESSION_RELEASED id=" + sessionId + " reason=" + reason);
            onSessionReleased(this, !microphoneRequested || recognitionEnded || stopRequestAccepted);
        }

        /** SDK 정리 실패 시 원문을 남기지 않고 다음 자원 해제를 진행한다. */
        private void cleanup(String action, Runnable operation) {
            try {
                operation.run();
            } catch (RuntimeException exception) {
                Log.w(TAG, action + " failed: " + exception.getClass().getSimpleName());
            }
        }

        /** SDK 코드를 음성 메모 입력에 필요한 안내로 변환한다. */
        private String errorMessage(ErrorType error) {
            if (error == null) {
                return SERVICE_ERROR;
            }
            return switch (error) {
                case PERMISSION_DENIED -> "Pleos 음성 인식 사용 권한을 확인해 주세요.";
                case MIC_ERROR, AUDIO_ERROR -> "차량 마이크 입력을 확인해 주세요.";
                case TIMEOUT -> SESSION_TIMEOUT;
                case UNAVAILABLE_LANGUAGE -> "이 환경에서 한국어 음성 인식을 사용할 수 없습니다.";
                case ON_DEVICE_ENGINE_ERROR, NOT_SUPPORTED_API ->
                        "이 환경에서 Pleos 온디바이스 음성 인식을 사용할 수 없습니다.";
                default -> SERVICE_ERROR;
            };
        }
    }
}
