package com.example.pleosstarter.stores;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** 인증값을 다른 호스트로 전달하지 않는 제한된 HTTPS GET을 실행한다. */
final class HttpsStoreTransport implements StoreHttpTransport {
    private static final int TIMEOUT_MILLIS = 12000;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;

    /** 리다이렉트를 허용하지 않고 오류 응답도 파서에 전달한다. */
    @Override
    public StoreHttpResponse get(URL endpoint, String authorization) throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) endpoint.openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);
            connection.setRequestProperty("Accept", "application/json");
            if (!authorization.isEmpty()) {
                connection.setRequestProperty("Authorization", authorization);
            }

            int status = connection.getResponseCode();
            InputStream stream = connection.getErrorStream();
            if (status >= 200 && status < 400) {
                stream = connection.getInputStream();
            }
            if (stream == null) {
                return new StoreHttpResponse(status, "");
            }
            try (InputStream input = stream) {
                return new StoreHttpResponse(status, readLimited(input));
            }
        } finally {
            connection.disconnect();
        }
    }

    /** 큰 응답과 취소된 작업이 무제한 메모리를 사용하지 않게 한다. */
    private String readLimited(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedIOException("CANCELLED");
            }
            if (output.size() + count > MAX_RESPONSE_BYTES) {
                throw new IOException("RESPONSE_TOO_LARGE");
            }
            output.write(buffer, 0, count);
        }
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
