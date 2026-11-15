package com.example.pleosstarter.simulation;

import android.os.Handler;

import java.util.Objects;

/** 주입된 Android 화면 스레드 실행기로 재생 예약과 취소만 처리한다. */
public final class AndroidPlaybackScheduler implements PlaybackScheduler {
    private final Handler handler;

    /** 화면과 같은 직렬 실행 문맥의 Handler를 주입한다. */
    public AndroidPlaybackScheduler(Handler handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    /** 다음 재생 갱신을 지정된 지연 뒤 한 번 예약한다. */
    @Override
    public void schedule(Runnable task, long delayMillis) {
        handler.postDelayed(task, delayMillis);
    }

    /** 이 재생 작업의 예약만 취소하고 다른 화면 작업은 유지한다. */
    @Override
    public void cancel(Runnable task) {
        handler.removeCallbacks(task);
    }
}
