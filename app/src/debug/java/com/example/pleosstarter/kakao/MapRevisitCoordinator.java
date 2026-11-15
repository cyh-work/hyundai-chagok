package com.example.pleosstarter.kakao;

import com.example.pleosstarter.records.RecordRevisitService;
import com.example.pleosstarter.simulation.PlaybackScheduler;
import com.example.pleosstarter.ui.MapRevisitDisplay;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** 차량·시험 위치와 화면 수명을 재방문 표시 서비스에 연결한다. */
public final class MapRevisitCoordinator {
    private static final long DISPLAY_MILLIS = 8_000L;
    private final RecordRevisitService vehicle;
    private final RecordRevisitService simulation;
    private final MapRevisitDisplay display;
    private final PlaybackScheduler scheduler;
    private final Consumer<MapRecordMarker> openRecord;
    private final Runnable hideCard = this::dismiss;
    private MapRecordMarker visible;
    private boolean ready;
    private boolean foreground;
    private boolean interactionBlocked;
    private boolean hasVehiclePosition;
    private boolean closed;
    private boolean testMovementActive;
    private boolean hasTestPosition;
    private double vehicleLatitude;
    private double vehicleLongitude;
    private double testLatitude;
    private double testLongitude;
    private long displayGeneration;

    /** 독립된 위치 출처의 진입 상태와 화면·예약·기록 열기를 주입한다. */
    public MapRevisitCoordinator(RecordRevisitService vehicle, RecordRevisitService simulation,
            MapRevisitDisplay display, PlaybackScheduler scheduler, Consumer<MapRecordMarker> openRecord) {
        this.vehicle = vehicle;
        this.simulation = simulation;
        this.display = display;
        this.scheduler = scheduler;
        this.openRecord = openRecord;
    }

    /** 저장이 끝난 기록 스냅샷을 두 위치 출처에 전달한다. */
    public void replaceRecords(List<MapRecordMarker> records) {
        if (closed) {
            return;
        }
        dismiss();
        vehicle.replaceRecords(records);
        simulation.replaceRecords(records);
    }

    /** 지도 준비 여부에 맞춰 재방문 표시를 허용한다. */
    public void setReady(boolean ready) {
        this.ready = ready;
        if (!ready) {
            dismiss();
        }
    }

    /** 화면 이탈 중에는 카드를 제거한다. */
    public void setForeground(boolean foreground) {
        this.foreground = foreground;
        if (!foreground) {
            hasVehiclePosition = false;
            hasTestPosition = false;
            vehicle.resetPosition();
            simulation.resetPosition();
            dismiss();
        }
    }

    /** 그리기·마커 이동·모달이 끝난 뒤에만 재방문 카드를 표시한다. */
    public void setInteractionBlocked(boolean blocked) {
        interactionBlocked = blocked;
        if (blocked) {
            dismiss();
        } else if (testMovementActive && hasTestPosition) {
            onTestPosition(testLatitude, testLongitude);
        } else if (!testMovementActive && hasVehiclePosition) {
            onVehiclePosition(vehicleLatitude, vehicleLongitude);
        }
    }

    /** 연결이 끝난 화면으로 재방문 콜백을 보내지 않는다. */
    public void close() {
        closed = true;
        hasVehiclePosition = false;
        hasTestPosition = false;
        dismiss();
    }

    /** 시험 이동과 차량 위치가 서로의 진입 이력을 바꾸지 않게 출처를 전환한다. */
    public void setTestMovementActive(boolean active) {
        if (closed || testMovementActive == active) {
            return;
        }
        testMovementActive = active;
        hasVehiclePosition = false;
        hasTestPosition = false;
        vehicle.resetPosition();
        simulation.resetPosition();
        dismiss();
    }

    /** 시험 재생이 보여주는 좌표만 해당 출처의 재방문 판단에 사용한다. */
    public void onTestPosition(double latitude, double longitude) {
        if (closed || !foreground || !testMovementActive) {
            return;
        }
        Optional<MapRecordMarker> candidate = simulation.onPosition(latitude, longitude, canPresent());
        hasTestPosition = true;
        testLatitude = latitude;
        testLongitude = longitude;
        present(candidate);
    }

    /** 차량 위치가 유실되면 이전 좌표로 재방문 표시를 재개하지 않는다. */
    public void onVehicleUnavailable() {
        hasVehiclePosition = false;
        vehicle.resetPosition();
        if (!testMovementActive) {
            dismiss();
        }
    }

    /** 차량에서 수신한 위치로 재방문을 판단한다. */
    public void onVehiclePosition(double latitude, double longitude) {
        if (closed || !foreground || testMovementActive) {
            return;
        }
        Optional<MapRecordMarker> candidate = vehicle.onPosition(latitude, longitude, canPresent());
        hasVehiclePosition = true;
        vehicleLatitude = latitude;
        vehicleLongitude = longitude;
        present(candidate);
    }

    /** 지도 준비·입력 상태와 이미 보이는 카드에 따라 표시를 허용한다. */
    private boolean canPresent() {
        return ready && foreground && !closed && !interactionBlocked && visible == null;
    }

    /** 표시 가능한 후보 한 건만 카드와 일회성 만료 예약에 연결한다. */
    private void present(Optional<MapRecordMarker> candidate) {
        if (candidate.isPresent()) {
            MapRecordMarker marker = candidate.get();
            long generation = ++displayGeneration;
            visible = marker;
            display.showRecord(marker, () -> openVisible(marker, generation),
                    () -> dismissGeneration(generation));
            scheduler.schedule(hideCard, DISPLAY_MILLIS);
        }
    }

    /** 현재 표시한 카드의 한 번 선택만 기존 기록 모달로 전달한다. */
    private void openVisible(MapRecordMarker marker, long generation) {
        if (closed || !foreground || !ready || interactionBlocked
                || visible != marker || displayGeneration != generation) {
            return;
        }
        dismiss();
        openRecord.accept(marker);
    }

    /** 이전 표시 회차의 늦은 닫기 입력으로 새 카드를 지우지 않는다. */
    private void dismissGeneration(long generation) {
        if (displayGeneration == generation) {
            dismiss();
        }
    }

    /** 표시만 정리하고 같은 방문에서 소비한 기록을 다시 활성화하지 않는다. */
    private void dismiss() {
        displayGeneration++;
        scheduler.cancel(hideCard);
        visible = null;
        display.hideRecord();
    }
}
