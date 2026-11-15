package com.example.pleosstarter.stores;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** 소상공인 상가정보의 반경 검색을 호출하고 건물 입점 목록과 구분한다. */
final class PublicDataStoreClient implements StoreSearchClient {
    private static final String ENDPOINT =
            "https://apis.data.go.kr/B553077/api/open/sdsc2/storeListInRadius";
    private final StoreHttpTransport transport;

    /** 응답 변환을 네트워크와 분리해 시험할 수 있도록 전송을 주입한다. */
    PublicDataStoreClient(StoreHttpTransport transport) {
        this.transport = transport;
    }

    /** 선택 지점 500m 안의 전체 업종 첫 30건을 요청한다. */
    @Override
    public StoreSearchResult search(double latitude, double longitude, String key)
            throws IOException, StoreSearchException {
        String decodedKey = key;
        if (key.contains("%")) {
            try {
                decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8.name());
            } catch (IllegalArgumentException failure) {
                throw new StoreSearchException(new StoreSearchFailure(
                        StoreSearchError.INVALID_CONFIGURATION, "공공데이터 인증키 형식을 확인해 주세요.", 0));
            }
        }
        String encodedKey = URLEncoder.encode(decodedKey, StandardCharsets.UTF_8.name());
        URL endpoint = new URL(ENDPOINT + "?ServiceKey=" + encodedKey
                + "&type=json&pageNo=1&numOfRows=30&radius=500&cx=" + longitude + "&cy=" + latitude);
        StoreHttpResponse response = transport.get(endpoint, "");
        StoreResponseValidation.requireSuccess(response);
        return parse(response, latitude, longitude);
    }

    /** HTTP 성공과 API 결과코드를 모두 확인한 뒤에만 매장 목록을 반환한다. */
    StoreSearchResult parse(StoreHttpResponse response, double latitude, double longitude)
            throws StoreSearchException {
        rejectGatewayError(response);
        try {
            JSONObject root = new JSONObject(response.body);
            if (root.optJSONObject("response") != null) {
                root = root.getJSONObject("response");
            }
            JSONObject header = root.optJSONObject("header");
            if (header == null) {
                header = root;
            }
            requireResultCode(StoreResponseValidation.text(header, "resultCode"), response.status);

            JSONObject body = root.optJSONObject("body");
            if (body == null) {
                body = root;
            }
            int totalCount = body.getInt("totalCount");
            JSONArray items = items(body, totalCount);
            if (totalCount < 0 || totalCount < items.length() || items.length() > 30) {
                throw StoreResponseValidation.invalidResponse(response.status);
            }
            String referenceMonth = StoreResponseValidation.text(body, "stdrYm");
            if (referenceMonth.isEmpty()) {
                referenceMonth = StoreResponseValidation.text(header, "stdrYm");
            }

            List<StoreSummary> stores = new ArrayList<>();
            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                stores.add(mapStore(item, latitude, longitude, referenceMonth));
            }
            return new StoreSearchResult(StoreProvider.PUBLIC_DATA, stores, totalCount,
                    totalCount, totalCount > items.length(), referenceMonth,
                    System.currentTimeMillis(), latitude, longitude);
        } catch (JSONException failure) {
            throw StoreResponseValidation.invalidResponse(response.status);
        }
    }

    /** 배열과 XML 변환형 item 구조를 읽으며 빈 응답은 총 0건일 때만 허용한다. */
    private JSONArray items(JSONObject body, int totalCount) throws JSONException {
        Object items = body.opt("items");
        if (items instanceof JSONArray) {
            return (JSONArray) items;
        }
        if (items instanceof JSONObject) {
            Object item = ((JSONObject) items).opt("item");
            if (item instanceof JSONArray) {
                return (JSONArray) item;
            }
            if (item instanceof JSONObject) {
                return new JSONArray().put(item);
            }
        }
        if (totalCount == 0 && (items == null || items == JSONObject.NULL || "".equals(items))) {
            return new JSONArray();
        }
        throw new JSONException("INVALID_ITEMS");
    }

    /** 기관에서 제공한 정보만 옮기며 좌표 없는 업소도 목록에 보존한다. */
    private StoreSummary mapStore(JSONObject item, double latitude, double longitude,
                                  String responseReferenceMonth) throws JSONException {
        String id = StoreResponseValidation.text(item, "bizesId");
        String name = StoreResponseValidation.text(item, "bizesNm");
        if (id.isEmpty() || name.isEmpty()) {
            throw new JSONException("MISSING_STORE_ID_OR_NAME");
        }
        String branch = StoreResponseValidation.text(item, "brchNm");
        if (!branch.isEmpty() && !name.endsWith(branch)) {
            name = name + " " + branch;
        }
        String referenceMonth = StoreResponseValidation.text(item, "stdrYm");
        if (referenceMonth.isEmpty()) {
            referenceMonth = responseReferenceMonth;
        }
        Double storeLatitude = StoreResponseValidation.number(item, "lat", -90, 90);
        Double storeLongitude = StoreResponseValidation.number(item, "lon", -180, 180);
        return new StoreSummary(id, name,
                StoreResponseValidation.text(item, "indsSclsNm"),
                StoreResponseValidation.text(item, "lnoAdr"),
                StoreResponseValidation.text(item, "rdnmAdr"), "",
                StoreResponseValidation.text(item, "flrNo"),
                StoreResponseValidation.text(item, "hoNo"),
                StoreResponseValidation.text(item, "bldNm"),
                StoreResponseValidation.text(item, "bldMngNo"), referenceMonth,
                storeLatitude, storeLongitude,
                StoreResponseValidation.distance(latitude, longitude, storeLatitude, storeLongitude), "");
    }

    /** 서비스 결과코드를 HTTP 상태와 별도로 분류한다. */
    private void requireResultCode(String resultCode, int httpStatus) throws StoreSearchException {
        if ("00".equals(resultCode)) {
            return;
        }
        if (resultCode.isEmpty()) {
            throw StoreResponseValidation.invalidResponse(httpStatus);
        }
        StoreSearchError code = StoreSearchError.REMOTE_FAILURE;
        String message = "공공 상가정보에서 요청을 처리하지 못했어요.";
        if ("20".equals(resultCode) || "30".equals(resultCode) || "31".equals(resultCode)
                || "32".equals(resultCode) || "33".equals(resultCode)) {
            code = StoreSearchError.AUTHORIZATION_DENIED;
            message = "공공데이터 인증키와 활용 승인을 확인해 주세요.";
        } else if ("22".equals(resultCode)) {
            code = StoreSearchError.QUOTA_EXCEEDED;
            message = "공공데이터 조회 한도를 초과했어요.";
        }
        throw new StoreSearchException(new StoreSearchFailure(code, message, httpStatus));
    }

    /** 게이트웨이가 JSON 요청에 XML 인증 오류를 반환해도 실패로 처리한다. */
    private void rejectGatewayError(StoreHttpResponse response) throws StoreSearchException {
        if (response.body.contains("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                || response.body.contains("SERVICE_ACCESS_DENIED_ERROR")
                || response.body.contains("DEADLINE_HAS_EXPIRED_ERROR")) {
            throw new StoreSearchException(new StoreSearchFailure(
                    StoreSearchError.AUTHORIZATION_DENIED,
                    "공공데이터 인증키와 활용 승인을 확인해 주세요.", response.status));
        }
        if (response.body.contains("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")) {
            throw new StoreSearchException(new StoreSearchFailure(
                    StoreSearchError.QUOTA_EXCEEDED, "공공데이터 조회 한도를 초과했어요.", response.status));
        }
    }
}
