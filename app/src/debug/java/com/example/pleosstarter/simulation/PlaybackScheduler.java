package com.example.pleosstarter.simulation;

/** 재생 타이머를 UI 스레드 또는 검증용 가상 시간 실행기에 연결한다. */
public interface PlaybackScheduler {
    /** 지정한 지연 뒤 같은 직렬 실행 문맥에서 작업을 한 번 실행한다. */
    void schedule(Runnable task, long delayMillis);

    /** 해당 작업의 아직 실행하지 않은 예약을 모두 제거한다. */
    void cancel(Runnable task);
}
