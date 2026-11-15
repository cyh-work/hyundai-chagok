package android.util;

/** 제품 로그 호출이 Android 런타임을 요구하지 않도록 수용한다. */
public final class Log {
    /** 정보 로그를 테스트 표준 출력과 분리한다. */
    public static int i(String tag, String message) {
        return 0;
    }

    /** 경고 로그를 테스트 표준 출력과 분리한다. */
    public static int w(String tag, String message) {
        return 0;
    }
}
