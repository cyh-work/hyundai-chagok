package com.example.pleosstarter.notes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 위치 기록별 이탈과 재진입 상태를 관리해 같은 통과 중 반복 알림을 막는다. */
final class LocationReentryTracker {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private static final double ENTRY_RADIUS_METERS = 100;
    private static final double EXIT_RADIUS_METERS = 130;
    private final Map<LocationNote, Boolean> armedNotes = new HashMap<>();

    /** 새로 저장한 기록은 이탈하기 전까지 재진입 대상으로 등록하지 않는다. */
    void suppressUntilExit(LocationNote note) {
        armedNotes.put(note, false);
    }

    /** 이동 전 기록이 더 없으면 상태를 정리하고 이동 직후 새 위치의 알림은 억제한다. */
    void relocate(LocationNote original, LocationNote moved, boolean originalRemains) {
        if (!originalRemains) {
            armedNotes.remove(original);
        }
        suppressUntilExit(moved);
    }

    /** 같은 값의 마지막 기록을 삭제했을 때 해당 위치의 재진입 상태도 정리한다. */
    void forget(LocationNote note) {
        armedNotes.remove(note);
    }

    /** 처음 만난 복원 기록과 재진입한 기록 중 가장 가까운 하나를 반환한다. */
    LocationNote onPosition(List<LocationNote> notes, double latitude, double longitude) {
        LocationNote nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LocationNote note : notes) {
            double distance = distanceMeters(latitude, longitude, note.latitude(), note.longitude());
            boolean armed = armedNotes.getOrDefault(note, true);
            if (distance > EXIT_RADIUS_METERS) {
                armedNotes.put(note, true);
                continue;
            }
            if (armed && distance <= ENTRY_RADIUS_METERS && distance < nearestDistance) {
                nearest = note;
                nearestDistance = distance;
            }
        }

        if (nearest != null) {
            armedNotes.put(nearest, false);
        }
        return nearest;
    }

    /** 하버사인 공식으로 두 위경도의 지표면 거리를 미터로 계산한다. */
    private double distanceMeters(double firstLatitude, double firstLongitude,
            double secondLatitude, double secondLongitude) {
        double latitudeDifference = Math.toRadians(secondLatitude - firstLatitude);
        double longitudeDifference = Math.toRadians(secondLongitude - firstLongitude);
        double latitudeSine = Math.sin(latitudeDifference / 2);
        double longitudeSine = Math.sin(longitudeDifference / 2);
        double haversine = latitudeSine * latitudeSine
                + Math.cos(Math.toRadians(firstLatitude)) * Math.cos(Math.toRadians(secondLatitude))
                * longitudeSine * longitudeSine;
        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.min(1, Math.max(0, haversine))));
    }
}
