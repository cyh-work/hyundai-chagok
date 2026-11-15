package com.example.pleosstarter.stores;

/** 파서에 전달할 HTTP 상태와 제한된 길이의 응답 본문을 보관한다. */
final class StoreHttpResponse {
    final int status;
    final String body;

    /** 실제 수신한 값만 보관하며 로그용 문자열 변환을 제공하지 않는다. */
    StoreHttpResponse(int status, String body) {
        this.status = status;
        this.body = body;
    }
}
