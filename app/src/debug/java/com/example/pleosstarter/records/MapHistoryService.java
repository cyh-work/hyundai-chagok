package com.example.pleosstarter.records;

import com.example.pleosstarter.notes.LocationNote;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.LongSupplier;

/** 기존 저장 모델을 합치며 실제 신규 저장 시각과 과거 미확인 시각을 구분한다. */
public final class MapHistoryService {
    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
    private final MapRecordsService records;
    private final HistoryMetadataRepository metadata;
    private final LongSupplier clock;

    /** 본 기록·시각 저장소·벽시계를 주입해 저장 원문과 표시 정렬을 분리한다. */
    public MapHistoryService(MapRecordsService records, HistoryMetadataRepository metadata, LongSupplier clock) {
        this.records = Objects.requireNonNull(records);
        this.metadata = Objects.requireNonNull(metadata);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 확인된 시각은 최근순, 시각이 없는 과거 항목은 기존의 안정된 순서로 반환한다. */
    public List<MapHistoryEntry> entries() {
        return mapEntries(metadata.readCreatedAt());
    }

    /** 시각 저장소에 문제가 있어도 실제 기록을 버리지 않고 이전 기록으로 표시한다. */
    public List<MapHistoryEntry> entriesWithoutTimes() {
        return mapEntries(Collections.emptyMap());
    }

    /** 저장 전 목록에 없는 즐겨찾기만 신규 기록으로 남긴다. */
    public void favoriteSaved(SavedMapPoint saved, List<SavedMapPoint> beforeSave) {
        for (SavedMapPoint previous : beforeSave) {
            if (previous.id().equals(saved.id())) {
                return;
            }
        }
        metadata.rememberCreatedAt(favoriteId(saved), clock.getAsLong());
    }

    /** 재저장 시각은 유지하고 실제 새 항목은 삭제 정리 실패로 남은 옛 시각을 물려받지 않는다. */
    public void noteSaved(LocationNote saved, List<LocationNote> beforeSave) {
        int beforeCount = matchingNotes(beforeSave, saved);
        int afterCount = matchingNotes(records.notes(), saved);
        if (afterCount <= beforeCount) {
            return;
        }
        String id = noteId(noteFingerprint(saved), afterCount - 1);
        long createdAt = clock.getAsLong();
        Map<String, Long> previousTimes = metadata.readCreatedAt();

        // 삭제된 같은 값의 시각이 남아 있어도 새 저장의 생성 시각만 교체한다.
        if (previousTimes.containsKey(id)) {
            Map<String, Long> updatedTimes = new HashMap<>(previousTimes);
            updatedTimes.put(id, createdAt);
            metadata.replaceCreatedAt(updatedTimes);
        } else {
            metadata.rememberCreatedAt(id, createdAt);
        }
    }

    /**
     * 메모 한 건의 위치 저장 성공 뒤 기존 순번별 생성 시각을 새 지문에 연결한다.
     * 중복 메모의 발생 순번이 바뀌어도 시각을 바꿔 끼우지 않으며 미확인 시각은 만들지 않는다.
     */
    public void notesMoved(List<LocationNote> beforeMove) {
        List<LocationNote> afterMove = records.notes();
        validateNoteMove(beforeMove, afterMove);
        if (beforeMove.equals(afterMove)) {
            return;
        }

        Map<String, Long> previousTimes = metadata.readCreatedAt();
        Map<String, Long> movedTimes = new HashMap<>(previousTimes);
        List<String> previousIds = noteIds(beforeMove);
        List<String> movedIds = noteIds(afterMove);
        for (String id : previousIds) {
            movedTimes.remove(id);
        }
        for (String id : movedIds) {
            movedTimes.remove(id);
        }

        // 저장 순번을 기준으로 복사해 같은 좌표·원문의 중복 발생 순번 이동도 보존한다.
        for (int index = 0; index < previousIds.size(); index++) {
            Long createdAt = previousTimes.get(previousIds.get(index));
            if (createdAt != null) {
                movedTimes.put(movedIds.get(index), createdAt);
            }
        }
        if (!previousTimes.equals(movedTimes)) {
            metadata.replaceCreatedAt(movedTimes);
        }
    }

    /**
     * 메모 한 건의 삭제 성공 뒤 남은 기록의 생성 시각을 원래 저장 순서대로 보존한다.
     * 같은 값의 발생 순번이 당겨져도 삭제한 기록의 시각을 다른 기록에 넘기지 않는다.
     */
    public void notesDeleted(List<LocationNote> beforeDelete, int deletedIndex) {
        List<LocationNote> afterDelete = records.notes();
        validateNoteDelete(beforeDelete, deletedIndex, afterDelete);

        // 즐겨찾기와 다른 메타데이터를 유지한 채 삭제 전후 메모 식별자만 다시 연결한다.
        Map<String, Long> previousTimes = metadata.readCreatedAt();
        Map<String, Long> remainingTimes = new HashMap<>(previousTimes);
        List<String> previousIds = noteIds(beforeDelete);
        List<String> remainingIds = noteIds(afterDelete);
        for (String id : previousIds) {
            remainingTimes.remove(id);
        }
        for (String id : remainingIds) {
            remainingTimes.remove(id);
        }

        int remainingIndex = 0;
        for (int previousIndex = 0; previousIndex < previousIds.size(); previousIndex++) {
            if (previousIndex == deletedIndex) {
                continue;
            }
            Long createdAt = previousTimes.get(previousIds.get(previousIndex));
            if (createdAt != null) {
                remainingTimes.put(remainingIds.get(remainingIndex), createdAt);
            }
            remainingIndex++;
        }
        if (!previousTimes.equals(remainingTimes)) {
            metadata.replaceCreatedAt(remainingTimes);
        }
    }

    /** 선택한 한 건만 빠지고 나머지 값과 순서가 같은 삭제 결과인지 검증한다. */
    private void validateNoteDelete(List<LocationNote> beforeDelete, int deletedIndex,
                                    List<LocationNote> afterDelete) {
        if (beforeDelete == null || deletedIndex < 0 || deletedIndex >= beforeDelete.size()
                || afterDelete.size() != beforeDelete.size() - 1) {
            throw invalidNoteDelete();
        }
        int remainingIndex = 0;
        for (int previousIndex = 0; previousIndex < beforeDelete.size(); previousIndex++) {
            LocationNote previous = beforeDelete.get(previousIndex);
            if (previous == null) {
                throw invalidNoteDelete();
            }
            if (previousIndex == deletedIndex) {
                continue;
            }
            if (!previous.equals(afterDelete.get(remainingIndex))) {
                throw invalidNoteDelete();
            }
            remainingIndex++;
        }
    }

    /** 본 메모 삭제와 후속 시각 정리 실패를 구분하는 기존 오류 형식을 반환한다. */
    private HistoryMetadataFailure invalidNoteDelete() {
        return new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                "메모는 삭제했지만 기록 시간은 정리하지 못했어요.", null);
    }

    /** 저장 순서·개수·원문을 유지한 단일 좌표 변경에만 시각 재매핑을 허용한다. */
    private void validateNoteMove(List<LocationNote> beforeMove, List<LocationNote> afterMove) {
        if (beforeMove == null || beforeMove.size() != afterMove.size()) {
            throw invalidNoteMove();
        }
        int changed = 0;
        for (int index = 0; index < beforeMove.size(); index++) {
            LocationNote previous = beforeMove.get(index);
            LocationNote moved = afterMove.get(index);
            if (previous == null || !previous.text().equals(moved.text())) {
                throw invalidNoteMove();
            }
            if (!previous.equals(moved)) {
                changed++;
            }
        }
        if (changed > 1) {
            throw invalidNoteMove();
        }
    }

    /** 메모 이동 계약 위반을 본 기록 오류와 구분되는 시각 저장 실패로 전달한다. */
    private HistoryMetadataFailure invalidNoteMove() {
        return new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                "위치는 옮겼지만 기록 시간은 갱신하지 못했어요.", null);
    }

    /** 저장 순서대로 중복 발생 순번까지 포함한 현재 메모 식별자를 계산한다. */
    private List<String> noteIds(List<LocationNote> notes) {
        List<String> result = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (LocationNote note : notes) {
            String fingerprint = noteFingerprint(note);
            int occurrence = occurrences.getOrDefault(fingerprint, 0);
            occurrences.put(fingerprint, occurrence + 1);
            result.add(noteId(fingerprint, occurrence));
        }
        return result;
    }

    /** 본 즐겨찾기 삭제 성공 뒤 해당 시각만 정리한다. */
    public void favoriteDeleted(SavedMapPoint deleted) {
        metadata.removeCreatedAt(favoriteId(deleted));
    }

    /** 히스토리에 담긴 식별자를 현재 저장된 즐겨찾기로 다시 확인한다. */
    public Optional<SavedMapPoint> findFavorite(String historyId) {
        for (SavedMapPoint point : records.favorites()) {
            if (favoriteId(point).equals(historyId)) {
                return Optional.of(point);
            }
        }
        return Optional.empty();
    }

    /** 같은 원문이 여러 번 복원된 경우에도 발생 순번으로 선택한 기록을 구분한다. */
    public Optional<LocationNote> findNote(String historyId) {
        List<LocationNote> notes = records.notes();
        OptionalInt index = findNoteIndex(historyId, notes);
        if (index.isPresent()) {
            return Optional.of(notes.get(index.getAsInt()));
        }
        return Optional.empty();
    }

    /** 히스토리 식별자가 가리키는 현재 저장 순번을 반환해 동일 값의 다른 메모와 구분한다. */
    public OptionalInt findNoteIndex(String historyId) {
        return findNoteIndex(historyId, records.notes());
    }

    /** 같은 목록 스냅샷에서 지문과 발생 순번을 함께 비교해 선택한 메모의 순번을 찾는다. */
    private OptionalInt findNoteIndex(String historyId, List<LocationNote> notes) {
        Map<String, Integer> occurrences = new HashMap<>();
        for (int index = 0; index < notes.size(); index++) {
            LocationNote note = notes.get(index);
            String fingerprint = noteFingerprint(note);
            int occurrence = occurrences.getOrDefault(fingerprint, 0);
            occurrences.put(fingerprint, occurrence + 1);
            if (noteId(fingerprint, occurrence).equals(historyId)) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    /** 모델이나 원문을 변경하지 않고 표시용 DTO로 합친다. */
    private List<MapHistoryEntry> mapEntries(Map<String, Long> timestamps) {
        List<MapHistoryEntry> favoriteEntries = new ArrayList<>();
        List<SavedMapPoint> favorites = records.favorites();
        for (int index = 0; index < favorites.size(); index++) {
            SavedMapPoint point = favorites.get(index);
            String id = favoriteId(point);
            String title = point.name();
            if ("지도에 기록한 위치".equals(title)) {
                title = "즐겨찾기 " + (index + 1);
            }
            favoriteEntries.add(new MapHistoryEntry(id, MapHistoryEntry.Type.FAVORITE, title, point.note(),
                    timestamps.getOrDefault(id, 0L)));
        }

        List<MapHistoryEntry> noteEntries = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (LocationNote note : records.notes()) {
            String fingerprint = noteFingerprint(note);
            int occurrence = occurrences.getOrDefault(fingerprint, 0);
            occurrences.put(fingerprint, occurrence + 1);
            String id = noteId(fingerprint, occurrence);
            noteEntries.add(new MapHistoryEntry(id, MapHistoryEntry.Type.NOTE, "메모", note.text(),
                    timestamps.getOrDefault(id, 0L)));
        }

        // 시각이 없는 기록은 유형별 원래 순서를 보존하며 교대로 보여 시간순으로 오해하지 않게 한다.
        List<MapHistoryEntry> entries = new ArrayList<>();
        int count = Math.max(favoriteEntries.size(), noteEntries.size());
        for (int index = 0; index < count; index++) {
            if (index < favoriteEntries.size()) {
                entries.add(favoriteEntries.get(index));
            }
            if (index < noteEntries.size()) {
                entries.add(noteEntries.get(index));
            }
        }
        entries.sort(Comparator.comparingLong(MapHistoryEntry::createdAtMillis).reversed());
        return Collections.unmodifiableList(entries);
    }

    /** 본 저장소의 안정적인 즐겨찾기 식별자를 그대로 이름 공간에 넣는다. */
    private String favoriteId(SavedMapPoint point) {
        return "favorite:" + point.id();
    }

    /** 동일 메모가 여러 개 있는 과거 저장본도 서로 다른 표시 식별자를 갖게 한다. */
    private String noteId(String fingerprint, int occurrence) {
        return "note:" + fingerprint + ":" + occurrence;
    }

    /** 같은 메모 값의 개수로 실제 신규 저장 여부를 판단한다. */
    private int matchingNotes(List<LocationNote> notes, LocationNote expected) {
        int count = 0;
        for (LocationNote note : notes) {
            if (note.equals(expected)) {
                count++;
            }
        }
        return count;
    }

    /** 메모 원문을 시각 저장소에 복사하지 않고 정확한 좌표·내용의 SHA-256 지문만 사용한다. */
    private String noteFingerprint(LocationNote note) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteBuffer coordinates = ByteBuffer.allocate(16);
            coordinates.putLong(Double.doubleToLongBits(note.latitude()));
            coordinates.putLong(Double.doubleToLongBits(note.longitude()));
            digest.update(coordinates.array());
            byte[] hash = digest.digest(note.text().getBytes(StandardCharsets.UTF_8));
            char[] encoded = new char[hash.length * 2];
            for (int index = 0; index < hash.length; index++) {
                int octet = hash[index] & 0xff;
                encoded[index * 2] = HEX_DIGITS[octet >>> 4];
                encoded[index * 2 + 1] = HEX_DIGITS[octet & 0x0f];
            }
            return new String(encoded);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("필수 SHA-256 구현이 없습니다.", failure);
        }
    }
}
