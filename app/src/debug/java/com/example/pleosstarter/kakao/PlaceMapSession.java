package com.example.pleosstarter.kakao;

import android.view.View;
import java.util.List;
import java.util.Optional;

/** 길 안내 없이 현재 위치와 지도 탐색·기록 표시만 제공하는 세션이다. */
public interface PlaceMapSession {
    /** 화면에 연결할 지도 뷰를 반환한다. */
    View getMapView();
    /** 인증과 지도 연결을 시작한다. */
    void initialize();
    /** 마지막 수신 위치로 돌아가 이후 위치를 따라간다. */
    void showCurrentLocation();
    /** 선택한 지도 지점으로 이동한다. */
    void showPlace(KakaoPlaceSelection selection);
    /** 안내 대상으로 선택한 장소에 핀과 이름을 표시하고 해당 지점으로 이동한다. */
    void showSelectedPlace(KakaoPlaceSelection selection);
    /** 안내 대상으로 선택한 장소의 핀만 제거한다. */
    void clearSelectedPlace();
    /** 지도 기울기를 변경한다. */
    void setMapPerspective(MapPerspective perspective);
    /** 현재 지도 중앙을 검색 기준으로 반환한다. */
    KakaoPlaceSelection getSearchCenter();
    /** 그림 중심의 지도 좌표를 반환하며 변환 불가 시 예외를 전달한다. */
    KakaoPlaceSelection locationAtScreen(float x, float y);
    /** 그림을 그리는 동안 위치 추적 카메라를 해제한다. */
    void freezeCamera();
    /** 터치 전 지도 투영을 보관하고 자동 추적을 잠시 해제한다. */
    void beginMapInteraction();
    /** 도형을 그리기 시작한 투영으로 복귀한 뒤에만 완료 콜백을 호출한다. */
    void restoreGestureCamera(Runnable restored, java.util.function.Consumer<String> failed);
    /** 도형·탭이면 이전 추적 상태로 복귀하고 일반 탐색이면 추적 해제를 유지한다. */
    void finishMapInteraction(boolean resumeFollowing);
    /** 실제 위치와 구분되는 시험 위치 표시를 시작한다. */
    void beginTestMovement();
    /** 자체 재생기가 계산한 시험 위치만 지도에 전달한다. */
    void showTestPosition(double latitude, double longitude);
    /** 시험 위치를 제거하고 마지막 차량 위치 수신으로 복귀한다. */
    void endTestMovement();
    /** 저장한 기록의 지도 마커를 교체한다. */
    void showRecords(List<MapRecordMarker> markers);
    /** 현재 화면에서 잡을 수 있는 저장 마커가 있을 때만 미리보기를 시작한다. */
    boolean beginRecordDrag(float x, float y);
    /** 저장하지 않고 잡은 마커의 화면상 위치만 변경한다. */
    void previewRecordDrag(float x, float y);
    /** 미리보기를 복원하고 실제 이동을 놓은 경우에만 저장 요청을 반환한다. */
    Optional<MapRecordMove> endRecordDrag(float x, float y, boolean cancelled);
    /** 짧은 지도 탭으로 읽을 저장 마커를 찾으며 빈 즐겨찾기도 반환한다. */
    Optional<MapRecordMarker> findRecordAt(float x, float y);
    /** 짧은 지도 탭에서 이전 장소 선택을 해제한다. */
    void dismissSelectionAt(float x, float y);
    /** 화면 복귀 시 지도와 위치 수신을 재개한다. */
    void resume();
    /** 화면 이탈 시 지도와 위치 수신을 정지한다. */
    void pause();
    /** 지도와 위치 서비스 연결을 해제한다. */
    void close();
}
