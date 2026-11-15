package com.example.pleosstarter.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 카카오 공개 경로 좌표의 x=경도·y=위도를 검증된 재생 경로로 변환한다. */
public final class KakaoRouteGeometryMapper {
    private static final String LONGITUDE_KEY = "x";
    private static final String LATITUDE_KEY = "y";

    /** 누락된 좌표를 건너뛰어 도로를 가로지르지 않고 잘못된 응답 전체를 거부한다. */
    public RoutePath map(List<Map<String, Number>> geometry) {
        if (geometry == null || geometry.size() < 2) {
            throw new IllegalArgumentException("자동 이동에 사용할 도로 좌표가 없습니다.");
        }

        List<RoutePoint> points = new ArrayList<>();
        for (Map<String, Number> coordinate : geometry) {
            if (coordinate == null || coordinate.get(LONGITUDE_KEY) == null
                    || coordinate.get(LATITUDE_KEY) == null) {
                throw new IllegalArgumentException("자동 이동 경로의 좌표가 누락되었습니다.");
            }
            points.add(new RoutePoint(coordinate.get(LATITUDE_KEY).doubleValue(),
                    coordinate.get(LONGITUDE_KEY).doubleValue()));
        }
        return new RoutePath(points);
    }
}
