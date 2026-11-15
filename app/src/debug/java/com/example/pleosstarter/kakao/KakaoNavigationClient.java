package com.example.pleosstarter.kakao;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.kakaomobility.knsdk.KNRoutePriority;
import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.common.objects.KNError;
import com.kakaomobility.knsdk.common.objects.KNPOI;
import com.kakaomobility.knsdk.common.util.DoublePoint;
import com.kakaomobility.knsdk.common.util.IntPoint;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_GuideStateDelegate;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_LocationGuideDelegate;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_RouteGuideDelegate;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuidance_VoiceGuideDelegate;
import com.kakaomobility.knsdk.guidance.knguidance.KNGuideRouteChangeReason;
import com.kakaomobility.knsdk.guidance.knguidance.KNSimulGuidance;
import com.kakaomobility.knsdk.guidance.knguidance.common.KNLocation;
import com.kakaomobility.knsdk.guidance.knguidance.locationguide.KNGuide_Location;
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.KNGuide_Route;
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNDirection;
import com.kakaomobility.knsdk.guidance.knguidance.routeguide.objects.KNMultiRouteInfo;
import com.kakaomobility.knsdk.guidance.knguidance.voiceguide.KNGuide_Voice;
import com.kakaomobility.knsdk.trip.kntrip.KNTrip;
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute;

import java.util.List;
import java.util.Locale;

import kotlin.Unit;

/** 카카오 인증·지도·경로·안내 엔진을 연결하는 디버그 시험 세션이다. */
public final class KakaoNavigationClient implements NavigationTestSession,
        KNGuidance_GuideStateDelegate, KNGuidance_LocationGuideDelegate,
        KNGuidance_RouteGuideDelegate, KNGuidance_VoiceGuideDelegate {
    private static final String TAG = "KakaoNavigationTest";
    private static final double PANGYO_LONGITUDE = 127.11019;
    private static final double PANGYO_LATITUDE = 37.39418;
    private static final double SEOUL_STATION_LONGITUDE = 126.97256;
    private static final double SEOUL_STATION_LATITUDE = 37.55465;
    private static final int AVOID_NOTHING = 0;
    private static final KNRoutePriority PRIORITY = KNRoutePriority.KNRoutePriority_Recommand;
    private final KakaoSdkRuntime sdkRuntime;
    private final NavigationTestListener listener;
    private final KakaoMapController map;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private KNGuidance guidance;
    private KNLocation currentLocation;
    private boolean ready;
    private boolean closed;
    private boolean simulation;
    private int requestGeneration;
    private int locationUpdates;

    /** SDK 실행 객체와 화면 수신자를 주입하고 지도 표시 책임을 분리한다. */
    public KakaoNavigationClient(Context context, KakaoSdkRuntime sdkRuntime,
                                 NavigationTestListener listener) {
        this.sdkRuntime = sdkRuntime;
        this.listener = listener;
        this.map = new KakaoMapController(context, selection -> {
            Log.i(TAG, "POI_DOUBLE_TAP id=" + selection.getPoiId()
                    + " guidanceActive=" + (guidance != null));
            listener.onPlaceSelected(selection);
        });
    }

    /** 인증 전에도 표시할 수 있는 지도 컨테이너를 제공한다. */
    @Override
    public View getMapView() {
        return map.getView();
    }

    /** 인증 성공 시에만 지도 생성·바인딩으로 이어진다. */
    @Override
    public void initialize() {
        listener.onStatus("카카오 지도 연결 중…", false);
        sdkRuntime.initialize(new KakaoSdkRuntime.Completion() {
            /** 인증된 SDK로 지도 표시를 요청한다. */
            @Override
            public void onReady() {
                runSafely(() -> map.bind(() -> {
                    ready = true;
                    showPangyo();
                    listener.onStatus("판교 시험 위치 · 가게 이름을 두 번 눌러보세요", true);
                }, KakaoNavigationClient.this::reportFailure));
            }

            /** 인증 실패를 지도 성공으로 바꾸지 않고 화면에 전달한다. */
            @Override
            public void onFailure(KakaoTestFailure failure) {
                reportFailure(failure);
            }
        });
    }

    /** 고정 시험 위치를 보여주며 현재 GPS 위치라고 표시하지 않는다. */
    @Override
    public void showPangyo() {
        runSafely(() -> {
            map.showLocation(PANGYO_LONGITUDE, PANGYO_LATITUDE);
            if (guidance == null) {
                listener.onStatus("판교 시험 위치 · 가게 이름을 두 번 눌러보세요", ready);
            } else {
                listener.onStatus("판교 시험 위치 · 안내는 유지되며 지도만 탐색합니다", ready);
            }
        });
    }

    /** 장소 탐색은 안내 엔진을 종료하거나 목적지를 변경하지 않는다. */
    @Override
    public void showPlace(KakaoPlaceSelection selection) {
        if (selection == null || !ready || closed) {
            return;
        }
        runSafely(() -> map.showLocation(selection.getLongitude(), selection.getLatitude()));
    }

    /** 보기 전환은 안내 엔진과 위치 추적을 유지하며 지도 기울기만 바꾼다. */
    @Override
    public void setMapPerspective(MapPerspective perspective) {
        runSafely(() -> map.setPerspective(perspective));
    }

    /** 화면 중앙의 좌표를 검색 기준으로 제공하고 초기화 전에는 시험 위치를 사용한다. */
    @Override
    public KakaoPlaceSelection getSearchCenter() {
        return map.getSearchCenter(PANGYO_LONGITUDE, PANGYO_LATITUDE);
    }

    /** 실제 카카오 경로 검색 결과를 받아 GPS 안내 또는 SDK 모의안내를 시작한다. */
    @Override
    public void startSeoulStationRoute(boolean simulation) {
        if (!ready || closed) {
            reportFailure(new KakaoTestFailure(KakaoTestError.SESSION_NOT_READY,
                    "지도가 연결된 뒤 경로를 요청해 주세요."));
            return;
        }

        stopGuidance();
        this.simulation = simulation;
        int generation = ++requestGeneration;
        listener.onStatus("판교 → 서울역 경로 찾는 중…", true);
        runSafely(() -> {
            KNPOI start = createPoi("판교 시험 출발점", PANGYO_LONGITUDE, PANGYO_LATITUDE);
            KNPOI goal = createPoi("서울역", SEOUL_STATION_LONGITUDE, SEOUL_STATION_LATITUDE);
            KNSDK.INSTANCE.makeTripWithStart(start, goal, null, null, (error, trip) -> {
                mainHandler.post(() -> {
                    if (closed || generation != requestGeneration) {
                        return;
                    }
                    if (error != null || trip == null) {
                        KakaoSdkErrorReporter.report("MAKE_TRIP_FAILED", error);
                        reportFailure(new KakaoTestFailure(KakaoTestError.ROUTE_REQUEST_FAILED,
                                "카카오 경로 요청을 만들지 못했습니다."));
                        return;
                    }
                    requestRoutes(trip, generation);
                });
                return Unit.INSTANCE;
            });
        });
    }

    /** 카카오가 반환한 경로가 존재할 때만 안내 엔진을 시작한다. */
    private void requestRoutes(KNTrip trip, int generation) {
        runSafely(() -> trip.routeWithPriority(PRIORITY, AVOID_NOTHING, (error, routes) -> {
            mainHandler.post(() -> runSafely(() -> {
                if (generation != requestGeneration) {
                    return;
                }
                if (error != null) {
                    KakaoSdkErrorReporter.report("ROUTE_REQUEST_FAILED", error);
                    reportFailure(new KakaoTestFailure(KakaoTestError.ROUTE_REQUEST_FAILED,
                            "카카오 길찾기 요청에 실패했습니다."));
                    return;
                }
                if (routes == null || routes.isEmpty()) {
                    reportFailure(new KakaoTestFailure(KakaoTestError.ROUTE_EMPTY,
                            "서울역까지 안내할 경로가 없습니다."));
                    return;
                }

                map.showRoutes(routes);
                map.setFollowLocation(true);
                if (simulation) {
                    KNSimulGuidance simulatedGuidance = KNSDK.INSTANCE.sharedSimulGuidance();
                    simulatedGuidance.setSimulationSpeed(60);
                    guidance = simulatedGuidance;
                } else {
                    guidance = KNSDK.INSTANCE.sharedGuidance();
                }
                guidance.setGuideStateDelegate(this);
                guidance.setLocationGuideDelegate(this);
                guidance.setRouteGuideDelegate(this);
                guidance.setVoiceGuideDelegate(this);
                locationUpdates = 0;
                Log.i(TAG, "ROUTE_RECEIVED count=" + routes.size()
                        + " distance=" + routes.get(0).getTotalDist()
                        + " simulation=" + simulation);
                guidance.startWithTrip(trip, PRIORITY, AVOID_NOTHING);
            }));
            return Unit.INSTANCE;
        }));
    }

    /** WGS84 시험 지점을 SDK가 요구하는 정수 KATEC POI로 변환한다. */
    private KNPOI createPoi(String name, double longitude, double latitude) {
        DoublePoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(longitude, latitude);
        IntPoint position = new IntPoint((int) Math.round(coordinate.getX()),
                (int) Math.round(coordinate.getY()));
        return new KNPOI(name, position);
    }

    /** 요청 중인 경로 결과를 무효화하고 안내 엔진·리스너를 함께 해제한다. */
    @Override
    public void stopGuidance() {
        requestGeneration++;
        if (guidance != null) {
            KNGuidance previous = guidance;
            guidance = null;
            previous.setGuideStateDelegate(null);
            previous.setLocationGuideDelegate(null);
            previous.setRouteGuideDelegate(null);
            previous.setVoiceGuideDelegate(null);
            previous.stop();
        }
        currentLocation = null;
        map.clearGuidance();
        if (!closed) {
            listener.onStatus("안내 종료 · 지도에서 가게를 선택할 수 있습니다", ready);
            listener.onGuidanceUpdate("안내 중지 · 지도에서 가게를 선택할 수 있습니다");
            Log.i(TAG, "GUIDANCE_STOPPED");
        }
    }

    /** 화면이 다시 표시되면 지도 렌더링을 재개한다. */
    @Override
    public void resume() {
        runSafely(map::resume);
    }

    /** 화면이 가려지면 지도 렌더링을 멈춘다. */
    @Override
    public void pause() {
        runSafely(map::pause);
    }

    /** 화면 종료 뒤 도착하는 비동기 결과를 차단하고 자원을 해제한다. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        stopGuidance();
        closed = true;
        map.close();
    }

    /** 안내가 실제로 시작됐다는 SDK 콜백을 기록한다. */
    @Override
    public void guidanceGuideStarted(KNGuidance sender) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender != guidance) {
                return;
            }
            String mode = simulation ? "모의주행 중" : "GPS 안내 시작 · 위치 수신 대기";
            listener.onStatus(mode + " · 서울역", true);
            Log.i(TAG, "GUIDANCE_STARTED simulation=" + simulation);
        }));
    }

    /** 경로 변경 확인 중인 상태를 전달한다. */
    @Override
    public void guidanceCheckingRouteChange(KNGuidance sender) {
        postGuidanceStatus(sender, "더 나은 경로를 확인하고 있습니다");
    }

    /** 기존 경로가 유지됐음을 전달한다. */
    @Override
    public void guidanceRouteUnchanged(KNGuidance sender) {
        postGuidanceStatus(sender, "현재 경로 안내를 계속합니다");
    }

    /** 재탐색 실패와 현재 안내 유지 상태를 구분한다. */
    @Override
    public void guidanceRouteUnchangedWithError(KNGuidance sender, KNError error) {
        KakaoSdkErrorReporter.report("REROUTE_FAILED", error);
        postGuidanceStatus(sender, "경로 갱신 실패 · 기존 안내 유지");
    }

    /** 경로 이탈 상태를 전달한다. */
    @Override
    public void guidanceOutOfRoute(KNGuidance sender) {
        postGuidanceStatus(sender, "경로 이탈 · 재탐색 대기");
    }

    /** SDK에서 실제 경로가 바뀌면 새 경로를 지도에 반영한다. */
    @Override
    public void guidanceRouteChanged(KNGuidance sender, KNRoute previousRoute,
                                     KNLocation previousLocation, KNRoute nextRoute,
                                     KNLocation nextLocation, KNGuideRouteChangeReason reason) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender == guidance && nextRoute != null) {
                map.showRoutes(java.util.Collections.singletonList(nextRoute));
                Log.i(TAG, "GUIDANCE_ROUTE_CHANGED");
            }
        }));
    }

    /** 목적지 도착 등 SDK의 안내 종료를 표시한다. */
    @Override
    public void guidanceGuideEnded(KNGuidance sender) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender == guidance) {
                stopGuidance();
                listener.onStatus("서울역 안내 종료 · 주변 가게를 선택할 수 있습니다", ready);
                Log.i(TAG, "GUIDANCE_ENDED");
            }
        }));
    }

    /** 경로 목록 갱신 콜백을 지도에 반영한다. */
    @Override
    public void guidanceDidUpdateRoutes(KNGuidance sender, List<KNRoute> routes,
                                        KNMultiRouteInfo routeInfo) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender == guidance && routes != null && !routes.isEmpty()) {
                map.showRoutes(routes);
                Log.i(TAG, "GUIDANCE_ROUTES_UPDATED count=" + routes.size());
            }
        }));
    }

    /** 실내 경로 수신 사실을 기록하며 실내 표시 성공으로 간주하지 않는다. */
    @Override
    public void guidanceDidUpdateIndoorRoute(KNGuidance sender, KNRoute route) {
        postGuidanceStatus(sender, "실내 경로 정보 수신 · 실내 지도는 미검증");
    }

    /** 실제 안내 콜백의 위치로 차량 표시를 갱신하고 모의주행 여부를 명시한다. */
    @Override
    public void guidanceDidUpdateLocation(KNGuidance sender, KNGuide_Location locationGuide) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender != guidance || locationGuide == null || locationGuide.getLocation() == null) {
                return;
            }
            currentLocation = locationGuide.getLocation();
            float angle = 0.0f;
            if (locationGuide.getGpsMatched() != null) {
                angle = locationGuide.getGpsMatched().getAngle();
            }
            map.updateLocation(currentLocation, angle);
            locationUpdates++;
            String mode = simulation ? "모의 위치" : "SDK 위치";
            listener.onGuidanceUpdate(mode + " 갱신 " + locationUpdates + "회 · "
                    + currentLocation.getRoadName());
            if (locationUpdates == 1 || locationUpdates % 10 == 0) {
                Log.i(TAG, "GUIDANCE_LOCATION count=" + locationUpdates + " simulation=" + simulation);
            }
        }));
    }

    /** 다음 안내 지점과 거리를 SDK의 경로 안내 정보에서 표시한다. */
    @Override
    public void guidanceDidUpdateRouteGuide(KNGuidance sender, KNGuide_Route routeGuide) {
        mainHandler.post(() -> runSafely(() -> {
            if (sender != guidance || routeGuide == null) {
                return;
            }
            KNDirection direction = routeGuide.getCurDirection();
            if (direction == null || currentLocation == null) {
                return;
            }
            int distance = Math.max(0, currentLocation.distToLocation(direction.getLocation()));
            String mode = simulation ? "모의주행" : "GPS 안내";
            listener.onStatus(String.format(Locale.KOREA, "%s · %dm · %s %s",
                    mode, distance, direction.getNodeName(), direction.getDirectionNames()), true);
        }));
    }

    /** SDK가 제공하는 안내 음성을 재생하도록 허용한다. */
    @Override
    public boolean shouldPlayVoiceGuide(KNGuidance sender, KNGuide_Voice voice, List<byte[]> buffers) {
        return !closed && sender == guidance;
    }

    /** 음성 재생 시작 콜백을 기록하며 실제 스피커 청취와 구분한다. */
    @Override
    public void willPlayVoiceGuide(KNGuidance sender, KNGuide_Voice voice) {
        Log.i(TAG, "VOICE_STARTED");
    }

    /** 음성 재생 완료 콜백을 기록한다. */
    @Override
    public void didFinishPlayVoiceGuide(KNGuidance sender, KNGuide_Voice voice) {
        Log.i(TAG, "VOICE_FINISHED");
    }

    /** 현재 안내 객체의 비동기 상태만 화면에 전달한다. */
    private void postGuidanceStatus(KNGuidance sender, String message) {
        mainHandler.post(() -> {
            if (!closed && sender == guidance) {
                listener.onGuidanceUpdate(message);
            }
        });
    }

    /** 예상 밖 SDK 예외와 네이티브 로드 실패를 인증정보 없이 분류한다. */
    private void runSafely(Runnable operation) {
        if (closed) {
            return;
        }
        try {
            operation.run();
        } catch (LinkageError error) {
            Log.e(TAG, "SDK_LINKAGE_FAILURE type=" + error.getClass().getSimpleName());
            reportFailure(new KakaoTestFailure(KakaoTestError.SDK_LOAD_FAILED,
                    "이 기기에서 카카오 SDK 라이브러리를 불러오지 못했습니다."));
        } catch (RuntimeException error) {
            Log.e(TAG, "SDK_OPERATION_FAILURE type=" + error.getClass().getSimpleName());
            reportFailure(new KakaoTestFailure(KakaoTestError.UNEXPECTED_FAILURE,
                    "카카오 지도 처리 중 오류가 발생했습니다."));
        }
    }

    /** 화면이 살아 있을 때만 별도 실패 결과를 전달한다. */
    private void reportFailure(KakaoTestFailure failure) {
        if (!closed) {
            Log.w(TAG, "FAILURE code=" + failure.getCode());
            listener.onFailure(failure);
        }
    }
}
