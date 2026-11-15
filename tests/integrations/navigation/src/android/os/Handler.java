package android.os;

import java.util.ArrayList;
import java.util.List;

/** 실제 시간을 기다리지 않고 콜백 큐와 제한 시간 경계를 재현한다. */
public final class Handler {
    private static final List<Scheduled> QUEUE = new ArrayList<>();
    private static long now;

    /** 예약 주체와 시각을 함께 보관한다. */
    private record Scheduled(Handler owner, Runnable callback, long due) {
    }

    /** 제품 클래스와 동일한 생성자 계약을 제공한다. */
    public Handler(Looper looper) {
    }

    /** 즉시 실행 대상도 큐에 보관해 지연 콜백을 시험한다. */
    public boolean post(Runnable callback) {
        return postDelayed(callback, 0);
    }

    /** 지정한 가상 시간 이후에 실행할 콜백을 예약한다. */
    public boolean postDelayed(Runnable callback, long delay) {
        QUEUE.add(new Scheduled(this, callback, now + delay));
        return true;
    }

    /** 현재 핸들러에서 동일한 콜백의 예약만 제거한다. */
    public void removeCallbacks(Runnable callback) {
        QUEUE.removeIf(item -> item.owner() == this && item.callback() == callback);
    }

    /** 현재 핸들러에 속한 작업만 제거한다. */
    public void removeCallbacksAndMessages(Object token) {
        QUEUE.removeIf(item -> item.owner() == this);
    }

    /** 현재 시각에 도달한 콜백을 예약 순서대로 모두 처리한다. */
    public static void flush() {
        while (true) {
            Scheduled next = null;
            for (Scheduled item : QUEUE) {
                if (item.due() <= now && (next == null || item.due() < next.due())) {
                    next = item;
                }
            }
            if (next == null) {
                return;
            }
            QUEUE.remove(next);
            next.callback().run();
        }
    }

    /** 가상 시간을 전진시킨 뒤 해당 시각까지의 작업을 처리한다. */
    public static void advance(long millis) {
        now += millis;
        flush();
    }

    /** 테스트 간 예약과 시각을 초기화한다. */
    public static void reset() {
        QUEUE.clear();
        now = 0;
    }
}
