package com.example.pleosstarter.map;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 도형 중심은 장소에, 선의 끝점은 보행 전용이 아닌 도로 지점에 연결한다. */
final class MapSelectionResolver {
    private static final Set<String> NON_DRIVING_ROADS = new HashSet<>(Arrays.asList(
            "footway", "path", "steps", "pedestrian", "cycleway", "railway"));

    /** 별·원 입력은 표시 장소를 선택하고 선 입력은 가까운 도로에 투영한다. */
    MapPlace resolve(MapGesture.Type type, float centerX, float centerY, List<MapPlace> visiblePlaces,
            MapData map, MapProjection projection, float selectionRadius) {
        if (type == MapGesture.Type.LINE) {
            return resolveRoad(centerX, centerY, map, projection, selectionRadius);
        }

        MapPlace closest = null;
        double closestDistance = selectionRadius;
        for (MapPlace place : visiblePlaces) {
            double distance = Math.hypot(projection.x(place.longitude()) - centerX,
                    projection.y(place.latitude()) - centerY);
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = place;
            }
        }
        return closest;
    }

    /** 끝점 근처에서 차량 통행에 쓰이는 도로 선분을 찾는다. */
    private MapPlace resolveRoad(float centerX, float centerY, MapData map, MapProjection projection,
            float selectionRadius) {
        // 보행 전용 선을 제외하고 실제 도로 선분의 최근접 좌표를 계산한다.
        StrokePoint snapped = null;
        double closestDistance = selectionRadius;
        for (MapData.Road road : map.roads()) {
            if (NON_DRIVING_ROADS.contains(road.kind())) {
                continue;
            }
            for (int index = 1; index < road.points().size(); index++) {
                MapData.Coordinate first = road.points().get(index - 1);
                MapData.Coordinate second = road.points().get(index);
                StrokePoint candidate = closestPoint(centerX, centerY, projection.x(first.longitude()),
                        projection.y(first.latitude()), projection.x(second.longitude()), projection.y(second.latitude()));
                double distance = Math.hypot(candidate.x() - centerX, candidate.y() - centerY);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    snapped = candidate;
                }
            }
        }
        if (snapped == null) {
            return null;
        }
        return new MapPlace("선택한 도로 지점", "도로", projection.latitude(snapped.y()), projection.longitude(snapped.x()));
    }

    /** 선분 바깥으로 벗어나지 않는 최근접 화면 좌표를 구한다. */
    private StrokePoint closestPoint(float x, float y, float startX, float startY, float endX, float endY) {
        float differenceX = endX - startX;
        float differenceY = endY - startY;
        float squaredLength = differenceX * differenceX + differenceY * differenceY;
        float fraction = 0;
        if (squaredLength > 0) {
            fraction = Math.max(0, Math.min(1, ((x - startX) * differenceX + (y - startY) * differenceY) / squaredLength));
        }
        return new StrokePoint(startX + fraction * differenceX, startY + fraction * differenceY);
    }
}
