package com.example.pleosstarter.records;

import android.content.SharedPreferences;

import com.example.pleosstarter.notes.AndroidKeyStoreLocationNoteCipher;
import com.example.pleosstarter.notes.LocationNoteCipher;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.ProviderException;

/** 기존 위치 즐겨찾기를 복원하고 새 메모를 포함한 전체 목록은 인증 암호문으로 저장한다. */
public final class SharedPreferencesFavoriteRepository implements FavoriteRepository {
    private static final String STORAGE_KEY = "map_favorites_v1";
    private static final int LEGACY_SCHEMA_VERSION = 1;
    private static final int SCHEMA_VERSION = 2;
    private final SharedPreferences preferences;
    private final LocationNoteCipher cipher;

    /** 기존 다른 앱 기록과 분리한 전용 로컬 설정을 주입받는다. */
    public SharedPreferencesFavoriteRepository(SharedPreferences preferences) {
        this(preferences, new AndroidKeyStoreLocationNoteCipher());
    }

    /** 저장소와 기존 위치 기록 암호화 경계를 주입해 실패와 마이그레이션을 검증한다. */
    public SharedPreferencesFavoriteRepository(SharedPreferences preferences, LocationNoteCipher cipher) {
        this.preferences = Objects.requireNonNull(preferences, "즐겨찾기 저장소가 필요합니다.");
        this.cipher = Objects.requireNonNull(cipher, "즐겨찾기 암호화 기능이 필요합니다.");
    }

    /** 손상된 저장본을 지우지 않고 검증된 위치만 복원한다. */
    @Override
    public List<SavedMapPoint> readAll() {
        try {
            String serialized = preferences.getString(STORAGE_KEY, null);
            List<SavedMapPoint> favorites = new ArrayList<>();
            if (serialized == null) {
                return favorites;
            }
            JSONObject root = new JSONObject(serialized);
            int version = root.getInt("version");
            if (version == SCHEMA_VERSION) {
                root = new JSONObject(cipher.decrypt(root.getString("payload")));
                if (root.getInt("version") != SCHEMA_VERSION) {
                    throw new JSONException("암호화된 저장 버전 불일치");
                }
            } else if (version != LEGACY_SCHEMA_VERSION) {
                throw new JSONException("지원하지 않는 저장 버전");
            }
            JSONArray entries = root.getJSONArray("favorites");
            if (entries.length() > LocalMapRecordsService.MAXIMUM_FAVORITES) {
                throw new JSONException("즐겨찾기 개수 초과");
            }
            Set<String> ids = new HashSet<>();
            for (int index = 0; index < entries.length(); index++) {
                JSONObject entry = entries.getJSONObject(index);
                String note = "";
                if (version == SCHEMA_VERSION) {
                    note = entry.getString("note");
                }
                SavedMapPoint point = new SavedMapPoint(entry.getString("id"), entry.getString("name"),
                        entry.getDouble("latitude"), entry.getDouble("longitude"), note);
                if (!ids.add(point.id())) {
                    throw new JSONException("즐겨찾기 식별자 중복");
                }
                favorites.add(point);
            }
            return favorites;
        } catch (JSONException | ClassCastException | MapRecordFailure | GeneralSecurityException
                | IOException | IllegalArgumentException | ProviderException | SecurityException exception) {
            throw new MapRecordFailure(MapRecordFailure.Code.RESTORE_FAILED,
                    "저장된 즐겨찾기를 불러오지 못했습니다. 기존 기록은 유지됩니다.", exception);
        }
    }

    /** 동기 저장 성공 뒤에만 반환해 저장 실패가 성공처럼 표시되지 않게 한다. */
    @Override
    public void writeAll(List<SavedMapPoint> favorites) {
        if (favorites == null || favorites.size() > LocalMapRecordsService.MAXIMUM_FAVORITES) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "즐겨찾기 목록을 확인해 주세요.");
        }
        Set<String> ids = new HashSet<>();
        for (SavedMapPoint point : favorites) {
            if (point == null || !ids.add(point.id())) {
                throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                        "즐겨찾기 목록에 잘못된 항목이 있습니다.");
            }
        }
        try {
            JSONArray entries = new JSONArray();
            for (SavedMapPoint point : favorites) {
                JSONObject entry = new JSONObject();
                entry.put("id", point.id());
                entry.put("name", point.name());
                entry.put("latitude", point.latitude());
                entry.put("longitude", point.longitude());
                entry.put("note", point.note());
                entries.put(entry);
            }
            JSONObject root = new JSONObject();
            root.put("version", SCHEMA_VERSION);
            root.put("favorites", entries);
            JSONObject envelope = new JSONObject();
            envelope.put("version", SCHEMA_VERSION);
            envelope.put("payload", cipher.encrypt(root.toString()));
            if (!preferences.edit().putString(STORAGE_KEY, envelope.toString()).commit()) {
                throw new MapRecordFailure(MapRecordFailure.Code.WRITE_FAILED,
                        "즐겨찾기를 저장하지 못했습니다. 다시 시도해 주세요.");
            }
        } catch (JSONException | GeneralSecurityException | IOException | ProviderException
                | SecurityException exception) {
            throw new MapRecordFailure(MapRecordFailure.Code.WRITE_FAILED,
                    "즐겨찾기를 저장하지 못했습니다. 다시 시도해 주세요.", exception);
        }
    }
}
