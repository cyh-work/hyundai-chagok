package com.example.pleosstarter.kakao;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;
import com.example.pleosstarter.BuildConfig;
import com.example.pleosstarter.R;
import com.example.pleosstarter.gesture.MapGestureInputLayout;
import com.example.pleosstarter.map.GeometricGestureClassifier;
import com.example.pleosstarter.location.CurrentPosition;
import com.example.pleosstarter.location.LocationFailure;
import com.example.pleosstarter.location.PleosCurrentLocationSource;
import com.example.pleosstarter.notes.LocationNotes;
import com.example.pleosstarter.records.LocalMapRecordsService;
import com.example.pleosstarter.records.LocalRecordRevisitService;
import com.example.pleosstarter.records.MapHistoryService;
import com.example.pleosstarter.records.SharedPreferencesHistoryMetadataRepository;
import com.example.pleosstarter.records.SharedPreferencesFavoriteRepository;
import com.example.pleosstarter.records.PleosMemoSpeechInput;
import com.example.pleosstarter.speech.PleosSpeechClient;
import com.example.pleosstarter.ui.MapScreenView;
import com.example.pleosstarter.simulation.AndroidPlaybackScheduler;
import com.example.pleosstarter.simulation.KakaoPreviewRouteSource;
import com.example.pleosstarter.stores.HttpStoreSearchService;
import com.example.pleosstarter.stores.StoreSearchService;
import com.example.pleosstarter.stores.StoreResultsDialog;
import com.example.pleosstarter.stores.StoreSummary;
import com.example.pleosstarter.navigation.DestinationNavigationClient;
import com.example.pleosstarter.navigation.NavigationDestination;
import com.example.pleosstarter.navigation.NavigationEvent;
import com.example.pleosstarter.navigation.PleosDestinationNavigationClient;


/** Pleos 위치 지도에 원·별 기록과 히스토리 화면을 연결한다. */
public final class KakaoTestActivity extends FragmentActivity implements PlaceMapListener {
    private static final int LOCATION_PERMISSION_REQUEST = 307;
    private static final String TAG = "KakaoNavigationTest";
    private PlaceMapSession session;
    private MapGestureInputLayout gestureInput;
    private MapRecordCoordinator recordCoordinator;
    private MapInteractionCoordinator interactions;
    private MapRevisitCoordinator revisits;
    private MapScreenView screen;
    private boolean cameraInitialized;
    private boolean recordsLoaded;
    private FrameLayout mapContainer;
    private StoreResultsDialog activeStoreDialog;
    private boolean mapReady;
    private boolean destroyed;
    private boolean resumed;
    private DestinationNavigationClient destinationNavigation;
    private NavigationDestination selectedNavigationDestination;
    private NavigationDestination requestedNavigationDestination;
    private boolean navigationPending;

    /** 키 설정과 위치 권한을 확인한 뒤에만 SDK 연결을 시작한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildScreen();

        KakaoTestApplication application = (KakaoTestApplication) getApplication();
        if (!application.hasKakaoAppKey()) {
            onFailure(new KakaoTestFailure(KakaoTestError.MISSING_APP_KEY,
                    "카카오 앱 키 등록이 필요합니다. 등록한 키로 앱을 다시 빌드해 주세요."));
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, LOCATION_PERMISSION_REQUEST);
            return;
        }
        connectSession();
    }

    /** 지도 기록·시험 이동과 명시적으로 누르는 기본 내비 연결을 구성한다. */
    private void buildScreen() {
        screen = new MapScreenView(this);
        mapContainer = screen.getMapContainer();
        screen.getMovementButton().setOnClickListener(view -> interactions.toggleMovement());
        screen.getStopMovementButton().setOnClickListener(view -> interactions.stopMovement());
        screen.getNavigationButton().setOnClickListener(view -> startSelectedNavigation());
        screen.getHistoryButton().setOnClickListener(view -> openHistory());
        setContentView(screen);
        screen.requestFocus();
    }

    /** 이전 장소 선택을 정리하고 현재 저장된 메모·즐겨찾기 목록을 연다. */
    private void openHistory() {
        if (destroyed || !resumed || !mapReady || recordCoordinator == null) {
            return;
        }
        if (activeStoreDialog != null) {
            activeStoreDialog.dismiss();
        }
        session.clearSelectedPlace();
        onMapSelectionCleared();
        recordCoordinator.showSavedPlaces();
    }

    /** 빌드 종류에 따른 인증 경로를 조립하며 제출 앱에서는 중계만 사용한다. */
    private StoreSearchService createStoreSearchService() {
        if (BuildConfig.DEBUG) {
            return new HttpStoreSearchService(this);
        }
        return new HttpStoreSearchService(this, BuildConfig.PLACES_PROXY_URL);
    }

    /** 선택한 지도 지점 가까이의 실제 매장을 앱 내부 목록으로 조회한다. */
    private void openNearbyStores(KakaoPlaceSelection selection) {
        if (destroyed || isFinishing()) {
            return;
        }
        if (activeStoreDialog != null) {
            activeStoreDialog.dismiss();
        }

        StoreResultsDialog dialog = new StoreResultsDialog(this,
                createStoreSearchService(), selection.getName(),
                selection.getLatitude(), selection.getLongitude(), this::showStoreOnMap);
        activeStoreDialog = dialog;
        dialog.setOnDismissListener(closedDialog -> {
            if (activeStoreDialog == dialog) {
                activeStoreDialog = null;
                updatePlaceDialogVisibility();
            }
        });
        dialog.show();
        updatePlaceDialogVisibility();
    }

    /** REST 식별자 대신 확인된 좌표와 이름을 선택 핀·안내 대상으로 전달한다. */
    private void showStoreOnMap(StoreSummary store) {
        Double latitude = store.getLatitude();
        Double longitude = store.getLongitude();
        if (destroyed || session == null || !mapReady || latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return;
        }

        session.showSelectedPlace(new KakaoPlaceSelection(0, store.getName(), latitude, longitude));
        String address = store.getRoadAddress();
        if (address.isBlank()) {
            address = store.getAddress();
        }
        selectedNavigationDestination = new NavigationDestination(
                store.getName(), address, latitude, longitude);
        screen.showNavigationAction(store.getName());
        Log.i(TAG, "STORE_MAP_FOCUSED");
    }

    /** 버튼을 누른 시점의 선택 장소를 고정해 기본 내비에 한 번만 전달한다. */
    private void startSelectedNavigation() {
        if (destroyed || !resumed || !mapReady || navigationPending
                || selectedNavigationDestination == null) {
            return;
        }

        navigationPending = true;
        requestedNavigationDestination = selectedNavigationDestination;
        screen.setNavigationPending(true);
        try {
            if (destinationNavigation == null) {
                destinationNavigation = new PleosDestinationNavigationClient(
                        this, this::onDestinationNavigationEvent);
            }
            destinationNavigation.start(requestedNavigationDestination);
        } catch (RuntimeException failure) {
            Log.w(TAG, "PLACE_NAVIGATION_START_FAILED type=" + failure.getClass().getSimpleName());
            onDestinationNavigationEvent(new NavigationEvent(NavigationEvent.Status.REQUEST_FAILED));
        }
    }

    /** 기록 모달의 길안내 요청을 기존 목적지·핀·재시도 상태와 같은 내비 흐름으로 접수한다. */
    private boolean startRecordNavigation(NavigationDestination destination) {
        if (destroyed || !resumed || !mapReady || session == null || navigationPending
                || destination == null || !destination.isValid()) {
            return false;
        }

        selectedNavigationDestination = destination;
        session.showSelectedPlace(new KakaoPlaceSelection(0, destination.name(),
                destination.latitude(), destination.longitude()));
        screen.showNavigationAction(destination.name());
        startSelectedNavigation();
        return true;
    }

    /** 실제 내비 콜백의 완료·실패만 반영하고 다른 새 장소 선택을 지우지 않는다. */
    private void onDestinationNavigationEvent(NavigationEvent event) {
        if (destroyed) {
            return;
        }
        Log.i(TAG, "PLACE_NAVIGATION_EVENT status=" + event.status());
        switch (event.status()) {
            case CONFIRMED:
                if (requestedNavigationDestination != null
                        && requestedNavigationDestination.equals(selectedNavigationDestination)) {
                    selectedNavigationDestination = null;
                    session.clearSelectedPlace();
                    screen.hideNavigationAction();
                }
                finishNavigationRequest();
                break;
            case CONNECTION_FAILED:
                finishNavigationRequest();
                screen.showFeedback(getString(R.string.place_navigation_connection_failed));
                break;
            case REQUEST_FAILED:
                finishNavigationRequest();
                screen.showFeedback(getString(R.string.place_navigation_request_failed));
                break;
            case TIMED_OUT:
                finishNavigationRequest();
                screen.showFeedback(getString(R.string.place_navigation_timeout));
                break;
            default:
                // 연결·경로 계산 중에는 실제 목적지 확인 또는 실패를 기다린다.
                break;
        }
    }

    /** 처리 종료 후 새 목적지나 실패한 요청을 다시 선택할 수 있도록 조작을 해제한다. */
    private void finishNavigationRequest() {
        navigationPending = false;
        requestedNavigationDestination = null;
        screen.setNavigationPending(false);
    }

    /** 장소 목록의 열림 상태를 카메라 복귀 예약에 전달하며 위치 재생은 유지한다. */
    private void updatePlaceDialogVisibility() {
        if (!destroyed && interactions != null) {
            interactions.setPlaceDialogOpen(activeStoreDialog != null);
        }
    }

    /** SDK 클래스 로드 실패와 인증 실패를 별도 상태로 유지하며 세션을 연결한다. */
    private void connectSession() {
        if (session != null || isFinishing()) {
            return;
        }
        try {
            KakaoTestApplication application = (KakaoTestApplication) getApplication();
            boolean emulated = Build.FINGERPRINT.contains("generic")
                    || Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish");
            session = new KakaoPlaceMapClient(this, application.getSdkRuntime(),
                    new PleosCurrentLocationSource(this, emulated), this);
            LocalMapRecordsService records = new LocalMapRecordsService(new SharedPreferencesFavoriteRepository(
                            getSharedPreferences("map_favorites", MODE_PRIVATE)),
                            new LocationNotes(getSharedPreferences("location_notes", MODE_PRIVATE)));
            MapHistoryService history = new MapHistoryService(records,
                    new SharedPreferencesHistoryMetadataRepository(
                            getSharedPreferences("history_metadata", MODE_PRIVATE)), System::currentTimeMillis);
            recordCoordinator = new MapRecordCoordinator(this, session, records, history,
                    new PleosMemoSpeechInput(new PleosSpeechClient(this)), screen::showFeedback,
                    this::createStoreSearchService, this::showStoreOnMap, this::startRecordNavigation);
            Handler handler = new Handler(Looper.getMainLooper());
            interactions = new MapInteractionCoordinator(session, recordCoordinator, screen,
                    new KakaoPreviewRouteSource(handler), new AndroidPlaybackScheduler(handler),
                    SystemClock::elapsedRealtime);
            revisits = new MapRevisitCoordinator(new LocalRecordRevisitService(),
                    new LocalRecordRevisitService(), screen.getRevisitCard(),
                    new AndroidPlaybackScheduler(handler), recordCoordinator::showRevisitRecord);
            recordCoordinator.setRecordsChangedListener(revisits::replaceRecords);
            interactions.setRevisitCoordinator(revisits);
            interactions.setForeground(resumed);
            recordCoordinator.setDialogVisibilityListener(interactions::setRecordDialogOpen);
            gestureInput = new MapGestureInputLayout(this, new GeometricGestureClassifier(), interactions);
            gestureInput.addView(session.getMapView(), new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            mapContainer.addView(gestureInput, 0, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            session.initialize();
            if (resumed) {
                session.resume();
            }
        } catch (LinkageError error) {
            Log.e(TAG, "SESSION_LINKAGE_FAILURE type=" + error.getClass().getSimpleName());
            onFailure(new KakaoTestFailure(KakaoTestError.SDK_LOAD_FAILED,
                    "이 기기에서 카카오 SDK 라이브러리를 불러오지 못했습니다."));
        } catch (RuntimeException error) {
            Log.e(TAG, "SESSION_CREATE_FAILURE type=" + error.getClass().getSimpleName());
            onFailure(new KakaoTestFailure(KakaoTestError.UNEXPECTED_FAILURE,
                    "카카오 지도 화면을 준비하지 못했습니다."));
        }
    }

    /** 위치 권한 거부를 숨기지 않고 안내하며 허용된 경우에만 SDK를 연결한다. */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            return;
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            connectSession();
        } else {
            onFailure(new KakaoTestFailure(KakaoTestError.LOCATION_PERMISSION_DENIED,
                    "지도를 사용하려면 위치 권한이 필요합니다. 권한을 허용한 뒤 다시 열어 주세요."));
        }
    }

    /** 화면 재진입 시 지도 렌더링을 재개한다. */
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (session != null) {
            session.resume();
        }
        if (interactions != null) {
            interactions.setForeground(true);
        }
    }

    /** 화면이 가려지면 지도 렌더링을 정지한다. */
    @Override
    protected void onPause() {
        resumed = false;
        if (interactions != null) {
            interactions.setForeground(false);
        }
        if (recordCoordinator != null) {
            recordCoordinator.pause();
        }
        if (session != null) {
            session.pause();
        }
        super.onPause();
    }

    /** 종료된 화면에 장소 상세·위치·음성 콜백이 남지 않도록 정리한다. */
    @Override
    protected void onDestroy() {
        destroyed = true;
        if (destinationNavigation != null) {
            destinationNavigation.close();
        }
        if (interactions != null) {
            interactions.close();
        }
        if (activeStoreDialog != null) {
            activeStoreDialog.dismiss();
            activeStoreDialog = null;
        }
        if (recordCoordinator != null) {
            recordCoordinator.close();
        }
        if (session != null) {
            session.close();
        }
        super.onDestroy();
    }

    /** 지도 준비 상태와 안내 문구를 화면에 반영한다. */
    @Override
    public void onStatus(String message, boolean mapReady) {
        this.mapReady = mapReady;
        screen.setReady(mapReady);
        if (revisits != null) {
            revisits.setReady(mapReady);
        }
        if (!mapReady) {
            screen.showStatus(message);
        }
        if (mapReady && !cameraInitialized && session != null) {
            cameraInitialized = true;
            session.setMapPerspective(MapPerspective.THREE_DIMENSIONAL);
        }
        if (mapReady && !recordsLoaded && recordCoordinator != null) {
            recordsLoaded = true;
            recordCoordinator.refreshMarkers();
        }
    }

    /** POI와 이름 없는 지도 지점 모두 선택 좌표의 가까운 매장 목록으로 연결한다. */
    @Override
    public void onPlaceSelected(KakaoPlaceSelection selection) {
        openNearbyStores(selection);
    }

    /** 선택한 목적지와 버튼만 해제하고 이미 전달한 내비 요청은 그대로 유지한다. */
    @Override
    public void onMapSelectionCleared() {
        if (!destroyed) {
            selectedNavigationDestination = null;
            screen.hideNavigationAction();
        }
    }

    /** 지도 시점은 지도 제스처로 처리하며 별도 전환 조작은 표시하지 않는다. */
    @Override
    public void onMapPerspectiveChanged(boolean threeDimensional) {
        // SDK의 기울기 제스처만 유지한다.
    }

    /** 실제 위치 공급자와 응답 수신 상태를 지도 탐색 상태와 구분한다. */
    @Override
    public void onPosition(CurrentPosition position) {
        screen.showPositionSource(position.getSourceLabel());
        if (revisits != null) {
            revisits.onVehiclePosition(position.getLatitude(), position.getLongitude());
        }
    }

    /** 수신 실패 시 마지막 점이 숨겨졌음을 표시한다. */
    @Override
    public void onLocationUnavailable(LocationFailure failure) {
        screen.showStatus(failure.getMessage());
        if (revisits != null) {
            revisits.onVehicleUnavailable();
        }
    }

    /** 실패 코드를 검증 로그에 남기고 사용자에게 구체적인 다음 행동을 안내한다. */
    @Override
    public void onFailure(KakaoTestFailure failure) {
        screen.showStatus(failure.getMessage());
        Log.w(TAG, "UI_FAILURE code=" + failure.getCode().name());
    }
}
