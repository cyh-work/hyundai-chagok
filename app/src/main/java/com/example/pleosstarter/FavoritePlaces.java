package com.example.pleosstarter;

import android.content.SharedPreferences;

import com.example.pleosstarter.map.MapPlace;

/** 장소별 즐겨찾기 상태를 현재 기기에 저장한다. */
final class FavoritePlaces {
    private final SharedPreferences preferences;

    /** 즐겨찾기 전용 저장소를 연결한다. */
    FavoritePlaces(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    /** 지정한 장소가 즐겨찾기에 저장되어 있는지 확인한다. */
    boolean contains(MapPlace place) {
        return preferences.getBoolean(placeKey(place), false);
    }

    /** 장소를 추가하거나 해제하고, 변경 후 저장 여부를 반환한다. */
    boolean toggle(MapPlace place) {
        String key = placeKey(place);
        if (contains(place)) {
            preferences.edit().remove(key).apply();
            return false;
        }

        preferences.edit().putBoolean(key, true).apply();
        return true;
    }

    /** 같은 이름의 서로 다른 장소를 좌표로 구별한다. */
    private String placeKey(MapPlace place) {
        return place.name() + "|" + place.latitude() + "|" + place.longitude();
    }
}
