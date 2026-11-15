package com.example.pleosstarter.records;

/** 지도에서 그린 위치와 즐겨찾기 메모를 안정적인 식별자에 연결한다. */
public record SavedMapPoint(String id, String name, double latitude, double longitude, String note) {
    /** 메모가 없던 기존 즐겨찾기와 네 인자 호출을 그대로 복원한다. */
    public SavedMapPoint(String id, String name, double latitude, double longitude) {
        this(id, name, latitude, longitude, "");
    }

    /** 저장본과 외부 입력 모두에 좌표·문자열 조건을 적용한다. */
    public SavedMapPoint {
        if (id == null || id.isBlank() || name == null || name.isBlank()
                || name.length() > 120 || note == null || note.length() > 2000
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "기록할 위치와 이름을 확인해 주세요.");
        }
        id = id.trim();
        name = name.trim();
        note = note.trim();
    }

    /** 자동 디버그 출력에 즐겨찾기 이름·메모·위치 원문을 포함하지 않는다. */
    @Override
    public String toString() {
        return "SavedMapPoint{hasNote=" + !note.isEmpty() + "}";
    }
}
