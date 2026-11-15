package com.example.pleosstarter.stores;

import java.io.IOException;
import java.net.URL;

/** 응답 파싱 검증에서 네트워크를 분리할 수 있는 읽기 전용 전송 계약이다. */
interface StoreHttpTransport {
    /** 선택적인 인증 헤더를 붙여 HTTPS GET을 실행한다. */
    StoreHttpResponse get(URL endpoint, String authorization) throws IOException;
}
