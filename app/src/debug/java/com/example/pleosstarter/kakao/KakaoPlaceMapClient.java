package com.example.pleosstarter.kakao;

import android.content.Context;
import android.util.Log;
import android.view.View;
import com.example.pleosstarter.location.CurrentLocationSource;
import com.example.pleosstarter.location.CurrentLocationListener;
import com.example.pleosstarter.location.CurrentPosition;
import com.example.pleosstarter.location.LocationFailure;
import java.util.List;
import java.util.Optional;

/** 카카오 지도에 Pleos 위치를 표시하며 경로·안내 엔진을 생성하지 않는다. */
public final class KakaoPlaceMapClient implements PlaceMapSession, CurrentLocationListener {
    private final KakaoSdkRuntime runtime;
    private final CurrentLocationSource positions;
    private final PlaceMapListener listener;
    private final KakaoMapController map;
    private CurrentPosition lastPosition;
    private boolean ready;
    private boolean resumed;
    private boolean closed;
    private boolean positioned;
    private boolean testMovement;
    private KakaoPlaceSelection testPosition;

    /** 인증, 위치 공급자와 화면 수신자를 주입한다. */
    public KakaoPlaceMapClient(Context context, KakaoSdkRuntime runtime,
            CurrentLocationSource positions, PlaceMapListener listener) {
        this.runtime = runtime;
        this.positions = positions;
        this.listener = listener;
        map = new KakaoMapController(context, listener::onPlaceSelected);
        map.setPerspectiveListener(listener::onMapPerspectiveChanged);
    }

    /** 지도 컨테이너를 화면에 제공한다. */
    @Override
    public View getMapView() {
        return map.getView();
    }

    /** 인증 후 지도와 위치 수신을 연결한다. */
    @Override
    public void initialize() {
        listener.onStatus("지도 연결 중…", false);
        runtime.initialize(new KakaoSdkRuntime.Completion() {
            /** 지도 바인딩이 완료되면 수신 위치를 기다린다. */
            @Override
            public void onReady() {
                if (closed) {
                    return;
                }
                map.bind(() -> {
                    ready = true;
                    listener.onStatus("지도가 준비됐어요 · 현재 위치 수신 중…", false);
                    Log.i("PlaceMap", "MAP_ONLY_READY routeEngine=false");
                    if (resumed) {
                        positions.start(KakaoPlaceMapClient.this);
                    }
                }, listener::onFailure);
            }

            /** 인증 실패는 지도 성공으로 바꾸지 않는다. */
            @Override
            public void onFailure(KakaoTestFailure failure) {
                if (!closed) {
                    listener.onFailure(failure);
                }
            }
        });
    }

    /** 받은 위치를 점으로 표시하고 첫 응답에서만 카메라를 초기화한다. */
    @Override
    public void onLocation(CurrentPosition position) {
        if (closed || !ready || !resumed) {
            return;
        }
        lastPosition = position;
        if (testMovement) {
            return;
        }
        if (!positioned) {
            map.showLocation(position.getLongitude(), position.getLatitude());
            map.setFollowLocation(true);
            positioned = true;
        }
        map.updateCurrentPosition(position.getLongitude(), position.getLatitude());
        listener.onStatus("제스처 지도", true);
        listener.onPosition(position);
    }

    /** 위치 실패 시 이전 점을 숨겨 오래된 위치가 현재처럼 보이지 않게 한다. */
    @Override
    public void onUnavailable(LocationFailure failure) {
        if (closed || !resumed) {
            return;
        }
        lastPosition = null;
        if (testMovement) {
            return;
        }
        map.hideCurrentPosition();
        listener.onLocationUnavailable(failure);
    }

    /** 현재 수신 위치를 중심으로 복귀하며 추적을 켠다. */
    @Override
    public void showCurrentLocation() {
        map.setFollowLocation(true);
        if (testMovement && testPosition != null) {
            map.updateCurrentPosition(testPosition.getLongitude(), testPosition.getLatitude());
            return;
        }
        if (lastPosition == null) {
            positions.start(this);
            return;
        }
        map.updateCurrentPosition(lastPosition.getLongitude(), lastPosition.getLatitude());
    }

    /** 장소 탐색은 현재 위치 점을 유지한다. */
    @Override
    public void showPlace(KakaoPlaceSelection selection) {
        map.showLocation(selection.getLongitude(), selection.getLatitude());
    }

    /** 저장 기록과 구분되는 선택 장소 핀을 표시하고 그 위치로 카메라를 옮긴다. */
    @Override
    public void showSelectedPlace(KakaoPlaceSelection selection) {
        map.showSelectedPlace(selection);
    }

    /** 현재 위치와 저장 기록은 유지하며 선택 장소 핀만 제거한다. */
    @Override
    public void clearSelectedPlace() {
        map.clearSelectedPlace();
    }

    /** 지도 시점만 변경한다. */
    @Override
    public void setMapPerspective(MapPerspective perspective) {
        map.setPerspective(perspective);
    }

    /** 현재 화면 중앙을 검색 좌표로 변환한다. */
    @Override
    public KakaoPlaceSelection getSearchCenter() {
        return map.locationAtScreen(map.getView().getWidth() / 2.0f,
                map.getView().getHeight() / 2.0f);
    }

    /** 지도의 투영 변환을 사용해 2D와 3D의 그림 위치를 구한다. */
    @Override
    public KakaoPlaceSelection locationAtScreen(float x, float y) {
        return map.locationAtScreen(x, y);
    }

    /** 손가락을 대면 자동 카메라 이동을 해제한다. */
    @Override
    public void freezeCamera() {
        map.setFollowLocation(false);
    }

    /** 터치 시작 위치를 도형 좌표의 기준으로 저장한다. */
    @Override
    public void beginMapInteraction() {
        map.beginMapInteraction();
    }

    /** 시작 투영이 실제 지도에 반영된 뒤에만 도형 저장을 진행한다. */
    @Override
    public void restoreGestureCamera(Runnable restored, java.util.function.Consumer<String> failed) {
        map.restoreGestureCamera(restored, failed);
    }

    /** 도형과 지도 탐색 결과에 따라 추적 재개 여부를 반영한다. */
    @Override
    public void finishMapInteraction(boolean resumeFollowing) {
        map.finishMapInteraction(resumeFollowing);
    }

    /** 차량 위치 수신은 보관하되 화면은 시험 위치가 전담하게 한다. */
    @Override
    public void beginTestMovement() {
        testMovement = true;
        testPosition = null;
        map.setTestPosition(true);
        map.hideCurrentPosition();
        map.setFollowLocation(true);
    }

    /** 시험 위치를 계속 갱신하되 늦은 첫 위치가 사용자의 카메라 추적 해제를 덮지 않게 한다. */
    @Override
    public void showTestPosition(double latitude, double longitude) {
        if (!testMovement || closed || !resumed) {
            return;
        }
        if (testPosition == null && map.isFollowingLocation()) {
            map.showLocation(longitude, latitude);
            map.setFollowLocation(true);
        }
        testPosition = new KakaoPlaceSelection(0, "테스트 이동 위치", latitude, longitude);
        map.updateCurrentPosition(longitude, latitude);
    }

    /** 시험 점을 제거하고 새로 수신한 차량 위치로 돌아간다. */
    @Override
    public void endTestMovement() {
        testMovement = false;
        testPosition = null;
        map.setTestPosition(false);
        if (lastPosition != null) {
            map.showLocation(lastPosition.getLongitude(), lastPosition.getLatitude());
            map.setFollowLocation(true);
            map.updateCurrentPosition(lastPosition.getLongitude(), lastPosition.getLatitude());
            listener.onPosition(lastPosition);
        } else {
            map.hideCurrentPosition();
            if (resumed && !closed) {
                listener.onStatus("차량 위치 수신 중…", true);
                positions.start(this);
            }
        }
    }

    /** 저장 완료된 기록만 지도에 표시한다. */
    @Override
    public void showRecords(List<MapRecordMarker> markers) {
        map.showRecordMarkers(markers);
    }

    /** 지도 표시 계층이 실제 저장 마커를 잡았는지 입력 라우터에 전달한다. */
    @Override
    public boolean beginRecordDrag(float x, float y) {
        return map.beginRecordDrag(x, y);
    }

    /** 기록을 저장하지 않는 드래그 미리보기를 지도에 전달한다. */
    @Override
    public void previewRecordDrag(float x, float y) {
        map.previewRecordDrag(x, y);
    }

    /** 취소 여부에 따라 미리보기를 정리하고 유효한 이동 결과만 반환한다. */
    @Override
    public Optional<MapRecordMove> endRecordDrag(float x, float y, boolean cancelled) {
        return map.endRecordDrag(x, y, cancelled);
    }

    /** 마커 한 번 탭으로 읽을 기록을 현재 투영에서 찾는다. */
    @Override
    public Optional<MapRecordMarker> findRecordAt(float x, float y) {
        return map.findRecordAt(x, y);
    }

    /** 지도 선택 표시와 화면의 안내 대상 상태를 같은 탭에서 해제한다. */
    @Override
    public void dismissSelectionAt(float x, float y) {
        if (!ready || closed || !resumed) {
            return;
        }
        map.dismissSelectionAt(x, y);
        listener.onMapSelectionCleared();
    }

    /** 지도와 위치 수신을 화면 생명주기에 맞춰 재개한다. */
    @Override
    public void resume() {
        resumed = true;
        map.resume();
        if (testMovement && testPosition != null) {
            map.updateCurrentPosition(testPosition.getLongitude(), testPosition.getLatitude());
        }
        if (ready && !closed) {
            positions.start(this);
        }
    }

    /** 백그라운드에서는 위치를 요청하지 않는다. */
    @Override
    public void pause() {
        resumed = false;
        positions.stop();
        map.hideCurrentPosition();
        lastPosition = null;
        map.pause();
    }

    /** 닫힌 화면에 서비스 콜백이 남지 않도록 정리한다. */
    @Override
    public void close() {
        closed = true;
        positions.close();
        map.close();
    }
}
