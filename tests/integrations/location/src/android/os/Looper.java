package android.os;

/** JVM 시험의 단일 메인 스레드를 표현한다. */
public final class Looper {
    private static final Looper MAIN = new Looper();

    /** 시험 메인 루퍼를 반환한다. */
    public static Looper getMainLooper() {
        return MAIN;
    }

    /** 시험은 메인 스레드에서만 진행한다. */
    public static Looper myLooper() {
        return MAIN;
    }
}
