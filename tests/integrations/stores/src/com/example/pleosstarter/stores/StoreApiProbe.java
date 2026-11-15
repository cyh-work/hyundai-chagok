package com.example.pleosstarter.stores;

import java.io.File;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 실제 제품 파서와 요청·취소 계약을 외부 호출 없이 검증한다. */
public final class StoreApiProbe {
    private static int assertions;
    private static final String KAKAO = "{\"meta\":{\"total_count\":21,\"pageable_count\":21,\"is_end\":false},\"documents\":[{\"id\":\"official-sample-id\",\"place_name\":\"검증용 음식점\",\"category_name\":\"음식점 > 한식\",\"address_name\":\"시험 지번주소\",\"road_address_name\":\"시험 도로주소\",\"phone\":null,\"x\":\"127.1\",\"y\":\"37.4\",\"distance\":\"18\",\"place_url\":\"https://place.map.kakao.com/1\"}]}";
    private static final String PUBLIC = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":31,\"stdrYm\":\"202609\",\"items\":[{\"bizesId\":\"sample-id\",\"bizesNm\":\"검증상점\",\"brchNm\":\"분점\",\"indsSclsNm\":\"슈퍼마켓\",\"lnoAdr\":\"시험 지번\",\"rdnmAdr\":\"시험 도로\",\"bldMngNo\":\"BUILDING-SAMPLE\",\"flrNo\":\"2\",\"hoNo\":\"201\",\"lon\":\"127.1\",\"lat\":\"37.4\"}]}}";

    /** 계약 검증 시나리오를 순서대로 실행하고 단언 수만 출력한다. */
    public static void main(String[] arguments) throws Exception {
        testKakaoMapping();
        testPublicMapping();
        testRequestsAndErrors();
        testConfiguration();
        testService();
        System.out.println("PASS assertions=" + assertions + " scope=offline-product-clients-service");
    }

    /** 실제 응답 필드·페이지 구분·누락 값과 읽기 전용 DTO를 확인한다. */
    private static void testKakaoMapping() throws Exception {
        KakaoLocalStoreClient client = new KakaoLocalStoreClient((url, auth) -> new StoreHttpResponse(200, KAKAO));
        StoreSearchResult result = client.search(37.4,127.1,"not-a-real-key");
        check(result.getProvider() == StoreProvider.KAKAO_LOCAL, "provider");
        check(result.getStores().size() == 1 && result.getTotalCount() == 21 && result.hasMore(), "paging");
        check(result.getPageableCount() == 21 && result.getRadiusMeters() == 500, "scope");
        StoreSummary store = result.getStores().get(0);
        check(store.getPhone().isEmpty() && store.getFloor().isEmpty(), "missing fields");
        check(store.getName().equals("검증용 음식점") && store.getDistanceMeters() == 18, "name distance");
        check(store.getLatitude() == 37.4 && store.getLongitude() == 127.1, "coordinate order");
        check(result.getReferenceMonth().isEmpty() && result.getRetrievedAtMillis() > 0, "time semantics");
        try {
            result.getStores().clear();
            throw new AssertionError("mutable list");
        } catch (UnsupportedOperationException expected) {
            check(true, "immutable list");
        }
        StoreSearchResult empty = kakaoResponse(
                "{\"meta\":{\"total_count\":0,\"pageable_count\":0,\"is_end\":true},\"documents\":[]}");
        check(empty.getStores().isEmpty() && !empty.hasMore(), "empty success");
        expectError(() -> kakaoResponse("{}"), StoreSearchError.INVALID_RESPONSE, 200);
        expectError(() -> kakaoResponse(KAKAO.replace("\"id\":\"official-sample-id\",", "")),
                StoreSearchError.INVALID_RESPONSE, 200);
        expectError(() -> kakaoResponse(KAKAO.replace("\"pageable_count\":21", "\"pageable_count\":22")),
                StoreSearchError.INVALID_RESPONSE, 200);
    }

    /** 공공 기준년월·층호·누락 좌표와 게이트웨이 오류를 확인한다. */
    private static void testPublicMapping() throws Exception {
        PublicDataStoreClient client = new PublicDataStoreClient((url,auth) -> new StoreHttpResponse(200,PUBLIC));
        StoreSearchResult result = client.search(37.4,127.1,"not-a-real-key");
        StoreSummary store = result.getStores().get(0);
        check(result.getProvider() == StoreProvider.PUBLIC_DATA && result.getTotalCount() == 31 && result.hasMore(),"public paging");
        check(store.getName().equals("검증상점 분점") && store.getFloor().equals("2") && store.getUnit().equals("201"),"public fields");
        check(store.getBuildingManagementNumber().equals("BUILDING-SAMPLE") && store.getPhone().isEmpty(),"no invented phone");
        check(store.getReferenceMonth().equals("202609") && result.getReferenceMonth().equals("202609"),"source month");
        check(store.getDistanceMeters() < 0.01,"computed center distance");
        StoreSearchResult missing = publicResponse(PUBLIC.replace("\"37.4\"", "null"));
        check(missing.getStores().size() == 1 && missing.getStores().get(0).getLatitude() == null
                && missing.getStores().get(0).getDistanceMeters() == null, "preserve missing coordinates");
        StoreSearchResult empty = publicResponse(
                "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"totalCount\":0,\"items\":\"\"}}");
        check(empty.getStores().isEmpty() && !empty.hasMore(), "public empty");
        expectError(() -> publicResponse(PUBLIC.replace("\"00\"", "\"30\"")), StoreSearchError.AUTHORIZATION_DENIED, 200);
        expectError(() -> publicResponse(PUBLIC.replace("\"00\"", "\"22\"")), StoreSearchError.QUOTA_EXCEEDED, 200);
        expectError(() -> publicResponse("<returnAuthMsg>SERVICE_KEY_IS_NOT_REGISTERED_ERROR</returnAuthMsg>"),
                StoreSearchError.AUTHORIZATION_DENIED, 200);
        expectError(() -> publicResponse("<html>invalid</html>"), StoreSearchError.INVALID_RESPONSE, 200);
    }

    /** 키를 정확한 경계에 붙이고 HTTP 실패를 성공 목록으로 바꾸지 않는지 확인한다. */
    private static void testRequestsAndErrors() throws Exception {
        new KakaoLocalStoreClient((url,auth) -> {
            check(url.getHost().equals("dapi.kakao.com") && url.getPath().endsWith("category.json"),"kakao endpoint");
            check(url.getQuery().contains("category_group_code=FD6") && url.getQuery().contains("x=127.1&y=37.4"),"kakao params");
            check(!url.toString().contains("dummy-key") && auth.equals("KakaoAK dummy-key"),"header auth only");
            return new StoreHttpResponse(200,KAKAO);
        }).search(37.4,127.1,"dummy-key");
        StoreHttpTransport publicTransport = (url,auth) -> {
            check(url.getPath().equals("/B553077/api/open/sdsc2/storeListInRadius"),"public endpoint");
            check(url.getQuery().contains("ServiceKey=a%2Bb%2Fc%3D") && auth.isEmpty(),"key encoded once");
            check(url.getQuery().contains("radius=500&cx=127.1&cy=37.4") && url.getQuery().contains("numOfRows=30"),"public params");
            return new StoreHttpResponse(200,PUBLIC);
        };
        new PublicDataStoreClient(publicTransport).search(37.4,127.1,"a+b/c=");
        new PublicDataStoreClient(publicTransport).search(37.4,127.1,"a%2Bb%2Fc%3D");
        for (int status : new int[]{401,403,429,500,302}) {
            StoreSearchError expected = StoreSearchError.REMOTE_FAILURE;
            if (status == 401 || status == 403) {
                expected = StoreSearchError.AUTHORIZATION_DENIED;
            } else if (status == 429) {
                expected = StoreSearchError.QUOTA_EXCEEDED;
            }
            expectError(() -> new KakaoLocalStoreClient((url,auth) -> new StoreHttpResponse(status,"sensitive response must not appear"))
                    .search(37.4,127.1,"dummy-key"),expected,status);
        }
    }

    /** 누락·잘못된 설정을 실제 통신 전에 구분한다. */
    private static void testConfiguration() throws Exception {
        File file = File.createTempFile("store-api-probe", ".properties");
        try {
            StoreApiConfiguration configuration = new StoreApiConfiguration(file);
            expectError(() -> configuration.readKey(StoreProvider.KAKAO_LOCAL),StoreSearchError.MISSING_KEY,0);
            Files.writeString(file.toPath(),"kakaoRestApiKey=bad\n");
            expectError(() -> configuration.readKey(StoreProvider.KAKAO_LOCAL),StoreSearchError.INVALID_CONFIGURATION,0);
            Files.writeString(file.toPath(),"kakaoRestApiKey=" + "a".repeat(32) + "\npublicDataServiceKey=TEST-ONLY\n");
            check(configuration.readKey(StoreProvider.KAKAO_LOCAL).length() == 32,"valid native-independent key");
            check(configuration.readKey(StoreProvider.PUBLIC_DATA).equals("TEST-ONLY"),"public key distinct");
            Files.writeString(file.toPath(),"x".repeat(17000));
            expectError(() -> configuration.readKey(StoreProvider.PUBLIC_DATA),StoreSearchError.INVALID_CONFIGURATION,0);
        } finally {
            Files.delete(file.toPath());
        }
    }

    /** 취소·후발응답·좌표 검증·시간 초과·키 갱신을 제품 서비스로 확인한다. */
    private static void testService() throws Exception {
        File file = File.createTempFile("store-api-service", ".properties");
        Files.writeString(file.toPath(),"kakaoRestApiKey=" + "a".repeat(32) + "\npublicDataServiceKey=TEST-ONLY\n");
        ConcurrentLinkedQueue<Runnable> callbacks = new ConcurrentLinkedQueue<>();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        StoreHttpTransport transport = (url, authorization) -> {
            int call = calls.incrementAndGet();
            if (call == 1) {
                firstEntered.countDown();
                awaitIgnoringInterrupts(releaseFirst);
            }
            return new StoreHttpResponse(200, KAKAO.replace(":21", ":" + call));
        };
        StoreSearchClient client = new KakaoLocalStoreClient(transport);
        StoreSearchClient publicClient = new PublicDataStoreClient((url, authorization) -> {
            throw new SocketTimeoutException("private URL");
        });
        HttpStoreSearchService service = new HttpStoreSearchService(new StoreApiConfiguration(file),
                client, publicClient, workers, callbacks::add);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        StoreSearchCallback callback = new StoreSearchCallback() {
            /** 테스트 전달 횟수를 세고 최신 요청인지 확인한다. */
            @Override
            public void onSuccess(StoreSearchResult result) {
                check(result.getTotalCount() == 2,"only new request delivered");
                successes.incrementAndGet();
            }
            /** 안전한 실패만 전달되는지 확인한다. */
            @Override
            public void onFailure(StoreSearchFailure failure) {
                check(!failure.getMessage().contains("private"),"safe service error");
                failures.incrementAndGet();
            }
        };
        try {
            service.search(StoreProvider.KAKAO_LOCAL,37.4,127.1,callback);
            check(firstEntered.await(2,TimeUnit.SECONDS),"first request started");
            service.search(StoreProvider.KAKAO_LOCAL,37.4,127.1,callback);
            awaitQueue(callbacks);
            drain(callbacks);
            releaseFirst.countDown();
            awaitQueue(callbacks);
            drain(callbacks);
            check(successes.get() == 1,"stale result suppressed");
            service.search(StoreProvider.PUBLIC_DATA,37.4,127.1,callback);
            awaitQueue(callbacks);
            drain(callbacks);
            check(failures.get() == 1,"timeout surfaced");
            int countBefore = calls.get();
            service.search(StoreProvider.KAKAO_LOCAL,Double.NaN,127.1,callback);
            drain(callbacks);
            check(calls.get() == countBefore && failures.get() == 2,"invalid coordinate no call");
            Files.writeString(file.toPath(),"");
            service.search(StoreProvider.KAKAO_LOCAL,37.4,127.1,callback);
            awaitQueue(callbacks);
            service.cancel();
            drain(callbacks);
            check(failures.get() == 2,"queued failure cancelled");
            service.search(StoreProvider.KAKAO_LOCAL,37.4,127.1,callback);
            awaitQueue(callbacks);
            drain(callbacks);
            check(failures.get() == 3 && calls.get() == countBefore,"config reloaded missing key");
            service.close();
            service.search(StoreProvider.KAKAO_LOCAL,37.4,127.1,callback);
            drain(callbacks);
            check(failures.get() == 4,"closed service rejected");
        } finally {
            releaseFirst.countDown();
            service.close();
            Files.delete(file.toPath());
        }
    }

    /** HTTP 응답만 주입하고 실제 카카오 공개 검색 계약으로 결과를 확인한다. */
    private static StoreSearchResult kakaoResponse(String body) throws Exception {
        StoreSearchClient client = new KakaoLocalStoreClient((url, authorization) -> new StoreHttpResponse(200, body));
        return client.search(37.4, 127.1, "TEST");
    }

    /** HTTP 응답만 주입하고 실제 공공 데이터 검색 계약으로 결과를 확인한다. */
    private static StoreSearchResult publicResponse(String body) throws Exception {
        StoreSearchClient client = new PublicDataStoreClient((url, authorization) -> new StoreHttpResponse(200, body));
        return client.search(37.4, 127.1, "TEST");
    }

    /** 취소에 늦게 반응하는 외부 호출을 재현한다. */
    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean released = false;
        while (!released) {
            try {
                released = latch.await(2,TimeUnit.SECONDS);
            } catch (InterruptedException expected) {
                // 의도적으로 늦은 응답을 만들어 결과 세대 검사를 검증한다.
            }
        }
    }

    /** 결과 대기 시간을 제한해 교착 시 시험을 실패시킨다. */
    private static void awaitQueue(ConcurrentLinkedQueue<Runnable> queue) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (queue.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        check(!queue.isEmpty(),"callback queued");
    }

    /** 화면 이벤트 루프에 쌓인 콜백을 현재 시험 스레드에서 실행한다. */
    private static void drain(ConcurrentLinkedQueue<Runnable> queue) {
        Runnable callback;
        while ((callback = queue.poll()) != null) {
            callback.run();
        }
    }

    /** 예상한 안전한 실패 종류와 실제 HTTP 경계를 확인한다. */
    private static void expectError(ThrowingAction action, StoreSearchError expected, int status) throws Exception {
        try {
            action.run();
            throw new AssertionError("expected error " + expected);
        } catch (StoreSearchException failure) {
            check(failure.getFailure().getCode() == expected,"error type");
            check(failure.getFailure().getHttpStatus() == status,"http status");
            check(!failure.getMessage().contains("sensitive"),"no raw response");
        }
    }

    /** 실패 가능한 검증 동작을 전달한다. */
    private interface ThrowingAction {
        /** 검증할 제품 동작을 실행한다. */
        void run() throws Exception;
    }

    /** 단언을 세고 첫 실패의 조건명을 보고한다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
