package com.example.pleosstarter.kakao;

/** 지도·안내 생명주기와 독립적으로 장소 검색과 상세 조회를 실행한다. */
public interface PlaceSearchService {
    int MAX_RESULTS = 10;

    /** 지도 중심을 기준 위치로 검색하며 새 요청은 이전 검색 응답을 무효화한다. */
    void search(String query, double latitude, double longitude, PlaceSearchCallback callback);

    /** 선택한 장소 ID로 상세 조회하며 새 선택은 이전 상세 응답을 무효화한다. */
    void loadDetails(KakaoPlaceSelection selection, PlaceDetailsCallback callback);

    /** 닫힌 검색창으로 늦은 검색 결과가 전달되지 않게 한다. */
    void cancelSearch();

    /** 닫힌 상세창으로 늦은 상세 결과가 전달되지 않게 한다. */
    void cancelDetails();

    /** 화면 종료 시 모든 결과 전달과 제한 시간 작업을 해제한다. */
    void close();
}
