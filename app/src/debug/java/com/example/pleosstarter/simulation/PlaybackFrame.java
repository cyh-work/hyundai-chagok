package com.example.pleosstarter.simulation;

/** 자동 이동 시험 위치와 진행 거리이며 실제 차량의 위치 응답이 아니다. */
public record PlaybackFrame(RoutePoint point, double distanceMeters, double totalMeters) {
}
