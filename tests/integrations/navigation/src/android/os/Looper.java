package android.os;

/** 단일 테스트 메인 루퍼를 제공한다. */
public final class Looper {
    private static final Looper MAIN = new Looper();

    /** 테스트의 단일 메인 루퍼를 반환한다. */
    public static Looper getMainLooper() {
        return MAIN;
    }
}
