package com.example.pleosstarter.stores;

/** 개발용 직접 인증과 키가 필요 없는 중계 요청의 설정 경계를 분리한다. */
interface StoreRequestCredentials {
    /** 지원 원천의 설정을 검증하며 인증 없는 중계 요청은 빈 문자열을 반환한다. */
    String readKey(StoreProvider provider) throws StoreSearchException;
}
