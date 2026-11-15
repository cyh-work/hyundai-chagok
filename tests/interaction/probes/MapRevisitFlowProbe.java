package com.example.pleosstarter.kakao;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.records.LocalRecordRevisitService;
import com.example.pleosstarter.simulation.PlaybackScheduler;
import com.example.pleosstarter.ui.MapRevisitDisplay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 실제 재방문 서비스와 화면 연결을 사용하고 표시·예약 경계만 대체한다. */
public final class MapRevisitFlowProbe {
    private static int assertions;

    /** 합의된 재방문·지도 선택 경계에서 사용자에게 보이는 결과를 확인한다. */
    public static void main(String[] arguments) {
        restoredRecordAppearsOnceAndOpens();
        cardExpiresWithoutRepeating();
        drawingAndModalsDeferUntilStillNearby();
        lifecycleAndRecordChangesDiscardOldCards();
        vehicleAndTestMovementUseIndependentVisits();
        locationLossDoesNotReplayOldPosition();
        System.out.println("PASS MapRevisitFlowProbe assertions=" + assertions);
    }

    /** 복원한 기록 근처에서 카드 하나가 나타나고 누르면 같은 저장 기록이 열린다. */
    private static void restoredRecordAppearsOnceAndOpens() {
        Fixture fixture = new Fixture();
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == fixture.note, "nearby saved note appears on map");
        Runnable open = fixture.display.open;
        open.run();
        check(fixture.opened.equals(List.of(fixture.note)), "card opens the same record");
        check(fixture.display.visible == null, "opening consumes the card");
        open.run();
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.opened.size() == 1 && fixture.display.visible == null,
                "old tap and nearby repeated updates do not reopen or redisplay");
    }

    /** 8초 표시가 끝나도 머무는 동안 반복하지 않고 실제 이탈 후에만 다시 표시한다. */
    private static void cardExpiresWithoutRepeating() {
        Fixture fixture = new Fixture();
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        fixture.scheduler.advance(7999);
        check(fixture.display.visible == fixture.note, "card remains before eight seconds");
        fixture.scheduler.advance(1);
        check(fixture.display.visible == null, "card disappears at eight seconds");
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.showCount == 1, "expiry does not repeat during same visit");
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.showCount == 2, "return after exit produces next card");
    }

    /** 입력 중에는 기다리고 종료 때도 근처인 기록만 보여주며 이미 뜬 카드는 즉시 가린다. */
    private static void drawingAndModalsDeferUntilStillNearby() {
        Fixture fixture = new Fixture();
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.setInteractionBlocked(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == null, "drawing or modal blocks card presentation");
        fixture.coordinator.setInteractionBlocked(false);
        check(fixture.display.visible == fixture.note, "still-nearby record appears after input");
        fixture.coordinator.setInteractionBlocked(true);
        check(fixture.display.visible == null && fixture.scheduler.tasks.isEmpty(),
                "new interaction hides card and cancels expiry");
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.setInteractionBlocked(false);
        check(fixture.display.visible == null, "already-passed record does not pop up after modal");
    }

    /** 가려진 화면·삭제한 기록·종료한 화면의 카드와 오래된 선택을 제거한다. */
    private static void lifecycleAndRecordChangesDiscardOldCards() {
        Fixture fixture = new Fixture();
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        Runnable staleOpen = fixture.display.open;
        fixture.coordinator.setForeground(false);
        check(fixture.display.visible == null, "background hides private record");
        staleOpen.run();
        check(fixture.opened.isEmpty(), "background rejects old click");
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == null, "ignored background fixes do not invent reentry");
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        staleOpen = fixture.display.open;
        fixture.coordinator.replaceRecords(List.of());
        staleOpen.run();
        check(fixture.display.visible == null && fixture.opened.isEmpty(), "removed record invalidates card");
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        fixture.coordinator.close();
        fixture.scheduler.advance(10000);
        fixture.coordinator.onVehiclePosition(37.402, 127.1);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == null && fixture.scheduler.tasks.isEmpty(), "closed screen stays silent");
    }

    /** 시험 좌표가 멀리 이동해도 실제 차량의 같은 방문을 다시 알리지 않는다. */
    private static void vehicleAndTestMovementUseIndependentVisits() {
        Fixture fixture = new Fixture();
        MapRecordMarker favorite = new MapRecordMarker(37.5, 127.1, true,
                new LocationNote(37.5, 127.1, "여행에서 찾은 가게"), "travel", -1);
        fixture.coordinator.replaceRecords(List.of(fixture.note, favorite));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        fixture.display.dismiss.run();
        fixture.coordinator.setTestMovementActive(true);
        fixture.coordinator.onTestPosition(37.5, 127.1);
        check(fixture.display.visible == favorite, "test movement also presents nearby favorite");
        fixture.coordinator.setInteractionBlocked(true);
        fixture.coordinator.onTestPosition(37.502, 127.1);
        fixture.coordinator.setInteractionBlocked(false);
        check(fixture.display.visible == null, "unblock uses latest test position");
        fixture.coordinator.onTestPosition(37.5, 127.1);
        check(fixture.display.visible == favorite, "test movement rearms after actual test exit");
        fixture.coordinator.setTestMovementActive(false);
        check(fixture.display.visible == null, "source change discards previous card");
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == null, "test distance did not rearm vehicle visit");
    }

    /** 위치 유실 뒤에는 이전 카드와 보류 좌표를 버리고 새 수신을 기다린다. */
    private static void locationLossDoesNotReplayOldPosition() {
        Fixture fixture = new Fixture();
        fixture.coordinator.replaceRecords(List.of(fixture.note));
        fixture.coordinator.setReady(true);
        fixture.coordinator.setForeground(true);
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        fixture.coordinator.onVehicleUnavailable();
        check(fixture.display.visible == null, "lost vehicle position hides card");
        fixture.coordinator.setInteractionBlocked(true);
        fixture.coordinator.setInteractionBlocked(false);
        check(fixture.display.visible == null, "unblock does not reuse unavailable position");
        fixture.coordinator.onVehiclePosition(37.4, 127.1);
        check(fixture.display.showCount == 1, "recovering same position does not rearm visit");
        fixture.coordinator.setTestMovementActive(true);
        fixture.coordinator.onTestPosition(37.4, 127.1);
        fixture.coordinator.onVehicleUnavailable();
        check(fixture.display.visible == fixture.note, "vehicle failure does not cancel valid test card");
    }

    /** 개별 공개 행동의 결과를 누적하고 첫 실패에서 종료한다. */
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 시험별 실제 서비스와 외부 표시·시간 경계를 새로 구성한다. */
    private static final class Fixture {
        private final MapRecordMarker note = new MapRecordMarker(37.4, 127.1, false,
                new LocationNote(37.4, 127.1, "다음에 들를 곳"), "", 0);
        private final Display display = new Display();
        private final Scheduler scheduler = new Scheduler();
        private final List<MapRecordMarker> opened = new ArrayList<>();
        private final MapRevisitCoordinator coordinator = new MapRevisitCoordinator(
                new LocalRecordRevisitService(), new LocalRecordRevisitService(),
                display, scheduler, opened::add);
    }

    /** Android 화면 대신 사용자가 보게 되는 카드와 선택 동작을 보관한다. */
    private static final class Display implements MapRevisitDisplay {
        private MapRecordMarker visible;
        private Runnable open;
        private Runnable dismiss;
        private int showCount;

        /** 표시된 카드와 사용자에게 제공한 동작을 기록한다. */
        @Override
        public void showRecord(MapRecordMarker marker, Runnable open, Runnable dismiss) {
            visible = marker;
            this.open = open;
            this.dismiss = dismiss;
            showCount++;
        }

        /** 화면에서 카드와 클릭 대상을 제거한다. */
        @Override
        public void hideRecord() {
            visible = null;
            open = null;
            dismiss = null;
        }
    }

    /** 지연된 UI 동작만 가상 시간으로 제어한다. */
    private static final class Scheduler implements PlaybackScheduler {
        private final Map<Runnable, Long> tasks = new LinkedHashMap<>();
        private long now;

        /** 요청된 지연이 지난 시각에만 실행하도록 등록한다. */
        @Override
        public void schedule(Runnable task, long delayMillis) {
            tasks.put(task, now + delayMillis);
        }

        /** 제거된 화면의 지연 작업을 취소한다. */
        @Override
        public void cancel(Runnable task) {
            tasks.remove(task);
        }

        /** 정해진 시간이 지난 외부 예약 작업을 한 번씩 실행한다. */
        private void advance(long millis) {
            now += millis;
            List<Runnable> due = new ArrayList<>();
            for (Map.Entry<Runnable, Long> task : tasks.entrySet()) {
                if (task.getValue() <= now) {
                    due.add(task.getKey());
                }
            }
            for (Runnable task : due) {
                tasks.remove(task);
                task.run();
            }
        }
    }
}
