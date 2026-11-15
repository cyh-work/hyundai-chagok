package com.example.pleosstarter.kakao;

import com.example.pleosstarter.location.CurrentPosition;
import com.example.pleosstarter.location.LocationFailure;

/** 지도 준비, 차량 위치와 장소 선택을 화면에 전달하는 계약이다. */
public interface PlaceMapListener {
    /** 지도 조작 가능 상태를 전달한다. */
    void onStatus(String message, boolean ready);
    /** 실제 위치 서비스 응답을 출처 정보와 함께 전달한다. */
    void onPosition(CurrentPosition position);
    /** 위치가 없거나 갱신에 실패했음을 전달한다. */
    void onLocationUnavailable(LocationFailure failure);
    /** 지도에서 두 번 누른 장소를 전달한다. */
    void onPlaceSelected(KakaoPlaceSelection selection);
    /** 지도 탭으로 이전 장소 선택이 해제되어 안내 버튼도 숨겨야 함을 알린다. */
    void onMapSelectionCleared();
    /** 버튼과 두 손가락 기울기 입력에 따른 실제 보기 상태를 전달한다. */
    void onMapPerspectiveChanged(boolean threeDimensional);
    /** 인증·지도 연결 실패를 전달한다. */
    void onFailure(KakaoTestFailure failure);
}
