package android.content;

/** JVM 시험에서 앱 컨텍스트만 제공한다. */
public class Context {
    /** 현재 모의 컨텍스트를 반환한다. */
    public Context getApplicationContext() {
        return this;
    }
}
