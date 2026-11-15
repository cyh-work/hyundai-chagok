package com.example.pleosstarter.simulation;

import android.os.Handler;
import android.util.Log;

import com.example.pleosstarter.kakao.KakaoSdkErrorReporter;
import com.kakaomobility.knsdk.KNRoutePriority;
import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.common.objects.KNPOI;
import com.kakaomobility.knsdk.common.util.DoublePoint;
import com.kakaomobility.knsdk.common.util.IntPoint;
import com.kakaomobility.knsdk.trip.kntrip.KNTrip;
import com.kakaomobility.knsdk.trip.kntrip.knroute.KNRoute;

import java.util.Objects;

import kotlin.Unit;

/** 이미 인증된 카카오 SDK에서 도로 좌표만 조회하며 안내 엔진과 지도 경로선을 만들지 않는다. */
public final class KakaoPreviewRouteSource implements RouteSource {
    private static final String TAG = "PlaceMap";
    private static final long REQUEST_TIMEOUT_MILLIS = 15000;
    private final Handler handler;
    private final KakaoRouteGeometryMapper mapper;
    private long generation;
    private Runnable timeout;
    private boolean closed;

    /** 화면과 같은 스레드의 응답 실행기와 좌표 변환 책임을 연결한다. */
    public KakaoPreviewRouteSource(Handler handler) {
        this(handler, new KakaoRouteGeometryMapper());
    }

    /** 외부 조회 결과의 검증과 응답 전달 문맥을 명시적으로 주입한다. */
    public KakaoPreviewRouteSource(Handler handler, KakaoRouteGeometryMapper mapper) {
        this.handler = Objects.requireNonNull(handler);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /** 호출자가 지정한 출발·도착 좌표만 요청하고 취소된 요청의 응답은 무시한다. */
    @Override
    public void load(RoutePoint origin, RoutePoint destination, Callback callback) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(destination);
        Objects.requireNonNull(callback);
        cancel();
        if (closed) {
            return;
        }
        long request = generation;
        timeout = () -> fail(request, callback, PlaybackFailure.Code.ROUTE_TIMEOUT,
                "자동 이동 경로 응답이 늦어지고 있어요. 다시 시도해 주세요.");
        handler.postDelayed(timeout, REQUEST_TIMEOUT_MILLIS);

        try {
            KNPOI start = createPoi("자동 이동 시작", origin);
            KNPOI goal = createPoi("자동 이동 도착", destination);
            Log.i(TAG, "PREVIEW_ROUTE_REQUEST generation=" + request);
            KNSDK.INSTANCE.makeTripWithStart(start, goal, null, null, (error, trip) -> {
                handler.post(() -> {
                    if (!isCurrent(request)) {
                        return;
                    }
                    if (error != null || trip == null) {
                        KakaoSdkErrorReporter.report("PREVIEW_TRIP_FAILED", error);
                        fail(request, callback, PlaybackFailure.Code.ROUTE_REQUEST_FAILED,
                                "자동 이동 경로를 준비하지 못했어요. 다시 시도해 주세요.");
                        return;
                    }
                    requestGeometry(trip, request, callback);
                });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException | LinkageError failure) {
            Log.w(TAG, "PREVIEW_TRIP_SETUP_FAILED type=" + failure.getClass().getSimpleName());
            fail(request, callback, PlaybackFailure.Code.ROUTE_REQUEST_FAILED,
                    "자동 이동 경로를 요청하지 못했어요. 지도 연결을 확인해 주세요.");
        }
    }

    /** 추천 경로 한 개의 공개 WGS84 좌표만 추출해 자체 재생에 전달한다. */
    private void requestGeometry(KNTrip trip, long request, Callback callback) {
        try {
            trip.setUseMultiRoute(false);
            trip.routeWithPriority(KNRoutePriority.KNRoutePriority_Recommand, 0, (error, routes) -> {
                handler.post(() -> {
                    if (!isCurrent(request)) {
                        return;
                    }
                    if (error != null) {
                        KakaoSdkErrorReporter.report("PREVIEW_ROUTE_FAILED", error);
                        fail(request, callback, PlaybackFailure.Code.ROUTE_REQUEST_FAILED,
                                "주변 도로 경로를 찾지 못했어요. 다른 위치에서 시도해 주세요.");
                        return;
                    }
                    if (routes == null || routes.isEmpty()) {
                        fail(request, callback, PlaybackFailure.Code.ROUTE_UNAVAILABLE,
                                "이동 가능한 도로 경로가 없어요. 다른 위치에서 시도해 주세요.");
                        return;
                    }
                    deliverGeometry(routes.get(0), request, callback);
                });
                return Unit.INSTANCE;
            });
        } catch (RuntimeException | LinkageError failure) {
            Log.w(TAG, "PREVIEW_ROUTE_SETUP_FAILED type=" + failure.getClass().getSimpleName());
            fail(request, callback, PlaybackFailure.Code.ROUTE_REQUEST_FAILED,
                    "도로 경로를 불러오지 못했어요. 다시 시도해 주세요.");
        }
    }

    /** 누락·비정상·길이 없는 경로를 거부하고 실제 응답 좌표만 전달한다. */
    private void deliverGeometry(KNRoute sdkRoute, long request, Callback callback) {
        RoutePath path;
        try {
            path = mapper.map(sdkRoute.routePolylineWGS84());
        } catch (RuntimeException | LinkageError failure) {
            Log.w(TAG, "PREVIEW_GEOMETRY_FAILED type=" + failure.getClass().getSimpleName());
            fail(request, callback, PlaybackFailure.Code.ROUTE_UNAVAILABLE,
                    "자동 이동에 사용할 도로 좌표가 없어요. 다른 위치에서 시도해 주세요.");
            return;
        }

        cancel();
        Log.i(TAG, "PREVIEW_ROUTE_READY points=" + path.pointCount()
                + " meters=" + Math.round(path.totalMeters()) + " guidance=false");
        callback.onSuccess(path);
    }

    /** WGS84 좌표를 경로 요청에 필요한 KATEC 지점으로 변환한다. */
    private KNPOI createPoi(String name, RoutePoint point) {
        DoublePoint coordinate = KNSDK.INSTANCE.convertWGS84ToKATEC(point.longitude(), point.latitude());
        return new KNPOI(name, new IntPoint((int) Math.round(coordinate.getX()),
                (int) Math.round(coordinate.getY())));
    }

    /** 실패도 한 번만 전달하고 요청 제한 시간과 늦은 결과를 정리한다. */
    private void fail(long request, Callback callback, PlaybackFailure.Code code, String message) {
        if (!isCurrent(request)) {
            return;
        }
        cancel();
        Log.w(TAG, "PREVIEW_ROUTE_FAILURE code=" + code);
        callback.onFailure(new PlaybackFailure(code, message));
    }

    /** 진행 중인 요청과 같은 결과만 받아들인다. */
    private boolean isCurrent(long request) {
        return !closed && generation == request;
    }

    /** 네트워크 결과 전달을 무효화하고 이 조회의 제한 시간 작업만 제거한다. */
    @Override
    public void cancel() {
        generation++;
        if (timeout != null) {
            handler.removeCallbacks(timeout);
            timeout = null;
        }
    }

    /** 종료 뒤 네트워크 응답과 제한 시간 콜백이 화면에 전달되지 않게 한다. */
    @Override
    public void close() {
        closed = true;
        cancel();
    }
}
