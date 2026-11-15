package com.example.pleosstarter.kakao;

/** 선택한 장소의 상세 조회 결과를 화면 스레드에서 전달한다. */
public interface PlaceDetailsCallback {
    /** 요청한 장소 ID와 일치하는 상세 정보를 전달한다. */
    void onSuccess(PlaceDetails details);

    /** 상세 정보 없음 또는 조회 실패를 기존 장소 선택과 구분한다. */
    void onFailure(KakaoTestFailure failure);
}
