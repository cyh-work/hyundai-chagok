package com.example.pleosstarter.simulation;

import java.util.Objects;

/** 조회한 도로 좌표를 30km/h로 재생하며 터치·사용자 정지·화면 이탈을 독립적으로 유지한다. */
public final class RoutePlaybackController {
    public static final long UPDATE_INTERVAL_MILLIS = 100;
    public static final double SPEED_KILOMETERS_PER_HOUR = 30;
    private static final double METERS_PER_MILLISECOND = SPEED_KILOMETERS_PER_HOUR / 3600;
    private final RouteSource source;
    private final PlaybackScheduler scheduler;
    private final PlaybackClock clock;
    private final PlaybackListener listener;
    private final Runnable tickTask = this::tick;
    private RoutePath route;
    private PlaybackState state = PlaybackState.STOPPED;
    private double distanceMeters;
    private long lastTickMillis;
    private long generation;
    private boolean userPaused;
    private boolean touchActive;
    private boolean foreground = true;
    private boolean closed;

    /** 경로 공급, 타이머, 시계와 화면 콜백을 주입하며 Android 없이도 실행할 수 있다. */
    public RoutePlaybackController(RouteSource source, PlaybackScheduler scheduler,
            PlaybackClock clock, PlaybackListener listener) {
        this.source = Objects.requireNonNull(source);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.clock = Objects.requireNonNull(clock);
        this.listener = Objects.requireNonNull(listener);
    }

    /** 새 경로를 조회하며 이전 재생과 늦은 요청 결과를 무효화한다. */
    public void start(RoutePoint origin, RoutePoint destination) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);
        if (closed) {
            return;
        }
        clearPlayback();
        userPaused = false;
        long request = generation;
        changeState(PlaybackState.LOADING);
        if (!isCurrent(request)) {
            return;
        }
        source.load(origin, destination, new RouteSource.Callback() {
            /** 화면이나 터치가 보류 중이면 첫 위치도 이동시키지 않고 대기한다. */
            @Override
            public void onSuccess(RoutePath path) {
                if (!isCurrent(request)) {
                    return;
                }
                route = Objects.requireNonNull(path);
                updatePlaybackGate();
            }

            /** 경로 실패를 별도 상태로 전달하고 실제 위치 복귀 정책은 호출자에 맡긴다. */
            @Override
            public void onFailure(PlaybackFailure failure) {
                if (!isCurrent(request)) {
                    return;
                }
                clearPlayback();
                changeState(PlaybackState.FAILED);
                listener.onFailure(failure);
            }
        });
    }

    /** 사용자 일시정지는 손가락을 떼거나 화면으로 복귀해도 자동 해제하지 않는다. */
    public void pause() {
        userPaused = true;
        updatePlaybackGate();
    }

    /** 사용자 정지만 해제하며 터치 중이거나 화면이 가려졌으면 계속 정지한다. */
    public void resume() {
        userPaused = false;
        updatePlaybackGate();
    }

    /** 그리기 중 좌표가 흔들리지 않도록 마지막으로 표시한 위치에서 정지한다. */
    public void setTouchActive(boolean active) {
        touchActive = active;
        updatePlaybackGate();
    }

    /** 화면이 가려진 동안 타이머를 제거하고 그 시간을 이동 시간에서 제외한다. */
    public void setForeground(boolean visible) {
        foreground = visible;
        updatePlaybackGate();
    }

    /** 재생과 조회를 정리하되 실제 차량 위치는 호출자가 별도로 복구하게 한다. */
    public void stop() {
        if (closed) {
            return;
        }
        clearPlayback();
        userPaused = false;
        changeState(PlaybackState.STOPPED);
    }

    /** 닫힌 화면에 타이머와 비동기 결과가 남지 않게 연결을 해제한다. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        clearPlayback();
        source.close();
        state = PlaybackState.STOPPED;
    }

    /** 화면이 현재 실행 상태에 맞는 조작을 표시할 수 있게 한다. */
    public PlaybackState getState() {
        return state;
    }

    /** 일시적인 터치 보류와 사용자가 누른 일시정지를 구분한다. */
    public boolean isUserPaused() {
        return userPaused;
    }

    /** 보류 이유가 모두 해제됐을 때만 멈춘 위치에서 재생을 시작한다. */
    private void updatePlaybackGate() {
        if (closed || route == null || state == PlaybackState.FINISHED) {
            return;
        }
        if (userPaused || touchActive || !foreground) {
            scheduler.cancel(tickTask);
            changeState(PlaybackState.PAUSED);
            return;
        }
        if (state == PlaybackState.PLAYING) {
            return;
        }

        lastTickMillis = clock.nowMillis();
        long request = generation;
        changeState(PlaybackState.PLAYING);
        if (!isCurrent(request) || state != PlaybackState.PLAYING) {
            return;
        }
        emitPosition();
        if (isCurrent(request) && state == PlaybackState.PLAYING) {
            scheduler.schedule(tickTask, UPDATE_INTERVAL_MILLIS);
        }
    }

    /** 실제 진행 시간만 거리로 바꾸며 도착점에서는 정확히 한 번 멈춘다. */
    private void tick() {
        if (closed || route == null || state != PlaybackState.PLAYING) {
            return;
        }
        long request = generation;
        long now = clock.nowMillis();
        long elapsed = Math.max(0, now - lastTickMillis);
        lastTickMillis = Math.max(lastTickMillis, now);
        distanceMeters = Math.min(route.totalMeters(),
                distanceMeters + elapsed * METERS_PER_MILLISECOND);
        emitPosition();
        if (!isCurrent(request) || state != PlaybackState.PLAYING) {
            return;
        }

        if (distanceMeters >= route.totalMeters()) {
            changeState(PlaybackState.FINISHED);
        } else {
            scheduler.schedule(tickTask, UPDATE_INTERVAL_MILLIS);
        }
    }

    /** 현재 거리의 도로 좌표를 실제 위치와 구별되는 프레임으로 전달한다. */
    private void emitPosition() {
        listener.onPosition(new PlaybackFrame(route.pointAt(distanceMeters),
                distanceMeters, route.totalMeters()));
    }

    /** 교체되거나 종료된 요청의 결과가 현재 재생을 변경하지 못하게 한다. */
    private boolean isCurrent(long request) {
        return !closed && request == generation;
    }

    /** 이 컨트롤러의 예약과 경로만 정리하고 화면·터치 보류는 보존한다. */
    private void clearPlayback() {
        generation++;
        scheduler.cancel(tickTask);
        source.cancel();
        route = null;
        distanceMeters = 0;
    }

    /** 같은 상태를 반복 전달하지 않고 실제 변화만 화면에 알린다. */
    private void changeState(PlaybackState next) {
        if (state != next) {
            state = next;
            listener.onStateChanged(next);
        }
    }
}
