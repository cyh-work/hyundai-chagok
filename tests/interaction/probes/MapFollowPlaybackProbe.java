package com.example.pleosstarter.kakao;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.records.LocalRecordRevisitService;
import com.example.pleosstarter.simulation.PlaybackScheduler;
import com.example.pleosstarter.simulation.RoutePath;
import com.example.pleosstarter.simulation.RoutePoint;
import com.example.pleosstarter.simulation.RouteSource;
import com.example.pleosstarter.ui.MapScreenView;
import com.example.pleosstarter.ui.MapRevisitDisplay;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 실제 지도·기록 조정자와 재생기를 연결해 입력·모달·화면 수명 계약을 검사한다. */
public final class MapFollowPlaybackProbe {
    private static int assertions;
    private static int scenarios;

    /** 합의한 추적·재생 경계를 서로 독립된 상태에서 실행한다. */
    public static void main(String[] arguments) {
        pointerKeepsPlaybackMoving();
        followWaitsUntilSixSeconds();
        recordModalKeepsPlaybackAndDefersFollow();
        placeModalKeepsPlaybackAndDefersFollow();
        manualPauseSurvivesModalAndInput();
        foregroundExcludesHiddenTime();
        closeRejectsLateRouteAndFollow();
        playbackShowsRevisitAndKeepsMovingWhenOpened();
        drawingDefersPlaybackRevisitUntilReleased();
        recordModalDefersPlaybackRevisit();
        placeModalDoesNotShowAlreadyPassedRevisit();
        sourceSwitchKeepsVehicleAndTestVisitsIndependent();
        System.out.println("PASS MapFollowPlaybackProbe scenarios=" + scenarios + " assertions=" + assertions);
    }

    /** 손가락을 누른 동안 카메라는 고정되고 위치 샘플은 계속 전진한다. */
    private static void pointerKeepsPlaybackMoving() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.coordinator.onPointerDown();
        int previousSamples = fixture.positions.size();
        fixture.time.advance(1000);
        check(fixture.positions.size() == previousSamples + 10, "pointer does not stop position playback");
        check(!fixture.following, "pointer holds map camera only");
        fixture.coordinator.onStrokeFinished();
        check(fixture.following, "tap restores previous following state");
        scenarios++;
    }

    /** 두 손가락 탐색 뒤 6초 전에는 복귀하지 않고 정확히 유예 뒤 추적한다. */
    private static void followWaitsUntilSixSeconds() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.pan();
        fixture.time.advance(5999);
        check(fixture.followRequests == 0, "no follow before six seconds");
        fixture.time.advance(1);
        check(fixture.followRequests == 1 && fixture.following, "follow resumes at six seconds");
        scenarios++;
    }

    /** 기록 모달을 읽는 동안 이동은 계속되고 닫은 뒤 새 유예 시간을 준다. */
    private static void recordModalKeepsPlaybackAndDefersFollow() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.pan();
        fixture.coordinator.setRecordDialogOpen(true);
        int previousSamples = fixture.positions.size();
        fixture.time.advance(9000);
        check(fixture.positions.size() == previousSamples + 90, "record modal does not pause playback");
        check(fixture.followRequests == 0, "record modal defers camera follow");
        fixture.coordinator.setRecordDialogOpen(false);
        fixture.time.advance(5999);
        check(fixture.followRequests == 0, "closing modal starts a fresh grace interval");
        fixture.time.advance(1);
        check(fixture.followRequests == 1, "closing record modal eventually resumes follow");
        scenarios++;
    }

    /** 주변 장소 목록도 재생을 유지하고 열린 동안 카메라 유휴 복귀를 보류한다. */
    private static void placeModalKeepsPlaybackAndDefersFollow() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.pan();
        fixture.coordinator.setPlaceDialogOpen(true);
        int previousSamples = fixture.positions.size();
        fixture.time.advance(9000);
        check(fixture.positions.size() == previousSamples + 90, "store modal does not pause playback");
        check(fixture.followRequests == 0, "store modal defers camera follow");
        fixture.coordinator.setPlaceDialogOpen(false);
        fixture.time.advance(6000);
        check(fixture.followRequests == 1, "closing store modal resumes follow after grace");
        scenarios++;
    }

    /** 사용자가 명시적으로 멈춘 재생은 모달·입력·추적 복귀로 다시 시작하지 않는다. */
    private static void manualPauseSurvivesModalAndInput() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.coordinator.toggleMovement();
        int previousSamples = fixture.positions.size();
        fixture.coordinator.onPointerDown();
        fixture.coordinator.onStrokeFinished();
        fixture.coordinator.setRecordDialogOpen(true);
        fixture.coordinator.setRecordDialogOpen(false);
        fixture.time.advance(7000);
        check(fixture.positions.size() == previousSamples && fixture.screen.paused,
                "explicit pause survives input and modal closure");
        fixture.coordinator.toggleMovement();
        fixture.time.advance(1000);
        check(fixture.positions.size() > previousSamples, "explicit resume continues playback");
        scenarios++;
    }

    /** 화면이 가려진 시간은 위치 진행에 포함하지 않으며 복귀 뒤 현재 위치에서 이어간다. */
    private static void foregroundExcludesHiddenTime() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.coordinator.setForeground(false);
        RoutePoint last = fixture.positions.get(fixture.positions.size() - 1);
        int previousSamples = fixture.positions.size();
        fixture.time.advance(60000);
        check(fixture.positions.size() == previousSamples, "hidden screen produces no position samples");
        fixture.coordinator.setForeground(true);
        check(fixture.positions.get(fixture.positions.size() - 1).equals(last),
                "foreground does not add hidden time to traveled distance");
        fixture.time.advance(100);
        check(!fixture.positions.get(fixture.positions.size() - 1).equals(last), "visible playback continues");
        scenarios++;
    }

    /** 종료된 화면은 늦은 경로 응답과 남은 타이머로 지도·재생을 다시 켜지 않는다. */
    private static void closeRejectsLateRouteAndFollow() {
        Fixture fixture = new Fixture();
        fixture.start();
        fixture.pan();
        RouteSource.Callback oldRequest = fixture.routes.callback;
        int previousSamples = fixture.positions.size();
        fixture.coordinator.close();
        oldRequest.onSuccess(path());
        fixture.time.advance(60000);
        check(fixture.positions.size() == previousSamples, "closed screen ignores late route response");
        check(fixture.followRequests == 0, "closed screen never resumes following");
        check(fixture.time.jobs.isEmpty(), "closed screen leaves no timer");
        scenarios++;
    }

    /** 시험 이동으로 가까워진 기록을 열어도 재생과 지도 추적을 임의로 멈추지 않는다. */
    private static void playbackShowsRevisitAndKeepsMovingWhenOpened() {
        Fixture fixture = new Fixture();
        MapRecordMarker note = savedNote(37.401, 127.101);
        fixture.enableRevisits(List.of(note));
        fixture.start();
        check(fixture.display.visible == null, "distant record has no revisit card at route start");

        fixture.time.advance(6000);
        check(fixture.display.visible == note, "actual playback enters the saved record radius");
        check(fixture.following && fixture.followRequests == 0,
                "showing a revisit card leaves the camera following state untouched");
        int previousSamples = fixture.positions.size();
        Runnable open = fixture.display.open;
        open.run();
        check(fixture.opened.equals(List.of(note)) && fixture.display.visible == null,
                "card tap delivers the displayed saved record to its existing modal boundary");
        fixture.time.advance(1000);
        check(fixture.positions.size() == previousSamples + 10 && !fixture.screen.paused,
                "opening the revisit record modal does not stop playback");
        check(fixture.following && fixture.followRequests == 0,
                "opening the card does not request a separate map move");
        open.run();
        check(fixture.opened.size() == 1, "old card tap cannot open a second modal");
        scenarios++;
    }

    /** 그리는 중 실제 시험 위치가 기록에 진입해도 손을 뗄 때까지 카드만 보류한다. */
    private static void drawingDefersPlaybackRevisitUntilReleased() {
        Fixture fixture = new Fixture();
        MapRecordMarker note = savedNote(37.401, 127.101);
        fixture.enableRevisits(List.of(note));
        fixture.start();
        fixture.coordinator.onPointerDown();
        int previousSamples = fixture.positions.size();
        fixture.time.advance(6000);
        check(fixture.display.visible == null && fixture.display.showCount == 0,
                "pointer input defers a revisit reached by ongoing playback");
        check(fixture.positions.size() == previousSamples + 60,
                "deferred revisit does not pause the route timeline");
        fixture.coordinator.onStrokeFinished();
        check(fixture.display.visible == note, "release presents a still-nearby saved record");

        Runnable oldOpen = fixture.display.open;
        fixture.coordinator.onPointerDown();
        check(fixture.display.visible == null, "new drawing immediately hides an existing revisit card");
        oldOpen.run();
        fixture.coordinator.onStrokeFinished();
        check(fixture.opened.isEmpty() && fixture.display.showCount == 1,
                "drawing rejects a stale card click and does not repeat the same visit");
        scenarios++;
    }

    /** 다른 기록을 읽는 동안 진입한 장소는 닫은 뒤에도 근처일 때만 카드로 표시한다. */
    private static void recordModalDefersPlaybackRevisit() {
        Fixture fixture = new Fixture();
        MapRecordMarker note = savedNote(37.401, 127.101);
        fixture.enableRevisits(List.of(note));
        fixture.start();
        fixture.coordinator.setRecordDialogOpen(true);
        int previousSamples = fixture.positions.size();
        fixture.time.advance(6000);
        check(fixture.display.showCount == 0, "record modal defers newly reached revisit card");
        check(fixture.positions.size() == previousSamples + 60,
                "record modal and deferred revisit preserve position updates");
        fixture.coordinator.setRecordDialogOpen(false);
        check(fixture.display.visible == note, "closing record modal shows the nearby saved text");
        scenarios++;
    }

    /** 장소 목록을 보는 사이 지나친 기록은 목록을 닫아도 뒤늦게 꺼내지 않는다. */
    private static void placeModalDoesNotShowAlreadyPassedRevisit() {
        Fixture fixture = new Fixture();
        MapRecordMarker note = savedNote(37.401, 127.101);
        fixture.enableRevisits(List.of(note));
        fixture.start();
        fixture.coordinator.setPlaceDialogOpen(true);
        int previousSamples = fixture.positions.size();
        fixture.time.advance(6000);
        check(fixture.display.showCount == 0, "place modal blocks a nearby revisit card");
        fixture.time.advance(34000);
        check(fixture.positions.size() == previousSamples + 400,
                "place modal does not stop playback while passing the saved record");
        fixture.coordinator.setPlaceDialogOpen(false);
        check(fixture.display.visible == null && fixture.display.showCount == 0,
                "closing place modal discards a record already left behind");
        scenarios++;
    }

    /** 시험 시작·종료로 위치 출처가 바뀌어도 두 출처의 방문 이력을 섞지 않는다. */
    private static void sourceSwitchKeepsVehicleAndTestVisitsIndependent() {
        Fixture fixture = new Fixture();
        MapRecordMarker note = savedNote(37.4, 127.1);
        fixture.enableRevisits(List.of(note));
        fixture.revisits.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == note, "vehicle position presents its own first visit");
        Runnable vehicleOpen = fixture.display.open;
        Runnable vehicleDismiss = fixture.display.dismiss;

        fixture.coordinator.toggleMovement();
        check(fixture.display.visible == null, "starting test movement removes the vehicle card");
        vehicleOpen.run();
        check(fixture.opened.isEmpty(), "source switch rejects the old vehicle card click");
        fixture.routes.callback.onSuccess(path());
        fixture.time.advance(500);
        check(fixture.display.visible == note && fixture.display.showCount == 2,
                "test position has an independent first visit to the same record");
        vehicleOpen.run();
        check(fixture.opened.isEmpty() && fixture.display.visible == note,
                "old vehicle card callback cannot open the new test-source card for the same record");
        vehicleDismiss.run();
        check(fixture.display.visible == note,
                "old vehicle dismiss callback cannot remove the new test-source card");

        // 시험 중 차량 공급이 멀어져도 차량 방문의 이탈로 간주하지 않는다.
        fixture.revisits.onVehiclePosition(37.41, 127.11);
        fixture.coordinator.stopMovement();
        check(fixture.display.visible == null, "stopping test movement removes its previous card");
        int previousSamples = fixture.positions.size();
        fixture.time.advance(1000);
        check(fixture.positions.size() == previousSamples, "stopped test source emits no new positions");
        fixture.revisits.onTestPosition(37.41, 127.11);
        fixture.revisits.onVehiclePosition(37.4, 127.1);
        check(fixture.display.showCount == 2 && fixture.display.visible == null,
                "ignored vehicle fixes and old test fixes cannot rearm the vehicle visit");
        fixture.revisits.onVehiclePosition(37.402, 127.1);
        fixture.revisits.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == note && fixture.display.showCount == 3,
                "fresh vehicle exit and return produces the next vehicle card");
        fixture.display.dismiss.run();
        fixture.revisits.onVehiclePosition(37.4, 127.1);
        check(fixture.display.visible == null && fixture.display.showCount == 3,
                "current card dismiss closes it without repeating the same visit");
        scenarios++;
    }

    /** 실제 복원 기록과 같은 저장 참조를 가진 메모를 공개 DTO로 구성한다. */
    private static MapRecordMarker savedNote(double latitude, double longitude) {
        return new MapRecordMarker(latitude, longitude, false,
                new LocationNote(latitude, longitude, "다시 들를 장소"), "", 0);
    }

    /** 정해진 도로 선분을 외부 경로 응답의 독립 기준으로 사용한다. */
    private static RoutePath path() {
        return new RoutePath(List.of(new RoutePoint(37.4, 127.1), new RoutePoint(37.41, 127.11)));
    }

    /** 동작별 외부 관측 결과를 검사하고 실제 실행한 단언만 센다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    /** 화면·지도 SDK·시계만 경계로 대체하며 두 조정자와 재생기는 실제 제품을 사용한다. */
    private static final class Fixture {
        final Time time = new Time();
        final Routes routes = new Routes();
        final MapScreenView screen = new MapScreenView();
        final List<RoutePoint> positions = new ArrayList<>();
        final Display display = new Display();
        final List<MapRecordMarker> opened = new ArrayList<>();
        final MapInteractionCoordinator coordinator;
        MapRevisitCoordinator revisits;
        boolean following = true;
        boolean previousFollowing;
        int followRequests;

        /** 저장 동작을 하지 않는 카메라·재생 경계에 실제 기록 조정자를 연결한다. */
        Fixture() {
            PlaceMapSession map = (PlaceMapSession) Proxy.newProxyInstance(
                    PlaceMapSession.class.getClassLoader(), new Class<?>[]{PlaceMapSession.class},
                    (proxy, method, arguments) -> {
                        switch (method.getName()) {
                            case "getSearchCenter":
                                return new KakaoPlaceSelection(0, "시험 위치", 37.4, 127.1);
                            case "beginMapInteraction":
                                previousFollowing = following;
                                following = false;
                                return null;
                            case "finishMapInteraction":
                                if ((Boolean) arguments[0]) {
                                    following = previousFollowing;
                                }
                                return null;
                            case "showCurrentLocation":
                                followRequests++;
                                following = true;
                                return null;
                            case "beginTestMovement":
                                following = true;
                                return null;
                            case "showTestPosition":
                                positions.add(new RoutePoint((Double) arguments[0], (Double) arguments[1]));
                                return null;
                            case "endTestMovement":
                                return null;
                            case "endRecordDrag":
                                return Optional.empty();
                            default:
                                throw new AssertionError("Unexpected map boundary: " + method.getName());
                        }
                    });
            MapRecordCoordinator records = new MapRecordCoordinator(null, map, null, null, null,
                    message -> {
                        // 이번 경계는 기록 저장 피드백을 발생시키지 않는다.
                    }, null, null, null);
            coordinator = new MapInteractionCoordinator(map, records, screen, routes, time, () -> time.now);
            coordinator.setForeground(true);
        }

        /** 실제 두 출처의 방문 서비스와 지도 조정자를 연결하고 모달 표시 통지만 대체한다. */
        void enableRevisits(List<MapRecordMarker> records) {
            revisits = new MapRevisitCoordinator(new LocalRecordRevisitService(),
                    new LocalRecordRevisitService(), display, time, marker -> {
                        opened.add(marker);
                        coordinator.setRecordDialogOpen(true);
                    });
            revisits.replaceRecords(records);
            revisits.setReady(true);
            coordinator.setRevisitCoordinator(revisits);
        }

        /** 도로 응답을 받아 정상 재생 중인 기준 상태를 구성한다. */
        void start() {
            coordinator.toggleMovement();
            routes.callback.onSuccess(path());
            time.advance(500);
        }

        /** 공개 입력 콜백으로 지도 탐색 완료를 알린다. */
        void pan() {
            coordinator.onPointerDown();
            coordinator.onMapPanStarted();
            coordinator.onStrokeFinished();
        }
    }

    /** Android 카드 출력과 클릭 입력만 보관하며 재방문 후보나 소비 상태를 판단하지 않는다. */
    private static final class Display implements MapRevisitDisplay {
        MapRecordMarker visible;
        Runnable open;
        Runnable dismiss;
        int showCount;

        /** 실제 표시 요청에 들어온 저장 기록과 열기 동작을 관측한다. */
        @Override
        public void showRecord(MapRecordMarker marker, Runnable open, Runnable dismiss) {
            visible = marker;
            this.open = open;
            this.dismiss = dismiss;
            showCount++;
        }

        /** 카드가 닫히면 사용자가 누를 수 있는 입력도 제거한다. */
        @Override
        public void hideRecord() {
            visible = null;
            open = null;
            dismiss = null;
        }
    }

    /** 외부 경로 조회만 제어하고 실제 재생 로직에는 개입하지 않는다. */
    private static final class Routes implements RouteSource {
        Callback callback;

        /** 호출 시점과 비동기 응답 시점을 분리한다. */
        public void load(RoutePoint origin, RoutePoint destination, Callback supplied) {
            callback = supplied;
        }

        /** 취소 후 늦은 응답 검증을 위해 이전 콜백은 시험 안에 보관한다. */
        public void cancel() {
        }

        /** 이 경계에는 실제 네트워크 자원이 없다. */
        public void close() {
        }
    }

    /** 제품의 타이머 취소와 가상 시각을 동일 순서로 실행한다. */
    private static final class Time implements PlaybackScheduler {
        final Map<Runnable, Long> jobs = new LinkedHashMap<>();
        long now;

        /** 실제 예약처럼 한 타이머의 다음 실행 시각을 보관한다. */
        public void schedule(Runnable task, long delayMillis) {
            if (jobs.containsKey(task)) {
                throw new AssertionError("Duplicate scheduled task");
            }
            jobs.put(task, now + delayMillis);
        }

        /** 지정 타이머만 취소한다. */
        public void cancel(Runnable task) {
            jobs.remove(task);
        }

        /** 실제 대기 없이 시간순으로 예정 작업을 실행한다. */
        void advance(long milliseconds) {
            long target = now + milliseconds;
            while (true) {
                Map.Entry<Runnable, Long> next = null;
                for (Map.Entry<Runnable, Long> job : jobs.entrySet()) {
                    if (job.getValue() <= target && (next == null || job.getValue() < next.getValue())) {
                        next = job;
                    }
                }
                if (next == null) {
                    break;
                }
                Runnable task = next.getKey();
                now = next.getValue();
                jobs.remove(task);
                task.run();
            }
            now = target;
        }
    }
}
