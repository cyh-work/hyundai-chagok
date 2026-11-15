package com.example.pleosstarter.stores;

/** 키 누락, 인증 거부, 통신 실패와 잘못된 응답을 구분한다. */
public enum StoreSearchError {
    INVALID_COORDINATE,
    INVALID_QUERY,
    MISSING_KEY,
    MISSING_CONFIGURATION,
    INVALID_CONFIGURATION,
    AUTHORIZATION_DENIED,
    QUOTA_EXCEEDED,
    TIMEOUT,
    NETWORK_FAILURE,
    REMOTE_FAILURE,
    INVALID_RESPONSE,
    CLOSED
}
