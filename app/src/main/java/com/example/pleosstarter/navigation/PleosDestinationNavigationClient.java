package com.example.pleosstarter.navigation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.function.Consumer;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** 선택 장소를 Pleos 기본 내비에 넘기고 요청한 목적지가 실제 설치됐는지 확인한다. */
public final class PleosDestinationNavigationClient implements DestinationNavigationClient {
    private static final String TAG = "PleosDestination";
    private static final long TIMEOUT_MILLIS = 30000L;
    private static final double COORDINATE_TOLERANCE = 0.001;

    private final Context applicationContext;
    private final Consumer<NavigationEvent> observer;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::handleTimeout;
    private NaviHelper helper;
    private RequestListener listener;
    private NavigationDestination requestedDestination;
    private long generation;
    private boolean pending;
    private boolean connected;
    private boolean routeEventReceived;
    private boolean closed;

    /** 연결 수명과 무관한 앱 컨텍스트 및 메인 스레드 결과 관찰자를 저장한다. */
    public PleosDestinationNavigationClient(Context context, Consumer<NavigationEvent> observer) {
        applicationContext = context.getApplicationContext();
        this.observer = observer;
    }

    /** 입력을 검증한 뒤 요청별 새 연결로 시작하여 과거 콜백과 중복 입력을 격리한다. */
    @Override
    public void start(NavigationDestination destination) {
        if (closed || pending) {
            return;
        }
        if (destination == null || !destination.isValid()) {
            emit(NavigationEvent.Status.REQUEST_FAILED);
            return;
        }

        requestedDestination = destination;
        pending = true;
        routeEventReceived = false;
        long requestGeneration = ++generation;
        mainHandler.postDelayed(timeout, TIMEOUT_MILLIS);
        emit(NavigationEvent.Status.CONNECTING);
        if (closed || !pending) {
            return;
        }

        try {
            NaviHelper source = new NaviHelper(applicationContext);
            helper = source;
            source.initialize(
                    () -> {
                        mainHandler.post(() -> handleConnected(source, requestGeneration));
                        return Unit.INSTANCE;
                    },
                    failure -> {
                        mainHandler.post(() -> handleConnectionFailure(source, requestGeneration, failure));
                        return Unit.INSTANCE;
                    });
        } catch (RuntimeException failure) {
            Log.w(TAG, "connect failed type=" + failure.getClass().getSimpleName());
            finish(NavigationEvent.Status.CONNECTION_FAILED);
        }
    }

    /** 현재 요청의 연결을 한 번만 등록하고 중복 연결 콜백의 재안내 요청을 차단한다. */
    private void handleConnected(NaviHelper source, long requestGeneration) {
        if (!isActive(source, requestGeneration) || connected) {
            return;
        }

        try {
            listener = new RequestListener(source, requestGeneration);
            source.addListener(listener);
            connected = true;
        } catch (RuntimeException failure) {
            handleConnectionFailure(source, requestGeneration, failure);
            return;
        }

        mainHandler.removeCallbacks(timeout);
        mainHandler.postDelayed(timeout, TIMEOUT_MILLIS);
        NavigationDestination destination = requestedDestination;
        emit(NavigationEvent.Status.REQUESTED);
        if (!isActive(source, requestGeneration)) {
            return;
        }

        try {
            RouteInfo route = new RouteInfo(destination.latitude(), destination.longitude(),
                    null, null, destination.name(), "", destination.address(), "",
                    RouteOption.RECOMMENDED, "", false);
            Log.i(TAG, "requestRoute requested=true");
            source.requestRoute(route);
        } catch (RuntimeException failure) {
            Log.w(TAG, "requestRoute failed type=" + failure.getClass().getSimpleName());
            finish(NavigationEvent.Status.REQUEST_FAILED);
        }
    }

    /** 해제되거나 대체된 연결 실패를 버리고 현재 요청만 재시도 가능한 실패로 마친다. */
    private void handleConnectionFailure(NaviHelper source, long requestGeneration, Throwable failure) {
        if (!isActive(source, requestGeneration)) {
            return;
        }
        Log.w(TAG, "connection failed type=" + failure.getClass().getSimpleName());
        finish(NavigationEvent.Status.CONNECTION_FAILED);
    }

    /** 콜백이 현재 연결과 진행 중인 요청에 속하는지 확인한다. */
    private boolean isActive(NaviHelper source, long requestGeneration) {
        return !closed && pending && helper == source && generation == requestGeneration;
    }

    /** 경로 이벤트 이후 기본 내비가 실제 보관한 목적지를 비동기로 조회한다. */
    private void queryDestination() {
        try {
            helper.getDestinationInfo();
        } catch (RuntimeException failure) {
            Log.w(TAG, "getDestinationInfo failed type=" + failure.getClass().getSimpleName());
            finish(NavigationEvent.Status.REQUEST_FAILED);
        }
    }

    /** 경로 신호와 요청한 이름·좌표가 모두 확인된 경우에만 목적지 확인을 전달한다. */
    private void handleDestination(DestinationInfo destination) {
        if (!routeEventReceived || destination == null) {
            return;
        }

        NavigationDestination requested = requestedDestination;
        String returnedName = destination.getName();
        boolean nameMatches = returnedName != null && requested.name().equals(returnedName.trim());
        boolean coordinatesMatch = Double.isFinite(destination.getLatitude())
                && Double.isFinite(destination.getLongitude())
                && Math.abs(destination.getLatitude() - requested.latitude()) < COORDINATE_TOLERANCE
                && Math.abs(destination.getLongitude() - requested.longitude()) < COORDINATE_TOLERANCE;
        Log.i(TAG, "destination matched=" + (nameMatches && coordinatesMatch)
                + " distance=" + destination.getRemainDistance());
        if (!nameMatches || !coordinatesMatch) {
            return;
        }

        NavigationEvent confirmed = new NavigationEvent(NavigationEvent.Status.CONFIRMED,
                destination.getName(), Math.max(0, destination.getRemainDistance()));
        endRequest();
        observer.accept(confirmed);
    }

    /** 연결 또는 목적지 확인 제한 시간이 지나면 자원을 해제하고 재시도를 허용한다. */
    private void handleTimeout() {
        if (pending && !closed) {
            Log.w(TAG, "request timed out connected=" + connected);
            finish(NavigationEvent.Status.TIMED_OUT);
        }
    }

    /** 실패 상태를 내보내기 전에 현재 요청과 SDK 연결을 정리한다. */
    private void finish(NavigationEvent.Status status) {
        if (closed) {
            return;
        }
        endRequest();
        emit(status);
    }

    /** 현재 요청을 끝내지만 이미 시작된 기본 내비 안내는 유지한다. */
    private void endRequest() {
        pending = false;
        mainHandler.removeCallbacks(timeout);
        releaseHelper();
        requestedDestination = null;
    }

    /** 현재 목적지의 진행 상태를 화면 관찰자로 전달한다. */
    private void emit(NavigationEvent.Status status) {
        if (!closed) {
            observer.accept(new NavigationEvent(status));
        }
    }

    /** SDK 이벤트 구독과 서비스 연결만 해제하며 해제 오류는 기록한다. */
    private void releaseHelper() {
        NaviHelper source = helper;
        RequestListener registeredListener = listener;
        helper = null;
        listener = null;
        connected = false;
        if (source == null) {
            return;
        }

        try {
            if (registeredListener != null) {
                source.removeListener(registeredListener);
            }
        } catch (RuntimeException failure) {
            Log.w(TAG, "removeListener failed type=" + failure.getClass().getSimpleName());
        } finally {
            try {
                source.release();
            } catch (RuntimeException failure) {
                Log.w(TAG, "release failed type=" + failure.getClass().getSimpleName());
            }
        }
    }

    /** 화면 파괴 후의 SDK 콜백과 예약 작업을 차단한다. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        endRequest();
        mainHandler.removeCallbacksAndMessages(null);
    }

    /** 요청별 연결과 세대를 캡처하여 이전 요청 이벤트가 새 요청을 완료하지 못하게 한다. */
    private final class RequestListener implements NaviHelperEventListener {
        private final NaviHelper source;
        private final long requestGeneration;

        /** 리스너가 속한 연결과 요청 세대를 고정한다. */
        private RequestListener(NaviHelper source, long requestGeneration) {
            this.source = source;
            this.requestGeneration = requestGeneration;
        }

        /** 선택한 장소의 안내 시작 신호를 구분하고 목적지 확인을 요청한다. */
        @Override
        public void onRouteStarted(RouteStartInfo route) {
            mainHandler.post(() -> {
                if (!isActive(source, requestGeneration) || route == null
                        || route.getName() == null
                        || !requestedDestination.name().equals(route.getName().trim())) {
                    return;
                }
                Log.i(TAG, "onRouteStarted");
                routeEventReceived = true;
                emit(NavigationEvent.Status.STARTED);
                if (isActive(source, requestGeneration)) {
                    queryDestination();
                }
            });
        }

        /** 경로 계산 완료를 안내 시작과 구분하여 전달하고 목적지를 대조한다. */
        @Override
        public void onRoutingCompleted() {
            mainHandler.post(() -> {
                if (!isActive(source, requestGeneration)) {
                    return;
                }
                Log.i(TAG, "onRoutingCompleted");
                routeEventReceived = true;
                emit(NavigationEvent.Status.CALCULATED);
                if (isActive(source, requestGeneration)) {
                    queryDestination();
                }
            });
        }

        /** 목적지가 갱신되면 요청한 장소가 설치됐는지 다시 확인한다. */
        @Override
        public void onDestinationChanged() {
            mainHandler.post(() -> {
                if (isActive(source, requestGeneration)) {
                    queryDestination();
                }
            });
        }

        /** 반환 목적지를 현재 요청의 이름·좌표와 대조하도록 전달한다. */
        @Override
        public void onDestinationInfo(DestinationInfo destination) {
            mainHandler.post(() -> {
                if (isActive(source, requestGeneration)) {
                    handleDestination(destination);
                }
            });
        }

        /** 현재 요청의 내비 오류만 실패로 전달하고 과거 연결의 오류는 버린다. */
        @Override
        public void onError(NaviErrorCode error) {
            mainHandler.post(() -> {
                if (isActive(source, requestGeneration)) {
                    Log.w(TAG, "onError code=" + error);
                    finish(NavigationEvent.Status.REQUEST_FAILED);
                }
            });
        }
    }
}
