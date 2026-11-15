package com.example.pleosstarter.notes;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/** 위치 기록 저장과 한 번의 재진입 알림을 묶어 제공하는 앱 단위 서비스다. */
public final class LocationNotes {
    private final LocationNoteRepository repository;
    private final LocationReentryTracker tracker;
    private List<LocationNote> notes;

    /**
     * 로컬 기록을 복원하고 현재 실행의 진입 상태를 초기화한다.
     * 복원 기록은 앱 시작 후 첫 100m 진입 때 한 번 표시할 수 있다.
     *
     * @throws LocationNotesException 저장소 입력 또는 기존 JSON이 올바르지 않을 때
     */
    public LocationNotes(SharedPreferences preferences) {
        this(new SharedPreferencesLocationNoteRepository(preferences), new LocationReentryTracker());
    }

    /** 저장과 진입 판단을 주입해 외부 시스템 없이 서비스 동작을 검증한다. */
    LocationNotes(LocationNoteRepository repository, LocationReentryTracker tracker) {
        this.repository = repository;
        this.tracker = tracker;
        notes = new ArrayList<>(repository.readAll());
    }

    /**
     * 좌표와 텍스트를 저장한 뒤 해당 위치를 벗어나기 전까지 알림을 억제한다.
     * 동일 좌표·내용의 재저장은 중복 목록을 만들지 않는다.
     *
     * @return 검증과 로컬 저장이 끝난 기록
     * @throws LocationNotesException 입력이 유효하지 않거나 로컬 저장에 실패할 때
     */
    public synchronized LocationNote save(double latitude, double longitude, String text) {
        LocationNote note = new LocationNote(latitude, longitude, text);
        if (!notes.contains(note)) {
            List<LocationNote> updated = new ArrayList<>(notes);
            updated.add(note);
            repository.writeAll(updated);
            notes = updated;
        }
        tracker.suppressUntilExit(note);
        return note;
    }

    /**
     * 선택 당시의 순번과 값이 일치하는 메모의 좌표만 교체한다.
     * 저장 실패 시 목록과 재진입 상태는 유지하며 같은 내용의 다른 메모와 합치지 않는다.
     */
    public synchronized LocationNote move(int index, LocationNote expected,
                                          double latitude, double longitude) {
        if (expected == null || index < 0 || index >= notes.size()
                || !notes.get(index).equals(expected)) {
            throw new LocationNotesException(LocationNotesException.Code.INVALID_INPUT,
                    "이동할 메모가 변경됐어요. 다시 선택해 주세요.");
        }
        LocationNote moved = new LocationNote(latitude, longitude, expected.text());
        if (moved.equals(expected)) {
            return expected;
        }

        List<LocationNote> updated = new ArrayList<>(notes);
        updated.set(index, moved);
        repository.writeAll(updated);
        notes = updated;
        tracker.relocate(expected, moved, updated.contains(expected));
        return moved;
    }

    /**
     * 선택 당시의 순번과 값이 일치하는 메모 한 건만 삭제한다.
     * 영속 저장 성공 후에만 목록과 재진입 상태를 바꾸며 동일한 다른 메모는 유지한다.
     */
    public synchronized void remove(int index, LocationNote expected) {
        if (expected == null || index < 0 || index >= notes.size()
                || !notes.get(index).equals(expected)) {
            throw new LocationNotesException(LocationNotesException.Code.INVALID_INPUT,
                    "삭제할 메모가 변경됐어요. 다시 선택해 주세요.");
        }

        // 원문이 같은 복원 기록도 선택한 순번 한 건만 저장 목록에서 제거한다.
        List<LocationNote> updated = new ArrayList<>(notes);
        updated.remove(index);
        repository.writeAll(updated);
        notes = updated;
        if (!updated.contains(expected)) {
            tracker.forget(expected);
        }
    }

    /**
     * 130m 밖으로 벗어났다가 100m 안으로 들어온 기록을 한 번 반환한다.
     * 여러 기록이 겹치면 아직 알리지 않은 가장 가까운 기록을 한 개씩 반환한다.
     *
     * @return 이번 위치에서 표시할 기록, 새 진입 기록이 없으면 null
     * @throws LocationNotesException 위치가 유효한 위경도가 아닐 때
     */
    public synchronized LocationNote onPosition(double latitude, double longitude) {
        LocationNote.validatePosition(latitude, longitude);
        return tracker.onPosition(notes, latitude, longitude);
    }

    /** 지도 표시용으로 내부 목록을 수정할 수 없는 현재 기록 스냅샷을 반환한다. */
    public synchronized List<LocationNote> list() {
        return Collections.unmodifiableList(new ArrayList<>(notes));
    }
}
