package android.content;

/** SDK 연결 없이 앱 컨텍스트의 수명만 재현한다. */
public class Context {
    /** 테스트 컨텍스트를 그대로 반환한다. */
    public Context getApplicationContext() {
        return this;
    }
}
