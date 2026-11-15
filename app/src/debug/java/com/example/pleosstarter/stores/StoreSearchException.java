package com.example.pleosstarter.stores;

/** 내부 예외 원문을 보존하지 않고 정해진 오류 계약만 전달한다. */
final class StoreSearchException extends Exception {
    private final StoreSearchFailure failure;

    /** 외부로 전달 가능한 실패 정보만 예외에 담는다. */
    StoreSearchException(StoreSearchFailure failure) {
        super(failure.getCode().name());
        this.failure = failure;
    }

    /** 안전한 사용자 안내와 오류 종류를 반환한다. */
    StoreSearchFailure getFailure() {
        return failure;
    }
}
