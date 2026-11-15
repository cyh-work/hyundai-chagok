package ai.pleos.playground.navi.data;

/** 응답 좌표만 담는 SDK 대역이다. 실제 SDK API 컴파일은 별도로 실행한다. */
public final class CurrentLocationInfo {
    private final double latitude;
    private final double longitude;

    /** 시험용 좌표를 저장한다. */
    public CurrentLocationInfo(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /** 시험 위도를 반환한다. */
    public double getLatitude() {
        return latitude;
    }

    /** 시험 경도를 반환한다. */
    public double getLongitude() {
        return longitude;
    }
}
