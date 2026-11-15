package android.util;

/** Android 로그 호출을 수용하되 시험용 인증값을 출력하지 않는다. */
public class Log {
    /** 정보 로그를 외부 출력 없이 수용한다. */
    public static int i(String tag, String message) {
        return 0;
    }

    /** 경고 로그를 외부 출력 없이 수용한다. */
    public static int w(String tag, String message) {
        return 0;
    }
}
