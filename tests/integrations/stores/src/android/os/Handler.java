package android.os;

/** 별도 실행기를 주입한 서비스의 Android 연결 표면을 제공한다. */
public class Handler {
    /** 이 대역의 기본 경로는 추가 스레드나 큐를 만들지 않는다. */
    public Handler(Looper looper) {
    }

    /** 호출한 스레드에서 작업을 실행한다. 실제 비동기 검증은 주입 실행기를 사용한다. */
    public boolean post(Runnable operation) {
        operation.run();
        return true;
    }
}
