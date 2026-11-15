package com.example.pleosstarter.simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 경로의 누적 거리를 계산하고 도로 선분을 따라 이동 위치를 보간한다. */
public final class RoutePath {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final double MIN_SEGMENT_METERS = 0.001;
    private final List<RoutePoint> points;
    private final double[] distances;

    /** 중복된 연속 점을 제거하고 이동 거리가 없는 경로는 명확히 거부한다. */
    public RoutePath(List<RoutePoint> sourcePoints) {
        Objects.requireNonNull(sourcePoints, "경로 좌표가 필요합니다.");
        List<RoutePoint> filtered = new ArrayList<>();
        for (RoutePoint point : sourcePoints) {
            Objects.requireNonNull(point, "경로에 비어 있는 좌표가 있습니다.");
            if (filtered.isEmpty()
                    || distanceBetween(filtered.get(filtered.size() - 1), point) > MIN_SEGMENT_METERS) {
                filtered.add(point);
            }
        }
        if (filtered.size() < 2) {
            throw new IllegalArgumentException("이동 가능한 길이가 있는 경로가 필요합니다.");
        }

        points = Collections.unmodifiableList(filtered);
        distances = new double[points.size()];
        for (int index = 1; index < points.size(); index++) {
            distances[index] = distances[index - 1]
                    + distanceBetween(points.get(index - 1), points.get(index));
        }
    }

    /** 검증된 경로의 전체 이동 거리를 미터로 반환한다. */
    public double totalMeters() {
        return distances[distances.length - 1];
    }

    /** 실행 근거에 사용할 실제 경로 좌표 수를 반환한다. */
    public int pointCount() {
        return points.size();
    }

    /** 시작·끝을 넘지 않도록 제한한 뒤 해당 거리의 도로 선분 위 좌표를 반환한다. */
    public RoutePoint pointAt(double traveledMeters) {
        if (!Double.isFinite(traveledMeters)) {
            throw new IllegalArgumentException("이동 거리는 유한한 값이어야 합니다.");
        }
        if (traveledMeters <= 0) {
            return points.get(0);
        }
        if (traveledMeters >= totalMeters()) {
            return points.get(points.size() - 1);
        }

        int found = Arrays.binarySearch(distances, traveledMeters);
        if (found >= 0) {
            return points.get(found);
        }
        int nextIndex = -found - 1;
        int previousIndex = nextIndex - 1;
        double ratio = (traveledMeters - distances[previousIndex])
                / (distances[nextIndex] - distances[previousIndex]);
        RoutePoint previous = points.get(previousIndex);
        RoutePoint next = points.get(nextIndex);
        double latitude = previous.latitude() + (next.latitude() - previous.latitude()) * ratio;
        double longitude = previous.longitude()
                + longitudeDelta(previous.longitude(), next.longitude()) * ratio;
        if (longitude > 180) {
            longitude -= 360;
        } else if (longitude < -180) {
            longitude += 360;
        }
        return new RoutePoint(latitude, longitude);
    }

    /** 경로 좌표 사이의 대권 거리를 구해 일정한 미터 단위 속도를 유지한다. */
    private static double distanceBetween(RoutePoint first, RoutePoint second) {
        double latitudeDelta = Math.toRadians(second.latitude() - first.latitude());
        double longitudeDelta = Math.toRadians(longitudeDelta(first.longitude(), second.longitude()));
        double haversine = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(first.latitude())) * Math.cos(Math.toRadians(second.latitude()))
                * Math.pow(Math.sin(longitudeDelta / 2), 2);
        return EARTH_RADIUS_METERS * 2 * Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    /** 날짜 변경선을 지나는 경로도 짧은 방향으로 보간한다. */
    private static double longitudeDelta(double from, double to) {
        double delta = to - from;
        if (delta > 180) {
            delta -= 360;
        } else if (delta < -180) {
            delta += 360;
        }
        return delta;
    }
}
