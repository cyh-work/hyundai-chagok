package android.content;

/** 앱 컨텍스트 보관만 사용하는 실제 클라이언트를 JVM에서 실행하는 대역이다. */
public class Context {
    /** 동일한 시험 컨텍스트를 반환한다. */
    public Context getApplicationContext() {
        return this;
    }
}
