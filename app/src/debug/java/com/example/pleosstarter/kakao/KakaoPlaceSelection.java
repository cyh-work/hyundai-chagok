package com.example.pleosstarter.kakao;

/** 지도에서 실제 선택한 카카오 POI의 식별자, 이름, WGS84 좌표를 보관한다. */
public final class KakaoPlaceSelection {
    private final long poiId;
    private final String lookupId;
    private final String name;
    private final double latitude;
    private final double longitude;

    /** POI 콜백에서 변환한 장소 정보를 화면용 값으로 구성한다. */
    public KakaoPlaceSelection(long poiId, String name, double latitude, double longitude) {
        this(Long.toString(poiId), name, latitude, longitude);
    }

    /** 검색 응답의 문자열 ID를 손실 없이 보관해 상세 조회에 그대로 사용한다. */
    public KakaoPlaceSelection(String lookupId, String name, double latitude, double longitude) {
        this.lookupId = lookupId == null ? "" : lookupId.trim();
        long numericId = 0;
        try {
            numericId = Long.parseLong(this.lookupId);
        } catch (NumberFormatException ignored) {
            // 주소 검색 등 숫자 POI가 없는 항목도 원래 조회 ID와 위치를 보존한다.
        }
        this.poiId = numericId;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** 상세 API 요청에는 숫자 변환 없이 공급자의 원본 장소 ID를 사용한다. */
    public String getLookupId() {
        return lookupId;
    }

    /** 카카오 지도에서 받은 장소 식별자를 반환한다. */
    public long getPoiId() {
        return poiId;
    }

    /** 선택한 장소의 표시 이름을 반환한다. */
    public String getName() {
        return name;
    }

    /** 장소의 WGS84 위도를 반환한다. */
    public double getLatitude() {
        return latitude;
    }

    /** 장소의 WGS84 경도를 반환한다. */
    public double getLongitude() {
        return longitude;
    }
}
