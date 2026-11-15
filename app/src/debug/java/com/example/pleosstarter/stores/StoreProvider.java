package com.example.pleosstarter.stores;

/** 서로 다른 매장 검색 원천과 화면에 표시할 제공기관을 구분한다. */
public enum StoreProvider {
    KAKAO_LOCAL("카카오 장소", "주변 음식점"),
    PUBLIC_DATA("소상공인 상가정보", "주변 매장 · 전체 업종");

    private final String displayName;
    private final String queryLabel;

    /** 원천별 표시명과 실제 조회 범위를 지정한다. */
    StoreProvider(String displayName, String queryLabel) {
        this.displayName = displayName;
        this.queryLabel = queryLabel;
    }

    /** 제공기관 표시명을 반환한다. */
    public String getDisplayName() {
        return displayName;
    }

    /** 원천별 업종 필터를 포함한 조회 설명을 반환한다. */
    public String getQueryLabel() {
        return queryLabel;
    }
}
