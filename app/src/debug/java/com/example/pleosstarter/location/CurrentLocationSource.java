package com.example.pleosstarter.location;

/** 길 안내를 시작하지 않고 차량이 보고하는 현재 위치를 공급한다. 모든 생명주기 호출은 메인 스레드에서 한다. */
public interface CurrentLocationSource extends AutoCloseable {
    /** 수신자를 등록하고 조회를 시작한다. 이미 조회 중이면 기존 조회를 중단하고 새 수신자로 교체한다. */
    void start(CurrentLocationListener listener);

    /** 진행 중인 조회와 연결을 해제한다. 이후 start로 다시 시작할 수 있다. */
    void stop();

    /** 연결을 영구 해제한다. 호출이 끝난 뒤에는 수신자에게 결과를 전달하지 않는다. */
    @Override
    void close();
}
