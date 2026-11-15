package com.example.pleosstarter.stores;

import org.json.JSONObject;

/** HTTP 및 데이터 필드의 공통 경계를 검사하며 원문 오류는 노출하지 않는다. */
final class StoreResponseValidation {
    /** 인스턴스 상태가 없는 응답 경계 검사기다. */
    private StoreResponseValidation() {
    }

    /** 성공 상태 이외의 응답을 사용자에게 안전한 실패로 변환한다. */
    static void requireSuccess(StoreHttpResponse response) throws StoreSearchException {
        if (response.status >= 200 && response.status < 300) {
            return;
        }
        StoreSearchError code = StoreSearchError.REMOTE_FAILURE;
        String message = "매장 정보 제공처에서 요청을 처리하지 못했어요.";
        if (response.status == 401 || response.status == 403) {
            code = StoreSearchError.AUTHORIZATION_DENIED;
            message = "인증 또는 API 사용 승인이 필요해요.";
        } else if (response.status == 429) {
            code = StoreSearchError.QUOTA_EXCEEDED;
            message = "현재 조회 한도를 초과했어요. 잠시 후 다시 시도해 주세요.";
        }
        throw new StoreSearchException(new StoreSearchFailure(code, message, response.status));
    }

    /** JSON null을 실제 텍스트로 표시하지 않도록 문자 필드를 읽는다. */
    static String text(JSONObject object, String name) {
        if (object.isNull(name)) {
            return "";
        }
        return object.optString(name, "").trim();
    }

    /** 누락·범위 밖·비유한 수는 좌표로 사용하지 않는다. */
    static Double number(JSONObject object, String name, double minimum, double maximum) {
        double number = object.optDouble(name, Double.NaN);
        if (!Double.isFinite(number) || number < minimum || number > maximum) {
            return null;
        }
        return number;
    }

    /** API가 반환하지 않은 거리만 실제 좌표로 계산하며 좌표 누락이면 보류한다. */
    static Double distance(double latitude, double longitude, Double storeLatitude,
                           Double storeLongitude) {
        if (storeLatitude == null || storeLongitude == null) {
            return null;
        }
        double latitudeDelta = Math.toRadians(storeLatitude - latitude);
        double longitudeDelta = Math.toRadians(storeLongitude - longitude);
        double haversine = Math.sin(latitudeDelta / 2) * Math.sin(latitudeDelta / 2)
                + Math.cos(Math.toRadians(latitude)) * Math.cos(Math.toRadians(storeLatitude))
                * Math.sin(longitudeDelta / 2) * Math.sin(longitudeDelta / 2);
        return 6371000.0 * 2 * Math.atan2(Math.sqrt(haversine),
                Math.sqrt(Math.max(0, 1 - haversine)));
    }

    /** 계약과 다른 정상 HTTP 본문을 빈 성공 결과로 취급하지 않는다. */
    static StoreSearchException invalidResponse(int status) {
        return new StoreSearchException(new StoreSearchFailure(
                StoreSearchError.INVALID_RESPONSE, "매장 정보 응답 형식을 확인하지 못했어요.", status));
    }
}
