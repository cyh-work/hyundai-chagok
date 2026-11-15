package com.example.pleosstarter.stores;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 카카오 Local의 음식점·카페·편의점·관광지 중 선택한 분류의 첫 페이지를 조회한다. */
final class KakaoLocalStoreClient implements StoreSearchClient {
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/local/search/category.json";
    private final StoreHttpTransport transport;

    /** 실제 HTTPS 또는 검증용 전송 경계를 주입받는다. */
    KakaoLocalStoreClient(StoreHttpTransport transport) {
        this.transport = transport;
    }

    /** 기존 음식점 500m 호출 계약을 유지하며 새 조건 조회로 위임한다. */
    @Override
    public StoreSearchResult search(double latitude, double longitude, String key)
            throws IOException, StoreSearchException {
        return search(new KakaoStoreQuery(latitude, longitude, KakaoStoreCategory.RESTAURANT,
                KakaoStoreQuery.EXPANDED_RADIUS_METERS), key);
    }

    /** 선택한 단일 분류만 조회하며 요청 검증·취소·결과 정렬 계약을 유지한다. */
    @Override
    public StoreSearchResult search(KakaoStoreQuery query, String key)
            throws IOException, StoreSearchException {
        if (query == null || !query.hasSupportedScope()) {
            throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_QUERY,
                    "장소 분류와 검색 범위를 다시 선택해 주세요.", 0));
        }

        // 필터 하나당 요청 하나만 보내고 기존 중복 제거와 거리 정렬을 적용한다.
        requireNotCancelled();
        StoreSearchResult categoryResult = searchCategory(query, key);
        requireNotCancelled();
        return new KakaoStoreResultMerger().merge(query, Collections.singletonList(categoryResult));
    }

    /** 선택한 단일 분류의 거리순 첫 15건만 한 번 요청한다. */
    private StoreSearchResult searchCategory(KakaoStoreQuery query, String key)
            throws IOException, StoreSearchException {
        URL endpoint = new URL(ENDPOINT + "?category_group_code=" + query.getCategory().getApiCode()
                + "&radius=" + query.getRadiusMeters() + "&page=1&size=15&sort=distance"
                + "&x=" + query.getLongitude() + "&y=" + query.getLatitude());
        StoreHttpResponse response = transport.get(endpoint, "KakaoAK " + key);
        requireNotCancelled();
        StoreResponseValidation.requireSuccess(response);
        return parse(response, query);
    }

    /** 기존 파서 검증의 음식점 500m 입력 계약을 유지한다. */
    StoreSearchResult parse(StoreHttpResponse response, double latitude, double longitude)
            throws StoreSearchException {
        return parse(response, new KakaoStoreQuery(latitude, longitude,
                KakaoStoreCategory.RESTAURANT, KakaoStoreQuery.EXPANDED_RADIUS_METERS));
    }

    /** 목록과 메타데이터를 검증하고 누락한 선택 항목은 비워 둔다. */
    StoreSearchResult parse(StoreHttpResponse response, KakaoStoreQuery query)
            throws StoreSearchException {
        try {
            JSONObject root = new JSONObject(response.body);
            JSONObject meta = root.getJSONObject("meta");
            JSONArray documents = root.getJSONArray("documents");
            int totalCount = meta.getInt("total_count");
            int pageableCount = meta.getInt("pageable_count");
            boolean hasMore = !meta.getBoolean("is_end");
            if (totalCount < 0 || pageableCount < documents.length()
                    || pageableCount > totalCount || pageableCount > 45 || documents.length() > 15) {
                throw StoreResponseValidation.invalidResponse(response.status);
            }

            List<StoreSummary> stores = new ArrayList<>();
            for (int index = 0; index < documents.length(); index++) {
                JSONObject document = documents.getJSONObject(index);
                String id = StoreResponseValidation.text(document, "id");
                String name = StoreResponseValidation.text(document, "place_name");
                if (id.isEmpty() || name.isEmpty()) {
                    throw StoreResponseValidation.invalidResponse(response.status);
                }
                Double latitude = StoreResponseValidation.number(document, "y", -90, 90);
                Double longitude = StoreResponseValidation.number(document, "x", -180, 180);
                Double distance = StoreResponseValidation.number(document, "distance", 0, Double.MAX_VALUE);
                if (distance == null) {
                    distance = StoreResponseValidation.distance(query.getLatitude(), query.getLongitude(),
                            latitude, longitude);
                }
                stores.add(new StoreSummary(id, name,
                        StoreResponseValidation.text(document, "category_name"),
                        StoreResponseValidation.text(document, "address_name"),
                        StoreResponseValidation.text(document, "road_address_name"),
                        StoreResponseValidation.text(document, "phone"), "", "", "", "", "",
                        latitude, longitude, distance,
                        StoreResponseValidation.text(document, "place_url")));
            }
            StoreCategoryCount count = new StoreCategoryCount(query.getCategory(), totalCount,
                    pageableCount, stores.size(), hasMore);
            return new StoreSearchResult(StoreProvider.KAKAO_LOCAL, stores, totalCount,
                    pageableCount, hasMore, "", System.currentTimeMillis(), query.getLatitude(),
                    query.getLongitude(), query.getRadiusMeters(), query.getCategory().getQueryLabel(),
                    Collections.singletonList(count));
        } catch (JSONException failure) {
            throw StoreResponseValidation.invalidResponse(response.status);
        }
    }

    /** 취소된 요청의 전송 또는 결과 처리를 중단한다. */
    private void requireNotCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("CANCELLED");
        }
    }
}
