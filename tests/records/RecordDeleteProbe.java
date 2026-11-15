package com.example.pleosstarter.notes;

import com.example.pleosstarter.records.FavoriteRepository;
import com.example.pleosstarter.records.HistoryMetadataFailure;
import com.example.pleosstarter.records.HistoryMetadataRepository;
import com.example.pleosstarter.records.LocalMapRecordsService;
import com.example.pleosstarter.records.MapHistoryEntry;
import com.example.pleosstarter.records.MapHistoryService;
import com.example.pleosstarter.records.MapRecordFailure;
import com.example.pleosstarter.records.SavedMapPoint;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 실제 제품 삭제 서비스의 단일 항목 선택·실패 보존·메타데이터 정합을 확인한다. */
public final class RecordDeleteProbe {
    private static int assertions;
    private static final LocationNote FIRST = new LocationNote(37, 127, "검증 메모 첫째");
    private static final LocationNote SECOND = new LocationNote(38, 128, "검증 메모 둘째");
    private static final LocationNote THIRD = new LocationNote(39, 129, "검증 메모 셋째");
    private static final SavedMapPoint FAVORITE = new SavedMapPoint("test-favorite", "검증 장소", 36, 126, "즐겨찾기 원문");

    /** 사용자 데이터 없이 정상·실패·중복·재진입·시각 계약을 모두 실행한다. */
    public static void main(String[] arguments) {
        noteDeletionCases();
        noteFailureCases();
        reentryCases();
        favoriteCases();
        historyPreservationCases();
        historyFailureCases();
        historySelectionCases();
        recreatedNoteReceivesNewCreationTime();
        reentryRadiusBoundaryCases();
        System.out.println("PASS RecordDelete assertions=" + assertions);
        System.out.println("Actual LocationNotes, LocalMapRecordsService, MapHistoryService and DTOs executed.");
        System.out.println("Injected memory repositories; no emulator, Android KeyStore or disk persistence exercised.");
    }

    /** 처음·중간·끝·마지막 및 같은 원문 복원 기록도 선택한 한 건만 제거한다. */
    private static void noteDeletionCases() {
        List<LocationNote> initial = List.of(FIRST, SECOND, THIRD);
        for (int selected = 0; selected < initial.size(); selected++) {
            MemoryNotes notes = new MemoryNotes(initial);
            LocalMapRecordsService records = service(notes, new MemoryFavorites());
            List<LocationNote> snapshot = records.notes();
            List<LocationNote> expected = new ArrayList<>(initial);
            expected.remove(selected);
            records.removeNote(selected, initial.get(selected));
            check(records.notes().equals(expected), "first/middle/last removes exact note");
            check(notes.entries.equals(expected) && notes.writes == 1, "one persistent write");
            check(snapshot.equals(initial), "previous snapshot unchanged");
            check(service(notes, new MemoryFavorites()).notes().equals(expected), "reload retains deletion");
        }

        MemoryNotes notes = new MemoryNotes(List.of(FIRST, SECOND, FIRST, FIRST));
        LocalMapRecordsService records = service(notes, new MemoryFavorites());
        records.removeNote(2, FIRST);
        check(records.notes().equals(List.of(FIRST, SECOND, FIRST)), "only selected duplicate is removed");
        records.removeNote(0, FIRST);
        check(records.notes().equals(List.of(SECOND, FIRST)), "remaining duplicate remains independent");
        records.removeNote(1, FIRST);
        records.removeNote(0, SECOND);
        check(records.notes().isEmpty() && notes.entries.isEmpty(), "last removal persists empty list");
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(0, SECOND));
        check(notes.writes == 4, "repeated removal never writes");
    }

    /** 무효 순번·원문 불일치·저장 실패에서 목록과 영속 스냅샷을 보존한다. */
    private static void noteFailureCases() {
        MemoryNotes notes = new MemoryNotes(List.of(FIRST, SECOND));
        LocalMapRecordsService records = service(notes, new MemoryFavorites());
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(-1, FIRST));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(2, FIRST));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(Integer.MAX_VALUE, FIRST));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(0, null));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(0, SECOND));
        check(notes.writes == 0 && notes.attempts == 0, "invalid note deletions avoid storage");

        notes.fail = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> records.removeNote(0, FIRST));
        check(records.notes().equals(List.of(FIRST, SECOND)), "write failure retains service snapshot");
        check(notes.entries.equals(List.of(FIRST, SECOND)) && notes.writes == 0, "write failure retains persisted notes");
        notes.fail = false;
        records.removeNote(0, FIRST);
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeNote(0, FIRST));
        check(records.notes().equals(List.of(SECOND)) && notes.writes == 1, "stale selection cannot delete next note");
    }

    /** 실패 시 재진입 억제를 유지하고 삭제 후 다른 메모·중복 메모의 상태를 보존한다. */
    private static void reentryCases() {
        MemoryNotes notes = new MemoryNotes(List.of(FIRST, FIRST, SECOND));
        LocationReentryTracker tracker = new LocationReentryTracker();
        LocationNotes locationNotes = new LocationNotes(notes, tracker);
        LocalMapRecordsService records = new LocalMapRecordsService(new MemoryFavorites(), locationNotes);
        check(records.onPosition(37, 127).equals(FIRST), "restored note initially enters");
        check(records.onPosition(37, 127) == null, "entered duplicate is suppressed");
        records.removeNote(0, FIRST);
        check(records.onPosition(37, 127) == null, "deleting duplicate keeps remaining suppression");
        notes.fail = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> records.removeNote(0, FIRST));
        check(records.onPosition(37, 127) == null, "failed deletion keeps suppression");
        notes.fail = false;
        records.onPosition(37.01, 127);
        check(records.onPosition(37, 127).equals(FIRST), "remaining duplicate can reenter after exit");
        records.removeNote(0, FIRST);
        check(records.onPosition(37, 127) == null, "deleted note no longer enters");
        check(records.onPosition(38, 128).equals(SECOND), "other note retains entry behavior");

        // 재생성한 기록도 공개 서비스의 이탈·재진입 흐름으로 다시 읽을 수 있다.
        LocationNote recreated = records.saveNote(FIRST.latitude(), FIRST.longitude(), FIRST.text());
        records.onPosition(37.01, 127);
        check(records.onPosition(37, 127).equals(recreated), "recreated note supports a later visit");
    }

    /** 기존 즐겨찾기 삭제 계약을 그대로 사용하며 실패와 잘못된 식별자를 확인한다. */
    private static void favoriteCases() {
        MemoryFavorites favorites = new MemoryFavorites();
        SavedMapPoint second = new SavedMapPoint("other", "다른 장소", 35, 125, "보존할 글");
        favorites.entries.addAll(List.of(FAVORITE, second));
        LocalMapRecordsService records = service(new MemoryNotes(List.of(FIRST)), favorites);
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeFavorite(null));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeFavorite(" "));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> records.removeFavorite("missing"));
        check(favorites.writes == 0, "invalid favorite deletion never writes");
        favorites.fail = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> records.removeFavorite(FAVORITE.id()));
        check(records.favorites().equals(List.of(FAVORITE, second)), "favorite write failure preserves cache");
        check(favorites.entries.equals(List.of(FAVORITE, second)), "favorite write failure preserves storage");
        favorites.fail = false;
        records.removeFavorite(FAVORITE.id());
        check(records.favorites().equals(List.of(second)) && favorites.writes == 1, "favorite selection deletes one ID");
        check(records.notes().equals(List.of(FIRST)), "favorite deletion never affects notes");
        check(service(new MemoryNotes(List.of()), favorites).favorites().equals(List.of(second)), "favorite deletion reloads");
    }

    /** 중복 발생 순번 변경에도 남은 생성 시각과 미확인 시각·즐겨찾기 시각을 보존한다. */
    private static void historyPreservationCases() {
        List<LocationNote> initial = List.of(FIRST, SECOND, FIRST, FIRST, THIRD);
        for (int selected = 0; selected < initial.size(); selected++) {
            MemoryNotes notes = new MemoryNotes(initial);
            MemoryFavorites favorites = new MemoryFavorites();
            favorites.entries.add(FAVORITE);
            LocalMapRecordsService records = service(notes, favorites);
            MemoryMetadata metadata = new MemoryMetadata();
            MapHistoryService history = new MapHistoryService(records, metadata, () -> 9999L);
            List<String> beforeIds = noteIds(history);
            List<Long> beforeTimes = List.of(100L, 200L, 0L, 400L, 500L);
            for (int index = 0; index < beforeIds.size(); index++) {
                if (beforeTimes.get(index) > 0) {
                    metadata.entries.put(beforeIds.get(index), beforeTimes.get(index));
                }
            }
            metadata.entries.put("favorite:" + FAVORITE.id(), 700L);
            metadata.entries.put("unrelated-metadata", 800L);

            List<LocationNote> beforeDelete = records.notes();
            records.removeNote(selected, initial.get(selected));
            history.notesDeleted(beforeDelete, selected);
            List<String> afterIds = noteIds(history);
            List<Long> expectedTimes = new ArrayList<>(beforeTimes);
            expectedTimes.remove(selected);
            for (int index = 0; index < afterIds.size(); index++) {
                check(metadata.entries.getOrDefault(afterIds.get(index), 0L).equals(expectedTimes.get(index)),
                        "survivor creation time follows exact original occurrence");
            }
            check(metadata.entries.get("favorite:" + FAVORITE.id()) == 700L, "favorite creation time unchanged");
            check(metadata.entries.get("unrelated-metadata") == 800L, "unrelated metadata unchanged");
            for (String oldId : beforeIds) {
                if (!afterIds.contains(oldId)) {
                    check(!metadata.entries.containsKey(oldId), "removed old occurrence metadata cleaned");
                }
            }
            check(metadata.writes <= 1, "metadata replacement occurs at most once");
        }

        MemoryNotes notes = new MemoryNotes(List.of(FIRST));
        LocalMapRecordsService records = service(notes, new MemoryFavorites());
        MemoryMetadata metadata = new MemoryMetadata();
        MapHistoryService history = new MapHistoryService(records, metadata, () -> 9999L);
        String id = noteIds(history).get(0);
        metadata.entries.put(id, 100L);
        List<LocationNote> beforeDelete = records.notes();
        records.removeNote(0, FIRST);
        history.notesDeleted(beforeDelete, 0);
        check(metadata.entries.isEmpty() && history.entries().isEmpty(), "final note time removed");

        MemoryFavorites favorites = new MemoryFavorites();
        favorites.entries.add(FAVORITE);
        records = service(new MemoryNotes(List.of(FIRST)), favorites);
        metadata = new MemoryMetadata();
        history = new MapHistoryService(records, metadata, () -> 9999L);
        metadata.entries.put("favorite:" + FAVORITE.id(), 100L);
        String noteId = noteIds(history).get(0);
        metadata.entries.put(noteId, 200L);
        records.removeFavorite(FAVORITE.id());
        history.favoriteDeleted(FAVORITE);
        check(metadata.entries.equals(Map.of(noteId, 200L)), "existing favorite metadata cleanup leaves note time");
    }

    /** 잘못된 삭제 전 스냅샷과 저장 실패는 시각 데이터를 바꾸지 않고 오류를 전달한다. */
    private static void historyFailureCases() {
        MemoryNotes notes = new MemoryNotes(List.of(FIRST, SECOND, THIRD));
        LocalMapRecordsService records = service(notes, new MemoryFavorites());
        MemoryMetadata metadata = new MemoryMetadata();
        MapHistoryService history = new MapHistoryService(records, metadata, () -> 9999L);
        List<LocationNote> beforeDelete = records.notes();
        List<String> ids = noteIds(history);
        for (int index = 0; index < ids.size(); index++) {
            metadata.entries.put(ids.get(index), 100L + index);
        }
        Map<String, Long> originalTimes = new HashMap<>(metadata.entries);
        expectMetadata(() -> history.notesDeleted(beforeDelete, 0));
        records.removeNote(1, SECOND);
        expectMetadata(() -> history.notesDeleted(null, 1));
        expectMetadata(() -> history.notesDeleted(beforeDelete, -1));
        expectMetadata(() -> history.notesDeleted(beforeDelete, beforeDelete.size()));
        expectMetadata(() -> history.notesDeleted(beforeDelete, 0));
        expectMetadata(() -> history.notesDeleted(List.of(FIRST, THIRD), 1));
        expectMetadata(() -> history.notesDeleted(List.of(THIRD, SECOND, FIRST), 1));
        expectMetadata(() -> history.notesDeleted(Arrays.asList(FIRST, null, THIRD), 1));
        check(metadata.writes == 0 && metadata.reads == 0, "invalid history snapshots never touch metadata");
        check(metadata.entries.equals(originalTimes), "invalid history snapshots retain times");

        metadata.fail = true;
        expectMetadata(() -> history.notesDeleted(beforeDelete, 1));
        check(records.notes().equals(List.of(FIRST, THIRD)), "metadata failure does not undo successful main deletion");
        check(metadata.entries.equals(originalTimes), "metadata write failure keeps original times");
        metadata.fail = false;
        history.notesDeleted(beforeDelete, 1);
        check(metadata.entries.equals(Map.of(ids.get(0), 100L, ids.get(2), 102L)), "metadata retry with original snapshot succeeds");

        MemoryNotes oldNotes = new MemoryNotes(List.of(FIRST, SECOND));
        LocalMapRecordsService oldRecords = service(oldNotes, new MemoryFavorites());
        MemoryMetadata noTimes = new MemoryMetadata();
        MapHistoryService oldHistory = new MapHistoryService(oldRecords, noTimes, () -> 9999L);
        List<LocationNote> oldSnapshot = oldRecords.notes();
        oldRecords.removeNote(0, FIRST);
        oldHistory.notesDeleted(oldSnapshot, 0);
        check(noTimes.writes == 0 && oldHistory.entries().get(0).createdAtMillis() == 0,
                "unknown historical time stays unknown without unnecessary write");
    }

    /** 동일 좌표·원문 메모의 서로 다른 히스토리 식별자가 정확한 현재 순번을 가리킨다. */
    private static void historySelectionCases() {
        LocationNote duplicate = new LocationNote(FIRST.latitude(), FIRST.longitude(), FIRST.text());
        MemoryNotes notes = new MemoryNotes(List.of(FIRST, SECOND, duplicate));
        LocalMapRecordsService records = service(notes, new MemoryFavorites());
        MapHistoryService history = new MapHistoryService(records, new MemoryMetadata(), () -> 9999L);
        List<String> ids = noteIds(history);
        check(!ids.get(0).equals(ids.get(2)), "identical note values have separate occurrence identifiers");
        check(history.findNoteIndex(ids.get(0)).orElse(-1) == 0, "first duplicate resolves to first index");
        check(history.findNoteIndex(ids.get(1)).orElse(-1) == 1, "different note resolves to middle index");
        check(history.findNoteIndex(ids.get(2)).orElse(-1) == 2, "later duplicate resolves to its original index");
        check(history.findNote(ids.get(0)).orElseThrow().equals(FIRST), "first history lookup preserves the saved value");
        check(history.findNote(ids.get(2)).orElseThrow().equals(duplicate), "later history lookup preserves the saved value");
        check(history.findNoteIndex("missing").isEmpty(), "unknown history identifier is absent");
        check(history.findNoteIndex(null).isEmpty(), "null history identifier is absent");
        check(history.findNoteIndex("favorite:" + FAVORITE.id()).isEmpty(), "favorite identifier cannot select a note");
        check(history.findNote("missing").isEmpty(), "existing lookup keeps empty result contract");

        records.removeNote(1, SECOND);
        check(history.findNoteIndex(ids.get(2)).orElse(-1) == 1, "current storage index follows intervening removal");
        check(history.findNoteIndex(ids.get(1)).isEmpty(), "removed different note identifier is absent");
    }

    /** 삭제 후 시각 정리가 실패했어도 같은 내용의 새 기록은 이번 생성 시각을 받는다. */
    private static void recreatedNoteReceivesNewCreationTime() {
        LocalMapRecordsService records = service(new MemoryNotes(List.of()), new MemoryFavorites());
        MemoryMetadata metadata = new MemoryMetadata();
        long[] now = {100L};
        MapHistoryService history = new MapHistoryService(records, metadata, () -> now[0]);

        // 첫 기록을 남긴 뒤 본 기록 삭제만 성공하고 시각 저장은 실패한 경우를 만든다.
        LocationNote first = records.saveNote(37.5, 127.1, "다시 기록할 장소");
        history.noteSaved(first, List.of());
        List<LocationNote> beforeDelete = records.notes();
        records.removeNote(0, first);
        metadata.fail = true;
        expectMetadata(() -> history.notesDeleted(beforeDelete, 0));
        check(history.entries().isEmpty(), "failed time cleanup never resurrects deleted record");

        // 같은 위치·내용도 실제 새 저장이므로 삭제된 항목의 옛 시각을 물려받으면 안 된다.
        metadata.fail = false;
        now[0] = 300L;
        List<LocationNote> beforeSave = records.notes();
        LocationNote recreated = records.saveNote(37.5, 127.1, "다시 기록할 장소");
        history.noteSaved(recreated, beforeSave);
        check(history.entries().get(0).createdAtMillis() == 300L,
                "recreated note uses new creation time after failed old cleanup");

        now[0] = 500L;
        history.noteSaved(recreated, records.notes());
        check(history.entries().get(0).createdAtMillis() == 300L,
                "identical repeat save retains recreated note creation time");
    }

    /** 100m 진입과 130m 이탈 사이의 위치 흔들림은 같은 기록을 반복 표시하지 않는다. */
    private static void reentryRadiusBoundaryCases() {
        LocationNote origin = new LocationNote(0, 0, "경계 검증용 기록");
        MemoryNotes stored = new MemoryNotes(List.of(origin));
        LocalMapRecordsService records = service(stored, new MemoryFavorites());

        // 적도 위의 고정 위도는 각각 약 222m·99m·111m·56m·131m 거리다.
        check(records.onPosition(0.002, 0) == null, "outside entry radius does not show a note");
        check(records.onPosition(0.00089, 0).equals(origin), "entering below 100m shows restored note");
        check(records.onPosition(0.001, 0) == null, "111m position does not rearm the note");
        check(records.onPosition(0.0005, 0) == null, "returning from hysteresis band stays suppressed");
        check(records.onPosition(0.00118, 0) == null, "leaving beyond 130m only rearms the note");
        check(records.onPosition(0.00089, 0).equals(origin), "reentry after full exit shows note once");
        check(records.onPosition(0, 0) == null, "remaining inside never repeats the same visit");

        // 저장 버튼을 누른 직후는 재방문이 아니며 다시 이탈한 다음에만 표시한다.
        LocalMapRecordsService newlySaved = service(new MemoryNotes(List.of()), new MemoryFavorites());
        LocationNote created = newlySaved.saveNote(0, 0, "방금 남긴 기록");
        check(newlySaved.onPosition(0, 0) == null, "new record is suppressed at its saved position");
        check(newlySaved.onPosition(0.00118, 0) == null, "new record rearms after leaving 130m");
        check(newlySaved.onPosition(0.00089, 0).equals(created), "new record appears on a later visit");
    }

    /** 주입 가능한 실제 서비스로 저장 경계와 Android 실행 의존성을 분리한다. */
    private static LocalMapRecordsService service(MemoryNotes notes, MemoryFavorites favorites) {
        return new LocalMapRecordsService(favorites, new LocationNotes(notes, new LocationReentryTracker()));
    }

    /** 시각 없는 표시 결과에서 저장 순서의 메모 식별자를 얻어 내부 지문 구현을 복제하지 않는다. */
    private static List<String> noteIds(MapHistoryService history) {
        List<String> result = new ArrayList<>();
        for (MapHistoryEntry entry : history.entriesWithoutTimes()) {
            if (entry.type() == MapHistoryEntry.Type.NOTE) {
                result.add(entry.id());
            }
        }
        return result;
    }

    /** 정상 결과로 잘못 숨겨진 저장 오류가 없는지 코드까지 검증한다. */
    private static void expectRecord(MapRecordFailure.Code expected, Runnable action) {
        try {
            action.run();
        } catch (MapRecordFailure failure) {
            check(failure.getCode() == expected, "record typed failure code");
            return;
        }
        throw new AssertionError("Expected record failure: " + expected);
    }

    /** 본 삭제와 별도인 시각 정리 실패가 기존 타입으로 전달되는지 확인한다. */
    private static void expectMetadata(Runnable action) {
        try {
            action.run();
        } catch (HistoryMetadataFailure failure) {
            check(failure.getCode() == HistoryMetadataFailure.Code.WRITE_FAILED, "metadata typed failure code");
            return;
        }
        throw new AssertionError("Expected metadata failure");
    }

    /** 하나의 계약 위반도 성공으로 집계하지 않고 즉시 실행을 실패시킨다. */
    private static void check(boolean passed, String description) {
        if (!passed) {
            throw new AssertionError(description);
        }
        assertions++;
    }

    /** 메모 영속 경계의 성공·실패를 재현하며 실제 업무 로직은 대체하지 않는다. */
    private static final class MemoryNotes implements LocationNoteRepository {
        private List<LocationNote> entries;
        private boolean fail;
        private int attempts;
        private int writes;

        /** 복원에 포함될 실제 모델 스냅샷을 복사한다. */
        private MemoryNotes(List<LocationNote> initial) {
            entries = new ArrayList<>(initial);
        }

        /** 외부에서 수정할 수 없는 복원 결과를 제공한다. */
        @Override
        public List<LocationNote> readAll() {
            return List.copyOf(entries);
        }

        /** 실패를 저장 이전에 전달해 제품 서비스의 메모리 반영 순서를 검증한다. */
        @Override
        public void writeAll(List<LocationNote> updated) {
            attempts++;
            if (fail) {
                throw new LocationNotesException(LocationNotesException.Code.WRITE_FAILED, "검증 저장 실패");
            }
            entries = new ArrayList<>(updated);
            writes++;
        }
    }

    /** 기존 즐겨찾기 서비스의 저장 경계만 통제한다. */
    private static final class MemoryFavorites implements FavoriteRepository {
        private List<SavedMapPoint> entries = new ArrayList<>();
        private boolean fail;
        private int writes;

        /** 현재 저장 스냅샷을 복원한다. */
        @Override
        public List<SavedMapPoint> readAll() {
            return List.copyOf(entries);
        }

        /** 실패일 때 영속 상태를 바꾸지 않는 기존 저장 계약을 재현한다. */
        @Override
        public void writeAll(List<SavedMapPoint> updated) {
            if (fail) {
                throw new MapRecordFailure(MapRecordFailure.Code.WRITE_FAILED, "검증 저장 실패");
            }
            entries = new ArrayList<>(updated);
            writes++;
        }
    }

    /** 생성 시각 저장 경계를 통제하며 원자적 전체 교체 실패를 재현한다. */
    private static final class MemoryMetadata implements HistoryMetadataRepository {
        private Map<String, Long> entries = new HashMap<>();
        private boolean fail;
        private int reads;
        private int writes;

        /** 외부 변경 없는 시각 스냅샷을 반환한다. */
        @Override
        public Map<String, Long> readCreatedAt() {
            reads++;
            return Map.copyOf(entries);
        }

        /** 새 시각 저장을 전체 교체 경계와 같게 처리한다. */
        @Override
        public void rememberCreatedAt(String id, long createdAt) {
            Map<String, Long> updated = new HashMap<>(entries);
            updated.putIfAbsent(id, createdAt);
            replaceCreatedAt(updated);
        }

        /** 선택한 즐겨찾기의 시각 제거만 수행한다. */
        @Override
        public void removeCreatedAt(String id) {
            Map<String, Long> updated = new HashMap<>(entries);
            updated.remove(id);
            replaceCreatedAt(updated);
        }

        /** 오류를 저장 전에 전달해 기존 시각의 부분 변경을 막는다. */
        @Override
        public void replaceCreatedAt(Map<String, Long> updated) {
            if (fail) {
                throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED, "검증 시각 저장 실패", null);
            }
            entries = new HashMap<>(updated);
            writes++;
        }
    }
}
