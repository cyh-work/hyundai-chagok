package com.example.pleosstarter.kakao;

import java.util.List;

/** 장소 검색의 성공·실패를 화면 스레드에서 전달한다. */
public interface PlaceSearchCallback {
    /** 최대 10개의 검색 결과를 전달하며 결과 없음은 빈 목록으로 알린다. */
    void onSuccess(List<PlaceDetails> places);

    /** 잘못된 입력과 외부 조회 실패를 구분해 전달한다. */
    void onFailure(KakaoTestFailure failure);
}
