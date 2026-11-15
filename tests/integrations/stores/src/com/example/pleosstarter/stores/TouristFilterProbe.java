package com.example.pleosstarter.stores;

import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** 선택한 장소 필터만 조회하며 기존 응답·오류 계약을 보존하는지 검사한다. */
public final class TouristFilterProbe {
    private static int assertions;

    /** 실제 제품 클라이언트에 전송 대역을 주입하여 요청과 반환값을 확인한다. */
    public static void main(String[] arguments) throws Exception {
        testEveryFilterAndRadius();
        testEmptyInvalidAndFailure();
        testCancellation();
        System.out.println("PASS assertions=" + assertions + " scope=offline-real-client-fake-transport");
    }

    /** 네 분류 각각의 100m·500m 요청이 한 번이며 응답이 섞이지 않는지 확인한다. */
    private static void testEveryFilterAndRadius() throws Exception {
        String[] expectedCodes = {"FD6", "CE7", "CS2", "AT4"};
        KakaoStoreCategory[] filters = KakaoStoreCategory.values();
        check(filters.length == expectedCodes.length, "four explicit filters without all");
        for (int index = 0; index < filters.length; index++) {
            KakaoStoreCategory filter = filters[index];
            for (int radius : new int[]{100, 500}) {
                List<String> requests = new ArrayList<>();
                KakaoLocalStoreClient client = new KakaoLocalStoreClient((url, authorization) -> {
                    requests.add(url.getQuery());
                    return response(3, true, place("far", 40), place("near", 10), place("far", 30));
                });
                StoreSearchResult result = client.search(new KakaoStoreQuery(37.4, 127.1, filter, radius), "TEST");
                check(requests.size() == 1, "one selected-filter request");
                String request = requests.get(0);
                check(request.contains("category_group_code=" + expectedCodes[index] + "&"), "expected category only");
                check(request.contains("radius=" + radius + "&page=1&size=15&sort=distance"), "bounded nearby scope");
                check(result.getCategoryCounts().size() == 1 && result.getCategoryCounts().get(0).getCategory() == filter,
                        "single selected-category provenance");
                check(!result.isCountSum() && result.getTotalCount() == 3, "source count not category sum");
                check(result.getStores().size() == 2 && result.getStores().get(0).getId().equals("near"),
                        "deduplication and nearest-first preserved");
                check(result.getStores().get(1).getDistanceMeters() == 30, "nearest duplicate retained");
            }
        }
        check(filters[3].getDisplayName().equals("관광지") && filters[3].getQueryLabel().equals("주변 관광지"),
                "attraction label");
    }

    /** 빈 결과와 잘못된 입력·권한 실패·응답 손상을 성공으로 바꾸지 않는지 확인한다. */
    private static void testEmptyInvalidAndFailure() throws Exception {
        List<String> requests = new ArrayList<>();
        KakaoLocalStoreClient empty = new KakaoLocalStoreClient((url, authorization) -> {
            requests.add(url.getQuery());
            return response(0, true);
        });
        KakaoStoreQuery tourist = new KakaoStoreQuery(37.4, 127.1, KakaoStoreCategory.TOURIST_ATTRACTION, 100);
        check(empty.search(tourist, "TEST").getStores().isEmpty(), "empty attraction result");
        expectFailure(empty, new KakaoStoreQuery(37.4, 127.1, KakaoStoreCategory.TOURIST_ATTRACTION, 200),
                StoreSearchError.INVALID_QUERY);
        expectFailure(empty, new KakaoStoreQuery(37.4, 127.1, null, 100), StoreSearchError.INVALID_QUERY);
        check(requests.size() == 1, "invalid query makes no request");

        KakaoLocalStoreClient denied = new KakaoLocalStoreClient((url, authorization) -> new StoreHttpResponse(403, "PRIVATE_BODY"));
        expectFailure(denied, tourist, StoreSearchError.AUTHORIZATION_DENIED);
        KakaoLocalStoreClient malformed = new KakaoLocalStoreClient((url, authorization) -> new StoreHttpResponse(200, "not-json"));
        expectFailure(malformed, tourist, StoreSearchError.INVALID_RESPONSE);
    }

    /** 이미 취소된 작업은 전송하지 않고 응답 직후 취소도 결과로 반환하지 않는다. */
    private static void testCancellation() throws Exception {
        List<String> requests = new ArrayList<>();
        KakaoLocalStoreClient client = new KakaoLocalStoreClient((url, authorization) -> {
            requests.add(url.getQuery());
            Thread.currentThread().interrupt();
            return response(0, true);
        });
        KakaoStoreQuery query = new KakaoStoreQuery(37.4, 127.1, KakaoStoreCategory.TOURIST_ATTRACTION, 500);
        for (boolean interruptedBefore : new boolean[]{true, false}) {
            if (interruptedBefore) {
                Thread.currentThread().interrupt();
            }
            try {
                client.search(query, "TEST");
                throw new AssertionError("cancelled request returned success");
            } catch (InterruptedIOException expected) {
                check(requests.size() == (interruptedBefore ? 0 : 1), "cancellation boundary");
            } finally {
                Thread.interrupted();
            }
        }
    }

    /** 오류 코드가 유지되고 원문 실패 내용은 노출되지 않는지 확인한다. */
    private static void expectFailure(KakaoLocalStoreClient client, KakaoStoreQuery query, StoreSearchError expected) throws Exception {
        try {
            client.search(query, "TEST");
            throw new AssertionError("expected failure");
        } catch (StoreSearchException failure) {
            check(failure.getFailure().getCode() == expected, "error classification");
            check(!failure.getFailure().getMessage().contains("PRIVATE_BODY"), "private response hidden");
        }
    }

    /** 장소 ID와 거리만 다른 독립적인 응답 행을 구성한다. */
    private static JSONObject place(String id, int distance) {
        return new JSONObject().put("id", id).put("place_name", id).put("distance", String.valueOf(distance));
    }

    /** 실제 API 구조와 같은 최소 메타데이터·장소 배열을 반환한다. */
    private static StoreHttpResponse response(int total, boolean end, JSONObject... places) {
        JSONArray documents = new JSONArray();
        for (JSONObject place : places) {
            documents.put(place);
        }
        JSONObject meta = new JSONObject().put("total_count", total).put("pageable_count", total).put("is_end", end);
        return new StoreHttpResponse(200, new JSONObject().put("meta", meta).put("documents", documents).toString());
    }

    /** 실패 조건을 즉시 중단시키고 성공 단언 수를 기록한다. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }
}
