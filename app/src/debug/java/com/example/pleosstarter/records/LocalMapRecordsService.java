package com.example.pleosstarter.records;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.notes.LocationNotes;
import com.example.pleosstarter.notes.LocationNotesException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** 기존 암호화 메모 서비스를 재사용하고 좌표 즐겨찾기 추가·삭제를 관리한다. */
public final class LocalMapRecordsService implements MapRecordsService {
    public static final int MAXIMUM_FAVORITES = 500;
    private static final double DUPLICATE_RADIUS_METERS = 8.0;
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final int MAXIMUM_NOTE_LENGTH = 2000;
    private final FavoriteRepository favoriteRepository;
    private final LocationNotes locationNotes;
    private List<SavedMapPoint> savedFavorites;

    /** 저장 계약과 기존 메모 서비스를 주입하고 즐겨찾기를 복원한다. */
    public LocalMapRecordsService(FavoriteRepository favoriteRepository, LocationNotes locationNotes) {
        this.favoriteRepository = Objects.requireNonNull(favoriteRepository, "즐겨찾기 저장소가 필요합니다.");
        this.locationNotes = Objects.requireNonNull(locationNotes, "위치 메모 서비스가 필요합니다.");
        savedFavorites = new ArrayList<>(favoriteRepository.readAll());
    }

    /** 8m 이내의 재입력은 기존 항목을 반환하며 저장 실패 시 메모리 목록도 보존한다. */
    @Override
    public synchronized SavedMapPoint addFavorite(double latitude, double longitude, String name) {
        SavedMapPoint candidate = new SavedMapPoint(UUID.randomUUID().toString(), name, latitude, longitude);
        for (SavedMapPoint favorite : savedFavorites) {
            if (distanceMeters(favorite, candidate) <= DUPLICATE_RADIUS_METERS) {
                return favorite;
            }
        }
        if (savedFavorites.size() >= MAXIMUM_FAVORITES) {
            throw new MapRecordFailure(MapRecordFailure.Code.LIMIT_REACHED,
                    "즐겨찾기는 최대 500개까지 저장할 수 있습니다.");
        }

        List<SavedMapPoint> updated = new ArrayList<>(savedFavorites);
        updated.add(candidate);
        favoriteRepository.writeAll(updated);
        savedFavorites = updated;
        return candidate;
    }

    /** 사용자가 확인한 메모만 저장하며 중복 위치의 원래 식별자·이름·좌표를 보존한다. */
    @Override
    public synchronized SavedMapPoint saveFavorite(double latitude, double longitude, String name, String note) {
        if (note == null || note.isBlank() || note.length() > MAXIMUM_NOTE_LENGTH) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "즐겨찾기 메모를 1~2,000자로 입력해 주세요.");
        }
        SavedMapPoint candidate = new SavedMapPoint(UUID.randomUUID().toString(), name,
                latitude, longitude, note);
        List<SavedMapPoint> updated = new ArrayList<>(savedFavorites);

        // 가까운 기존 항목은 명시적으로 확인한 메모만 바꾸며 신규 항목 한도와 구분한다.
        for (int index = 0; index < savedFavorites.size(); index++) {
            SavedMapPoint favorite = savedFavorites.get(index);
            if (distanceMeters(favorite, candidate) <= DUPLICATE_RADIUS_METERS) {
                SavedMapPoint replacement = new SavedMapPoint(favorite.id(), favorite.name(),
                        favorite.latitude(), favorite.longitude(), candidate.note());
                if (replacement.equals(favorite)) {
                    return favorite;
                }
                updated.set(index, replacement);
                favoriteRepository.writeAll(updated);
                savedFavorites = updated;
                return replacement;
            }
        }
        if (savedFavorites.size() >= MAXIMUM_FAVORITES) {
            throw new MapRecordFailure(MapRecordFailure.Code.LIMIT_REACHED,
                    "즐겨찾기는 최대 500개까지 저장할 수 있습니다.");
        }

        // 영속 저장 성공 뒤에만 화면에서 읽는 목록에 새 항목을 공개한다.
        updated.add(candidate);
        favoriteRepository.writeAll(updated);
        savedFavorites = updated;
        return candidate;
    }

    /** 같은 중복 거리 규칙으로 편집할 기존 항목을 찾으며 조회만으로 기록을 만들지 않는다. */
    @Override
    public synchronized Optional<SavedMapPoint> findFavoriteNear(double latitude, double longitude) {
        SavedMapPoint candidate = new SavedMapPoint("lookup", "조회 위치", latitude, longitude);
        for (SavedMapPoint favorite : savedFavorites) {
            if (distanceMeters(favorite, candidate) <= DUPLICATE_RADIUS_METERS) {
                return Optional.of(favorite);
            }
        }
        return Optional.empty();
    }

    /** 호출자가 내부 목록을 변경할 수 없는 스냅샷을 반환한다. */
    @Override
    public synchronized List<SavedMapPoint> favorites() {
        return Collections.unmodifiableList(new ArrayList<>(savedFavorites));
    }

    /** 같은 식별자의 위치만 교체하고 영속 저장 성공 뒤에 메모리 목록을 갱신한다. */
    @Override
    public synchronized SavedMapPoint moveFavorite(String id, double latitude, double longitude) {
        if (id == null || id.isBlank()) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "이동할 즐겨찾기를 선택해 주세요.");
        }

        for (int index = 0; index < savedFavorites.size(); index++) {
            SavedMapPoint original = savedFavorites.get(index);
            if (!original.id().equals(id)) {
                continue;
            }
            SavedMapPoint moved = new SavedMapPoint(original.id(), original.name(),
                    latitude, longitude, original.note());
            if (moved.equals(original)) {
                return original;
            }

            List<SavedMapPoint> updated = new ArrayList<>(savedFavorites);
            updated.set(index, moved);
            favoriteRepository.writeAll(updated);
            savedFavorites = updated;
            return moved;
        }
        throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                "이동할 즐겨찾기를 찾을 수 없습니다.");
    }

    /** 식별자가 없으면 오류로 알리고 저장 완료 후에만 삭제 상태를 반영한다. */
    @Override
    public synchronized void removeFavorite(String id) {
        if (id == null || id.isBlank()) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "삭제할 즐겨찾기를 선택해 주세요.");
        }
        List<SavedMapPoint> updated = new ArrayList<>(savedFavorites);
        boolean removed = updated.removeIf(point -> point.id().equals(id));
        if (!removed) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "삭제할 즐겨찾기를 찾을 수 없습니다.");
        }
        favoriteRepository.writeAll(updated);
        savedFavorites = updated;
    }

    /** 공백·과도한 텍스트를 막고 기존 암호화 메모 저장 결과를 그대로 연결한다. */
    @Override
    public LocationNote saveNote(double latitude, double longitude, String text) {
        if (text == null || text.isBlank() || text.length() > MAXIMUM_NOTE_LENGTH) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "메모 내용을 1~2,000자로 입력해 주세요.");
        }
        try {
            return locationNotes.save(latitude, longitude, text);
        } catch (LocationNotesException exception) {
            throw convertNoteFailure(exception);
        }
    }

    /** 기존 메모 서비스의 변경 불가능한 목록을 제공한다. */
    @Override
    public List<LocationNote> notes() {
        return locationNotes.list();
    }

    /** 선택 이후 변경된 메모를 잘못 옮기지 않도록 기존 서비스의 순번·원문 검사를 재사용한다. */
    @Override
    public LocationNote moveNote(int index, LocationNote expected, double latitude, double longitude) {
        try {
            return locationNotes.move(index, expected, latitude, longitude);
        } catch (LocationNotesException exception) {
            throw convertNoteFailure(exception);
        }
    }

    /** 선택 당시 순번·원문 검사를 재사용해 삭제하고 메모 저장 오류를 공통 오류로 전달한다. */
    @Override
    public void removeNote(int index, LocationNote expected) {
        try {
            locationNotes.remove(index, expected);
        } catch (LocationNotesException exception) {
            throw convertNoteFailure(exception);
        }
    }

    /** 좌표 갱신에 기존 재진입 조건을 적용하고 오류를 기록 서비스 형식으로 변환한다. */
    @Override
    public LocationNote onPosition(double latitude, double longitude) {
        try {
            return locationNotes.onPosition(latitude, longitude);
        } catch (LocationNotesException exception) {
            throw convertNoteFailure(exception);
        }
    }

    /** 두 좌표의 대권 거리를 구해 그리기 입력의 작은 흔들림을 중복으로 처리한다. */
    private double distanceMeters(SavedMapPoint first, SavedMapPoint second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(second.longitude() - first.longitude());
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double haversine = Math.pow(Math.sin(latitudeDelta / 2.0), 2.0)
                + Math.cos(firstLatitude) * Math.cos(secondLatitude)
                * Math.pow(Math.sin(longitudeDelta / 2.0), 2.0);
        return EARTH_RADIUS_METERS * 2.0 * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }

    /** 메모 저장소의 오류 종류와 안전한 안내 문구를 서비스 오류로 맞춘다. */
    private MapRecordFailure convertNoteFailure(LocationNotesException exception) {
        MapRecordFailure.Code code = switch (exception.getCode()) {
            case INVALID_INPUT -> MapRecordFailure.Code.INVALID_INPUT;
            case RESTORE_FAILED -> MapRecordFailure.Code.RESTORE_FAILED;
            case WRITE_FAILED -> MapRecordFailure.Code.WRITE_FAILED;
        };
        return new MapRecordFailure(code, exception.getMessage(), exception);
    }
}
