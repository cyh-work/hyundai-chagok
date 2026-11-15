package com.example.pleosstarter.stores;

/** 각 분류의 원천 검색 수와 실제 수신 수를 중복 제거된 통합 목록과 구분한다. */
public final class StoreCategoryCount {
    private final KakaoStoreCategory category;
    private final int totalCount;
    private final int pageableCount;
    private final int receivedCount;
    private final boolean hasMore;

    /** 한 분류의 원천 메타데이터와 첫 페이지 수신 건수를 보관한다. */
    public StoreCategoryCount(KakaoStoreCategory category, int totalCount, int pageableCount,
                              int receivedCount, boolean hasMore) {
        this.category = category;
        this.totalCount = totalCount;
        this.pageableCount = pageableCount;
        this.receivedCount = receivedCount;
        this.hasMore = hasMore;
    }

    /** 실제 조회한 단일 분류를 반환한다. */
    public KakaoStoreCategory getCategory() {
        return category;
    }

    /** 중복 제거 전 해당 분류의 원천 검색 건수를 반환한다. */
    public int getTotalCount() {
        return totalCount;
    }

    /** 해당 분류에서 원천이 노출할 수 있는 최대 건수를 반환한다. */
    public int getPageableCount() {
        return pageableCount;
    }

    /** 해당 분류 첫 페이지에서 실제 받은 행 수를 반환한다. */
    public int getReceivedCount() {
        return receivedCount;
    }

    /** 원천이 추가 페이지가 있다고 명시했는지 반환한다. */
    public boolean hasMore() {
        return hasMore;
    }

    /** 첫 페이지 제한 또는 원천 노출 한도로 결과 일부만 받은 상태를 반환한다. */
    public boolean isTruncated() {
        return hasMore || receivedCount < totalCount;
    }
}
