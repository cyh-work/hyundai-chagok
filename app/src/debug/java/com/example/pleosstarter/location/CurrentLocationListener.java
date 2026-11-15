package com.example.pleosstarter.location;

/** 위치 조회의 성공 또는 실패를 메인 스레드에서 받는다. */
public interface CurrentLocationListener {
    /** 유효한 차량 보고 좌표를 받는다. 수신 시각은 GPS 측위 시각을 의미하지 않는다. */
    void onLocation(CurrentPosition position);

    /** 좌표를 얻지 못한 이유를 받는다. 이전 좌표가 있다면 화면에서 오래된 위치로 구분해야 한다. */
    void onUnavailable(LocationFailure failure);
}
