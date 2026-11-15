package android.os;

/** 시험에서 직접 이동시키는 단조 시계다. */
public final class SystemClock {
    static long now;

    /** 현재 시험 시각을 반환한다. */
    public static long elapsedRealtime() {
        return now;
    }
}
