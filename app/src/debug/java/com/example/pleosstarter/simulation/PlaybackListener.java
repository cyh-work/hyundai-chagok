package com.example.pleosstarter.simulation;

/** 재생 위치와 상태를 지도 화면에 전달하며 지도나 실제 위치 공급자를 직접 변경하지 않는다. */
public interface PlaybackListener {
    /** 자동 이동 위치를 전달하며 실제 차량 위치와 구분해서 표시해야 한다. */
    void onPosition(PlaybackFrame frame);

    /** 조회·일시정지·종료 상태 변경을 전달한다. */
    void onStateChanged(PlaybackState state);

    /** 경로를 준비하지 못한 이유를 전달하며 임의 경로로 대체하지 않는다. */
    void onFailure(PlaybackFailure failure);
}
