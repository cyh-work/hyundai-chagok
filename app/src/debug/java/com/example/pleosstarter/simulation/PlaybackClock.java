package com.example.pleosstarter.simulation;

/** 재생 시간 계산에 사용하는 단조 증가 시계를 주입한다. */
public interface PlaybackClock {
    /** 현재 단조 증가 시각을 밀리초로 반환한다. */
    long nowMillis();
}
