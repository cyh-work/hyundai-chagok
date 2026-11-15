package com.example.pleosstarter.location;

/** 차량 위치를 얻지 못한 원인을 화면에서 구분하기 위한 오류 코드다. */
public enum LocationError {
    CONNECTION_FAILED,
    CONNECTION_TIMEOUT,
    PERMISSION_DENIED,
    REQUEST_FAILED,
    REQUEST_TIMEOUT,
    INVALID_COORDINATE
}
