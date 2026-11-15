package com.example.pleosstarter.stores;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** APK 밖의 앱 전용 파일에서 시험용 인증정보를 읽고 한곳에서 검증한다. */
public final class StoreApiConfiguration implements StoreRequestCredentials {
    public static final String FILE_NAME = "store-api.properties";
    private static final int MAX_FILE_BYTES = 16384;
    private final File configurationFile;

    /** 앱 전용 files 디렉터리의 파일만 사용하도록 주입받는다. */
    public StoreApiConfiguration(File configurationFile) {
        this.configurationFile = configurationFile;
    }

    /** 매 요청마다 새 설정을 읽되 원문은 오류와 로그에 포함하지 않는다. */
    @Override
    public String readKey(StoreProvider provider) throws StoreSearchException {
        if (!configurationFile.isFile()) {
            throw missingKey(provider);
        }
        if (configurationFile.length() > MAX_FILE_BYTES) {
            throw invalidConfiguration();
        }

        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configurationFile), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | IllegalArgumentException failure) {
            throw invalidConfiguration();
        }

        String propertyName = "publicDataServiceKey";
        if (provider == StoreProvider.KAKAO_LOCAL) {
            propertyName = "kakaoRestApiKey";
        }
        String key = properties.getProperty(propertyName, "").trim();
        if (key.isEmpty()) {
            throw missingKey(provider);
        }
        if (key.length() > 1024 || key.indexOf('\n') >= 0 || key.indexOf('\r') >= 0) {
            throw invalidConfiguration();
        }
        if (provider == StoreProvider.KAKAO_LOCAL && !key.matches("[a-fA-F0-9]{32}")) {
            throw invalidConfiguration();
        }
        return key;
    }

    /** 원천별로 필요한 인증 종류를 안내하며 실제 호출 전 차단임을 밝힌다. */
    private StoreSearchException missingKey(StoreProvider provider) {
        String message = "공공데이터포털 인증키가 없어 아직 조회하지 못했어요.";
        if (provider == StoreProvider.KAKAO_LOCAL) {
            message = "카카오 REST API 키가 없어 아직 조회하지 못했어요.";
        }
        return new StoreSearchException(new StoreSearchFailure(
                StoreSearchError.MISSING_KEY, message, 0));
    }

    /** 인증 파일 문제를 원문 없이 전달한다. */
    private StoreSearchException invalidConfiguration() {
        return new StoreSearchException(new StoreSearchFailure(
                StoreSearchError.INVALID_CONFIGURATION, "매장 조회 인증 설정을 확인해 주세요.", 0));
    }
}
