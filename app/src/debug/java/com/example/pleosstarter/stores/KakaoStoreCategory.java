package com.example.pleosstarter.stores;

/** 카카오 장소 검색에서 선택할 수 있는 네 가지 단일 분류를 정의한다. */
public enum KakaoStoreCategory {
    RESTAURANT("음식점", "주변 음식점", "FD6"),
    CAFE("카페", "주변 카페", "CE7"),
    CONVENIENCE("편의점", "주변 편의점", "CS2"),
    TOURIST_ATTRACTION("관광지", "주변 관광지", "AT4");

    private final String displayName;
    private final String queryLabel;
    private final String apiCode;

    /** 화면에 표시할 이름을 해당 단일 API 범주와 연결한다. */
    KakaoStoreCategory(String displayName, String queryLabel, String apiCode) {
        this.displayName = displayName;
        this.queryLabel = queryLabel;
        this.apiCode = apiCode;
    }

    /** 분류 탭에 사용할 짧은 이름을 반환한다. */
    public String getDisplayName() {
        return displayName;
    }

    /** 선택한 장소 분류의 검색 범위를 반환한다. */
    public String getQueryLabel() {
        return queryLabel;
    }

    /** 선택한 단일 분류의 공식 API 코드를 반환한다. */
    public String getApiCode() {
        return apiCode;
    }

    /** 모든 장소 분류는 선택한 지점의 100m 안에서 조회한다. */
    public int getDefaultRadiusMeters() {
        return KakaoStoreQuery.DEFAULT_RADIUS_METERS;
    }
}
