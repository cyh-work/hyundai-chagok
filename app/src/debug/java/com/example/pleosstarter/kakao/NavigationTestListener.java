package com.example.pleosstarter.kakao;

/** SDK의 비동기 결과를 시험 화면에 전달하는 계약이다. */
public interface NavigationTestListener {
    /** 초기화·경로 검색·안내 상태와 사용 가능한 조작 여부를 전달한다. */
    void onStatus(String message, boolean mapReady);

    /** 지도 POI를 두 번 누른 실제 결과를 전달한다. */
    void onPlaceSelected(KakaoPlaceSelection selection);

    /** 위치 수신이나 모의주행 갱신을 실제 GPS와 구분해 전달한다. */
    void onGuidanceUpdate(String message);

    /** 실패를 별도 코드와 사용자 안내로 전달한다. */
    void onFailure(KakaoTestFailure failure);
}
