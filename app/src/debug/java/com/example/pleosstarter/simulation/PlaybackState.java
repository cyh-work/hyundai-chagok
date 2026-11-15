package com.example.pleosstarter.simulation;

/** 자동 이동의 조회·재생·정지 상태를 화면 상태와 실제 위치로부터 구분한다. */
public enum PlaybackState {
    STOPPED,
    LOADING,
    PLAYING,
    PAUSED,
    FINISHED,
    FAILED
}
