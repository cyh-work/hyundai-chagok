package com.example.pleosstarter.records;

import com.example.pleosstarter.navigation.NavigationDestination;

/** 기록 화면의 명시적인 길안내 요청을 기존 차량 내비 연결 화면에 전달한다. */
@FunctionalInterface
public interface RecordNavigationHandler {
    /** 요청을 접수하면 true, 화면 상태나 진행 중인 요청 때문에 접수하지 못하면 false를 반환한다. */
    boolean requestNavigation(NavigationDestination destination);
}
