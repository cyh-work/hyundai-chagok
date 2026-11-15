package com.example.pleosstarter.navigation;

/** 내비 요청의 진행 상태와 목적지 확인 결과를 화면에 전달한다. */
public record NavigationEvent(Status status, String destination, int distanceMeters) {

    /** 연결, 경로 탐색, 목적지 확인 및 실패 상태를 구분한다. */
    public enum Status {
        CONNECTING,
        READY,
        REQUESTED,
        STARTED,
        CALCULATED,
        CONFIRMED,
        WAYPOINT_CONFIRMED,
        WAYPOINT_NAME_RECEIVED,
        CONNECTION_FAILED,
        REQUEST_FAILED,
        TIMED_OUT
    }

    /** 목적지 데이터 없이 전달할 진행 이벤트를 만든다. */
    public NavigationEvent(Status status) {
        this(status, "", 0);
    }
}
