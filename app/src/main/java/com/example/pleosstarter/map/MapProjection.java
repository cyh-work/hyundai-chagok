package com.example.pleosstarter.map;

/** 고정 지도 경계를 View의 안쪽 표시 영역과 상호 변환한다. */
final class MapProjection {
    private final MapData map;
    private final float padding;
    private final float width;
    private final float height;

    /** 표시 가능한 크기와 패딩을 고정해 한 프레임의 투영을 구성한다. */
    MapProjection(MapData map, int width, int height, float padding) {
        this.map = map;
        this.padding = padding;
        this.width = Math.max(1, width - padding * 2);
        this.height = Math.max(1, height - padding * 2);
    }

    /** 경도를 화면 가로 좌표로 변환한다. */
    float x(double longitude) {
        return padding + (float) ((longitude - map.west()) / (map.east() - map.west())) * width;
    }

    /** 위도를 화면 세로 좌표로 변환한다. */
    float y(double latitude) {
        return padding + (float) ((map.north() - latitude) / (map.north() - map.south())) * height;
    }

    /** 화면 가로 좌표에서 경도를 복원한다. */
    double longitude(float x) {
        return map.west() + (x - padding) / width * (map.east() - map.west());
    }

    /** 화면 세로 좌표에서 위도를 복원한다. */
    double latitude(float y) {
        return map.north() - (y - padding) / height * (map.north() - map.south());
    }
}
