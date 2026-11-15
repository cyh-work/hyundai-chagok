package com.example.pleosstarter.notes;

import com.example.pleosstarter.kakao.MapRecordMarker;
import com.example.pleosstarter.records.LocalRecordRevisitService;
import com.example.pleosstarter.records.MapRecordFailure;
import com.example.pleosstarter.records.RecordRevisitService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 원·별 스냅샷과 위치라는 공개 경계로 재방문 카드의 표시 결정을 검증한다. */
public final class RecordRevisitProbe {
    private static int assertions;

    /** 독립적인 사용자 이동 흐름을 실제 재방문 서비스로 실행한다. */
    public static void main(String[] arguments) {
        restoredRecordsEnterOnFirstPosition();
        nearestRecordConsumesTheCurrentVisit();
        blockedPresentationWaitsForAFreshNearbyPosition();
        successfulSnapshotChangesRequireANewExit();
        remainingNotesKeepTheirVisitStateAcrossIndexChanges();
        duplicateNotesShareOneVisitWithoutLosingRemainingState();
        invalidInputsPreserveTheLastGoodSnapshot();
        textChangesAndEmptyFavoritesUseTheSameRules();
        positionSourcesKeepIndependentVisitState();
        System.out.println("PASS assertions=" + assertions);
    }

    /** 글자 수정도 새로운 저장으로 억제하고 원문 없는 이전 별은 그대로 재방문할 수 있다. */
    private static void textChangesAndEmptyFavoritesUseTheSameRules() {
        MapRecordMarker[] previous = {
                note(0, 0, "수정 전 메모", 0),
                favorite("edited-favorite", 0, 0, "수정 전 별")
        };
        MapRecordMarker[] changed = {
                note(0, 0, "수정 후 메모", 0),
                favorite("edited-favorite", 0, 0, "수정 후 별")
        };
        for (int index = 0; index < previous.length; index++) {
            RecordRevisitService service = new LocalRecordRevisitService();
            service.replaceRecords(List.of(previous[index]));
            service.replaceRecords(List.of(previous[index]));
            check(service.onPosition(0, 0, true).orElseThrow().equals(previous[index]), "unchanged restoration refresh remains eligible");
            service.onPosition(0.002, 0, true);
            service.replaceRecords(List.of(changed[index]));
            check(service.onPosition(0, 0, true).isEmpty(), "edited text waits for another full exit");
            service.onPosition(0.002, 0, true);
            check(service.onPosition(0, 0, true).orElseThrow().equals(changed[index]), "revisited edited record returns latest text reference");
        }
        MapRecordMarker empty = favorite("legacy-empty", 0, 0, "");
        RecordRevisitService service = new LocalRecordRevisitService();
        service.replaceRecords(List.of(empty));
        check(service.onPosition(0, 0, true).orElseThrow().equals(empty), "legacy favorite without note remains visitable");
    }

    /** 실제 위치와 시험 재생이 서로 다른 서비스일 때 가상의 이탈을 공유하지 않는다. */
    private static void positionSourcesKeepIndependentVisitState() {
        MapRecordMarker note = note(0, 0, "소스 전환 기록", 0);
        RecordRevisitService actual = new LocalRecordRevisitService();
        RecordRevisitService playback = new LocalRecordRevisitService();
        actual.replaceRecords(List.of(note));
        playback.replaceRecords(List.of(note));

        check(actual.onPosition(0, 0, true).orElseThrow().equals(note), "actual source first visit is eligible");
        actual.resetPosition();
        check(playback.onPosition(0.002, 0, true).isEmpty(), "playback exit has no actual-source effect");
        check(playback.onPosition(0, 0, true).orElseThrow().equals(note), "playback source owns its first visit");
        playback.resetPosition();
        check(actual.onPosition(0, 0, true).isEmpty(), "returning to actual source cannot repeat from playback movement");
        actual.onPosition(0.002, 0, true);
        check(actual.onPosition(0, 0, true).orElseThrow().equals(note), "actual full exit permits actual reentry");
        check(playback.onPosition(0, 0, true).isEmpty(), "actual exit cannot rearm playback source");
    }

    /** 잘못된 위치·목록은 공통 입력 오류로 거부하고 유효한 목록과 방문 상태를 보존한다. */
    private static void invalidInputsPreserveTheLastGoodSnapshot() {
        MapRecordMarker note = note(0, 0, "보존할 기록", 0);
        RecordRevisitService service = new LocalRecordRevisitService();
        List<MapRecordMarker> callerOwned = new ArrayList<>(List.of(note));
        service.replaceRecords(callerOwned);
        callerOwned.clear();

        double[][] invalidPositions = {
                {Double.NaN, 0}, {0, Double.NaN}, {Double.POSITIVE_INFINITY, 0},
                {0, Double.NEGATIVE_INFINITY}, {90.01, 0}, {-90.01, 0}, {0, 180.01}, {0, -180.01}
        };
        for (double[] position : invalidPositions) {
            expectInvalid(() -> service.onPosition(position[0], position[1], true));
        }
        expectInvalid(() -> service.replaceRecords(null));
        expectInvalid(() -> service.replaceRecords(Arrays.asList(note, null)));
        expectInvalid(() -> service.replaceRecords(List.of(new MapRecordMarker(0, 0, true, null))));
        check(service.onPosition(0, 0, true).orElseThrow().equals(note), "invalid inputs and caller list mutation preserve last good snapshot");
        expectInvalid(() -> service.onPosition(Double.NaN, 0, false));
        check(service.onPosition(0, 0, true).isEmpty(), "invalid update cannot rearm consumed visit");
        check(service.onPosition(-90, 180, false).isEmpty(), "valid south pole and date line accepted");
        check(service.onPosition(90, -180, false).isEmpty(), "valid north pole and date line accepted");
        check(service.onPosition(0, 0, true).orElseThrow().equals(note), "valid distant boundary positions rearm visit");
    }

    /** 동일 값 복원 메모는 함께 소비하고 이동으로 중복이 늘면 새 이탈을 기다린다. */
    private static void duplicateNotesShareOneVisitWithoutLosingRemainingState() {
        MapRecordMarker first = note(0, 0, "같은 값 메모", 0);
        MapRecordMarker duplicate = note(0, 0, "같은 값 메모", 1);
        RecordRevisitService service = new LocalRecordRevisitService();
        service.replaceRecords(List.of(first, duplicate));
        check(service.onPosition(0, 0, true).orElseThrow().equals(first), "restored duplicates choose first current reference once");
        check(service.onPosition(0, 0, true).isEmpty(), "restored duplicate cannot produce another card");
        service.replaceRecords(List.of(first));
        check(service.onPosition(0, 0, true).isEmpty(), "deleting one duplicate preserves remaining suppression");

        service.onPosition(0.002, 0, true);
        service.replaceRecords(List.of(first, duplicate));
        check(service.onPosition(0, 0, true).isEmpty(), "adding duplicate by movement suppresses the same-value group until exit");
        service.onPosition(0.002, 0, true);
        service.replaceRecords(List.of(first));
        check(service.onPosition(0, 0, true).orElseThrow().equals(first), "deleting duplicate preserves remaining rearmed state");
    }

    /** 다른 메모 삭제로 저장 순번만 바뀌어도 남은 기록의 재방문 자격과 최신 참조를 보존한다. */
    private static void remainingNotesKeepTheirVisitStateAcrossIndexChanges() {
        MapRecordMarker removed = note(0, 0, "삭제할 앞 메모", 0);
        MapRecordMarker remaining = note(1, 0, "남겨 둔 메모", 1);
        RecordRevisitService service = new LocalRecordRevisitService();
        service.replaceRecords(List.of(removed, remaining));
        check(service.onPosition(0, 0, true).orElseThrow().equals(removed), "first note is visited before deletion");

        MapRecordMarker reindexed = note(1, 0, "남겨 둔 메모", 0);
        service.replaceRecords(List.of(reindexed));
        check(service.onPosition(1, 0, true).orElseThrow().equals(reindexed), "index-only change preserves unvisited state and returns current index");
        service.replaceRecords(List.of(note(1, 0, "남겨 둔 메모", 3)));
        check(service.onPosition(1, 0, true).isEmpty(), "index-only change also preserves consumed state");
    }

    /** 새 저장·이동·삭제는 최신 목록으로 반영하고 저장 직후를 재방문으로 알리지 않는다. */
    private static void successfulSnapshotChangesRequireANewExit() {
        MapRecordMarker[] initial = {
                note(0, 0, "새로 쓴 메모", 0),
                favorite("new-favorite", 0, 0, "새로 쓴 별")
        };
        MapRecordMarker[] moved = {
                note(1, 0, "새로 쓴 메모", 0),
                favorite("new-favorite", 1, 0, "새로 쓴 별")
        };
        for (int index = 0; index < initial.length; index++) {
            RecordRevisitService service = new LocalRecordRevisitService();
            service.replaceRecords(List.of());
            service.replaceRecords(List.of(initial[index]));
            check(service.onPosition(0, 0, true).isEmpty(), "new saved record suppressed after initially empty restoration");
            service.onPosition(0.002, 0, true);
            check(service.onPosition(0, 0, true).orElseThrow().equals(initial[index]), "new record becomes eligible after exit");

            service.replaceRecords(List.of(initial[index]));
            check(service.onPosition(0, 0, true).isEmpty(), "unchanged refresh preserves consumed visit");
            service.replaceRecords(List.of(moved[index]));
            check(service.onPosition(1, 0, true).isEmpty(), "moved record suppressed at its new position");
            check(service.onPosition(0, 0, true).isEmpty(), "old position cannot show moved record");
            check(service.onPosition(1, 0, true).orElseThrow().equals(moved[index]), "moved record shows only after leaving new position");

            service.replaceRecords(List.of());
            check(service.onPosition(1, 0, true).isEmpty(), "deleted record cannot be returned");
            service.replaceRecords(List.of(moved[index]));
            check(service.onPosition(1, 0, true).isEmpty(), "same-value recreation is a new suppressed record");
        }
    }

    /** 모달·그리기 중에는 소비하지 않고 표시가 풀린 시점의 실제 근접 여부를 다시 확인한다. */
    private static void blockedPresentationWaitsForAFreshNearbyPosition() {
        MapRecordMarker note = note(0, 0, "대기 중인 메모", 0);
        MapRecordMarker favorite = favorite("blocked-favorite", 0, 0.0004, "대기 중인 별");
        RecordRevisitService service = new LocalRecordRevisitService();
        service.replaceRecords(List.of(favorite, note));

        check(service.onPosition(0, 0, false).isEmpty(), "blocked presentation does not display");
        check(service.onPosition(0, 0, false).isEmpty(), "blocked nearby update does not consume");
        check(service.onPosition(0.001, 0, true).isEmpty(), "unblocking beyond 100m cannot show an old pending visit");
        check(service.onPosition(0, 0, true).orElseThrow().equals(note), "still eligible nearest shows after unblocking nearby");
        service.resetPosition();
        check(service.onPosition(0, 0, true).isEmpty(), "reset preserves consumed note and favorite");
        check(service.onPosition(0.002, 0, false).isEmpty(), "blocked position still observes full exit");
        check(service.onPosition(0, 0, false).isEmpty(), "blocked reentry stays unconsumed");
        service.resetPosition();
        check(service.onPosition(0.001, 0, true).isEmpty(), "reset cannot replay prior blocked position outside entry radius");
        check(service.onPosition(0, 0, true).orElseThrow().equals(note), "fresh nearby position is eligible after observed exit");
    }

    /** 한 번의 진입에서는 가장 가까운 원·별만 보여 주고 충분히 벗어난 뒤 다시 허용한다. */
    private static void nearestRecordConsumesTheCurrentVisit() {
        MapRecordMarker nearest = note(0, 0, "가장 가까운 기록", 0);
        MapRecordMarker other = favorite("nearby-favorite", 0, 0.0004, "같이 지나가는 별");
        RecordRevisitService service = new LocalRecordRevisitService();
        service.replaceRecords(List.of(other, nearest));

        check(service.onPosition(0.00089, 0, true).orElseThrow().equals(nearest), "within 100m selects nearest across note and favorite");
        check(service.onPosition(0, 0, true).orElseThrow().equals(other), "later entry of another record can notify once");
        check(service.onPosition(0, 0, true).isEmpty(), "remaining nearby cannot repeat");
        check(service.onPosition(0.001, 0, true).isEmpty(), "111m does not rearm current visit");
        check(service.onPosition(0, 0, true).isEmpty(), "moving back inside 100m cannot repeat before full exit");
        check(service.onPosition(0.002, 0, true).isEmpty(), "outside 130m rearms without displaying");
        check(service.onPosition(0, 0, true).orElseThrow().equals(nearest), "return after full exit selects nearest");
        check(service.onPosition(0, 0, true).isEmpty(), "simultaneous nearby favorite is consumed with nearest note");
    }

    /** 기존 원·별은 앱 시작 후 처음 들어온 100m 안에서 표시할 수 있다. */
    private static void restoredRecordsEnterOnFirstPosition() {
        MapRecordMarker note = note(0, 0, "다시 올 곳", 0);
        RecordRevisitService notes = new LocalRecordRevisitService();
        notes.replaceRecords(List.of(note));
        check(notes.onPosition(0.002, 0, true).isEmpty(), "outside restored note does not enter");
        check(notes.onPosition(0, 0, true).orElseThrow().equals(note), "restored note enters on first nearby position");

        MapRecordMarker favorite = favorite("favorite-one", 0, 0, "다시 찾은 가게");
        RecordRevisitService favorites = new LocalRecordRevisitService();
        favorites.replaceRecords(List.of(favorite));
        check(favorites.onPosition(0.002, 0, true).isEmpty(), "outside restored favorite does not enter");
        check(favorites.onPosition(0, 0, true).orElseThrow().equals(favorite), "restored favorite enters on first nearby position");
    }

    /** 실제 메모 DTO와 저장 순번을 가진 지도 참조를 만든다. */
    private static MapRecordMarker note(double latitude, double longitude, String text, int index) {
        return new MapRecordMarker(latitude, longitude, false,
                new LocationNote(latitude, longitude, text), "", index);
    }

    /** 실제 즐겨찾기 식별자와 원문을 가진 지도 참조를 만든다. */
    private static MapRecordMarker favorite(String id, double latitude, double longitude, String text) {
        LocationNote note = text.isEmpty() ? null : new LocationNote(latitude, longitude, text);
        return new MapRecordMarker(latitude, longitude, true, note, id, -1);
    }

    /** 공개 경계의 입력 실패가 기록 서비스의 정해진 오류 코드로 반환되는지 확인한다. */
    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected INVALID_INPUT");
        } catch (MapRecordFailure failure) {
            check(failure.getCode() == MapRecordFailure.Code.INVALID_INPUT, "invalid input uses shared record failure");
        }
    }

    /** 공개 동작이 기대와 다르면 해당 시나리오를 실패로 종료한다. */
    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) {
            throw new AssertionError(message);
        }
    }
}
