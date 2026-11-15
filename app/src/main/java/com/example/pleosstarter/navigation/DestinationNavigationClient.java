package com.example.pleosstarter.navigation;

/** 선택 장소를 기본 내비에 전달하고 진행 결과는 생성 시 주입한 관찰자로 알린다. */
public interface DestinationNavigationClient {

    /** 메인 스레드에서 연결과 경로 요청을 시작하며 진행 중 중복 호출은 무시한다. */
    void start(NavigationDestination destination);

    /** 메인 스레드에서 대기·연결·콜백을 해제하며 이미 시작된 기본 내비 안내는 취소하지 않는다. */
    void close();
}
