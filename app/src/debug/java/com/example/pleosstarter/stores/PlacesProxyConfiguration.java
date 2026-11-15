package com.example.pleosstarter.stores;

import java.net.URI;
import java.net.URISyntaxException;

/** 제출 앱의 HTTPS 중계 주소만 검증하며 로컬 인증 파일은 읽지 않는다. */
final class PlacesProxyConfiguration implements StoreRequestCredentials {
    private final String baseUrl;

    /** 빌드 시 주입한 공개 서버 주소를 보관하되 지도 시작을 막지 않고 조회 때 검증한다. */
    PlacesProxyConfiguration(String baseUrl) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
    }

    /** 카카오 중계만 지원하며 REST 키 없이 요청하도록 명시한다. */
    @Override
    public String readKey(StoreProvider provider) throws StoreSearchException {
        if (provider != StoreProvider.KAKAO_LOCAL) {
            throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_QUERY,
                    "이 앱에서는 카카오 주변 장소 조회를 사용해 주세요.", 0));
        }
        baseUri();
        return "";
    }

    /** 공개 HTTPS와 단순 경로만 허용하며 끝 슬래시를 하나 보장해 하위 API 경로를 유지한다. */
    URI baseUri() throws StoreSearchException {
        if (baseUrl.isEmpty()) {
            throw new StoreSearchException(new StoreSearchFailure(StoreSearchError.MISSING_CONFIGURATION,
                    "주변 장소 서버가 아직 설정되지 않았어요. 앱 제공자에게 확인해 주세요.", 0));
        }
        try {
            URI uri = new URI(baseUrl);
            String host = uri.getHost();
            String path = uri.getRawPath();
            boolean validPath = path != null && (path.isEmpty() || path.equals("/")
                    || path.matches("/[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?"));
            boolean validHost = host != null
                    && host.matches("(?i)[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")
                    && !host.matches("[0-9.]+")
                    && !host.toLowerCase(java.util.Locale.ROOT).endsWith(".localhost");
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !validHost
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !validPath) {
                throw invalidConfiguration();
            }
            if (baseUrl.endsWith("/")) {
                return uri;
            }
            return new URI(baseUrl + "/");
        } catch (URISyntaxException failure) {
            throw invalidConfiguration();
        }
    }

    /** 잘못된 주소의 원문 대신 고정된 안내만 전달한다. */
    private StoreSearchException invalidConfiguration() {
        return new StoreSearchException(new StoreSearchFailure(StoreSearchError.INVALID_CONFIGURATION,
                "주변 장소 서버 설정을 확인하지 못했어요. 앱 제공자에게 확인해 주세요.", 0));
    }
}
