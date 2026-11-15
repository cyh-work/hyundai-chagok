package com.example.pleosstarter.notes;

import android.content.SharedPreferences;
import com.example.pleosstarter.records.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** 실제 기록·메타 서비스와 저장소의 이동 계약을 독립 JVM에서 확인한다. */
public final class RecordRelocationProbe {
    private static int assertions;

    /** 기존 회귀와 별개로 이동·실패·복원·시간 보존을 검사한다. */
    public static void main(String[] args) throws Exception {
        favoriteCases();
        noteCases();
        metadataCases();
        repositoryCases();
        System.out.println("PASS RecordRelocation assertions=" + assertions);
        System.out.println("Actual product service/repository/DTO; memory SharedPreferences and JCE AES boundary.");
        System.out.println("Android KeyStore, emulator UI and real disk persistence are not exercised.");
    }

    /** 즐겨찾기 이동의 동일성·순서·충돌·한도·검증·실패 보존을 확인한다. */
    private static void favoriteCases() {
        MemoryFavorites favorites = new MemoryFavorites();
        SavedMapPoint first = new SavedMapPoint("f1", "상호", 37, 127, "원문\n둘째 줄");
        SavedMapPoint second = new SavedMapPoint("f2", "다른 상호", 38, 128, "다른 메모");
        favorites.entries.addAll(List.of(first, second));
        LocalMapRecordsService service = service(favorites, new MemoryNotes());
        List<SavedMapPoint> originalSnapshot = service.favorites();
        SavedMapPoint moved = service.moveFavorite(first.id(), second.latitude(), second.longitude());
        check(moved.id().equals(first.id()) && moved.name().equals(first.name()), "favorite identity and name");
        check(moved.note().equals(first.note()), "favorite text retained");
        check(service.favorites().equals(List.of(moved, second)), "favorite order and count retained at overlap");
        check(originalSnapshot.equals(List.of(first, second)), "favorite snapshot immutable across move");
        check(favorites.writes == 1, "favorite single write");
        check(service.moveFavorite("f1", 38, 128).equals(moved) && favorites.writes == 1, "favorite no-op has no write");
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite(null, 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite(" ", 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite("missing", 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite("f1", Double.NaN, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite("f1", 91, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveFavorite("f1", 37, -181));
        check(favorites.writes == 1, "invalid favorite moves never write");
        favorites.fail = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> service.moveFavorite("f1", 39, 129));
        check(service.favorites().equals(List.of(moved, second)), "favorite failed write preserves cache");
        check(favorites.entries.equals(List.of(moved, second)), "favorite failed write preserves backing list");
        favorites.fail = false;
        check(service.moveFavorite("f1", -90, 180).latitude() == -90, "favorite boundary valid");
        for (int index = 2; index < 500; index++) {
            favorites.entries.add(new SavedMapPoint("f" + index, "항목", 10, 10));
        }
        LocalMapRecordsService full = service(favorites, new MemoryNotes());
        check(full.moveFavorite("f1", 90, -180).longitude() == -180, "favorite move allowed at capacity");
        check(full.favorites().size() == 500, "capacity move retains count");
    }

    /** 메모의 정확한 순번 선택·중복 보존과 이동 직후 재진입 억제를 확인한다. */
    private static void noteCases() {
        LocationNote first = new LocationNote(37, 127, "같은 원문");
        LocationNote third = new LocationNote(38, 128, "다른 원문");
        MemoryNotes notes = new MemoryNotes();
        notes.entries.addAll(List.of(first, first, third));
        LocalMapRecordsService service = service(new MemoryFavorites(), notes);
        List<LocationNote> originalSnapshot = service.notes();
        LocationNote moved = service.moveNote(1, first, 39, 129);
        check(service.notes().equals(List.of(first, moved, third)), "only selected duplicate index moves");
        check(moved.text().equals(first.text()), "note text retained");
        check(originalSnapshot.equals(List.of(first, first, third)), "note snapshot retained");
        check(service.moveNote(1, moved, 39, 129).equals(moved) && notes.writes == 1, "note no-op avoids write");
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(-1, first, 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(3, first, 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(1, first, 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(0, null, 37, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(0, first, Double.POSITIVE_INFINITY, 127));
        expectRecord(MapRecordFailure.Code.INVALID_INPUT, () -> service.moveNote(0, first, 37, 181));
        check(notes.writes == 1, "invalid note moves never write");
        notes.fail = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> service.moveNote(1, moved, 40, 130));
        check(service.notes().equals(List.of(first, moved, third)), "failed note write preserves cache");
        check(notes.entries.equals(List.of(first, moved, third)), "failed note write preserves backing list");
        notes.fail = false;
        LocationNote collision = service.moveNote(1, moved, first.latitude(), first.longitude());
        check(collision.equals(first) && service.notes().size() == 3, "same-value destination does not merge notes");

        MemoryNotes single = new MemoryNotes();
        single.entries.add(first);
        LocalMapRecordsService reentry = service(new MemoryFavorites(), single);
        check(reentry.onPosition(37, 127).equals(first), "restored note initially emits");
        LocationNote newPosition = reentry.moveNote(0, first, 38, 128);
        check(reentry.onPosition(38, 128) == null, "moved note suppressed until exit");
        check(reentry.onPosition(38.01, 128) == null, "leaving moved position arms without notification");
        check(reentry.onPosition(38, 128).equals(newPosition), "moved position emits on reentry");
        check(reentry.onPosition(37, 127) == null, "old position no longer emits");
        reentry.moveNote(0, newPosition, -90, 180);
        check(reentry.notes().get(0).latitude() == -90, "note coordinate boundary valid");
    }

    /** 생성 시각의 유지·중복 순번 재매핑·미확인 시각과 별도 실패를 확인한다. */
    private static void metadataCases() {
        LocationNote first = new LocationNote(37, 127, "같은 원문");
        LocationNote other = new LocationNote(38, 128, "같은 원문");
        MemoryNotes notes = new MemoryNotes();
        notes.entries.addAll(List.of(first, first, other));
        LocalMapRecordsService service = service(new MemoryFavorites(), notes);
        MemoryPreferences memory = new MemoryPreferences();
        SharedPreferencesHistoryMetadataRepository metadata = new SharedPreferencesHistoryMetadataRepository(memory.proxy());
        MapHistoryService history = new MapHistoryService(service, metadata, () -> 99999L);
        List<MapHistoryEntry> entries = history.entries();
        String firstId = entries.get(0).id();
        String secondId = entries.get(1).id();
        String otherId = entries.get(2).id();
        metadata.rememberCreatedAt(firstId, 100);
        metadata.rememberCreatedAt(secondId, 200);
        metadata.rememberCreatedAt(otherId, 300);
        metadata.rememberCreatedAt("favorite:unrelated", 400);
        memory.values.put("unrelated-setting", "keep");

        List<LocationNote> before = service.notes();
        service.moveNote(0, first, other.latitude(), other.longitude());
        history.notesMoved(before);
        Map<String, Long> times = metadata.readCreatedAt();
        check(times.get(firstId) == 200, "remaining duplicate retains own time after occurrence shift");
        check(!times.containsKey(secondId), "obsolete duplicate occurrence removed");
        check(times.get(otherId) == 100, "moved first note retains original time at destination occurrence zero");
        check(times.get(otherId.substring(0, otherId.lastIndexOf(':') + 1) + "1") == 300, "destination duplicate retains original time");
        check(times.get("favorite:unrelated") == 400, "favorite timestamp untouched");
        check(memory.values.get("unrelated-setting").equals("keep"), "unrelated settings untouched");
        check(times.size() == 4, "time remap neither invents nor loses known times");
        int commits = memory.commits;
        history.notesMoved(service.notes());
        check(memory.commits == commits, "no-op move does not write metadata");

        Map<String, Long> beforeFailure = new HashMap<>(times);
        List<LocationNote> beforeSecondMove = service.notes();
        service.moveNote(1, first, 40, 130);
        memory.failCommit = true;
        expectMetadata(() -> history.notesMoved(beforeSecondMove));
        check(metadata.readCreatedAt().equals(beforeFailure), "failed metadata transaction unchanged at boundary");
        check(service.notes().get(1).latitude() == 40, "metadata failure does not claim base move rolled back");
        memory.failCommit = false;
        history.notesMoved(beforeSecondMove);
        check(history.entries().stream().anyMatch(e -> e.createdAtMillis() == 200), "metadata retry preserves original timestamp");
        expectMetadata(() -> history.notesMoved(null));
        expectMetadata(() -> history.notesMoved(List.of(first)));
        expectMetadata(() -> history.notesMoved(List.of(new LocationNote(38, 128, "변조"), first, other)));

        MemoryNotes unknown = new MemoryNotes();
        unknown.entries.add(first);
        LocalMapRecordsService unknownService = service(new MemoryFavorites(), unknown);
        MemoryPreferences unknownPrefs = new MemoryPreferences();
        SharedPreferencesHistoryMetadataRepository unknownMeta = new SharedPreferencesHistoryMetadataRepository(unknownPrefs.proxy());
        MapHistoryService unknownHistory = new MapHistoryService(unknownService, unknownMeta, () -> 99999L);
        List<LocationNote> unknownBefore = unknownService.notes();
        unknownService.moveNote(0, first, 41, 131);
        unknownHistory.notesMoved(unknownBefore);
        check(unknownHistory.entries().get(0).createdAtMillis() == 0, "unknown time remains unknown after move");
        check(unknownPrefs.commits == 0, "unknown time does not create metadata");

        List<LocationNote> knownTarget = unknownService.notes();
        String staleTargetId = unknownHistory.entries().get(0).id();
        unknownService.moveNote(0, knownTarget.get(0), first.latitude(), first.longitude());
        unknownMeta.rememberCreatedAt(staleTargetId, 1234);
        List<LocationNote> withoutTime = unknownService.notes();
        unknownService.moveNote(0, first, 41, 131);
        unknownHistory.notesMoved(withoutTime);
        check(unknownHistory.entries().get(0).createdAtMillis() == 0, "unknown moved note never inherits stale destination timestamp");
        check(!unknownMeta.readCreatedAt().containsKey(staleTargetId), "stale destination timestamp removed");

        MemoryFavorites favoriteStore = new MemoryFavorites();
        LocalMapRecordsService favoriteService = service(favoriteStore, new MemoryNotes());
        MemoryPreferences favoriteTimes = new MemoryPreferences();
        MapHistoryService favoriteHistory = new MapHistoryService(favoriteService,
                new SharedPreferencesHistoryMetadataRepository(favoriteTimes.proxy()), () -> 321L);
        SavedMapPoint favorite = favoriteService.saveFavorite(37, 127, "장소", "메모");
        favoriteHistory.favoriteSaved(favorite, List.of());
        String favoriteHistoryId = favoriteHistory.entries().get(0).id();
        int favoriteTimeWrites = favoriteTimes.commits;
        favoriteService.moveFavorite(favorite.id(), 38, 128);
        check(favoriteHistory.entries().get(0).id().equals(favoriteHistoryId), "favorite history id stable after move");
        check(favoriteHistory.entries().get(0).createdAtMillis() == 321, "favorite created time stable after move");
        check(favoriteTimes.commits == favoriteTimeWrites, "favorite move requires no metadata write");

        Map<String, Long> invalid = new HashMap<>();
        invalid.put("note:x:0", null);
        expectMetadata(() -> metadata.replaceCreatedAt(invalid));
        expectMetadata(() -> metadata.replaceCreatedAt(Map.of("", 1L)));
        expectMetadata(() -> metadata.replaceCreatedAt(Map.of("note:x:0", 0L)));
        expectMetadata(() -> metadata.replaceCreatedAt(null));
    }

    /** 실제 JSON·암호화 저장소로 이동 후 새 서비스에서 복원되는 값을 확인한다. */
    private static void repositoryCases() throws Exception {
        MemoryPreferences memory = new MemoryPreferences();
        TestCipher cipher = new TestCipher();
        SharedPreferencesFavoriteRepository favorites = new SharedPreferencesFavoriteRepository(memory.proxy(), cipher);
        SharedPreferencesLocationNoteRepository noteRepository = new SharedPreferencesLocationNoteRepository(memory.proxy(), cipher);
        LocalMapRecordsService service = new LocalMapRecordsService(favorites,
                new LocationNotes(noteRepository, new LocationReentryTracker()));
        SavedMapPoint favorite = service.saveFavorite(37, 127, "시험 장소", "비공개 원문");
        LocationNote note = service.saveNote(37, 127, "원 메모 원문");
        SavedMapPoint movedFavorite = service.moveFavorite(favorite.id(), 38, 128);
        LocationNote movedNote = service.moveNote(0, note, 39, 129);
        LocalMapRecordsService restored = new LocalMapRecordsService(favorites,
                new LocationNotes(noteRepository, new LocationReentryTracker()));
        check(restored.favorites().equals(List.of(movedFavorite)), "favorite encrypted move restored");
        check(restored.notes().equals(List.of(movedNote)), "note encrypted move restored");
        check(!memory.values.toString().contains("원문"), "plaintext not present in stored envelopes");
        check(((String) memory.values.get("map_favorites_v1")).contains("\"version\":2"), "favorite v2 format retained");
        Map<String, Object> before = new HashMap<>(memory.values);
        memory.failCommit = true;
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> restored.moveFavorite(favorite.id(), 40, 130));
        expectRecord(MapRecordFailure.Code.WRITE_FAILED, () -> restored.moveNote(0, movedNote, 40, 130));
        check(memory.values.equals(before), "failed encrypted commits preserve stored bytes at boundary");
        check(restored.favorites().equals(List.of(movedFavorite)) && restored.notes().equals(List.of(movedNote)), "failed encrypted moves retain caches");
    }

    /** 실제 서비스에 최소 저장 경계만 주입한다. */
    private static LocalMapRecordsService service(MemoryFavorites favorites, MemoryNotes notes) {
        return new LocalMapRecordsService(favorites, new LocationNotes(notes, new LocationReentryTracker()));
    }

    /** 기대한 업무 실패 코드만 통과시킨다. */
    private static void expectRecord(MapRecordFailure.Code code, Runnable action) {
        try {
            action.run();
            throw new AssertionError("missing expected record failure");
        } catch (MapRecordFailure failure) {
            check(failure.getCode() == code, "record failure code");
        }
    }

    /** 본 저장과 별개인 시간 저장 실패를 확인한다. */
    private static void expectMetadata(Runnable action) {
        try {
            action.run();
            throw new AssertionError("missing expected metadata failure");
        } catch (HistoryMetadataFailure failure) {
            check(failure.getCode() == HistoryMetadataFailure.Code.WRITE_FAILED, "metadata failure code");
        }
    }

    /** 단언 횟수를 세고 원문 없는 검사 이름으로만 실패를 알린다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    /** 즐겨찾기 저장의 성공·실패 경계다. */
    private static final class MemoryFavorites implements FavoriteRepository {
        private List<SavedMapPoint> entries = new ArrayList<>();
        private boolean fail;
        private int writes;
        /** 복원 스냅샷을 반환한다. */
        public List<SavedMapPoint> readAll() {
            return new ArrayList<>(entries);
        }
        /** 성공한 쓰기만 반영한다. */
        public void writeAll(List<SavedMapPoint> updated) {
            if (fail) {
                throw new MapRecordFailure(MapRecordFailure.Code.WRITE_FAILED, "test");
            }
            writes++;
            entries = new ArrayList<>(updated);
        }
    }

    /** 메모 저장의 성공·실패 경계다. */
    private static final class MemoryNotes implements LocationNoteRepository {
        private List<LocationNote> entries = new ArrayList<>();
        private boolean fail;
        private int writes;
        /** 복원 스냅샷을 반환한다. */
        public List<LocationNote> readAll() {
            return new ArrayList<>(entries);
        }
        /** 성공한 쓰기만 반영한다. */
        public void writeAll(List<LocationNote> updated) {
            if (fail) {
                throw new LocationNotesException(LocationNotesException.Code.WRITE_FAILED, "test");
            }
            writes++;
            entries = new ArrayList<>(updated);
        }
    }

    /** 공개 SharedPreferences 계약의 단일 편집 성공·실패를 대체한다. */
    private static final class MemoryPreferences {
        private final Map<String, Object> values = new HashMap<>();
        private boolean failCommit;
        private int commits;
        /** 제품이 쓰는 설정 메서드만 엄격하게 제공한다. */
        private SharedPreferences proxy() {
            return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getAll":
                                return new HashMap<>(values);
                            case "contains":
                                return values.containsKey(args[0]);
                            case "getString":
                            case "getLong":
                                return values.getOrDefault(args[0], args[1]);
                            case "edit":
                                return editor();
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
        /** 여러 키의 제거·쓰기를 하나의 성공 commit에서만 적용한다. */
        private SharedPreferences.Editor editor() {
            Map<String, Object> pending = new HashMap<>(values);
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "putString":
                            case "putLong":
                                pending.put((String) args[0], args[1]);
                                return proxy;
                            case "remove":
                                pending.remove(args[0]);
                                return proxy;
                            case "commit":
                                commits++;
                                if (failCommit) {
                                    return false;
                                }
                                values.clear();
                                values.putAll(pending);
                                return true;
                            default:
                                throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }

    /** AndroidKeyStore 대신 이번 실행에서만 쓰는 실제 AES/GCM 키를 주입한다. */
    private static final class TestCipher implements LocationNoteCipher {
        private final SecretKey key;
        /** 저장소 복원에 재사용할 시험 키를 생성한다. */
        private TestCipher() throws GeneralSecurityException {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
        }
        /** 새 nonce와 인증 태그를 포함해 암호화한다. */
        public String encrypt(String plaintext) throws GeneralSecurityException {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher operation = Cipher.getInstance("AES/GCM/NoPadding");
            operation.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(operation.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        }
        /** 인증을 통과한 시험 암호문만 복원한다. */
        public String decrypt(String encrypted) throws GeneralSecurityException {
            String[] parts = encrypted.split("\\.");
            Cipher operation = Cipher.getInstance("AES/GCM/NoPadding");
            operation.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(operation.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        }
    }
}
