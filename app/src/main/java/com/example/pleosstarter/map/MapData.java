package com.example.pleosstarter.map;

import java.util.List;

/** 번들 지도 경계, 도로 및 장소의 검증된 읽기 전용 묶음이다. */
record MapData(double west, double south, double east, double north,
        List<Road> roads, List<MapPlace> places) {
    /** 주어진 차량 좌표가 이 자료의 지도 범위에 들어오는지 확인한다. */
    boolean contains(double latitude, double longitude) {
        return latitude >= south && latitude <= north && longitude >= west && longitude <= east;
    }

    /** 실제 좌표로 구성한 도로의 종류와 꼭짓점 목록이다. */
    record Road(String kind, List<Coordinate> points) {
    }

    /** 위경도 순서 혼동을 방지하는 지도 좌표다. */
    record Coordinate(double longitude, double latitude) {
    }
}
