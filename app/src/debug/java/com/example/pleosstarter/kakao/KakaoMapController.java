package com.example.pleosstarter.kakao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.common.util.DoublePoint;
import com.kakaomobility.knsdk.common.util.FloatPoint;
import com.kakaomobility.knsdk.common.util.IntPoint;
import com.kakaomobility.knsdk.guidance.knguidance.common.KNLocation;
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCameraUpdate;
import com.kakaomobility.knsdk.map.knmapview.KNMapView;
import com.kakaomobility.knsdk.map.knmapview.idl.KNMapPOIEventListener;
import com.kakaomobility.knsdk.map.knmapview.idl.KNMapPOIProperties;
import com.kakaomobility.knsdk.map.knmapview.idl.KNMapViewEventListener;
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapUserLocation;
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.KNMapMarker;
import com.kakaomobility.knsdk.map.uicustomsupport.renewal.theme.base.KNMapTheme;
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import kotlin.Unit;

/** SDK 지도 렌더링, 카메라, 건물·POI 표시와 POI 입력만 담당한다. */
public final class KakaoMapController implements KNMapPOIEventListener, KNMapViewEventListener {
    private static final String TAG = "KakaoNavigationTest";
    private static final float NEIGHBORHOOD_ZOOM = 1.0f;
    private static final long CAMERA_SETTLE_DELAY_MILLIS = 600L;
    private final Context context;
    private final FrameLayout container;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Consumer<KakaoPlaceSelection> placeListener;
    private KNMapView mapView;
    private boolean ready;
    private boolean closed;
    private boolean resumed;
    private boolean followLocation;
    private MapCameraSnapshot gestureCamera;
    private long cameraRestoreGeneration;
    private boolean testPosition;
    private KNMapMarker positionMarker;
    private MapMemoMarkerLayer recordLayer;
    private MapSelectedPlaceLayer selectedPlaceLayer;
    private TwoFingerMapCamera twoFingerCamera;
    private Consumer<Boolean> perspectiveListener;
    private float cameraTilt = MapPerspective.TWO_DIMENSIONAL.getTiltDegrees();

    /** 지도 컨테이너와 장소 선택 결과 수신자를 주입한다. */
    public KakaoMapController(Context context, Consumer<KakaoPlaceSelection> placeListener) {
        this.context = context;
        this.placeListener = placeListener;
        this.container = new FrameLayout(context);
    }

    /** SDK 인증 전에도 배치할 수 있는 빈 지도 컨테이너를 반환한다. */
    public View getView() {
        return container;
    }

    /** 인증 후 실제 SDK 지도를 생성하고 도로·건물·장소 표시를 요청한다. */
    public void bind(Runnable onReady, Consumer<KakaoTestFailure> onFailure) {
        mapView = new KNMapView(context);
        container.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        KNSDK.INSTANCE.bindingMapView(mapView, KNMapTheme.Companion.driveDay(), error -> {
            mainHandler.post(() -> {
                if (closed) {
                    return;
                }
                if (error != null) {
                    KakaoSdkErrorReporter.report("MAP_BIND_FAILED", error);
                    onFailure.accept(new KakaoTestFailure(KakaoTestError.MAP_BIND_FAILED,
                            "카카오 지도를 불러오지 못했습니다. 연결 상태를 확인하세요."));
                    return;
                }

                mapView.setVisibleBuilding(true);
                // 두 손가락 이동·확대·회전은 별도 입력 계층에서 처리하고 기울기는 유지한다.
                mapView.setUsePanningGesture(false);
                mapView.setUseZoomGesture(false);
                mapView.setUseBearingGesture(false);
                mapView.setUseTiltGesture(false);
                // 새 속성을 완성한 뒤 setter로 전달해 렌더러의 POI picking 연결도 갱신한다.
                KNMapPOIProperties poiProperties = new KNMapPOIProperties();
                poiProperties.setVisible(true);
                poiProperties.setUseSingleTapped(true);
                poiProperties.setUseDoubleTapped(true);
                poiProperties.setEventListener(this);
                mapView.setPoiProperties(poiProperties);
                // SDK의 POI 선택기도 일반 지도 이벤트 리스너가 연결돼야 활성화된다.
                mapView.setMapViewEventListener(this);
                recordLayer = new MapMemoMarkerLayer(context, mapView);
                recordLayer.setActive(resumed);
                selectedPlaceLayer = new MapSelectedPlaceLayer(mapView);
                twoFingerCamera = new TwoFingerMapCamera(mapView, () -> followLocation = false);
                observeTouchForDiagnosis();
                ready = true;
                if (resumed) {
                    mapView.onResume();
                }
                Log.i(TAG, "MAP_BOUND buildings=true poi=true doubleTap=true");
                onReady.run();
            });
            return Unit.INSTANCE;
        });
    }

    /** 지정된 시험 위치를 표시하며 실제 현재 위치로 오인할 표시를 추가하지 않는다. */
    public void showLocation(double longitude, double latitude) {
        if (!ready || closed) {
            return;
        }
        followLocation = false;
        DoublePoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(longitude, latitude);
        KNMapCameraUpdate camera = KNMapCameraUpdate.Creator.targetTo(coordinate.toFloatPoint())
                .anchorTo(new FloatPoint(0.5f, 0.5f))
                .zoomTo(NEIGHBORHOOD_ZOOM).tiltTo(cameraTilt).bearingTo(0.0f);
        mapView.moveCamera(camera, false, false);
    }

    /** 선택 장소의 핀을 실제 좌표에 고정하고 기존 장소 탐색 방식으로 이동한다. */
    public void showSelectedPlace(KakaoPlaceSelection selection) {
        if (!ready || closed) {
            return;
        }

        selectedPlaceLayer.show(selection);
        showLocation(selection.getLongitude(), selection.getLatitude());
    }

    /** 카메라 추적과 다른 마커를 바꾸지 않고 선택 장소 표시만 해제한다. */
    public void clearSelectedPlace() {
        if (selectedPlaceLayer != null) {
            selectedPlaceLayer.clear();
        }
    }

    /** 위치·배율·안내 추적을 바꾸지 않고 선택한 시점만 지도에 적용한다. */
    public void setPerspective(MapPerspective perspective) {
        if (!ready || closed) {
            return;
        }

        cameraTilt = perspective.getTiltDegrees();
        mapView.moveCamera(KNMapCameraUpdate.Creator.tiltTo(cameraTilt), false, false);
        notifyPerspective();

        // 렌더러에 실제 반영된 기울기를 요청 값과 구분해 기록한다.
        float requestedTilt = cameraTilt;
        mainHandler.postDelayed(() -> {
            if (!closed && ready && Float.compare(cameraTilt, requestedTilt) == 0) {
                Log.i(TAG, "MAP_PERSPECTIVE mode=" + perspective.name()
                        + " requestedTilt=" + requestedTilt + " actualTilt=" + mapView.getTilt()
                        + " followLocation=" + followLocation);
            }
        }, CAMERA_SETTLE_DELAY_MILLIS);
    }

    /** 지도 시점을 화면 버튼에 동기화할 수신자를 연결한다. */
    public void setPerspectiveListener(Consumer<Boolean> listener) {
        perspectiveListener = listener;
    }

    /** 수동 기울기와 버튼 입력의 공통 상태를 UI 스레드에서 알린다. */
    private void notifyPerspective() {
        boolean threeDimensional = cameraTilt > 1.0f;
        mainHandler.post(() -> {
            if (!closed && perspectiveListener != null) {
                perspectiveListener.accept(threeDimensional);
            }
        });
    }

    /** 검색 중심은 실제 현재 위치가 아닌 사용자가 보고 있는 지도 중앙이다. */
    public KakaoPlaceSelection getSearchCenter(double fallbackLongitude, double fallbackLatitude) {
        if (!ready || closed || mapView.getWidth() == 0 || mapView.getHeight() == 0) {
            return new KakaoPlaceSelection(0, "지도 중심", fallbackLatitude, fallbackLongitude);
        }

        FloatPoint coordinate = mapView.screenToKatec(new FloatPoint(
                mapView.getWidth() / 2.0f, mapView.getHeight() / 2.0f));
        DoublePoint wgs84 = KNSDK.INSTANCE.convertKATECToWGS84(
                Math.round(coordinate.getX()), Math.round(coordinate.getY()));
        return new KakaoPlaceSelection(0, "지도 중심", wgs84.getY(), wgs84.getX());
    }

    /** 안내 엔진이 돌려준 실제 경로 객체를 지도에 표시한다. */
    public void showRoutes(List<KNRoute> routes) {
        if (ready && !closed) {
            mapView.setRoutes(routes);
        }
    }

    /** 안내 시작 이후 수신 위치를 따라갈지 설정한다. */
    public void setFollowLocation(boolean followLocation) {
        this.followLocation = followLocation;
    }

    /** 현재 위치 갱신에 따라 카메라를 이동해도 되는지 현재 추적 의도를 반환한다. */
    public boolean isFollowingLocation() {
        return followLocation;
    }

    /** 위치 갱신은 유지하고 카메라 추적만 멈춘 뒤 터치 시작 시점의 투영을 복사한다. */
    public void beginMapInteraction() {
        cameraRestoreGeneration++;
        if (ready && !closed) {
            twoFingerCamera.reset();
            gestureCamera = new MapCameraSnapshot(mapView, followLocation);
        }
        followLocation = false;
    }

    /** 그리기 시작 시점의 투영을 확인하고 다른 비동기 변경이 있을 때만 복원한다. */
    public void restoreGestureCamera(Runnable restored, Consumer<String> failed) {
        if (!ready || closed || gestureCamera == null) {
            failed.accept("그린 위치를 확인하지 못했어요. 다시 그려 주세요.");
            return;
        }
        long request = ++cameraRestoreGeneration;
        if (gestureCamera.matches(mapView)) {
            restored.run();
            return;
        }
        mapView.moveCamera(gestureCamera.cameraUpdate(), false, false);
        awaitGestureCamera(request, 0, restored, failed);
    }

    /** 비동기 지도 투영이 일치할 때만 저장 좌표 계산을 허용한다. */
    private void awaitGestureCamera(long request, int attempt, Runnable restored, Consumer<String> failed) {
        mainHandler.postDelayed(() -> {
            if (closed || request != cameraRestoreGeneration || gestureCamera == null) {
                return;
            }
            if (gestureCamera.matches(mapView)) {
                Log.i("PlaceMap", "GESTURE_CAMERA_RESTORED attempts=" + attempt);
                restored.run();
            } else if (attempt < 30) {
                awaitGestureCamera(request, attempt + 1, restored, failed);
            } else {
                Log.w("PlaceMap", "GESTURE_CAMERA_RESTORE_FAILED");
                failed.accept("지도가 아직 움직이고 있어요. 멈춘 뒤 다시 그려 주세요.");
            }
        }, 16L);
    }

    /** 일반 탐색은 유지하고 도형·단순 탭에만 이전 자동 추적을 복원한다. */
    public void finishMapInteraction(boolean resumeFollowing) {
        cameraRestoreGeneration++;
        if (gestureCamera != null && resumeFollowing) {
            followLocation = gestureCamera.wasFollowing();
        }
        gestureCamera = null;
    }

    /** 시험 이동은 네이비 점으로 실제 차량의 파란 점과 구분한다. */
    public void setTestPosition(boolean enabled) {
        testPosition = enabled;
        if (positionMarker != null) {
            positionMarker.setIcon(enabled ? MapMarkerIcons.testPosition() : MapMarkerIcons.currentPosition());
        }
    }

    /** 지도 자체의 투영으로 화면 좌표를 WGS84로 변환하고 왕복 오차를 기록한다. */
    public KakaoPlaceSelection locationAtScreen(float x, float y) {
        if (!ready || closed || !Float.isFinite(x) || !Float.isFinite(y)
                || x < 0 || y < 0 || x >= mapView.getWidth() || y >= mapView.getHeight()) {
            throw new IllegalArgumentException("지도 안의 위치를 선택해 주세요.");
        }
        FloatPoint katec = mapView.screenToKatec(new FloatPoint(x, y));
        DoublePoint location = KNSDK.INSTANCE.convertKATECToWGS84(
                Math.round(katec.getX()), Math.round(katec.getY()));
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY())
                || Math.abs(location.getX()) > 180 || Math.abs(location.getY()) > 90) {
            throw new IllegalArgumentException("이 지점을 지도 좌표로 바꾸지 못했습니다.");
        }
        FloatPoint roundTrip = mapView.katecToScreen(katec);
        Log.i("PlaceMap", "SCREEN_POSITION x=" + x + " y=" + y
                + " tilt=" + mapView.getTilt() + " roundTripError="
                + Math.hypot(roundTrip.getX() - x, roundTrip.getY() - y));
        return new KakaoPlaceSelection(0, "지도에 기록한 위치", location.getY(), location.getX());
    }

    /** Pleos에서 수신한 위치를 방향 없는 파란 점으로 갱신한다. */
    public void updateCurrentPosition(double longitude, double latitude) {
        if (!ready || closed) {
            return;
        }
        FloatPoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(longitude, latitude).toFloatPoint();
        if (positionMarker == null) {
            positionMarker = new KNMapMarker(coordinate);
            positionMarker.setIcon(testPosition ? MapMarkerIcons.testPosition() : MapMarkerIcons.currentPosition());
            positionMarker.setPixelOffset(new IntPoint(0, 28));
            positionMarker.setPriority(100);
            mapView.addMarker(positionMarker);
        } else {
            positionMarker.setCoordinate(coordinate);
        }
        positionMarker.setVisible(true);
        if (followLocation) {
            // 초기 위치 수신이 렌더링보다 빨라도 요청한 기울기를 이전 프레임 값으로 덮지 않는다.
            mapView.moveCamera(KNMapCameraUpdate.Creator.targetTo(coordinate)
                    .anchorTo(new FloatPoint(0.5f, 0.5f)).tiltTo(cameraTilt), false, false);
        }
    }

    /** 위치 수신 중단 시 과거 위치 점을 숨긴다. */
    public void hideCurrentPosition() {
        if (positionMarker != null) {
            positionMarker.setVisible(false);
        }
    }

    /** 현재 위치 점을 보존하며 저장된 기록 마커만 교체한다. */
    public void showRecordMarkers(List<MapRecordMarker> records) {
        if (!ready || closed) {
            return;
        }
        recordLayer.replace(records);
    }

    /** 준비된 지도에 표시된 저장 마커만 길게 잡을 수 있게 한다. */
    public boolean beginRecordDrag(float x, float y) {
        return ready && resumed && !closed && recordLayer != null && recordLayer.beginDrag(x, y);
    }

    /** 고정된 카메라에서 마커만 미리 이동하며 차량 추적과 기록 저장은 수행하지 않는다. */
    public void previewRecordDrag(float x, float y) {
        if (ready && !closed && recordLayer != null) {
            recordLayer.previewDrag(x, y);
        }
    }

    /** 화면 이탈 중에도 잡은 마커를 정리하고 저장할 수 있는 결과만 반환한다. */
    public Optional<MapRecordMove> endRecordDrag(float x, float y, boolean cancelled) {
        if (recordLayer == null) {
            return Optional.empty();
        }
        return recordLayer.endDrag(x, y, cancelled || !ready || !resumed || closed);
    }

    /** 화면 좌표에 해당하는 저장 기록만 반환하고 표시와 저장은 조정자에 맡긴다. */
    public Optional<MapRecordMarker> findRecordAt(float x, float y) {
        if (!ready || closed || !resumed || recordLayer == null) {
            return Optional.empty();
        }
        return recordLayer.findRecordAt(x, y);
    }

    /** 짧은 지도 탭에서 이전 안내 대상 핀만 해제한다. */
    public void dismissSelectionAt(float x, float y) {
        if (!ready || closed || !resumed) {
            return;
        }
        clearSelectedPlace();
    }

    /** 실제 안내 또는 모의안내 콜백의 위치로 차량 표시와 진행 경로를 갱신한다. */
    public void updateLocation(KNLocation location, float angle) {
        if (!ready || closed || location == null) {
            return;
        }

        KNMapUserLocation userLocation = mapView.getUserLocation();
        if (userLocation != null) {
            userLocation.setVisible(true);
            userLocation.animate(location.getPos(), angle, 400L, false);
        }
        mapView.cullPassedRoute(location, true);
        if (followLocation) {
            KNMapCameraUpdate camera = KNMapCameraUpdate.Creator
                    .targetTo(location.getPos().toFloatPoint()).zoomTo(NEIGHBORHOOD_ZOOM)
                    .tiltTo(cameraTilt);
            mapView.animateCamera(camera, 400L, false, false);
        }
    }

    /** 안내 종료 시 경로와 차량 표시를 제거한다. */
    public void clearGuidance() {
        if (!ready || closed) {
            return;
        }
        followLocation = false;
        mapView.removeRoutesAll();
        KNMapUserLocation userLocation = mapView.getUserLocation();
        if (userLocation != null) {
            userLocation.setVisible(false);
        }
    }

    /** 화면 표시 상태에 따라 GLSurfaceView 렌더링을 재개한다. */
    public void resume() {
        resumed = true;
        if (ready && !closed) {
            mapView.onResume();
            recordLayer.setActive(true);
        }
    }

    /** 화면이 가려지면 GLSurfaceView 렌더링을 멈춘다. */
    public void pause() {
        resumed = false;
        if (ready && !closed) {
            twoFingerCamera.reset();
            recordLayer.setActive(false);
            mapView.onPause();
        }
    }

    /** Activity에 연결된 지도 자원과 장소 콜백을 해제한다. */
    public void close() {
        closed = true;
        mainHandler.removeCallbacksAndMessages(null);
        if (mapView != null) {
            if (twoFingerCamera != null) {
                twoFingerCamera.reset();
            }
            if (recordLayer != null) {
                recordLayer.close();
                recordLayer = null;
            }
            if (selectedPlaceLayer != null) {
                selectedPlaceLayer.close();
                selectedPlaceLayer = null;
            }
            KNMapPOIProperties properties = mapView.getPoiProperties();
            if (properties != null) {
                properties.setEventListener(null);
            }
            mapView.setMapViewEventListener(null);
            mapView.setOnTouchListener(null);
            mapView.onHostActivityDestroyed();
            container.removeAllViews();
        }
    }

    /** 단일 탭은 이번 시험의 장소 정보 요청으로 처리하지 않는다. */
    @Override
    public void onSingleTapped(KNMapView view, long poiId, String[] names, FloatPoint coordinate) {
        Log.i(TAG, "POI_SINGLE_TAP id=" + poiId);
    }

    /** 추적 상태를 유지하며 실제 POI 더블탭 위치를 정보 패널에 전달한다. */
    @Override
    public void onDoubleTapped(KNMapView view, long poiId, String[] names, FloatPoint coordinate) {
        if (closed || coordinate == null) {
            return;
        }
        DoublePoint wgs84 = KNSDK.INSTANCE.convertKATECToWGS84(
                Math.round(coordinate.getX()), Math.round(coordinate.getY()));
        String name = firstName(names);
        KakaoPlaceSelection selection = new KakaoPlaceSelection(
                poiId, name, wgs84.getY(), wgs84.getX());
        mainHandler.post(() -> {
            if (!closed) {
                placeListener.accept(selection);
            }
        });
    }

    /** 길게 누르기는 이번 시험의 장소 정보 요청으로 처리하지 않는다. */
    @Override
    public void onLongPressed(KNMapView view, long poiId, String[] names, FloatPoint coordinate) {
    }

    /** 입력을 소비하지 않고 진단용으로 실제 터치 시각과 화면 좌표만 관찰한다. */
    @SuppressLint("ClickableViewAccessibility")
    private void observeTouchForDiagnosis() {
        mapView.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                Log.i(TAG, "POI_DIAG_TOUCH action=" + action + " time=" + event.getEventTime()
                        + " x=" + event.getX() + " y=" + event.getY());
            }
            return twoFingerCamera != null && twoFingerCamera.onTouch(event);
        });
    }

    /** 장소에 맞지 않은 일반 지도 단일 탭은 POI 정보 수신과 구분한다. */
    @Override
    public void onSingleTapped(KNMapView view, IntPoint screen, FloatPoint coordinate) {
        Log.i(TAG, "MAP_SINGLE_TAP");
    }

    /** 추적 상태를 유지하며 빈 지도 더블탭의 선택 좌표를 주변 매장 조회에 전달한다. */
    @Override
    public void onDoubleTapped(KNMapView view, IntPoint screen, FloatPoint coordinate) {
        Log.i(TAG, "MAP_DOUBLE_TAP");
        if (closed || coordinate == null) {
            return;
        }

        DoublePoint wgs84 = KNSDK.INSTANCE.convertKATECToWGS84(
                Math.round(coordinate.getX()), Math.round(coordinate.getY()));
        KakaoPlaceSelection selection = new KakaoPlaceSelection(
                0, "선택한 위치", wgs84.getY(), wgs84.getX());
        mainHandler.post(() -> {
            if (!closed) {
                placeListener.accept(selection);
            }
        });
    }

    /** 일반 지도 길게 누르기는 이번 POI 시험의 동작이 아니다. */
    @Override
    public void onLongPressed(KNMapView view, IntPoint screen, FloatPoint coordinate) {
    }

    /** 사용자가 지도를 끌면 안내를 유지하면서 카메라 추적만 해제한다. */
    @Override
    public void onPanningStarted(KNMapView view, IntPoint screen, FloatPoint coordinate) {
        followLocation = false;
    }

    /** 이동 중 지도 갱신은 SDK에 맡긴다. */
    @Override
    public void onPanningChanging(KNMapView view, IntPoint screen, FloatPoint coordinate) {
    }

    /** 이동 종료 후에도 사용자가 선택한 지도 범위를 유지한다. */
    @Override
    public void onPanningEnded(KNMapView view, IntPoint screen, FloatPoint coordinate) {
    }

    /** 사용자가 배율을 바꾸면 자동 카메라 추적만 해제한다. */
    @Override
    public void onZoomingStarted(KNMapView view, IntPoint screen, float zoom) {
        followLocation = false;
    }

    /** 배율 갱신은 SDK에 맡긴다. */
    @Override
    public void onZoomingChanging(KNMapView view, IntPoint screen, float zoom) {
    }

    /** 배율 변경 종료 시 별도의 장소 선택을 발생시키지 않는다. */
    @Override
    public void onZoomingEnded(KNMapView view, IntPoint screen, float zoom) {
    }

    /** 사용자가 지도 방향을 바꾸면 자동 추적만 해제한다. */
    @Override
    public void onBearingStarted(KNMapView view, IntPoint screen, float bearing) {
        followLocation = false;
    }

    /** 지도 회전 갱신은 SDK에 맡긴다. */
    @Override
    public void onBearingChanging(KNMapView view, IntPoint screen, float bearing) {
    }

    /** 회전 종료 시 실제 방위각만 기록하고 POI 선택은 발생시키지 않는다. */
    @Override
    public void onBearingEnded(KNMapView view, IntPoint screen, float bearing) {
        Log.i(TAG, "MAP_BEARING actualBearing=" + view.getBearing());
    }

    /** 사용자가 지도 기울기를 바꾸면 자동 추적만 해제한다. */
    @Override
    public void onTiltStarted(KNMapView view, IntPoint screen, float tilt) {
        followLocation = false;
    }

    /** 지도 기울기 갱신은 SDK에 맡긴다. */
    @Override
    public void onTiltChanging(KNMapView view, IntPoint screen, float tilt) {
    }

    /** 손으로 조절한 기울기도 이후 장소 이동과 안내 카메라에서 유지한다. */
    @Override
    public void onTiltEnded(KNMapView view, IntPoint screen, float tilt) {
        cameraTilt = tilt;
        notifyPerspective();
    }

    /** SDK 카메라 애니메이션 중 별도의 입력을 합성하지 않는다. */
    @Override
    public void onCameraAnimationChanging(KNMapView view, KNMapCameraUpdate camera) {
    }

    /** SDK 카메라 애니메이션 종료 후 상태를 유지한다. */
    @Override
    public void onCameraAnimationEnded(KNMapView view, KNMapCameraUpdate camera) {
    }

    /** SDK 카메라 애니메이션 취소를 장소 선택으로 처리하지 않는다. */
    @Override
    public void onCameraAnimationCanceled(KNMapView view, KNMapCameraUpdate camera) {
    }

    /** 외부 배열에서 표시 가능한 첫 장소 이름만 선택한다. */
    private String firstName(String[] names) {
        if (names != null) {
            for (String name : names) {
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        }
        return "이름 없는 장소";
    }
}
