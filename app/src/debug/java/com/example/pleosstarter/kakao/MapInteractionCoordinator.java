package com.example.pleosstarter.kakao;

import android.util.Log;
import com.example.pleosstarter.gesture.MapGestureInputLayout;
import com.example.pleosstarter.gesture.MapStrokeGesture;
import com.example.pleosstarter.simulation.PlaybackClock;
import com.example.pleosstarter.simulation.PlaybackFailure;
import com.example.pleosstarter.simulation.PlaybackFrame;
import com.example.pleosstarter.simulation.PlaybackListener;
import com.example.pleosstarter.simulation.PlaybackScheduler;
import com.example.pleosstarter.simulation.PlaybackState;
import com.example.pleosstarter.simulation.RoutePlaybackController;
import com.example.pleosstarter.simulation.RoutePoint;
import com.example.pleosstarter.simulation.RouteSource;
import com.example.pleosstarter.ui.MapScreenView;

/** 지도 입력·기록창의 카메라 추적과 시험 재생을 분리하며 좌표 공급과 저장은 각 서비스에 맡긴다. */
public final class MapInteractionCoordinator implements MapGestureInputLayout.Listener, PlaybackListener {
    private static final long FOLLOW_RESUME_DELAY_MILLIS = 6000L;
    private final PlaceMapSession map;
    private final MapRecordCoordinator records;
    private final MapScreenView screen;
    private final RoutePlaybackController playback;
    private final PlaybackScheduler followScheduler;
    private final Runnable resumeFollow = this::resumeFollowingWhenIdle;
    private boolean touchActive;
    private boolean recordDialogOpen;
    private boolean placeDialogOpen;
    private boolean explorationPending;
    private boolean panned;
    private boolean shapeClaimed;
    private boolean pendingGesture;
    private boolean testActive;
    private boolean closed;
    private boolean foreground;
    private long interactionGeneration;
    private int lastDisplayedMeters = -1;
    private int totalMeters;
    private MapRevisitCoordinator revisits;

    /** 지도·기록·화면과 재생 의존성을 주입한다. */
    public MapInteractionCoordinator(PlaceMapSession map, MapRecordCoordinator records, MapScreenView screen,
            RouteSource routes, PlaybackScheduler scheduler, PlaybackClock clock) {
        this.map = map;
        this.records = records;
        this.screen = screen;
        followScheduler = scheduler;
        playback = new RoutePlaybackController(routes, scheduler, clock, this);
    }

    /** 재방문 표시에 입력·화면 상태를 연결하고 재생 제어는 기존 책임으로 유지한다. */
    public void setRevisitCoordinator(MapRevisitCoordinator revisits) {
        this.revisits = revisits;
        revisits.setForeground(foreground);
        revisits.setTestMovementActive(testActive);
        updateHold();
    }

    /** 짧은 마커 탭을 읽기 모달로 처리하며 이전 장소 선택과 시험 재생을 분리한다. */
    @Override
    public boolean onMapTapped(float x, float y) {
        if (closed || !foreground) {
            return false;
        }
        map.dismissSelectionAt(x, y);
        return records.showRecordAt(x, y);
    }

    /** 시험 위치는 계속 갱신하고 손가락이 닿은 시점의 지도 투영만 기록 기준으로 고정한다. */
    @Override
    public void onPointerDown() {
        interactionGeneration++;
        touchActive = true;
        panned = false;
        shapeClaimed = false;
        pendingGesture = false;
        updateHold();
        map.beginMapInteraction();
    }

    /** 일반 지도 탐색은 손가락을 뗀 뒤 잠시 살펴볼 시간을 제공한다. */
    @Override
    public void onMapPanStarted() {
        panned = true;
    }

    /** 저장 마커를 잡으면 도형 대신 이동을 소유하고 자동 추적을 계속 보류한다. */
    @Override
    public boolean onRecordDragStart(float x, float y) {
        if (closed || !foreground || !records.beginRecordDrag(x, y)) {
            return false;
        }
        panned = true;
        shapeClaimed = false;
        pendingGesture = false;
        return true;
    }

    /** 마커 미리보기만 갱신하며 입력 보류 해제는 스트로크 종료 한 곳에서 처리한다. */
    @Override
    public void onRecordDragMove(float x, float y) {
        if (!closed && foreground) {
            records.previewRecordDrag(x, y);
        }
    }

    /** 실제 UP만 저장하고 취소·백그라운드 종료는 원래 마커 위치로 되돌린다. */
    @Override
    public void onRecordDragEnd(float x, float y, boolean cancelled) {
        records.endRecordDrag(x, y, cancelled || closed || !foreground);
    }

    /** 한 손가락 그리기가 시작되면 저장 기준 투영이 유지되는지 확인한다. */
    @Override
    public void onStrokeStarted() {
        shapeClaimed = true;
        map.restoreGestureCamera(() -> {
            // 도형 완성 시 다시 확인한 뒤 저장한다.
        }, message -> screen.showFeedback(message));
    }

    /** 도형 도중 두 손가락 탐색으로 바뀌면 저장과 기존 추적 복원을 모두 취소한다. */
    @Override
    public void onShapeAbandoned() {
        interactionGeneration++;
        shapeClaimed = false;
        pendingGesture = false;
        panned = true;
        map.finishMapInteraction(false);
    }

    /** 복원된 지도에서 도형 중심을 변환한 뒤에만 메모·즐겨찾기를 처리한다. */
    @Override
    public void onGesture(MapStrokeGesture gesture) {
        pendingGesture = true;
        long request = interactionGeneration;
        map.restoreGestureCamera(() -> {
            if (closed || !foreground || request != interactionGeneration) {
                return;
            }
            records.onGesture(gesture);
            pendingGesture = false;
            finishInteraction();
        }, message -> {
            if (closed || !foreground || request != interactionGeneration) {
                return;
            }
            screen.showFeedback(message);
            pendingGesture = false;
            finishInteraction();
        });
    }

    /** 저장 좌표 확인이 남아 있으면 보류하고 그 외 입력은 즉시 정리한다. */
    @Override
    public void onStrokeFinished() {
        if (!pendingGesture) {
            finishInteraction();
        }
    }

    /** 도형·탭은 원래 추적을 복구하고 일반 팬은 잠시 탐색한 뒤 자동 복귀한다. */
    private void finishInteraction() {
        map.finishMapInteraction(shapeClaimed || !panned);
        touchActive = false;
        if (panned && !shapeClaimed) {
            explorationPending = true;
        }
        updateHold();
    }

    /** 지도 중심 근처의 도로를 조회하거나 사용자가 정한 정지 상태를 전환한다. */
    public void toggleMovement() {
        if (closed) {
            return;
        }
        if (testActive && playback.getState() != PlaybackState.FINISHED) {
            if (playback.isUserPaused()) {
                playback.resume();
            } else {
                playback.pause();
            }
            renderState();
            return;
        }

        KakaoPlaceSelection center = map.getSearchCenter();
        RoutePoint origin = new RoutePoint(center.getLatitude(), center.getLongitude());
        RoutePoint destination = new RoutePoint(center.getLatitude() + 0.009,
                center.getLongitude() + 0.006);
        testActive = true;
        if (revisits != null) {
            revisits.setTestMovementActive(true);
        }
        explorationPending = false;
        followScheduler.cancel(resumeFollow);
        lastDisplayedMeters = -1;
        totalMeters = 0;
        map.beginTestMovement();
        playback.start(origin, destination);
    }

    /** 시험을 종료하면 실제 차량 위치 공급으로 돌아간다. */
    public void stopMovement() {
        if (!testActive) {
            return;
        }
        testActive = false;
        if (revisits != null) {
            revisits.setTestMovementActive(false);
        }
        explorationPending = false;
        followScheduler.cancel(resumeFollow);
        playback.stop();
        map.endTestMovement();
        renderState();
    }

    /** 기록 편집·읽기 창이 열린 동안 유휴 시간에 따른 카메라 자동 복귀를 보류한다. */
    public void setRecordDialogOpen(boolean open) {
        recordDialogOpen = open;
        if (!open) {
            explorationPending = true;
        }
        updateHold();
    }

    /** 장소 상세를 보는 동안 유휴 시간에 따른 카메라 자동 복귀를 보류한다. */
    public void setPlaceDialogOpen(boolean open) {
        placeDialogOpen = open;
        if (!open) {
            explorationPending = true;
        }
        updateHold();
    }

    /** 입력·모달은 카메라 복귀 예약에만 반영하고 시험 위치 재생은 계속 진행한다. */
    private void updateHold() {
        followScheduler.cancel(resumeFollow);
        boolean interacting = touchActive || recordDialogOpen || placeDialogOpen;
        if (revisits != null) {
            revisits.setInteractionBlocked(interacting);
        }
        if (!closed && foreground && !interacting && explorationPending) {
            followScheduler.schedule(resumeFollow, FOLLOW_RESUME_DELAY_MILLIS);
        }
    }

    /** 조작이 끝나면 배율·기울기를 유지한 채 차량 위치를 다시 따라간다. */
    private void resumeFollowingWhenIdle() {
        if (closed || !foreground || touchActive || recordDialogOpen || placeDialogOpen) {
            return;
        }
        explorationPending = false;
        map.showCurrentLocation();
        Log.i("PlaceMap", "MAP_FOLLOW_RESUMED idle=true");
    }

    /** 백그라운드 시간을 이동 거리로 계산하지 않는다. */
    public void setForeground(boolean foreground) {
        this.foreground = foreground;
        if (revisits != null) {
            revisits.setForeground(foreground);
        }
        playback.setForeground(foreground);
        if (!foreground) {
            records.cancelRecordDrag();
            interactionGeneration++;
            pendingGesture = false;
            touchActive = false;
            map.finishMapInteraction(false);
        }
        updateHold();
    }

    /** 시험 위치를 차량 점과 구분해 갱신하고 표시 거리만 적당한 간격으로 바꾼다. */
    @Override
    public void onPosition(PlaybackFrame frame) {
        if (closed || !testActive) {
            return;
        }
        map.showTestPosition(frame.point().latitude(), frame.point().longitude());
        if (revisits != null) {
            revisits.onTestPosition(frame.point().latitude(), frame.point().longitude());
        }
        int meters = (int) frame.distanceMeters();
        totalMeters = (int) Math.round(frame.totalMeters());
        if (meters != lastDisplayedMeters) {
            lastDisplayedMeters = meters;
            renderState();
            if (meters % 50 == 0) {
                Log.i("PlaceMap", "TEST_MOVEMENT_POSITION meters=" + meters);
            }
        }
    }

    /** 재생 상태와 안내 엔진 미사용 사실을 진단 로그에 남긴다. */
    @Override
    public void onStateChanged(PlaybackState state) {
        Log.i("PlaceMap", "TEST_MOVEMENT_STATE state=" + state + " guidance=false");
        renderState();
    }

    /** 경로 조회 실패는 허위 움직임으로 대체하지 않고 차량 위치로 복귀한다. */
    @Override
    public void onFailure(PlaybackFailure failure) {
        stopMovement();
        screen.showFeedback(failure.getMessage());
        Log.w("PlaceMap", "TEST_MOVEMENT_FAILURE code=" + failure.getCode());
    }

    /** 시험 이동의 출처·진행·수동 정지 상태를 화면에 표시한다. */
    private void renderState() {
        if (closed) {
            return;
        }
        PlaybackState state = playback.getState();
        String message = "테스트 이동 · 30 km/h · " + Math.max(0, lastDisplayedMeters) + " / " + totalMeters + " m";
        if (state == PlaybackState.LOADING) {
            message = "테스트 이동 · 주변 도로 확인 중…";
        } else if (state == PlaybackState.PAUSED) {
            message = "테스트 이동 · 잠시 멈춤 · " + Math.max(0, lastDisplayedMeters) + " m";
        } else if (state == PlaybackState.FINISHED) {
            message = "테스트 이동 완료 · 다시 눌러 새로 시작";
        }
        screen.showTestMovement(message, testActive, playback.isUserPaused(), state == PlaybackState.LOADING);
        if (state == PlaybackState.FINISHED) {
            screen.getMovementButton().setText("다시 이동");
        }
    }

    /** 화면 종료 뒤에 이동 타이머나 카메라 콜백이 남지 않게 한다. */
    public void close() {
        closed = true;
        if (revisits != null) {
            revisits.close();
        }
        records.cancelRecordDrag();
        interactionGeneration++;
        playback.close();
        followScheduler.cancel(resumeFollow);
        map.finishMapInteraction(false);
    }
}
