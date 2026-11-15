package com.example.pleosstarter.stores;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.URL;
import java.util.Collections;

/** REST 키를 보내지 않고 고정 100m 중계 응답을 기존 카카오 장소 계약으로 변환한다. */
final class PlacesProxyStoreClient implements StoreSearchClient {
    private final PlacesProxyConfiguration configuration;
    private final StoreHttpTransport transport;
    private final KakaoLocalStoreClient responseParser;

    /** 공개 주소 설정과 실제 또는 시험 전송 경계를 주입한다. */
    PlacesProxyStoreClient(PlacesProxyConfiguration configuration, StoreHttpTransport transport) {
        this.configuration = configuration;
        this.transport = transport;
        this.responseParser = new KakaoLocalStoreClient(transport);
    }

    /** 분류 없는 기존 호출은 음식점 100m 조회로 연결한다. */
    @Override
    public StoreSearchResult search(double latitude, double longitude, String unusedKey)
            throws IOException, StoreSearchException {
        return search(new KakaoStoreQuery(latitude, longitude, KakaoStoreCategory.RESTAURANT,
                KakaoStoreQuery.DEFAULT_RADIUS_METERS), unusedKey);
    }

    /** 설정·좌표·반경 검증 후 인증 헤더 없이 한 번만 호출하며 직접 API로 우회하지 않는다. */
    @Override
    public StoreSearchResult search(KakaoStoreQuery query, String unusedKey)
            throws IOException, StoreSearchException {
        if (query == null || query.getCategory() == null
                || query.getRadiusMeters() != KakaoStoreQuery.DEFAULT_RADIUS_METERS) {
            throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_QUERY,
                    "주변 장소는 선택한 분류의 100m 범위로 조회해 주세요.", 0));
        }
        if (!Double.isFinite(query.getLatitude()) || !Double.isFinite(query.getLongitude())
                || query.getLatitude() < -90 || query.getLatitude() > 90
                || query.getLongitude() < -180 || query.getLongitude() > 180) {
            throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_COORDINATE,
                    "지도에서 조회할 위치를 다시 선택해 주세요.", 0));
        }

        requireNotCancelled();
        URL endpoint = configuration.baseUri().resolve("v1/places?category="
                + query.getCategory().getApiCode() + "&latitude=" + query.getLatitude()
                + "&longitude=" + query.getLongitude()).toURL();
        StoreHttpResponse response = transport.get(endpoint, "");
        requireNotCancelled();
        requireSuccess(response);

        StoreSearchResult result = responseParser.parse(response, query);
        return new KakaoStoreResultMerger().merge(query, Collections.singletonList(result));
    }

    /** 서버 오류 본문은 해석하거나 노출하지 않고 상태별 안전한 실패로 변환한다. */
    private void requireSuccess(StoreHttpResponse response) throws StoreSearchException {
        StoreSearchError code;
        String message;
        if (response.status == 400) {
            code = StoreSearchError.INVALID_QUERY;
            message = "장소 검색 조건을 확인하고 다시 시도해 주세요.";
        } else if (response.status == 404 || response.status == 405) {
            code = StoreSearchError.INVALID_CONFIGURATION;
            message = "주변 장소 서버 설정을 확인해 주세요.";
        } else if (response.status == 504) {
            code = StoreSearchError.TIMEOUT;
            message = "매장 정보 응답이 늦어지고 있어요. 다시 시도해 주세요.";
        } else {
            StoreResponseValidation.requireSuccess(response);
            return;
        }
        throw new StoreSearchException(new StoreSearchFailure(code, message, response.status));
    }

    /** 취소된 화면에서 추가 전송하거나 결과를 계속 처리하지 않는다. */
    private void requireNotCancelled() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("CANCELLED");
        }
    }
}
