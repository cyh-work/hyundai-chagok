package com.example.pleosstarter.kakao;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.kakaomobility.knsdk.KNSDK;
import com.kakaomobility.knsdk.KNSearchReqType;
import com.kakaomobility.knsdk.common.util.DoublePoint;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import kotlin.Unit;

/** 인증된 카카오 SDK로 장소를 조회하고 취소·시간 초과·늦은 응답을 관리한다. */
public final class KakaoPlaceSearchService implements PlaceSearchService {
    private static final String TAG = "KakaoNavigationTest";
    private static final int FIRST_PAGE = 1;
    private static final int MAX_QUERY_LENGTH = 80;
    private static final long REQUEST_TIMEOUT_MS = 12000;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final KakaoPlaceMapper mapper = new KakaoPlaceMapper();
    private int searchGeneration;
    private int detailGeneration;
    private Runnable searchTimeout;
    private Runnable detailTimeout;
    private boolean closed;

    /** 빈 검색어·과도한 입력을 거부하고 지도 중심 부근을 우선 검색한다. */
    @Override
    public void search(String query, double latitude, double longitude, PlaceSearchCallback callback) {
        cancelSearch();
        if (closed) {
            return;
        }
        String keyword = query == null ? "" : query.trim();
        if (keyword.isEmpty() || keyword.length() > MAX_QUERY_LENGTH) {
            callback.onFailure(new KakaoTestFailure(KakaoTestError.SEARCH_INVALID_QUERY,
                    "장소 이름을 1~80자로 입력해 주세요."));
            return;
        }
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            callback.onFailure(new KakaoTestFailure(KakaoTestError.SEARCH_REQUEST_FAILED,
                    "지도 위치를 확인한 뒤 다시 검색해 주세요."));
            return;
        }

        int generation = searchGeneration;
        searchTimeout = () -> failSearch(generation, callback, KakaoTestError.PLACE_REQUEST_TIMEOUT,
                "검색 응답이 늦어지고 있습니다. 다시 검색해 주세요.");
        mainHandler.postDelayed(searchTimeout, REQUEST_TIMEOUT_MS);
        try {
            DoublePoint center = KNSDK.INSTANCE.convertWGS84ToKATEC(longitude, latitude);
            Log.i(TAG, "PLACE_SEARCH_REQUEST generation=" + generation);
            Unit accepted = KNSDK.INSTANCE.reqSearch(keyword, FIRST_PAGE, MAX_RESULTS,
                    KNSearchReqType.KNSearchReqType_3, (int) Math.round(center.getX()),
                    (int) Math.round(center.getY()), (error, result) -> {
                        mainHandler.post(() -> {
                            if (!isCurrentSearch(generation)) {
                                return;
                            }
                            if (error != null || result == null) {
                                KakaoSdkErrorReporter.report("PLACE_SEARCH_FAILED", error);
                                failSearch(generation, callback, KakaoTestError.SEARCH_REQUEST_FAILED,
                                        "장소를 검색하지 못했습니다. 연결 상태를 확인하고 다시 시도해 주세요.");
                                return;
                            }
                            List<PlaceDetails> places;
                            try {
                                places = mapper.fromSearch(result, MAX_RESULTS);
                            } catch (RuntimeException mappingFailure) {
                                failSearch(generation, callback, KakaoTestError.SEARCH_REQUEST_FAILED,
                                        "검색 결과를 읽지 못했습니다. 다시 시도해 주세요.");
                                return;
                            }
                            cancelSearch();
                            Log.i(TAG, "PLACE_SEARCH_RECEIVED count=" + places.size());
                            callback.onSuccess(places);
                        });
                        return Unit.INSTANCE;
                    });
            if (accepted == null) {
                failSearch(generation, callback, KakaoTestError.SESSION_NOT_READY,
                        "지도가 준비된 뒤 다시 검색해 주세요.");
            }
        } catch (RuntimeException failure) {
            failSearch(generation, callback, KakaoTestError.SEARCH_REQUEST_FAILED,
                    "장소 검색을 시작하지 못했습니다. 다시 시도해 주세요.");
        }
    }

    /** 지도 또는 검색 결과의 실제 ID로만 상세 정보를 요청한다. */
    @Override
    public void loadDetails(KakaoPlaceSelection selection, PlaceDetailsCallback callback) {
        cancelDetails();
        if (closed) {
            return;
        }
        if (selection == null || selection.getLookupId().isEmpty()
                || "0".equals(selection.getLookupId())) {
            callback.onFailure(new KakaoTestFailure(KakaoTestError.PLACE_DETAIL_UNAVAILABLE,
                    "이 장소에는 추가 상세정보가 없습니다."));
            return;
        }

        int generation = detailGeneration;
        detailTimeout = () -> failDetails(generation, callback, KakaoTestError.PLACE_REQUEST_TIMEOUT,
                "상세정보 응답이 늦어지고 있습니다. 잠시 후 다시 선택해 주세요.");
        mainHandler.postDelayed(detailTimeout, REQUEST_TIMEOUT_MS);
        try {
            Unit accepted = KNSDK.INSTANCE.reqPoiDetail(
                    Collections.singletonList(selection.getLookupId()), (error, result) -> {
                        mainHandler.post(() -> {
                            if (!isCurrentDetail(generation)) {
                                return;
                            }
                            if (error != null || result == null) {
                                KakaoSdkErrorReporter.report("PLACE_DETAIL_FAILED", error);
                                failDetails(generation, callback, KakaoTestError.PLACE_DETAIL_REQUEST_FAILED,
                                        "상세정보를 불러오지 못했습니다. 장소를 다시 선택해 주세요.");
                                return;
                            }
                            Optional<PlaceDetails> details;
                            try {
                                details = mapper.fromDetail(result, selection);
                            } catch (RuntimeException mappingFailure) {
                                failDetails(generation, callback, KakaoTestError.PLACE_DETAIL_REQUEST_FAILED,
                                        "상세정보를 읽지 못했습니다. 장소를 다시 선택해 주세요.");
                                return;
                            }
                            if (!details.isPresent()) {
                                failDetails(generation, callback, KakaoTestError.PLACE_DETAIL_UNAVAILABLE,
                                        "이 장소에는 추가 상세정보가 없습니다.");
                                return;
                            }
                            cancelDetails();
                            Log.i(TAG, "PLACE_DETAIL_RECEIVED id=" + selection.getPoiId()
                                    + " address=" + !details.get().getAddress().isEmpty()
                                    + " phone=" + !details.get().getPhone().isEmpty());
                            callback.onSuccess(details.get());
                        });
                        return Unit.INSTANCE;
                    });
            if (accepted == null) {
                failDetails(generation, callback, KakaoTestError.SESSION_NOT_READY,
                        "지도가 준비된 뒤 장소를 다시 선택해 주세요.");
            }
        } catch (RuntimeException failure) {
            failDetails(generation, callback, KakaoTestError.PLACE_DETAIL_REQUEST_FAILED,
                    "상세정보 조회를 시작하지 못했습니다. 다시 시도해 주세요.");
        }
    }

    /** 검색 세대를 바꾸고 제한 시간 작업을 제거한다. 네트워크 자체 취소는 SDK에 위임한다. */
    @Override
    public void cancelSearch() {
        searchGeneration++;
        if (searchTimeout != null) {
            mainHandler.removeCallbacks(searchTimeout);
            searchTimeout = null;
        }
    }

    /** 상세 응답을 무효화해 이전 장소가 새 모달을 덮어쓰지 않게 한다. */
    @Override
    public void cancelDetails() {
        detailGeneration++;
        if (detailTimeout != null) {
            mainHandler.removeCallbacks(detailTimeout);
            detailTimeout = null;
        }
    }

    /** 화면 종료 뒤에는 검색과 상세 콜백을 더 이상 전달하지 않는다. */
    @Override
    public void close() {
        closed = true;
        cancelSearch();
        cancelDetails();
    }

    /** 현재 검색에 대응하는 응답인지 검사한다. */
    private boolean isCurrentSearch(int generation) {
        return !closed && generation == searchGeneration;
    }

    /** 현재 상세 선택에 대응하는 응답인지 검사한다. */
    private boolean isCurrentDetail(int generation) {
        return !closed && generation == detailGeneration;
    }

    /** 검색 실패는 한 번만 전달하며 지도·안내 상태를 변경하지 않는다. */
    private void failSearch(int generation, PlaceSearchCallback callback,
                            KakaoTestError code, String message) {
        if (isCurrentSearch(generation)) {
            cancelSearch();
            Log.w(TAG, "PLACE_SEARCH_FAILURE code=" + code.name());
            callback.onFailure(new KakaoTestFailure(code, message));
        }
    }

    /** 상세 실패는 기본 장소 정보를 유지할 수 있는 별도 상태로 전달한다. */
    private void failDetails(int generation, PlaceDetailsCallback callback,
                             KakaoTestError code, String message) {
        if (isCurrentDetail(generation)) {
            cancelDetails();
            Log.w(TAG, "PLACE_DETAIL_FAILURE code=" + code.name());
            callback.onFailure(new KakaoTestFailure(code, message));
        }
    }
}
