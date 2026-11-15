package com.example.pleosstarter.navigation;

/** 차량 내비에 넘길 경유 후보의 표시 이름과 WGS84 좌표를 담는다. */
public record NavigationWaypoint(String name, double latitude, double longitude) {
}
