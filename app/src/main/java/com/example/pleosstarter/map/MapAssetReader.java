package com.example.pleosstarter.map;

import android.content.res.AssetManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** APK 내부의 공개 지도 JSON을 읽고 경계와 좌표를 검증한다. */
final class MapAssetReader {
    private final AssetManager assets;

    /** 지도 파일을 제공하는 Android 자산 관리자를 주입한다. */
    MapAssetReader(AssetManager assets) {
        this.assets = assets;
    }

    /** 지정한 번들 지도 파일을 읽으며 손상되거나 빠진 파일은 오류로 전달한다. */
    MapData read(String fileName) throws MapDataException {
        try (InputStream input = assets.open(fileName);
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            JSONObject bounds = root.getJSONObject("bounds");
            double west = bounds.getDouble("west");
            double south = bounds.getDouble("south");
            double east = bounds.getDouble("east");
            double north = bounds.getDouble("north");
            if (!(west < east && south < north && west >= -180 && east <= 180 && south >= -90 && north <= 90)) {
                throw new JSONException("지도 경계가 올바르지 않습니다.");
            }

            List<MapData.Road> roads = readRoads(root.getJSONArray("roads"));
            List<MapPlace> places = readPlaces(root.getJSONArray("places"));
            if (roads.isEmpty() || places.isEmpty()) {
                throw new JSONException("지도 도로 또는 장소가 없습니다.");
            }
            return new MapData(west, south, east, north, Collections.unmodifiableList(roads),
                    Collections.unmodifiableList(places));
        } catch (IOException | JSONException exception) {
            throw new MapDataException("지도 자료를 읽을 수 없습니다.", exception);
        }
    }

    /** 도로 좌표를 읽고 최소 선분 수를 확인한다. */
    private List<MapData.Road> readRoads(JSONArray entries) throws JSONException {
        List<MapData.Road> roads = new ArrayList<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            JSONArray coordinates = entry.getJSONArray("points");
            List<MapData.Coordinate> points = new ArrayList<>();
            for (int pointIndex = 0; pointIndex < coordinates.length(); pointIndex++) {
                JSONArray coordinate = coordinates.getJSONArray(pointIndex);
                double longitude = coordinate.getDouble(0);
                double latitude = coordinate.getDouble(1);
                validateCoordinate(longitude, latitude);
                points.add(new MapData.Coordinate(longitude, latitude));
            }
            if (points.size() >= 2) {
                roads.add(new MapData.Road(entry.getString("kind"), Collections.unmodifiableList(points)));
            }
        }
        return roads;
    }

    /** 장소 이름과 좌표를 검증해 표시 가능한 장소 목록을 반환한다. */
    private List<MapPlace> readPlaces(JSONArray entries) throws JSONException {
        List<MapPlace> places = new ArrayList<>();
        for (int index = 0; index < entries.length(); index++) {
            JSONObject entry = entries.getJSONObject(index);
            double longitude = entry.getDouble("longitude");
            double latitude = entry.getDouble("latitude");
            validateCoordinate(longitude, latitude);
            String name = entry.getString("name").trim();
            if (name.isEmpty()) {
                throw new JSONException("장소 이름이 비어 있습니다.");
            }
            places.add(new MapPlace(name, entry.getString("category"), latitude, longitude));
        }
        return places;
    }

    /** 전 지구 좌표 범위 밖이나 숫자가 아닌 좌표를 거부한다. */
    private void validateCoordinate(double longitude, double latitude) throws JSONException {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
            throw new JSONException("지도 좌표가 올바르지 않습니다.");
        }
    }
}
