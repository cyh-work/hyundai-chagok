package com.example.pleosstarter.stores;

/** 선택 지점·검색 분류·반경을 한 요청으로 보관하며 실행 경계에서 유효성을 검사한다. */
public final class KakaoStoreQuery {
    public static final int DEFAULT_RADIUS_METERS = 100;
    public static final int EXPANDED_RADIUS_METERS = 500;

    private final double latitude;
    private final double longitude;
    private final KakaoStoreCategory category;
    private final int radiusMeters;

    /** 지도에서 선택한 좌표를 차량 현재 위치와 구분해 요청에 고정한다. */
    public KakaoStoreQuery(double latitude, double longitude, KakaoStoreCategory category,
                           int radiusMeters) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.category = category;
        this.radiusMeters = radiusMeters;
    }

    /** 선택 지점 위도를 반환한다. */
    public double getLatitude() {
        return latitude;
    }

    /** 선택 지점 경도를 반환한다. */
    public double getLongitude() {
        return longitude;
    }

    /** 요청한 분류를 반환한다. */
    public KakaoStoreCategory getCategory() {
        return category;
    }

    /** 사용자에게 표시한 검색 반경을 미터로 반환한다. */
    public int getRadiusMeters() {
        return radiusMeters;
    }

    /** 제품에서 허용한 가까운 범위와 명시적 확장 범위인지 확인한다. */
    boolean hasSupportedScope() {
        return category != null && (radiusMeters == DEFAULT_RADIUS_METERS
                || radiusMeters == EXPANDED_RADIUS_METERS);
    }
}
