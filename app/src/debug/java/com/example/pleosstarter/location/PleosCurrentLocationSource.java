package com.example.pleosstarter.location;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import java.util.Objects;

import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** NAVI_ROUTE 조회 권한으로 차량 위치만 폴링한다. 경로 요청이나 기본 내비 화면 전환은 수행하지 않는다. */
public final class PleosCurrentLocationSource implements CurrentLocationSource {
    private static final String TAG = "PleosLocation";
    private static final long POLL_INTERVAL_MILLIS = 2000L;
    private static final long REQUEST_TIMEOUT_MILLIS = 5000L;
    private static final long CONNECTION_TIMEOUT_MILLIS = 8000L;

    private final Context applicationContext;
    private final boolean emulatedVehicle;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CurrentLocationListener listener;
    private Session session;
    private boolean closed;
    private long lifecycle;

    /** 앱 컨텍스트와 좌표 공급 환경을 저장한다. 에뮬레이터 좌표를 실제 차량 GPS로 표시하지 않는다. */
    public PleosCurrentLocationSource(Context context, boolean emulatedVehicle) {
        applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
        this.emulatedVehicle = emulatedVehicle;
    }

    /** 기존 수신자를 교체하고 별도 SDK 연결에서 위치 조회를 시작한다. 메인 스레드 전용이다. */
    @Override
    public void start(CurrentLocationListener locationListener) {
        requireMainThread();
        Objects.requireNonNull(locationListener, "listener");
        if (closed) {
            throw new IllegalStateException("해제된 위치 공급자는 다시 시작할 수 없습니다.");
        }

        stop();
        listener = locationListener;
        connect();
    }

    /** 예약 작업과 연결을 해제하고 늦은 응답을 무효화한다. 메인 스레드 전용이다. */
    @Override
    public void stop() {
        requireMainThread();
        lifecycle++;
        listener = null;
        mainHandler.removeCallbacksAndMessages(null);
        releaseSession();
    }

    /** 위치 공급을 영구 중단한다. 반환 후에는 늦은 SDK 응답도 전달하지 않는다. */
    @Override
    public void close() {
        requireMainThread();
        closed = true;
        stop();
    }

    /** SDK 연결을 시작하며 연결 응답이 오지 않는 경우도 제한 시간 안에 실패 처리한다. */
    private void connect() {
        if (closed || listener == null || session != null) {
            return;
        }

        try {
            Session connecting = new Session(new NaviHelper(applicationContext));
            session = connecting;
            mainHandler.postDelayed(connecting.connectionTimeout, CONNECTION_TIMEOUT_MILLIS);
            connecting.helper.initialize(
                    () -> {
                        mainHandler.post(() -> handleConnected(connecting));
                        return Unit.INSTANCE;
                    },
                    error -> {
                        mainHandler.post(() -> handleConnectionFailure(connecting, error));
                        return Unit.INSTANCE;
                    });
        } catch (RuntimeException error) {
            failAndReconnect(failureFor(error, LocationError.CONNECTION_FAILED,
                    "차량 위치 서비스에 연결하지 못했습니다."));
        }
    }

    /** 살아 있는 연결에만 리스너를 붙이고 첫 위치를 조회한다. */
    private void handleConnected(Session connectedSession) {
        if (!isActive(connectedSession) || connectedSession.connected) {
            return;
        }

        mainHandler.removeCallbacks(connectedSession.connectionTimeout);
        try {
            connectedSession.helper.addListener(connectedSession);
            connectedSession.connected = true;
            requestLocation(connectedSession);
        } catch (RuntimeException error) {
            failAndReconnect(failureFor(error, LocationError.CONNECTION_FAILED,
                    "차량 위치 서비스에 연결하지 못했습니다."));
        }
    }

    /** 현재 연결에서 발생한 초기화 실패만 전달한다. */
    private void handleConnectionFailure(Session failedSession, Throwable error) {
        if (isActive(failedSession)) {
            failAndReconnect(failureFor(error, LocationError.CONNECTION_FAILED,
                    "차량 위치 서비스에 연결하지 못했습니다."));
        }
    }

    /** 동시에 한 건만 요청하며 SDK의 실제 위치 콜백을 기다린다. */
    private void requestLocation(Session activeSession) {
        if (!isActive(activeSession) || !activeSession.connected || activeSession.pending) {
            return;
        }

        activeSession.pending = true;
        mainHandler.postDelayed(activeSession.requestTimeout, REQUEST_TIMEOUT_MILLIS);
        try {
            activeSession.helper.getCurrentLocationInfo();
        } catch (RuntimeException error) {
            failAndReconnect(failureFor(error, LocationError.REQUEST_FAILED,
                    "차량 위치를 조회하지 못했습니다."));
        }
    }

    /** 현재 요청에 대한 유효 좌표만 전달한다. 수신 시각을 측위 시각으로 바꾸거나 캐시를 재전달하지 않는다. */
    private void handleLocation(Session respondingSession, CurrentLocationInfo location,
            long receivedElapsedRealtimeMillis) {
        if (!isActive(respondingSession) || !respondingSession.pending) {
            return;
        }

        respondingSession.pending = false;
        mainHandler.removeCallbacks(respondingSession.requestTimeout);
        if (location == null) {
            failAndReconnect(new LocationFailure(LocationError.INVALID_COORDINATE,
                    "차량에서 유효한 위치를 받지 못했습니다."));
            return;
        }

        CurrentPosition position;
        try {
            position = new CurrentPosition(location.getLatitude(), location.getLongitude(),
                    receivedElapsedRealtimeMillis, emulatedVehicle);
        } catch (IllegalArgumentException error) {
            failAndReconnect(new LocationFailure(LocationError.INVALID_COORDINATE,
                    "차량에서 유효한 위치를 받지 못했습니다."));
            return;
        }

        Log.d(TAG, "locationResponse source=" + position.getSourceLabel() + " fixTimestampKnown=false");
        listener.onLocation(position);
        if (isActive(respondingSession)) {
            mainHandler.postDelayed(respondingSession.nextPoll, POLL_INTERVAL_MILLIS);
        }
    }

    /** 실패한 연결을 먼저 폐기하여 늦은 응답이 다음 조회를 완료하지 못하도록 한다. */
    private void failAndReconnect(LocationFailure failure) {
        if (closed || listener == null) {
            return;
        }

        long failedLifecycle = lifecycle;
        releaseSession();
        Log.w(TAG, "locationUnavailable code=" + failure.getCode());
        listener.onUnavailable(failure);

        // 수신자가 실패 처리 중 화면을 닫거나 다시 시작했으면 재연결하지 않는다.
        if (closed || listener == null || lifecycle != failedLifecycle
                || failure.getCode() == LocationError.PERMISSION_DENIED) {
            return;
        }
        mainHandler.postDelayed(() -> {
            if (lifecycle == failedLifecycle) {
                connect();
            }
        }, POLL_INTERVAL_MILLIS);
    }

    /** 현재 세션인지 확인해 중단·재시작·닫기 이후의 콜백을 차단한다. */
    private boolean isActive(Session candidate) {
        return !closed && listener != null && session == candidate;
    }

    /** 모든 세션 타이머와 SDK 연결을 해제하며 정리 오류는 별도로 기록한다. */
    private void releaseSession() {
        Session releasing = session;
        session = null;
        if (releasing == null) {
            return;
        }

        releasing.pending = false;
        mainHandler.removeCallbacks(releasing.connectionTimeout);
        mainHandler.removeCallbacks(releasing.requestTimeout);
        mainHandler.removeCallbacks(releasing.nextPoll);
        try {
            if (releasing.connected) {
                releasing.helper.removeListener(releasing);
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "removeListener failed type=" + error.getClass().getSimpleName());
        }
        try {
            releasing.helper.release();
        } catch (RuntimeException error) {
            Log.w(TAG, "release failed type=" + error.getClass().getSimpleName());
        }
    }

    /** 권한 오류와 일반 연동 오류를 내부 예외 내용 없이 구분한다. */
    private LocationFailure failureFor(Throwable error, LocationError fallback, String message) {
        if (error instanceof SecurityException) {
            return new LocationFailure(LocationError.PERMISSION_DENIED,
                    "차량 위치를 읽을 권한이 없습니다.");
        }
        return new LocationFailure(fallback, message);
    }

    /** 생명주기와 SDK 콜백의 순서를 한 스레드에서 보장한다. */
    private void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("위치 공급자의 생명주기는 메인 스레드에서 호출해야 합니다.");
        }
    }

    /** 연결별 리스너와 타이머를 묶어 이전 연결 응답이 새 요청으로 섞이지 않도록 한다. */
    private final class Session implements NaviHelperEventListener {
        private final NaviHelper helper;
        private final Runnable connectionTimeout;
        private final Runnable requestTimeout;
        private final Runnable nextPoll;
        private boolean connected;
        private boolean pending;

        /** 이 연결에만 적용되는 시간 제한과 다음 조회 작업을 만든다. */
        private Session(NaviHelper helper) {
            this.helper = helper;
            connectionTimeout = () -> {
                if (isActive(this)) {
                    failAndReconnect(new LocationFailure(LocationError.CONNECTION_TIMEOUT,
                            "차량 위치 서비스 연결 시간이 초과되었습니다."));
                }
            };
            requestTimeout = () -> {
                if (isActive(this) && pending) {
                    failAndReconnect(new LocationFailure(LocationError.REQUEST_TIMEOUT,
                            "차량 위치 응답이 아직 없습니다."));
                }
            };
            nextPoll = () -> requestLocation(this);
        }

        /** SDK 응답 수신 시각을 기록하고 메인 스레드에서 현재 연결의 결과인지 검사한다. */
        @Override
        public void onCurrentLocationInfo(CurrentLocationInfo location) {
            long receivedAt = SystemClock.elapsedRealtime();
            mainHandler.post(() -> handleLocation(this, location, receivedAt));
        }
    }
}
