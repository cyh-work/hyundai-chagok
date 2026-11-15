package com.example.pleosstarter.navigation;

/** Pleos 내비에서 받은 차량 위치를 WGS84 좌표로 보관한다. */
public record VehiclePosition(double latitude, double longitude) {
}
