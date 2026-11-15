package com.example.pleosstarter.location;

/** Pleos가 보고한 WGS84 좌표와 응답 수신 시각이다. SDK가 실제 측위 시각을 제공하지 않아 최신 GPS 여부는 알 수 없다. */
public final class CurrentPosition {
    private final double latitude;
    private final double longitude;
    private final long receivedElapsedRealtimeMillis;
    private final boolean emulatedVehicle;

    /** 유효한 좌표와 단조 증가 시계의 수신 시각을 저장하며 잘못된 입력은 거부한다. */
    public CurrentPosition(double latitude, double longitude,
            long receivedElapsedRealtimeMillis, boolean emulatedVehicle) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0
                || !Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0
                || (latitude == 0.0 && longitude == 0.0)
                || receivedElapsedRealtimeMillis < 0L) {
            throw new IllegalArgumentException("유효한 차량 좌표와 수신 시각이 필요합니다.");
        }

        this.latitude = latitude;
        this.longitude = longitude;
        this.receivedElapsedRealtimeMillis = receivedElapsedRealtimeMillis;
        this.emulatedVehicle = emulatedVehicle;
    }

    /** WGS84 위도를 반환한다. */
    public double getLatitude() {
        return latitude;
    }

    /** WGS84 경도를 반환한다. */
    public double getLongitude() {
        return longitude;
    }

    /** SDK 응답을 받은 elapsedRealtime 값을 반환한다. GPS 측위 시각으로 사용하면 안 된다. */
    public long getReceivedElapsedRealtimeMillis() {
        return receivedElapsedRealtimeMillis;
    }

    /** 호출자가 지정한 에뮬레이터 환경 여부를 반환한다. */
    public boolean isEmulatedVehicle() {
        return emulatedVehicle;
    }

    /** 좌표 공급 환경을 사용자가 구분할 수 있는 이름으로 반환한다. */
    public String getSourceLabel() {
        if (emulatedVehicle) {
            return "에뮬레이터 차량 위치";
        }
        return "차량 위치";
    }

    /** 현재 SDK는 측위 시각을 제공하지 않으므로 항상 false를 반환한다. */
    public boolean hasFixTimestamp() {
        return false;
    }
}
