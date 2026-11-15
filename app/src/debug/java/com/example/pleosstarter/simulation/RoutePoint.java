package com.example.pleosstarter.simulation;

/** 실제 위치와 구분하여 자동 이동 경로에만 사용하는 WGS84 좌표다. */
public record RoutePoint(double latitude, double longitude) {
    /** 유효한 지구 좌표만 경로 보간에 전달한다. */
    public RoutePoint {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("자동 이동 경로의 좌표가 유효하지 않습니다.");
        }
    }
}
