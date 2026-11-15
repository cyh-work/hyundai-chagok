package com.example.pleosstarter.stores;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 첫 페이지의 실제 결과와 전체 검색 수·조회 조건을 구분해 전달한다. */
public final class StoreSearchResult {
    private final StoreProvider provider;
    private final List<StoreSummary> stores;
    private final int totalCount;
    private final int pageableCount;
    private final boolean hasMore;
    private final String referenceMonth;
    private final long retrievedAtMillis;
    private final double latitude;
    private final double longitude;
    private final int radiusMeters;
    private final String queryLabel;
    private final List<StoreCategoryCount> categoryCounts;

    /** 첫 페이지 목록을 복사해 비동기 전달 후에도 변경되지 않게 한다. */
    public StoreSearchResult(StoreProvider provider, List<StoreSummary> stores,
                             int totalCount, int pageableCount, boolean hasMore,
                             String referenceMonth, long retrievedAtMillis,
                             double latitude, double longitude) {
        this(provider, stores, totalCount, pageableCount, hasMore, referenceMonth,
                retrievedAtMillis, latitude, longitude, 500, provider.getQueryLabel(),
                Collections.emptyList());
    }

    /** 실제 검색 범위와 분류별 건수를 함께 전달해 통합 수를 전체 상점 수로 오인하지 않게 한다. */
    public StoreSearchResult(StoreProvider provider, List<StoreSummary> stores,
                             int totalCount, int pageableCount, boolean hasMore,
                             String referenceMonth, long retrievedAtMillis,
                             double latitude, double longitude, int radiusMeters,
                             String queryLabel, List<StoreCategoryCount> categoryCounts) {
        this.provider = provider;
        this.stores = Collections.unmodifiableList(new ArrayList<>(stores));
        this.totalCount = totalCount;
        this.pageableCount = pageableCount;
        this.hasMore = hasMore;
        this.referenceMonth = referenceMonth;
        this.retrievedAtMillis = retrievedAtMillis;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusMeters = radiusMeters;
        this.queryLabel = queryLabel;
        this.categoryCounts = Collections.unmodifiableList(new ArrayList<>(categoryCounts));
    }

    /** 조회한 원천을 반환한다. */
    public StoreProvider getProvider() {
        return provider;
    }

    /** 실제로 수신한 첫 페이지 매장만 반환한다. */
    public List<StoreSummary> getStores() {
        return stores;
    }

    /** 단일 원천 검색 수 또는 분류별 검색 수의 합계이며 isCountSum으로 구분한다. */
    public int getTotalCount() {
        return totalCount;
    }

    /** 원천 노출 가능 수이며 통합 조회에서는 분류별 중복 가능 합계다. */
    public int getPageableCount() {
        return pageableCount;
    }

    /** 현재 목록 이후 추가 페이지가 있는지 반환한다. */
    public boolean hasMore() {
        return hasMore;
    }

    /** 실제 요청에 사용한 검색 반경을 미터로 반환한다. */
    public int getRadiusMeters() {
        return radiusMeters;
    }

    /** 음식점 필터 여부를 포함한 검색 범위를 반환한다. */
    public String getQueryLabel() {
        return queryLabel;
    }

    /** 원천 건수가 분류별 중복 가능 합계인지 반환한다. */
    public boolean isCountSum() {
        return categoryCounts.size() > 1;
    }

    /** 분류별 검색 수와 수신 수의 차이를 확인할 수 있는 원천 메타데이터를 반환한다. */
    public List<StoreCategoryCount> getCategoryCounts() {
        return categoryCounts;
    }

    /** 추가 페이지 또는 원천 노출 제한 때문에 일부 결과만 수신했는지 반환한다. */
    public boolean isTruncated() {
        if (categoryCounts.isEmpty()) {
            return hasMore || stores.size() < totalCount;
        }
        for (StoreCategoryCount count : categoryCounts) {
            if (count.isTruncated()) {
                return true;
            }
        }
        return false;
    }

    /** 원천 기준년월이며 제공되지 않으면 빈 문자열이다. */
    public String getReferenceMonth() {
        return referenceMonth;
    }

    /** 응답을 확인한 시간을 반환하며 원천 기준일과 구분한다. */
    public long getRetrievedAtMillis() {
        return retrievedAtMillis;
    }

    /** 사용자가 선택한 조회 중심 위도를 반환한다. */
    public double getLatitude() {
        return latitude;
    }

    /** 사용자가 선택한 조회 중심 경도를 반환한다. */
    public double getLongitude() {
        return longitude;
    }
}
