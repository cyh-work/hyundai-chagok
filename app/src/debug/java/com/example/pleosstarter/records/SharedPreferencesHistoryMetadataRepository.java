package com.example.pleosstarter.records;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** 기존 앱 전용 설정 저장 방식으로 히스토리 시각만 별도 보관한다. */
public final class SharedPreferencesHistoryMetadataRepository implements HistoryMetadataRepository {
    private static final String KEY_PREFIX = "created_at_v1.";
    private final SharedPreferences preferences;

    /** 본 기록 저장소와 분리한 앱 전용 시각 설정을 주입한다. */
    public SharedPreferencesHistoryMetadataRepository(SharedPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** 올바른 시각만 복원하며 손상된 원본을 지우거나 현재 시각으로 바꾸지 않는다. */
    @Override
    public Map<String, Long> readCreatedAt() {
        try {
            Map<String, Long> result = new HashMap<>();
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                if (!entry.getKey().startsWith(KEY_PREFIX)) {
                    continue;
                }
                if (!(entry.getValue() instanceof Long timestamp) || timestamp <= 0) {
                    throw new IllegalArgumentException("히스토리 생성 시각의 형식이 올바르지 않습니다.");
                }
                result.put(entry.getKey().substring(KEY_PREFIX.length()), timestamp);
            }
            return Collections.unmodifiableMap(result);
        } catch (ClassCastException | IllegalArgumentException | SecurityException failure) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.RESTORE_FAILED,
                    "기록은 유지되지만 히스토리 시간 정보를 읽지 못했어요.", failure);
        }
    }

    /** 중복 저장은 기존 시각을 유지하고 실제 동기 저장 실패를 별도로 알린다. */
    @Override
    public synchronized void rememberCreatedAt(String historyId, long createdAtMillis) {
        validateId(historyId);
        if (createdAtMillis <= 0) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "기록은 저장했어요. 히스토리 시간은 저장하지 못했어요.", null);
        }
        try {
            String key = KEY_PREFIX + historyId;
            if (preferences.contains(key)) {
                if (preferences.getLong(key, 0) <= 0) {
                    throw new IllegalArgumentException("저장된 생성 시각이 올바르지 않습니다.");
                }
                return;
            }
            if (!preferences.edit().putLong(key, createdAtMillis).commit()) {
                throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                        "기록은 저장했어요. 히스토리 시간은 저장하지 못했어요.", null);
            }
        } catch (ClassCastException | IllegalArgumentException | SecurityException failure) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "기록은 저장했어요. 히스토리 시간은 저장하지 못했어요.", failure);
        }
    }

    /** 삭제된 즐겨찾기의 시각만 제거하고 다른 기록은 그대로 보존한다. */
    @Override
    public synchronized void removeCreatedAt(String historyId) {
        validateId(historyId);
        try {
            if (!preferences.edit().remove(KEY_PREFIX + historyId).commit()) {
                throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                        "기록은 삭제했어요. 히스토리 시간 정보 정리는 완료하지 못했어요.", null);
            }
        } catch (SecurityException failure) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "기록은 삭제했어요. 히스토리 시간 정보 정리는 완료하지 못했어요.", failure);
        }
    }

    /** 시각 키만 단일 편집으로 교체하며 다른 설정과 기존 시각 값은 건드리지 않는다. */
    @Override
    public synchronized void replaceCreatedAt(Map<String, Long> timestamps) {
        if (timestamps == null) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "위치는 옮겼지만 기록 시간은 갱신하지 못했어요.", null);
        }
        Map<String, Long> snapshot = new HashMap<>(timestamps);
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            validateId(entry.getKey());
            if (entry.getValue() == null || entry.getValue() <= 0) {
                throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                        "위치는 옮겼지만 기록 시간은 갱신하지 못했어요.", null);
            }
        }

        try {
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : preferences.getAll().keySet()) {
                if (key.startsWith(KEY_PREFIX)) {
                    editor.remove(key);
                }
            }
            for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
                editor.putLong(KEY_PREFIX + entry.getKey(), entry.getValue());
            }
            if (!editor.commit()) {
                throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                        "위치는 옮겼지만 기록 시간은 갱신하지 못했어요.", null);
            }
        } catch (ClassCastException | IllegalArgumentException | SecurityException failure) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "위치는 옮겼지만 기록 시간은 갱신하지 못했어요.", failure);
        }
    }

    /** 다른 설정 키에 쓰지 않도록 히스토리 식별자의 범위를 확인한다. */
    private void validateId(String historyId) {
        if (historyId == null || historyId.isBlank() || historyId.length() > 200) {
            throw new HistoryMetadataFailure(HistoryMetadataFailure.Code.WRITE_FAILED,
                    "히스토리 시간 정보를 기록하지 못했어요.", null);
        }
    }
}
