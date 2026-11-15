package com.example.pleosstarter.records;

import com.example.pleosstarter.kakao.MapRecordMarker;
import com.example.pleosstarter.notes.LocationNote;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 현재 앱 실행 안에서 원·별의 재방문 상태를 관리하며 저장소를 변경하지 않는다. */
public final class LocalRecordRevisitService implements RecordRevisitService {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double ENTRY_RADIUS_METERS = 100;
    private static final double EXIT_RADIUS_METERS = 130;
    private List<MapRecordMarker> records = List.of();
    private final Map<RevisitKey, Boolean> armed = new HashMap<>();
    private boolean restored;

    /** 유효한 저장 참조만 원자적으로 교체하고 후속 목록 변경과 이전 방문 상태를 분리한다. */
    @Override
    public void replaceRecords(List<MapRecordMarker> records) {
        if (records == null) {
            throw invalidInput("다시 방문할 기록 목록을 확인해 주세요.");
        }
        for (MapRecordMarker record : records) {
            if (record == null || !record.movable()) {
                throw invalidInput("다시 방문할 기록의 저장 위치를 확인해 주세요.");
            }
        }

        // 새 목록을 모두 검증한 뒤 상태를 교체해 실패 때 이전 목록과 진입 상태를 보존한다.
        List<MapRecordMarker> replacement = List.copyOf(records);
        Map<RevisitKey, MapRecordMarker> previousRecords = new HashMap<>();
        Map<RevisitKey, Integer> previousCounts = counts(this.records);
        Map<RevisitKey, Integer> replacementCounts = counts(replacement);
        for (MapRecordMarker previous : this.records) {
            previousRecords.put(keyOf(previous), previous);
        }
        Map<RevisitKey, Boolean> nextArmed = new HashMap<>();
        for (MapRecordMarker record : replacement) {
            RevisitKey key = keyOf(record);
            MapRecordMarker previous = previousRecords.get(key);
            boolean unchanged = previous != null && (!record.favorite() || previous.equals(record))
                    && replacementCounts.get(key) <= previousCounts.get(key);
            boolean eligible = !restored || unchanged && armed.getOrDefault(key, true);
            nextArmed.put(key, eligible);
        }
        this.records = replacement;
        armed.clear();
        armed.putAll(nextArmed);
        restored = true;
    }

    /** 매번 현재 위치만 판단하며 표시가 가려져도 130m 이탈 상태는 계속 갱신한다. */
    @Override
    public Optional<MapRecordMarker> onPosition(double latitude, double longitude, boolean presentationAllowed) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw invalidInput("현재 위치를 확인해 주세요.");
        }

        MapRecordMarker nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (MapRecordMarker record : records) {
            RevisitKey key = keyOf(record);
            double distance = distanceMeters(latitude, longitude, record.latitude(), record.longitude());
            if (distance > EXIT_RADIUS_METERS) {
                armed.put(key, true);
                continue;
            }
            if (armed.getOrDefault(key, true) && distance <= ENTRY_RADIUS_METERS
                    && distance < nearestDistance) {
                nearest = record;
                nearestDistance = distance;
            }
        }

        if (!presentationAllowed) {
            return Optional.empty();
        }

        // 동시에 진입한 다른 기록도 소비해 같은 위치에서 카드가 연달아 나타나지 않는다.
        if (nearest != null) {
            for (MapRecordMarker record : records) {
                if (distanceMeters(latitude, longitude, record.latitude(), record.longitude()) <= ENTRY_RADIUS_METERS) {
                    armed.put(keyOf(record), false);
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** 위치나 표시 대기열을 보관하지 않으므로 이미 소비한 진입 상태만 그대로 유지한다. */
    @Override
    public void resetPosition() {
    }

    /** 즐겨찾기는 영속 ID, 메모는 좌표·원문 값으로 묶어 표시 순번 변경과 분리한다. */
    private RevisitKey keyOf(MapRecordMarker record) {
        if (record.favorite()) {
            return new RevisitKey(record.favoriteId(), null);
        }
        return new RevisitKey("", record.note());
    }

    /** 이동으로 같은 값 메모가 추가된 경우와 단순 순번 변경을 개수로 구분한다. */
    private Map<RevisitKey, Integer> counts(List<MapRecordMarker> records) {
        Map<RevisitKey, Integer> result = new HashMap<>();
        for (MapRecordMarker record : records) {
            RevisitKey key = keyOf(record);
            result.put(key, result.getOrDefault(key, 0) + 1);
        }
        return result;
    }

    /** 잘못된 위치나 저장 참조를 기존 기록 서비스의 입력 오류로 구분한다. */
    private MapRecordFailure invalidInput(String message) {
        return new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT, message);
    }

    /** 같은 값의 과거 중복 메모도 한 번의 방문으로 소비할 내부 식별 정보다. */
    private record RevisitKey(String favoriteId, LocationNote note) {
    }

    /** 기존 재진입 판단과 같은 지표면 거리로 표시 반경을 계산한다. */
    private double distanceMeters(double firstLatitude, double firstLongitude,
            double secondLatitude, double secondLongitude) {
        double latitudeSine = Math.sin(Math.toRadians(secondLatitude - firstLatitude) / 2);
        double longitudeSine = Math.sin(Math.toRadians(secondLongitude - firstLongitude) / 2);
        double haversine = latitudeSine * latitudeSine
                + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
                * longitudeSine * longitudeSine;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))));
    }
}
