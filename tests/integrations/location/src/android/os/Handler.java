package android.os;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 예약 시각 순서대로 실행하는 결정적 JVM 시험 스케줄러다. */
public final class Handler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static long sequence;

    /** 루퍼 의존성을 시험에서 수용한다. */
    public Handler(Looper looper) {
    }

    /** 즉시 실행 큐에 등록한다. */
    public boolean post(Runnable action) {
        return postDelayed(action, 0L);
    }

    /** 지정 시각에 실행할 작업을 등록한다. */
    public boolean postDelayed(Runnable action, long delayMillis) {
        TASKS.add(new Task(this, action, SystemClock.now + delayMillis, sequence++));
        return true;
    }

    /** 동일 작업의 예약만 취소한다. */
    public void removeCallbacks(Runnable action) {
        TASKS.removeIf(task -> task.owner == this && task.action == action);
    }

    /** 이 핸들러의 작업만 취소한다. */
    public void removeCallbacksAndMessages(Object token) {
        TASKS.removeIf(task -> task.owner == this);
    }

    /** 가상 시간을 전진시키며 만료 작업을 순서대로 실행한다. */
    public static void advance(long millis) {
        long target = SystemClock.now + millis;
        while (true) {
            Task next = TASKS.stream()
                    .filter(task -> task.when <= target)
                    .min(Comparator.comparingLong((Task task) -> task.when)
                            .thenComparingLong(task -> task.order))
                    .orElse(null);
            if (next == null) {
                break;
            }
            TASKS.remove(next);
            SystemClock.now = next.when;
            next.action.run();
        }
        SystemClock.now = target;
    }

    /** 독립 시나리오 사이 예약과 시간을 초기화한다. */
    public static void reset() {
        TASKS.clear();
        SystemClock.now = 0L;
        sequence = 0L;
    }

    /** 예약 작업의 소유자와 실행 순서를 보관한다. */
    private static final class Task {
        private final Handler owner;
        private final Runnable action;
        private final long when;
        private final long order;

        /** 예약 작업 정보를 저장한다. */
        private Task(Handler owner, Runnable action, long when, long order) {
            this.owner = owner;
            this.action = action;
            this.when = when;
            this.order = order;
        }
    }
}
