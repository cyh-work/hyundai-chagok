package com.example.pleosstarter.simulation;

/** 길 안내 엔진 없이 재생할 도로 경로 좌표만 비동기로 제공한다. */
public interface RouteSource {
    /** 경로 조회의 성공·실패를 재생 컨트롤러와 같은 스레드에서 전달한다. */
    interface Callback {
        /** 유효한 이동 거리를 가진 경로를 전달한다. */
        void onSuccess(RoutePath path);

        /** 조회 또는 좌표 변환 실패를 전달한다. */
        void onFailure(PlaybackFailure failure);
    }

    /** 이전 요청을 무효화하고 지정한 출발·도착점의 도로 경로를 요청한다. */
    void load(RoutePoint origin, RoutePoint destination, Callback callback);

    /** 이전 조회의 결과 전달과 제한 시간 작업을 무효화한다. */
    void cancel();

    /** 공급자를 종료하고 이후 콜백 전달을 막는다. */
    void close();
}
