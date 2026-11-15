package com.example.pleosstarter.stores;

/** 화면 스레드에서 성공과 실패를 별도로 전달한다. */
public interface StoreSearchCallback {
    /** 검색 결과 없음도 실제 성공 응답의 빈 목록으로 전달한다. */
    void onSuccess(StoreSearchResult result);

    /** 키 누락과 실제 외부 호출 실패를 구분해 전달한다. */
    void onFailure(StoreSearchFailure failure);
}
