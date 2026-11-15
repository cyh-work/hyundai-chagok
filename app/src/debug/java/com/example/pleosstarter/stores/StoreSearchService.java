package com.example.pleosstarter.stores;

/** 지도 선택 좌표에 대한 비동기 매장 조회와 수명 관리를 정의한다. */
public interface StoreSearchService {
    /** 각 원천의 새 요청은 해당 원천의 이전 요청을 취소한다. */
    void search(StoreProvider provider, double latitude, double longitude, StoreSearchCallback callback);

    /** 카카오 세 분류의 선택과 100m 또는 500m 범위를 반영해 매장을 조회한다. */
    void search(KakaoStoreQuery query, StoreSearchCallback callback);

    /** 진행 중인 모든 요청의 결과 전달을 취소한다. */
    void cancel();

    /** 요청을 취소하고 작업 스레드를 해제하며 이후 조회를 거부한다. */
    void close();
}
