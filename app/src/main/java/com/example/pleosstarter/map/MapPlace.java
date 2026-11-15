package com.example.pleosstarter.map;

/** 실제 지도 장소 또는 도로에 투영한 선택 지점의 좌표를 전달한다. */
public record MapPlace(String name, String category, double latitude, double longitude) {
}
