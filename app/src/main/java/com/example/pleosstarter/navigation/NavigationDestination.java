package com.example.pleosstarter.navigation;

/** 기본 내비에 전달할 선택 장소의 이름·주소·WGS84 좌표를 보관한다. */
public record NavigationDestination(String name, String address, double latitude, double longitude) {

    /** 표시 문자열을 정규화하되 좌표 유효성 판정은 연동 경계에서 수행한다. */
    public NavigationDestination {
        name = name == null ? "" : name.trim();
        address = address == null ? "" : address.trim();
    }

    /** 이름과 유한한 WGS84 좌표가 있어 내비 요청에 사용할 수 있는지 반환한다. */
    public boolean isValid() {
        return !name.isBlank() && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
