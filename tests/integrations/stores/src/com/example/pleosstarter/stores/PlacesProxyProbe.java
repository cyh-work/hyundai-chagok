package com.example.pleosstarter.stores;

import java.io.InterruptedIOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 실제 외부 호출 없이 제출 중계의 URL·무인증·실패·누락 설정 계약을 검증한다. */
public final class PlacesProxyProbe {
    private static final String BASE = "https://chagok-test.example.workers.dev";
    private static final String EMPTY = "{\"meta\":{\"total_count\":0,\"pageable_count\":0,\"is_end\":true},\"documents\":[]}";
    private static int assertions;

    /** 정상·실패와 서비스 조립 경계를 함께 실행한다. */
    public static void main(String[] arguments) throws Exception {
        successAndAuthentication();
        basePathPreservation();
        configurationAndInput();
        remoteFailures();
        missingConfigurationService();
        cancellation();
        System.out.println("PASS places-proxy assertions=" + assertions);
    }

    /** 네 분류 모두 정확한 경로와 좌표만 보내며 전달된 키도 헤더에 넣지 않는다. */
    private static void successAndAuthentication() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        for (KakaoStoreCategory category : KakaoStoreCategory.values()) {
            PlacesProxyConfiguration configuration = new PlacesProxyConfiguration(BASE + "/");
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(configuration, (endpoint, authorization) -> {
                calls.incrementAndGet();
                check(endpoint.getProtocol().equals("https"), "HTTPS only");
                check(endpoint.getHost().equals("chagok-test.example.workers.dev"), "configured host");
                check(endpoint.getPath().equals("/v1/places"), "fixed proxy route");
                check(endpoint.getQuery().equals("category=" + category.getApiCode()
                        + "&latitude=37.4&longitude=127.1"), "public query contract");
                check(authorization.isEmpty(), "no authorization header even with supplied key");
                return new StoreHttpResponse(200, EMPTY);
            });
            check(configuration.readKey(StoreProvider.KAKAO_LOCAL).isEmpty(), "proxy does not need REST key");
            StoreSearchResult result = client.search(query(category, 100), "MUST_NOT_SEND");
            check(result.getStores().isEmpty(), "empty result preserved");
            check(result.getRadiusMeters() == 100, "fixed radius in result");
        }
        check(calls.get() == 4, "one request per supported category");
    }

    /** 잘못된 서버 주소·좌표·반경을 네트워크 전송 전에 막는다. */
    private static void configurationAndInput() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StoreHttpTransport transport = (endpoint, authorization) -> {
            calls.incrementAndGet();
            return new StoreHttpResponse(200, EMPTY);
        };
        for (String address : new String[]{"http://test.workers.dev", "https://user:secret@test.workers.dev",
                "https://test.workers.dev/../path", "https://test.workers.dev?key=private", "https://test.workers.dev#fragment",
                "https://test.workers.dev:8443", "https://localhost", "https://127.0.0.1", "https://x.localhost", "invalid"}) {
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(new PlacesProxyConfiguration(address), transport);
            expectFailure(() -> client.search(query(KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.INVALID_CONFIGURATION);
        }
        for (String address : new String[]{"", "  ", null}) {
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(new PlacesProxyConfiguration(address), transport);
            expectFailure(() -> client.search(query(KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.MISSING_CONFIGURATION);
        }
        PlacesProxyConfiguration configuration = new PlacesProxyConfiguration(BASE);
        PlacesProxyStoreClient client = new PlacesProxyStoreClient(configuration, transport);
        expectFailure(() -> configuration.readKey(StoreProvider.PUBLIC_DATA), StoreSearchError.INVALID_QUERY);
        expectFailure(() -> client.search(query(KakaoStoreCategory.CAFE, 500), ""), StoreSearchError.INVALID_QUERY);
        expectFailure(() -> client.search(null, ""), StoreSearchError.INVALID_QUERY);
        expectFailure(() -> client.search(new KakaoStoreQuery(Double.NaN, 127, KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.INVALID_COORDINATE);
        expectFailure(() -> client.search(new KakaoStoreQuery(91, 127, KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.INVALID_COORDINATE);
        expectFailure(() -> client.search(new KakaoStoreQuery(37, Double.POSITIVE_INFINITY, KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.INVALID_COORDINATE);
        check(calls.get() == 0, "invalid settings never send or fallback");
    }

    /** 끝 슬래시 유무와 무관하게 공개 호스트 아래의 배포 경로를 보존한다. */
    private static void basePathPreservation() throws Exception {
        String[][] valid = {
                {"https://places.example.com/chagok", "/chagok/v1/places"},
                {"https://places.example.com/chagok/", "/chagok/v1/places"},
                {"https://places.example.com", "/v1/places"},
                {"https://places.example.com/", "/v1/places"},
                {"https://places.example.com/apps/chagok_v1-2", "/apps/chagok_v1-2/v1/places"}
        };
        for (String[] example : valid) {
            PlacesProxyConfiguration configuration = new PlacesProxyConfiguration(example[0]);
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(configuration, (endpoint, authorization) -> {
                check(endpoint.getHost().equals("places.example.com"), "base host preserved");
                check(endpoint.getPath().equals(example[1]), "base path preserved exactly once");
                check(endpoint.getQuery().equals("category=CE7&latitude=37.4&longitude=127.1"), "query appended after base path");
                check(authorization.isEmpty(), "base path never adds authentication");
                return new StoreHttpResponse(200, EMPTY);
            });
            check(configuration.baseUri().getPath().endsWith("/"), "canonical trailing slash");
            client.search(query(KakaoStoreCategory.CAFE, 100), "");
        }
        AtomicInteger calls = new AtomicInteger();
        for (String path : new String[]{"/chagok/../other", "/chagok/./", "/chagok/%2e%2e",
                "/chagok/%252e%252e", "/%63hagok", "/chagok%2fv1", "/chagok//", "//chagok",
                "/chagok\\other", "/chagok?target=/other", "/chagok#other", "/chagok;param=1"}) {
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(new PlacesProxyConfiguration("https://places.example.com" + path),
                    (endpoint, authorization) -> {
                        calls.incrementAndGet();
                        return new StoreHttpResponse(200, EMPTY);
                    });
            expectFailure(() -> client.search(query(KakaoStoreCategory.CAFE, 100), ""), StoreSearchError.INVALID_CONFIGURATION);
        }
        check(calls.get() == 0, "unsafe base paths never reach transport");
    }

    /** HTTP 오류 본문을 숨기고 시간 초과·한도·서버 오류를 구분한다. */
    private static void remoteFailures() throws Exception {
        int[] statuses = {400, 404, 405, 429, 502, 503, 504, 302};
        StoreSearchError[] errors = {StoreSearchError.INVALID_QUERY, StoreSearchError.INVALID_CONFIGURATION,
                StoreSearchError.INVALID_CONFIGURATION, StoreSearchError.QUOTA_EXCEEDED,
                StoreSearchError.REMOTE_FAILURE, StoreSearchError.REMOTE_FAILURE,
                StoreSearchError.TIMEOUT, StoreSearchError.REMOTE_FAILURE};
        for (int index = 0; index < statuses.length; index++) {
            int status = statuses[index];
            PlacesProxyStoreClient client = new PlacesProxyStoreClient(new PlacesProxyConfiguration(BASE),
                    (endpoint, authorization) -> new StoreHttpResponse(status, "PRIVATE_SERVER_BODY"));
            expectFailure(() -> client.search(query(KakaoStoreCategory.RESTAURANT, 100), ""), errors[index]);
        }
        PlacesProxyStoreClient malformed = new PlacesProxyStoreClient(new PlacesProxyConfiguration(BASE),
                (endpoint, authorization) -> new StoreHttpResponse(200, "PRIVATE_SERVER_BODY"));
        expectFailure(() -> malformed.search(query(KakaoStoreCategory.RESTAURANT, 100), ""), StoreSearchError.INVALID_RESPONSE);
    }

    /** 주소가 없는 제출 서비스는 생성 가능하며 조회만 명확하게 실패한다. */
    private static void missingConfigurationService() throws Exception {
        PlacesProxyConfiguration configuration = new PlacesProxyConfiguration("");
        AtomicInteger calls = new AtomicInteger();
        PlacesProxyStoreClient client = new PlacesProxyStoreClient(configuration, (endpoint, authorization) -> {
            calls.incrementAndGet();
            return new StoreHttpResponse(200, EMPTY);
        });
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<StoreSearchFailure> failure = new AtomicReference<>();
        HttpStoreSearchService service = new HttpStoreSearchService(configuration, client, client,
                Executors.newSingleThreadExecutor(), Runnable::run);
        try {
            service.search(query(KakaoStoreCategory.CAFE, 100), new StoreSearchCallback() {
                /** 성공으로 잘못 전달되는 경우도 대기를 끝내고 검증에서 실패시킨다. */
                @Override
                public void onSuccess(StoreSearchResult result) {
                    completed.countDown();
                }

                /** 안전한 설정 오류를 저장해 UI 전달 계약을 검증한다. */
                @Override
                public void onFailure(StoreSearchFailure result) {
                    failure.set(result);
                    completed.countDown();
                }
            });
            check(completed.await(3, TimeUnit.SECONDS), "configuration failure delivered");
            check(failure.get() != null && failure.get().getCode() == StoreSearchError.MISSING_CONFIGURATION,
                    "missing endpoint is not missing REST key");
            check(calls.get() == 0, "no direct fallback without endpoint");
        } finally {
            service.close();
        }
    }

    /** 취소된 작업이 외부 요청을 새로 만들지 않는지 확인한다. */
    private static void cancellation() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        PlacesProxyStoreClient client = new PlacesProxyStoreClient(new PlacesProxyConfiguration(BASE),
                (endpoint, authorization) -> {
                    calls.incrementAndGet();
                    return new StoreHttpResponse(200, EMPTY);
                });
        Thread.currentThread().interrupt();
        try {
            client.search(query(KakaoStoreCategory.CAFE, 100), "");
            throw new AssertionError("cancelled request succeeded");
        } catch (InterruptedIOException expected) {
            check(calls.get() == 0, "cancelled before transport");
        } finally {
            Thread.interrupted();
        }
    }

    /** 테스트마다 같은 좌표와 명시한 분류·반경을 사용한다. */
    private static KakaoStoreQuery query(KakaoStoreCategory category, int radius) {
        return new KakaoStoreQuery(37.4, 127.1, category, radius);
    }

    /** 내부 원문을 노출하지 않는 정해진 오류 종류만 허용한다. */
    private static void expectFailure(CheckedOperation operation, StoreSearchError expected) throws Exception {
        try {
            operation.run();
            throw new AssertionError("expected failure: " + expected);
        } catch (StoreSearchException failure) {
            check(failure.getFailure().getCode() == expected, "expected error code");
            check(!failure.getFailure().getMessage().contains("PRIVATE")
                    && !failure.getFailure().getMessage().contains("secret"), "no sensitive error body");
        }
    }

    /** 실패 위치를 남기고 통과 단언 수를 센다. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }

    /** 실패 검증에서 검사 예외를 전달한다. */
    private interface CheckedOperation {
        /** 검사할 한 가지 외부 요청 또는 설정 조회를 실행한다. */
        void run() throws Exception;
    }
}
