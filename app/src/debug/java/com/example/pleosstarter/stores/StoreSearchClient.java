package com.example.pleosstarter.stores;

import java.io.IOException;

/** 원천별 요청과 응답 변환을 서비스와 분리한다. */
interface StoreSearchClient {
    /** 선택 좌표의 첫 페이지를 실제 호출하며 오류는 정해진 계약으로 전달한다. */
    StoreSearchResult search(double latitude, double longitude, String key)
            throws IOException, StoreSearchException;

    /** 카카오 요청을 지원하지 않는 클라이언트가 검색 범위를 조용히 무시하지 않게 한다. */
    default StoreSearchResult search(KakaoStoreQuery query, String key)
            throws IOException, StoreSearchException {
        throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_QUERY,
                "이 제공처에서는 선택한 검색 방식을 사용할 수 없어요.", 0));
    }
}
