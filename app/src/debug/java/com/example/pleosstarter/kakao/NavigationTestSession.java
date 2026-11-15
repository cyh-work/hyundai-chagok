package com.example.pleosstarter.kakao;

import android.view.View;

/** 화면이 특정 SDK의 세부 호출에 의존하지 않고 지도·안내 시험을 실행한다. */
public interface NavigationTestSession {
    /** 지도 표시에 사용할 뷰를 반환한다. */
    View getMapView();

    /** SDK 인증 후 지도를 바인딩하고 POI 입력을 연결한다. */
    void initialize();

    /** 주변 건물과 가게를 보기 위한 판교 시험 위치로 카메라를 옮긴다. */
    void showPangyo();

    /** 안내를 유지하면서 선택한 장소로 지도 카메라만 이동한다. */
    void showPlace(KakaoPlaceSelection selection);

    /** 지도 중심과 안내 추적을 유지하면서 평면·입체 시점을 전환한다. */
    void setMapPerspective(MapPerspective perspective);

    /** 검색 기준으로 사용할 현재 지도 중심 좌표를 반환한다. */
    KakaoPlaceSelection getSearchCenter();

    /** 판교의 명시적인 시험 출발점에서 서울역까지 경로를 요청한다. */
    void startSeoulStationRoute(boolean simulation);

    /** 안내를 중지하고 지도 탐색 상태로 돌아간다. */
    void stopGuidance();

    /** Activity가 다시 보일 때 지도 렌더링을 재개한다. */
    void resume();

    /** Activity가 가려질 때 지도 렌더링을 정지한다. */
    void pause();

    /** 안내와 콜백 연결을 해제하고 지도 자원을 정리한다. */
    void close();
}
