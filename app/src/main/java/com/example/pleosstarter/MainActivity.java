package com.example.pleosstarter;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.example.pleosstarter.map.MapGesture;
import com.example.pleosstarter.map.MapPlace;
import com.example.pleosstarter.map.SeoulMapView;
import com.example.pleosstarter.navigation.NavigationEvent;
import com.example.pleosstarter.navigation.GuidanceSnapshot;
import com.example.pleosstarter.navigation.NavigationWaypoint;
import com.example.pleosstarter.navigation.PleosNavigationClient;
import com.example.pleosstarter.navigation.VehiclePosition;

/** 주변 장소 지도와 Pleos 내비 요청 화면을 연결한다. */
public final class MainActivity extends Activity {
    private static final String TAG = "GestureNavigation";

    private PleosNavigationClient navigationClient;
    private FavoritePlaces favorites;
    private SeoulMapView mapView;
    private TextView gestureStatus;
    private Button waypointButton;
    private Button routeButton;
    private TextView routeStatus;
    private TextView locationStatus;
    private Button locationButton;
    private MapPlace suggestedWaypoint;
    private boolean reconnect;
    private boolean canRequest;

    /** 지도, 즐겨찾기 저장소와 내비 연결을 초기화한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        favorites = new FavoritePlaces(getSharedPreferences("favorite_places", MODE_PRIVATE));
        mapView = findViewById(R.id.gesture_map);
        gestureStatus = findViewById(R.id.gesture_status);
        waypointButton = findViewById(R.id.waypoint_request);
        routeButton = findViewById(R.id.route_request);
        routeStatus = findViewById(R.id.route_status);
        locationStatus = findViewById(R.id.current_location_status);
        locationButton = findViewById(R.id.current_location_request);
        mapView.setFavoriteLookup(favorites::contains);
        mapView.setGestureListener(this::showGesture);

        navigationClient = new PleosNavigationClient(
                this, this::showNavigationEvent, this::showVehiclePosition, this::showGuidanceStatus);
        routeButton.setOnClickListener(view -> requestDirectRoute());
        waypointButton.setOnClickListener(view -> requestSuggestedRoute());
        locationButton.setOnClickListener(view -> requestVehiclePosition());
        navigationClient.connect();
    }

    /** 내비 조회에서 받은 활성 안내 상태를 표시한다. */
    private void showGuidanceStatus(GuidanceSnapshot snapshot) {
        if (snapshot.active()) {
            routeStatus.setText("Pleos 경로 안내 중");
        }
    }

    /** 내비에 위치를 다시 조회하고 화면에 대기 상태를 표시한다. */
    private void requestVehiclePosition() {
        locationStatus.setText(R.string.location_waiting);
        navigationClient.requestCurrentLocation();
    }

    /** 위치 수신, 지도 범위 밖, 미수신을 구분해 보여준다. */
    private void showVehiclePosition(VehiclePosition position) {
        if (position == null) {
            mapView.clearVehiclePosition();
            locationStatus.setText(R.string.location_unavailable);
            return;
        }
        boolean visible = mapView.setVehiclePosition(position.latitude(), position.longitude());
        locationStatus.setText(visible ? R.string.location_on_map : R.string.location_outside_map);
        Log.i(TAG, "currentLocation onDemoMap=" + visible);
    }

    /** 인식된 제스처로 저장, 장소 정보 또는 경유 제안 상태를 갱신한다. */
    private void showGesture(MapGesture gesture) {
        MapPlace place = gesture.place();
        suggestedWaypoint = null;
        if (gesture.type() == MapGesture.Type.UNRECOGNIZED || place == null) {
            gestureStatus.setText(R.string.map_gesture_unknown);
            updateWaypointButton();
            return;
        }

        Log.i(TAG, "gesture=" + gesture.type() + " place=" + place.name());
        switch (gesture.type()) {
            case STAR -> {
                boolean saved = favorites.toggle(place);
                int message = saved ? R.string.map_favorite_added : R.string.map_favorite_removed;
                gestureStatus.setText(getString(message, place.name()));
                mapView.invalidate();
            }
            case CIRCLE -> gestureStatus.setText(
                    getString(R.string.map_place_info, place.name(), place.category()));
            case LINE -> {
                suggestedWaypoint = place;
                gestureStatus.setText(getString(R.string.map_waypoint_suggestion, place.name()));
            }
        }
        updateWaypointButton();
    }

    /** 화면에 경유 제안이 있는 동안만 확인 버튼을 표시한다. */
    private void updateWaypointButton() {
        waypointButton.setVisibility(suggestedWaypoint == null ? View.GONE : View.VISIBLE);
        waypointButton.setEnabled(canRequest);
    }

    /** 연결 상태에 맞춰 서울역 경로를 요청하거나 SDK를 다시 연결한다. */
    private void requestDirectRoute() {
        if (reconnect) {
            navigationClient.connect();
        } else {
            navigationClient.requestSeoulStation();
        }
    }

    /** 사용자가 확인한 경유 후보를 서울역 목적지와 함께 전달한다. */
    private void requestSuggestedRoute() {
        if (suggestedWaypoint == null || !canRequest) {
            return;
        }
        navigationClient.requestSeoulStationVia(new NavigationWaypoint(
                suggestedWaypoint.name(), suggestedWaypoint.latitude(), suggestedWaypoint.longitude()));
    }

    /** SDK 연결과 요청 결과에 따라 안내문과 버튼을 갱신한다. */
    private void showNavigationEvent(NavigationEvent event) {
        reconnect = event.status() == NavigationEvent.Status.CONNECTION_FAILED;
        routeButton.setText(reconnect ? R.string.route_reconnect : R.string.route_request);
        switch (event.status()) {
            case CONNECTING -> showStatus(R.string.route_connecting, false);
            case READY -> {
                locationButton.setEnabled(true);
                showStatus(R.string.route_ready, true);
                requestVehiclePosition();
            }
            case REQUESTED -> showStatus(R.string.route_pending, false);
            case STARTED -> showStatus(R.string.route_started, false);
            case CALCULATED -> showStatus(R.string.route_calculated, false);
            case CONFIRMED -> {
                routeStatus.setText(getString(R.string.route_confirmed,
                        event.destination(), event.distanceMeters()));
                setRouteButtonsEnabled(true);
            }
            case WAYPOINT_CONFIRMED, WAYPOINT_NAME_RECEIVED -> {
                int message = event.status() == NavigationEvent.Status.WAYPOINT_CONFIRMED
                        ? R.string.waypoint_confirmed : R.string.waypoint_name_received;
                routeStatus.setText(getString(message, event.destination(), event.distanceMeters()));
                suggestedWaypoint = null;
                setRouteButtonsEnabled(true);
            }
            case CONNECTION_FAILED -> {
                locationButton.setEnabled(false);
                showVehiclePosition(null);
                showStatus(R.string.route_failed, true);
            }
            case REQUEST_FAILED -> showStatus(R.string.route_failed, true);
            case TIMED_OUT -> showStatus(R.string.route_timeout, true);
        }
    }

    /** 현재 상태 안내와 요청 가능 여부를 표시한다. */
    private void showStatus(int message, boolean enabled) {
        routeStatus.setText(message);
        setRouteButtonsEnabled(enabled);
    }

    /** 직접 요청과 경유 요청 버튼의 상태를 함께 갱신한다. */
    private void setRouteButtonsEnabled(boolean enabled) {
        canRequest = enabled && !reconnect;
        routeButton.setEnabled(enabled);
        updateWaypointButton();
    }

    /** Activity가 없어질 때 내비 서비스의 연결을 종료한다. */
    @Override
    protected void onDestroy() {
        navigationClient.close();
        super.onDestroy();
    }
}
