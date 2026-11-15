package com.example.pleosstarter.notes;

import android.content.SharedPreferences;
import com.example.pleosstarter.records.*;
import org.json.JSONArray;
import org.json.JSONObject;

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

/** 실제 저장·서비스 클래스를 JVM 경계 대역과 주입형 AES/GCM 암호화로 검사한다. */
public final class FavoriteVoiceStorageProbe {
    private static final String KEY = "map_favorites_v1";
    private static int assertions;

    /** 저장 기능의 실제 입력·실패·마이그레이션 시나리오를 순서대로 실행한다. */
    public static void main(String[] args) throws Exception {
        serviceCases();
        repositoryCases();
        System.out.println("PASS FavoriteVoiceStorage assertions=" + assertions);
        System.out.println("Actual service/repository/DTO; SharedPreferences memory boundary and JCE AES cipher injection.");
        System.out.println("Android KeyStore, disk persistence, APK, microphone and UI are not exercised.");
    }

    /** 즐겨찾기 생성·편집·조회·한도와 실패 시 서비스 캐시 보존을 확인한다. */
    private static void serviceCases() {
        MemoryFavorites store = new MemoryFavorites();
        LocationNotes notes = new LocationNotes(new MemoryNotes(), new LocationReentryTracker());
        LocalMapRecordsService service = new LocalMapRecordsService(store, notes);
        SavedMapPoint legacy = new SavedMapPoint("legacy", "기존 이름", 37.5, 127.0);
        check(legacy.note().isEmpty(), "legacy constructor has no note");
        SavedMapPoint saved = service.saveFavorite(37.5, 127.0, "상호", "  별 음성 메모  ");
        check(saved.note().equals("별 음성 메모"), "note trimmed");
        check(service.favorites().size() == 1, "one favorite created");
        check(service.notes().isEmpty(), "favorite does not create circle note");
        check(!saved.toString().contains(saved.note()) && !saved.toString().contains(saved.name()), "safe debug text");
        check(service.findFavoriteNear(37.50001, 127.0).orElseThrow().equals(saved), "near lookup");
        int writes = store.writes;
        check(service.findFavoriteNear(37.7, 127.0).isEmpty(), "far lookup empty");
        check(store.writes == writes, "lookup never writes");

        SavedMapPoint edited = service.saveFavorite(37.50001, 127.0, "변경된 장소명", "두 번째 메모");
        check(edited.id().equals(saved.id()), "edit stable id");
        check(edited.latitude() == saved.latitude() && edited.longitude() == saved.longitude(), "edit stable coordinate");
        check(edited.name().equals(saved.name()), "edit stable place name");
        check(edited.note().equals("두 번째 메모") && service.favorites().size() == 1, "edit replaces note only");
        writes = store.writes;
        check(service.saveFavorite(37.5, 127.0, "상호", "두 번째 메모").equals(edited), "identical confirmation stable");
        check(store.writes == writes, "identical confirmation no redundant write");
        check(service.addFavorite(37.5, 127.0, "legacy name").equals(edited), "legacy duplicate preserves new memo");

        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.saveFavorite(37.5, 127.0, "상호", " "));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.saveFavorite(37.5, 127.0, "상호", null));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.saveFavorite(37.5, 127.0, "상호", "가".repeat(2001)));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.findFavoriteNear(Double.NaN, 127.0));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.findFavoriteNear(91, 127.0));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> service.saveFavorite(37.5, 181, "상호", "메모"));
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> new SavedMapPoint("id", "name", 37, 127, null));
        check(service.saveFavorite(37.5, 127, "상호", "가".repeat(2000)).note().length() == 2000, "2000 characters accepted");

        List<SavedMapPoint> before = service.favorites();
        store.fail = true;
        expect(MapRecordFailure.Code.WRITE_FAILED, () -> service.saveFavorite(37.5, 127, "상호", "실패 메모"));
        check(service.favorites().equals(before), "failed edit leaves service cache");
        expect(MapRecordFailure.Code.WRITE_FAILED, () -> service.saveFavorite(37.8, 127, "새 항목", "실패 메모"));
        check(service.favorites().equals(before), "failed new save leaves service cache");
        store.fail = false;

        MemoryFavorites fullStore = new MemoryFavorites();
        for (int index = 0; index < LocalMapRecordsService.MAXIMUM_FAVORITES; index++) {
            fullStore.entries.add(new SavedMapPoint("id-" + index, "기존", 10 + index * 0.001, 127));
        }
        LocalMapRecordsService full = new LocalMapRecordsService(fullStore, notes);
        check(full.saveFavorite(10, 127, "무시할 이름", "한도 중 편집").id().equals("id-0"), "update at capacity");
        check(full.favorites().size() == 500, "capacity update count stable");
        expect(MapRecordFailure.Code.LIMIT_REACHED, () -> full.saveFavorite(30, 127, "신규", "새 메모"));
    }

    /** 기존 v1 복원과 v2 인증 암호화 전환·실패 원본 보존을 확인한다. */
    private static void repositoryCases() throws Exception {
        MemoryPreferences memory = new MemoryPreferences();
        TestCipher cipher = new TestCipher();
        SharedPreferencesFavoriteRepository repository = new SharedPreferencesFavoriteRepository(memory.proxy(), cipher);
        check(repository.readAll().isEmpty(), "empty storage");
        String legacy = "{\"version\":1,\"favorites\":[{\"id\":\"a\",\"name\":\"기존상호\",\"latitude\":37.5,\"longitude\":127.0},{\"id\":\"b\",\"name\":\"다른상호\",\"latitude\":37.6,\"longitude\":127.1}]}";
        memory.values.put(KEY, legacy);
        List<SavedMapPoint> restored = repository.readAll();
        check(restored.size() == 2 && restored.get(0).note().isEmpty(), "v1 restored with blank note");
        check(memory.values.get(KEY).equals(legacy) && memory.commits == 0, "reading v1 does not mutate");
        List<SavedMapPoint> edited = new ArrayList<>(restored);
        edited.set(0, new SavedMapPoint("a", "기존상호", 37.5, 127.0, "비공개 테스트 메모"));
        repository.writeAll(edited);
        String encrypted = memory.values.get(KEY);
        JSONObject envelope = new JSONObject(encrypted);
        check(envelope.getInt("version") == 2 && envelope.has("payload"), "v2 envelope");
        check(!encrypted.contains("비공개") && !encrypted.contains("기존상호") && !encrypted.contains("37.5"), "plaintext not stored");
        check(repository.readAll().equals(edited), "encrypted roundtrip and legacy item preserved");
        repository.writeAll(edited);
        check(!memory.values.get(KEY).equals(encrypted), "fresh AES nonce");
        check(repository.readAll().equals(edited), "second encryption roundtrip");

        String before = memory.values.get(KEY);
        cipher.failEncrypt = true;
        expect(MapRecordFailure.Code.WRITE_FAILED, () -> repository.writeAll(edited));
        check(memory.values.get(KEY).equals(before), "encrypt failure preserves storage");
        cipher.failEncrypt = false;
        memory.failCommit = true;
        expect(MapRecordFailure.Code.WRITE_FAILED, () -> repository.writeAll(edited));
        check(memory.values.get(KEY).equals(before), "failed boundary commit preserves backing bytes");
        memory.failCommit = false;
        cipher.failDecrypt = true;
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        check(memory.values.get(KEY).equals(before), "decrypt failure preserves raw storage");
        cipher.failDecrypt = false;

        JSONObject tampered = new JSONObject(before);
        String payload = tampered.getString("payload");
        tampered.put("payload", payload.substring(0, payload.length() - 4) + "AAAA");
        memory.values.put(KEY, tampered.toString());
        String corrupted = memory.values.get(KEY);
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        check(memory.values.get(KEY).equals(corrupted), "tampered ciphertext retained");
        memory.values.put(KEY, "{\"version\":99,\"favorites\":[]}");
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        memory.values.put(KEY, legacy.replace("\"id\":\"b\"", "\"id\":\"a\""));
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        memory.values.put(KEY, legacy.replace("37.5", "137.5"));
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        memory.values.put(KEY, "not-json");
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);

        JSONObject wrongInner = new JSONObject();
        wrongInner.put("version", 2);
        wrongInner.put("payload", cipher.encrypt("{\"version\":1,\"favorites\":[]}"));
        memory.values.put(KEY, wrongInner.toString());
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        wrongInner.put("payload", cipher.encrypt(legacy.replace("\"version\":1", "\"version\":2")));
        memory.values.put(KEY, wrongInner.toString());
        expect(MapRecordFailure.Code.RESTORE_FAILED, repository::readAll);
        expect(MapRecordFailure.Code.INVALID_INPUT, () -> repository.writeAll(List.of(edited.get(0), edited.get(0))));
        repository.writeAll(List.of());
        check(repository.readAll().isEmpty(), "empty v2 roundtrip");
    }

    /** 기대한 오류 코드 외의 성공이나 예외를 실패로 처리한다. */
    private static void expect(MapRecordFailure.Code code, Runnable action) {
        try {
            action.run();
        } catch (MapRecordFailure failure) {
            check(failure.getCode() == code, "failure code " + code);
            return;
        }
        throw new AssertionError("Expected failure " + code);
    }

    /** 판정 실패 시 즉시 중단하고 통과한 단언 개수를 집계한다. */
    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        assertions++;
    }

    /** 서비스 저장 성공·실패를 명시적으로 선택하는 저장소 대역이다. */
    private static final class MemoryFavorites implements FavoriteRepository {
        private List<SavedMapPoint> entries = new ArrayList<>();
        private boolean fail;
        private int writes;
        /** 복원 목록을 외부 변경과 분리한다. */
        public List<SavedMapPoint> readAll() {
            return new ArrayList<>(entries);
        }
        /** 성공한 쓰기만 메모리 저장소에 반영한다. */
        public void writeAll(List<SavedMapPoint> updated) {
            if (fail) {
                throw new MapRecordFailure(MapRecordFailure.Code.WRITE_FAILED, "test");
            }
            entries = new ArrayList<>(updated);
            writes++;
        }
    }

    /** 원 메모 서비스는 변경 없이 실제 클래스로 생성하되 저장 경계만 분리한다. */
    private static final class MemoryNotes implements LocationNoteRepository {
        private List<LocationNote> entries = new ArrayList<>();
        /** 현재 원 메모 목록을 반환한다. */
        public List<LocationNote> readAll() {
            return new ArrayList<>(entries);
        }
        /** 원 메모의 저장 여부를 관찰할 수 있게 목록을 보관한다. */
        public void writeAll(List<LocationNote> notes) {
            entries = new ArrayList<>(notes);
        }
    }

    /** SharedPreferences 공개 API를 사용하되 실제 Android 디스크 저장은 대체한다. */
    private static final class MemoryPreferences {
        private final Map<String, String> values = new HashMap<>();
        private boolean failCommit;
        private int commits;
        /** 실제 인터페이스의 사용하는 메서드만 지원하고 예상 밖 호출은 실패시킨다. */
        private SharedPreferences proxy() {
            return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("getString")) {
                            return values.getOrDefault((String) arguments[0], (String) arguments[1]);
                        }
                        if (method.getName().equals("edit")) {
                            return editor();
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
        /** 한 commit 단위의 보류된 문자열 쓰기를 분리한다. */
        private SharedPreferences.Editor editor() {
            Map<String, String> pending = new HashMap<>(values);
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("putString")) {
                            pending.put((String) arguments[0], (String) arguments[1]);
                            return proxy;
                        }
                        if (method.getName().equals("commit")) {
                            commits++;
                            if (failCommit) {
                                return false;
                            }
                            values.clear();
                            values.putAll(pending);
                            return true;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    /** 키스토어를 대신하는 JVM 시험 키로 인증 암호문의 왕복과 변조 거부를 확인한다. */
    private static final class TestCipher implements LocationNoteCipher {
        private final SecretKey key;
        private boolean failEncrypt;
        private boolean failDecrypt;
        /** 이번 검사에서만 유효한 AES 키를 만든다. */
        private TestCipher() throws GeneralSecurityException {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            key = generator.generateKey();
        }
        /** 랜덤 IV와 인증 태그를 포함한 문자열로 암호화한다. */
        public String encrypt(String plaintext) throws GeneralSecurityException {
            if (failEncrypt) {
                throw new GeneralSecurityException("probe encryption failure");
            }
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher operation = Cipher.getInstance("AES/GCM/NoPadding");
            operation.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return Base64.getEncoder().encodeToString(nonce) + "."
                    + Base64.getEncoder().encodeToString(operation.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        }
        /** 다른 태그나 시험 오류가 있으면 평문을 반환하지 않는다. */
        public String decrypt(String encrypted) throws GeneralSecurityException {
            if (failDecrypt) {
                throw new GeneralSecurityException("probe decryption failure");
            }
            String[] parts = encrypted.split("\\.");
            Cipher operation = Cipher.getInstance("AES/GCM/NoPadding");
            operation.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(operation.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        }
    }
}
