package android.os;

/** 실행기 주입 기반 검증에서 메인 루프 의존성만 충족한다. */
public class Looper {
    /** 기본 생성자 경로는 실행하지 않으므로 별도 루프를 만들지 않는다. */
    public static Looper getMainLooper() {
        return null;
    }
}
