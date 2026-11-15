package android.util;

import java.util.ArrayList;
import java.util.List;

/** 원문 없이 생명주기 코드만 수집하도록 로그 출력 대역을 제공한다. */
public final class Log {
    public static final List<String> MESSAGES = new ArrayList<>();

    /** 상태 진단을 메모리에 보관한다. */
    public static int i(String tag, String message) {
        MESSAGES.add(message);
        return 0;
    }

    /** 오류 진단을 메모리에 보관한다. */
    public static int w(String tag, String message) {
        MESSAGES.add(message);
        return 0;
    }
}
