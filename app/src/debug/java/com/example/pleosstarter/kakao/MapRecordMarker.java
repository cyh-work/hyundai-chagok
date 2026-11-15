package com.example.pleosstarter.kakao;

import com.example.pleosstarter.notes.LocationNote;

/** 원·별의 저장 위치와 선택적인 메모를 지도 표시 계층에 전달하며 원문을 로그로 출력하지 않는다. */
public record MapRecordMarker(double latitude, double longitude, boolean favorite, LocationNote note,
        String favoriteId, int noteIndex) {
    /** 메모의 원문과 좌표가 서로 다른 위치에 표시되지 않도록 검증한다. */
    public MapRecordMarker {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180
                || !favorite && note == null) {
            throw new IllegalArgumentException("지도 기록의 위치와 종류를 확인해 주세요.");
        }
        if (note != null && (Double.compare(latitude, note.latitude()) != 0
                || Double.compare(longitude, note.longitude()) != 0)) {
            throw new IllegalArgumentException("메모와 지도 기록의 위치가 일치하지 않습니다.");
        }
        if (favoriteId == null || noteIndex < -1 || favorite && noteIndex != -1
                || !favorite && !favoriteId.isEmpty()) {
            throw new IllegalArgumentException("지도 기록의 저장 참조를 확인해 주세요.");
        }
    }

    /** 저장 참조가 없는 기존 표시 전용 호출은 이동 대상으로 선택하지 않는다. */
    public MapRecordMarker(double latitude, double longitude, boolean favorite, LocationNote note) {
        this(latitude, longitude, favorite, note, "", -1);
    }

    /** 즐겨찾기 식별자 또는 메모 배열 순번이 있는 저장 기록만 이동할 수 있다. */
    public boolean movable() {
        return favorite ? !favoriteId.isBlank() : noteIndex >= 0;
    }

    /** 디버그 출력이 발생하더라도 메모 내용이 문자열에 포함되지 않게 한다. */
    @Override
    public String toString() {
        return "MapRecordMarker{favorite=" + favorite + "}";
    }
}
