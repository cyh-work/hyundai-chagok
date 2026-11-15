package com.example.pleosstarter.navigation;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NaviMode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.constants.WaypointIndex;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RequestWaypointInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.RouteStateInfo;
import ai.pleos.playground.navi.data.RouteWithWaypointsRequest;
import ai.pleos.playground.navi.data.TBTInfo;
import ai.pleos.playground.navi.data.WaypointInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** Pleos 내비에 서울역 안내를 요청하고 SDK 콜백을 메인 스레드로 전달한다. */
public final class PleosNavigationClient implements NaviHelperEventListener {
    private static final String TAG = "PleosRoute";
    private static final String DESTINATION_NAME = "서울역 KTX";
    private static final double DESTINATION_LATITUDE = 37.5547;
    private static final double DESTINATION_LONGITUDE = 126.9707;
    private static final double DESTINATION_TOLERANCE = 0.005;
    private static final double WAYPOINT_TOLERANCE = 0.001;
    private static final long REQUEST_TIMEOUT_MS = 30000L;

    private final Context applicationContext;
    private final Consumer<NavigationEvent> observer;
    private final Consumer<VehiclePosition> locationObserver;
    private final Consumer<GuidanceSnapshot> guidanceObserver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::handleTimeout;
    private final Runnable locationTimeout = () -> deliverLocation(null);
    private NaviHelper helper;
    private boolean connected;
    private boolean pending;
    private boolean routeEventReceived;
    private boolean closed;
    private NavigationWaypoint requestedWaypoint;
    private DestinationInfo confirmedDestination;
    private boolean waypointConfirmed;
    private boolean waypointNameReceived;
    private boolean locationPending;
    private NaviMode lastMode;
    private boolean guidanceActive;

    /** 앱 컨텍스트와 경로 및 위치 상태를 받을 관찰자를 저장한다. */
    public PleosNavigationClient(Context context, Consumer<NavigationEvent> observer,
            Consumer<VehiclePosition> locationObserver, Consumer<GuidanceSnapshot> guidanceObserver) {
        applicationContext = context.getApplicationContext();
        this.observer = observer;
        this.locationObserver = locationObserver;
        this.guidanceObserver = guidanceObserver;
    }

    /** 내비가 알고 있는 현재 위치를 한 번 조회한다. 좌표 없이 끝나면 null을 전달한다. */
    public void requestCurrentLocation() {
        if (!connected || closed || locationPending) {
            return;
        }
        locationPending = true;
        mainHandler.postDelayed(locationTimeout, 5000L);
        try {
            helper.getCurrentLocationInfo();
            helper.getRouteStateInfo();
        } catch (RuntimeException error) {
            Log.w(TAG, "getCurrentLocationInfo failed type=" + error.getClass().getSimpleName());
            deliverLocation(null);
        }
    }

    /** 현재 내비 상태를 받아 활성 경로에서만 다음 회전 정보를 조회한다. */
    @Override
    public void onRouteStateInfo(RouteStateInfo routeState) {
        mainHandler.post(() -> {
            if (closed || !connected || routeState == null) {
                return;
            }
            NaviMode mode = routeState.getNaviMode();
            if (lastMode != mode) {
                Log.i(TAG, "routeState mode=" + mode);
                lastMode = mode;
            }
            guidanceActive = mode == NaviMode.ROUTE_GUIDING || mode == NaviMode.SIMULATION_GUIDING;
            if (!guidanceActive) {
                guidanceObserver.accept(new GuidanceSnapshot(false, "", 0));
                return;
            }
            try {
                helper.getTBTInfo();
            } catch (RuntimeException error) {
                Log.w(TAG, "getTBTInfo failed type=" + error.getClass().getSimpleName());
                guidanceObserver.accept(new GuidanceSnapshot(true, "", 0));
            }
        });
    }

    /** Pleos가 계산한 다음 회전 정보를 자체 지도에 전달한다. */
    @Override
    public void onTBTInfo(List<TBTInfo> turns) {
        mainHandler.post(() -> {
            if (closed || !guidanceActive) {
                return;
            }
            if (turns == null || turns.isEmpty()) {
                guidanceObserver.accept(new GuidanceSnapshot(true, "", 0));
                return;
            }
            TBTInfo next = turns.get(0);
            Log.i(TAG, "guidance turnReceived=true");
            guidanceObserver.accept(new GuidanceSnapshot(true, next.getDescription(),
                    (int) Math.max(0, Math.round(next.getDistance()))));
        });
    }

    /** 내비가 보낸 위치 좌표를 확인해 화면 스레드에 전달한다. */
    @Override
    public void onCurrentLocationInfo(CurrentLocationInfo location) {
        mainHandler.post(() -> {
            if (location == null) {
                deliverLocation(null);
                return;
            }
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();
            if (!Double.isFinite(latitude) || Math.abs(latitude) > 90
                    || !Double.isFinite(longitude) || Math.abs(longitude) > 180
                    || (latitude == 0 && longitude == 0)) {
                deliverLocation(null);
                return;
            }
            deliverLocation(new VehiclePosition(latitude, longitude));
        });
    }

    /** 요청이 유효할 때만 위치 결과를 전달하고 조회 대기를 끝낸다. */
    private void deliverLocation(VehiclePosition position) {
        if (closed || !locationPending) {
            return;
        }
        locationPending = false;
        mainHandler.removeCallbacks(locationTimeout);
        Log.i(TAG, "currentLocation available=" + (position != null));
        locationObserver.accept(position);
    }

    /** 새 SDK 인스턴스로 연결을 시작한다. 메인 스레드에서 호출한다. */
    public void connect() {
        if (closed || helper != null) {
            return;
        }
        emit(NavigationEvent.Status.CONNECTING);
        NaviHelper connectingHelper = new NaviHelper(applicationContext);
        helper = connectingHelper;
        try {
            connectingHelper.initialize(
                    () -> {
                        mainHandler.post(() -> handleConnected(connectingHelper));
                        return Unit.INSTANCE;
                    },
                    error -> {
                        mainHandler.post(() -> handleConnectionFailure(connectingHelper, error));
                        return Unit.INSTANCE;
                    });
        } catch (RuntimeException error) {
            handleConnectionFailure(connectingHelper, error);
        }
    }

    /** 연결된 내비에 서울역 인근 좌표를 목적지로 보낸다. */
    public void requestSeoulStation() {
        requestRoute(null);
    }

    /** 사용자가 선택한 경유지를 포함하여 서울역 경로를 요청한다. */
    public void requestSeoulStationVia(NavigationWaypoint waypoint) {
        requestRoute(waypoint);
    }

    /** 요청 상태를 초기화하고 목적지와 선택된 경유지를 SDK에 전달한다. */
    private void requestRoute(NavigationWaypoint waypoint) {
        if (!connected || pending || closed) {
            return;
        }

        pending = true;
        routeEventReceived = false;
        requestedWaypoint = waypoint;
        confirmedDestination = null;
        waypointConfirmed = false;
        waypointNameReceived = false;
        emit(NavigationEvent.Status.REQUESTED);
        mainHandler.postDelayed(timeout, REQUEST_TIMEOUT_MS);

        try {
            RouteInfo destination = new RouteInfo(
                    DESTINATION_LATITUDE,
                    DESTINATION_LONGITUDE,
                    null,
                    null,
                    DESTINATION_NAME,
                    "",
                    "서울특별시 용산구 한강대로 405",
                    "",
                    RouteOption.RECOMMENDED,
                    "",
                    false);
            if (waypoint == null) {
                Log.i(TAG, "requestRoute destination=SeoulStation");
                helper.requestRoute(destination);
            } else {
                RequestWaypointInfo stop = new RequestWaypointInfo(
                        waypoint.latitude(), waypoint.longitude(), waypoint.name(),
                        WaypointIndex.FIRST, "", "", "", "");
                Log.i(TAG, "requestRouteWithWaypoints requested=true");
                helper.requestRouteWithWaypoints(new RouteWithWaypointsRequest(
                        destination, Collections.singletonList(stop)));
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "requestRoute failed type=" + error.getClass().getSimpleName());
            finish(NavigationEvent.Status.REQUEST_FAILED);
        }
    }

    /** 첫 안내 지점의 경로 이벤트를 받으면 최종 목적지와 경유지를 조회한다. */
    @Override
    public void onRouteStarted(RouteStartInfo route) {
        mainHandler.post(() -> {
            if (closed) {
                return;
            }
            Log.i(TAG, "onRouteStarted");
            if (!pending || !matchesRouteStart(route)) {
                return;
            }
            routeEventReceived = true;
            emit(NavigationEvent.Status.STARTED);
            queryRouteDetails();
        });
    }

    /** 요청 종류에 따라 안내를 시작한 지점의 이름을 대조한다. */
    private boolean matchesRouteStart(RouteStartInfo route) {
        if (requestedWaypoint == null) {
            return route.getName().contains("서울역");
        }
        return requestedWaypoint.name().equals(route.getName()) || route.getName().contains("서울역");
    }

    /** 탐색 완료 신호를 기록하고 설치된 목적지 조회를 요청한다. */
    @Override
    public void onRoutingCompleted() {
        mainHandler.post(() -> {
            if (closed) {
                return;
            }
            Log.i(TAG, "onRoutingCompleted");
            if (!pending) {
                return;
            }
            routeEventReceived = true;
            emit(NavigationEvent.Status.CALCULATED);
            queryRouteDetails();
        });
    }

    /** 목적지가 바뀌면 좌표와 경로 정보가 돌아오는지 확인한다. */
    @Override
    public void onDestinationChanged() {
        mainHandler.post(() -> {
            if (pending && !closed) {
                Log.i(TAG, "onDestinationChanged");
                queryRouteDetails();
            }
        });
    }

    /** 내비가 반환한 목적지를 확인하고 성공 여부를 화면에 전달한다. */
    @Override
    public void onDestinationInfo(DestinationInfo destination) {
        mainHandler.post(() -> handleDestination(destination));
    }

    /** 경로 탐색 오류 코드만 기록하고 사용자에게 실패 상태를 전달한다. */
    @Override
    public void onError(NaviErrorCode error) {
        mainHandler.post(() -> {
            if (pending && !closed) {
                Log.w(TAG, "onError code=" + error);
                finish(NavigationEvent.Status.REQUEST_FAILED);
            }
        });
    }

    /** 화면 파괴 시 진행 중인 대기와 SDK 리스너를 해제한다. */
    public void close() {
        closed = true;
        pending = false;
        mainHandler.removeCallbacksAndMessages(null);
        releaseHelper();
    }

    /** 현재 연결의 성공을 확인하고 요청 버튼을 사용할 수 있게 한다. */
    private void handleConnected(NaviHelper source) {
        if (helper != source || closed) {
            return;
        }
        try {
            source.addListener(this);
            connected = true;
            Log.i(TAG, "onConnected");
            emit(NavigationEvent.Status.READY);
        } catch (RuntimeException error) {
            handleConnectionFailure(source, error);
        }
    }

    /** 연결 실패를 알리고 다시 시도할 수 있도록 SDK를 해제한다. */
    private void handleConnectionFailure(NaviHelper source, Throwable error) {
        if (helper != source || closed) {
            return;
        }
        Log.w(TAG, "onConnectionFailed type=" + error.getClass().getSimpleName());
        releaseHelper();
        emit(NavigationEvent.Status.CONNECTION_FAILED);
    }

    /** SDK에 등록된 현재 목적지와 요청한 경유지를 조회한다. */
    private void queryRouteDetails() {
        try {
            helper.getDestinationInfo();
            if (requestedWaypoint != null) {
                helper.getWaypointInfo();
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "getRouteDetails failed type=" + error.getClass().getSimpleName());
            finish(NavigationEvent.Status.REQUEST_FAILED);
        }
    }

    /** 응답의 장소 이름과 좌표가 서울역 요청에 해당하는지 확인한다. */
    private void handleDestination(DestinationInfo destination) {
        if (!pending || closed || !routeEventReceived || destination == null) {
            return;
        }
        boolean matches = destination.getName().contains("서울역")
                && Math.abs(destination.getLatitude() - DESTINATION_LATITUDE) < DESTINATION_TOLERANCE
                && Math.abs(destination.getLongitude() - DESTINATION_LONGITUDE) < DESTINATION_TOLERANCE;
        int distanceMeters = destination.getRemainDistance();
        Log.i(TAG, "onDestinationInfo matched=" + matches + " distanceMeters=" + distanceMeters
                + " remainTime=" + destination.getRemainTime());
        if (!matches || distanceMeters <= 0) {
            return;
        }
        confirmedDestination = destination;
        confirmRequest();
    }

    /** SDK에서 경유지 목록을 받으면 요청한 좌표가 등록되었는지 확인한다. */
    @Override
    public void onWayPointInfo(List<WaypointInfo> waypoints) {
        mainHandler.post(() -> handleWaypoints(waypoints));
    }

    /** 반환된 경유지를 대조하고 좌표가 비어 있을 때는 이름 수신 여부를 기록한다. */
    private void handleWaypoints(List<WaypointInfo> waypoints) {
        if (!pending || closed || !routeEventReceived || requestedWaypoint == null || waypoints == null) {
            return;
        }

        for (WaypointInfo waypoint : waypoints) {
            double latitudeOffset = Math.abs(waypoint.getLatitude() - requestedWaypoint.latitude());
            double longitudeOffset = Math.abs(waypoint.getLongitude() - requestedWaypoint.longitude());
            Double displayLatitude = waypoint.getDisplayLatitude();
            Double displayLongitude = waypoint.getDisplayLongitude();
            boolean displayMatches = displayLatitude != null && displayLongitude != null
                    && Math.abs(displayLatitude - requestedWaypoint.latitude()) < WAYPOINT_TOLERANCE
                    && Math.abs(displayLongitude - requestedWaypoint.longitude()) < WAYPOINT_TOLERANCE;
            boolean coordinatesMatch = latitudeOffset < WAYPOINT_TOLERANCE
                    && longitudeOffset < WAYPOINT_TOLERANCE;
            if (coordinatesMatch || displayMatches) {
                waypointConfirmed = true;
                break;
            }
            boolean noCoordinate = waypoint.getLatitude() == 0 && waypoint.getLongitude() == 0;
            if (noCoordinate && requestedWaypoint.name().equals(waypoint.getName())) {
                waypointNameReceived = true;
            }
        }
        Log.i(TAG, "onWayPointInfo count=" + waypoints.size() + " coordinateMatched=" + waypointConfirmed
                + " nameOnly=" + waypointNameReceived);
        confirmRequest();
    }

    /** 경로 이벤트와 요청한 목적지 및 경유지가 모두 확인되면 결과를 전달한다. */
    private void confirmRequest() {
        if (!routeEventReceived || confirmedDestination == null) {
            return;
        }
        if (requestedWaypoint != null && !waypointConfirmed && !waypointNameReceived) {
            return;
        }

        pending = false;
        mainHandler.removeCallbacks(timeout);
        int distance = confirmedDestination.getRemainDistance();
        if (requestedWaypoint == null) {
            observer.accept(new NavigationEvent(
                    NavigationEvent.Status.CONFIRMED, confirmedDestination.getName(), distance));
        } else {
            NavigationEvent.Status status = NavigationEvent.Status.WAYPOINT_NAME_RECEIVED;
            if (waypointConfirmed) {
                status = NavigationEvent.Status.WAYPOINT_CONFIRMED;
            }
            observer.accept(new NavigationEvent(
                    status, requestedWaypoint.name(), distance));
        }
    }

    /** 응답 기한까지 목적지를 확인하지 못하면 요청을 끝낸다. */
    private void handleTimeout() {
        if (pending && !closed) {
            Log.w(TAG, "destination confirmation timed out");
            finish(NavigationEvent.Status.TIMED_OUT);
        }
    }

    /** 현재 요청의 시간 제한을 취소하고 마지막 상태를 전달한다. */
    private void finish(NavigationEvent.Status status) {
        pending = false;
        mainHandler.removeCallbacks(timeout);
        emit(status);
    }

    /** 화면에 목적지 데이터 없는 이벤트를 전달한다. */
    private void emit(NavigationEvent.Status status) {
        observer.accept(new NavigationEvent(status));
    }

    /** 현재 SDK 인스턴스의 구독과 서비스 연결을 정리한다. */
    private void releaseHelper() {
        NaviHelper currentHelper = helper;
        helper = null;
        if (currentHelper == null) {
            return;
        }
        try {
            if (connected) {
                currentHelper.removeListener(this);
            }
        } finally {
            connected = false;
            currentHelper.release();
        }
    }
}
