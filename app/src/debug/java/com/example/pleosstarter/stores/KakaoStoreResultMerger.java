package com.example.pleosstarter.stores;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 단일 카카오 분류 결과의 장소 ID 중복을 제거하고 실제 거리에 따라 정렬한다. */
final class KakaoStoreResultMerger {
    private static final Comparator<StoreSummary> NEAREST_FIRST = Comparator
            .comparing(StoreSummary::getDistanceMeters, Comparator.nullsLast(Double::compareTo))
            .thenComparing(StoreSummary::getId);

    /** 단일 분류 응답만 허용하며 원천 개수와 고유 표시 개수를 구분한다. */
    StoreSearchResult merge(KakaoStoreQuery query, List<StoreSearchResult> results)
            throws StoreSearchException {
        if (results.size() != 1) {
            throw StoreResponseValidation.invalidResponse(200);
        }

        Map<String, StoreSummary> uniqueStores = new LinkedHashMap<>();
        List<StoreCategoryCount> categoryCounts = new ArrayList<>();
        long totalCount = 0;
        long pageableCount = 0;
        boolean hasMore = false;
        for (StoreSearchResult result : results) {
            categoryCounts.addAll(result.getCategoryCounts());
            totalCount += result.getTotalCount();
            pageableCount += result.getPageableCount();
            hasMore = hasMore || result.hasMore();

            for (StoreSummary store : result.getStores()) {
                StoreSummary previous = uniqueStores.get(store.getId());
                if (previous == null || NEAREST_FIRST.compare(store, previous) < 0) {
                    uniqueStores.put(store.getId(), store);
                }
            }
        }
        if (totalCount > Integer.MAX_VALUE || pageableCount > Integer.MAX_VALUE) {
            throw StoreResponseValidation.invalidResponse(200);
        }

        List<StoreSummary> nearestStores = new ArrayList<>(uniqueStores.values());
        nearestStores.sort(NEAREST_FIRST);
        return new StoreSearchResult(StoreProvider.KAKAO_LOCAL, nearestStores, (int) totalCount,
                (int) pageableCount, hasMore, "", System.currentTimeMillis(), query.getLatitude(),
                query.getLongitude(), query.getRadiusMeters(), query.getCategory().getQueryLabel(), categoryCounts);
    }
}
