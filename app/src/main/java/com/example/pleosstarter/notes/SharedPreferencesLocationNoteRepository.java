package com.example.pleosstarter.notes;

import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.ProviderException;

/** 텍스트와 위경도 JSON을 암호화한 뒤 앱 전용 로컬 설정으로 저장한다. */
final class SharedPreferencesLocationNoteRepository implements LocationNoteRepository {
    private static final String STORAGE_KEY = "location_notes_v1";
    private static final int SCHEMA_VERSION = 1;
    private final SharedPreferences preferences;
    private final LocationNoteCipher cipher;

    /** 앱 전용 로컬 설정 저장소를 주입받는다. */
    SharedPreferencesLocationNoteRepository(SharedPreferences preferences) {
        this(preferences, new AndroidKeyStoreLocationNoteCipher());
    }

    /** 저장소와 암호화 경계를 각각 주입해 실패 조건을 독립적으로 검증한다. */
    SharedPreferencesLocationNoteRepository(SharedPreferences preferences, LocationNoteCipher cipher) {
        if (preferences == null) {
            throw new LocationNotesException(LocationNotesException.Code.INVALID_INPUT,
                    "위치 기록 저장소가 준비되지 않았습니다.");
        }
        this.preferences = preferences;
        this.cipher = cipher;
    }

    /** 저장된 JSON을 검증하며 손상 원본을 삭제하거나 덮어쓰지 않는다. */
    @Override
    public List<LocationNote> readAll() {
        try {
            String serialized = preferences.getString(STORAGE_KEY, null);
            List<LocationNote> notes = new ArrayList<>();
            if (serialized == null) {
                return notes;
            }

            JSONObject root = new JSONObject(cipher.decrypt(serialized));
            if (root.getInt("version") != SCHEMA_VERSION) {
                throw new JSONException("지원하지 않는 위치 기록 버전입니다.");
            }
            JSONArray entries = root.getJSONArray("notes");
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                notes.add(new LocationNote(entry.getDouble("latitude"), entry.getDouble("longitude"),
                        entry.getString("text")));
            }
            return notes;
        } catch (JSONException | ClassCastException | LocationNotesException | GeneralSecurityException
                | IOException | IllegalArgumentException | ProviderException | SecurityException exception) {
            throw new LocationNotesException(LocationNotesException.Code.RESTORE_FAILED,
                    "저장된 위치 기록을 불러오지 못했습니다. 기존 기록은 유지됩니다.", exception);
        }
    }

    /** 동기 저장 성공을 확인하고 실패 시 성공한 기록으로 보고하지 않는다. */
    @Override
    public void writeAll(List<LocationNote> notes) {
        try {
            JSONArray entries = new JSONArray();
            for (LocationNote note : notes) {
                JSONObject entry = new JSONObject();
                entry.put("latitude", note.latitude());
                entry.put("longitude", note.longitude());
                entry.put("text", note.text());
                entries.put(entry);
            }
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            root.put("notes", entries);

            String encrypted = cipher.encrypt(root.toString());
            boolean committed = preferences.edit().putString(STORAGE_KEY, encrypted).commit();
            if (!committed) {
                throw new LocationNotesException(LocationNotesException.Code.WRITE_FAILED,
                        "위치 기록을 저장하지 못했습니다. 다시 시도해 주세요.");
            }
        } catch (JSONException | GeneralSecurityException | IOException | ProviderException | SecurityException exception) {
            throw new LocationNotesException(LocationNotesException.Code.WRITE_FAILED,
                    "위치 기록을 저장하지 못했습니다. 다시 시도해 주세요.", exception);
        }
    }
}
