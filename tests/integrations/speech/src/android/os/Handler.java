package android.os;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 실제 클라이언트의 타이머와 지연 콜백 순서를 가상 시간으로 제어한다. */
public final class Handler {
    private record Task(long due, long order, Runnable runnable) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static long time;
    private static long order;

    /** 실제 API와 동일한 생성자 서명을 제공한다. */
    public Handler(Looper looper) {
    }

    /** 현재 시점에 실행할 콜백을 추가한다. */
    public boolean post(Runnable operation) {
        return postDelayed(operation, 0);
    }

    /** 지연 콜백을 등록하되 시험이 시간을 진행하기 전에는 실행하지 않는다. */
    public boolean postDelayed(Runnable operation, long delay) {
        TASKS.add(new Task(time + delay, order++, operation));
        return true;
    }

    /** 동일한 콜백 인스턴스의 예약만 제거한다. */
    public void removeCallbacks(Runnable operation) {
        TASKS.removeIf(task -> task.runnable() == operation);
    }

    /** 현재 즉시 콜백을 모두 실행한다. */
    public static void drain() {
        advance(0);
    }

    /** 목표 시점까지 예약을 순서대로 실행해 지연 정리를 검증한다. */
    public static void advance(long milliseconds) {
        long target = time + milliseconds;
        while (true) {
            TASKS.sort(Comparator.comparingLong(Task::due).thenComparingLong(Task::order));
            if (TASKS.isEmpty() || TASKS.get(0).due() > target) {
                break;
            }
            Task task = TASKS.remove(0);
            time = task.due();
            task.runnable().run();
        }
        time = target;
    }

    /** 시나리오마다 남은 콜백을 제거한다. */
    public static void reset() {
        TASKS.clear();
        time = 0;
        order = 0;
    }

    /** 세션 종료 후 예약 누수를 확인한다. */
    public static int pendingCount() {
        return TASKS.size();
    }
}
