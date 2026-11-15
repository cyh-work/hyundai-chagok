package android.os;

/** 클라이언트의 공개 호출을 메인 스레드 호출로 재현한다. */
public final class Looper {
    private static final Looper MAIN = new Looper();

    /** 시험용 메인 루퍼를 반환한다. */
    public static Looper getMainLooper() {
        return MAIN;
    }

    /** 현재 시험이 메인 스레드에서 실행 중임을 반환한다. */
    public static Looper myLooper() {
        return MAIN;
    }
}
