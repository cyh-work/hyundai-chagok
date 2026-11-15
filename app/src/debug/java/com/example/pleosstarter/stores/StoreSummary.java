package com.example.pleosstarter.stores;

/** 원천에 실제 포함된 매장 필드만 담으며 층·입점 관계를 추측하지 않는다. */
public final class StoreSummary {
    private final String id;
    private final String name;
    private final String category;
    private final String address;
    private final String roadAddress;
    private final String phone;
    private final String floor;
    private final String unit;
    private final String buildingName;
    private final String buildingManagementNumber;
    private final String referenceMonth;
    private final Double latitude;
    private final Double longitude;
    private final Double distanceMeters;
    private final String placeUrl;

    /** 누락된 문자 필드는 빈 문자열, 누락된 좌표·거리는 null로 보관한다. */
    public StoreSummary(String id, String name, String category, String address,
                        String roadAddress, String phone, String floor, String unit,
                        String buildingName, String buildingManagementNumber,
                        String referenceMonth, Double latitude, Double longitude,
                        Double distanceMeters, String placeUrl) {
        this.id = clean(id);
        this.name = clean(name);
        this.category = clean(category);
        this.address = clean(address);
        this.roadAddress = clean(roadAddress);
        this.phone = clean(phone);
        this.floor = clean(floor);
        this.unit = clean(unit);
        this.buildingName = clean(buildingName);
        this.buildingManagementNumber = clean(buildingManagementNumber);
        this.referenceMonth = clean(referenceMonth);
        this.latitude = latitude;
        this.longitude = longitude;
        this.distanceMeters = distanceMeters;
        this.placeUrl = clean(placeUrl);
    }

    /** 누락한 문자 필드가 화면에 null로 표시되지 않게 정리한다. */
    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }

    /** 원천별 매장 식별자를 반환한다. */
    public String getId() {
        return id;
    }

    /** 실제 응답의 상호와 지점명을 반환한다. */
    public String getName() {
        return name;
    }

    /** 실제 응답의 업종을 반환한다. */
    public String getCategory() {
        return category;
    }

    /** 지번주소를 반환한다. */
    public String getAddress() {
        return address;
    }

    /** 도로명주소를 반환한다. */
    public String getRoadAddress() {
        return roadAddress;
    }

    /** 원천이 제공한 전화번호를 반환한다. */
    public String getPhone() {
        return phone;
    }

    /** 제공된 층 정보만 반환한다. */
    public String getFloor() {
        return floor;
    }

    /** 제공된 호 정보만 반환한다. */
    public String getUnit() {
        return unit;
    }

    /** 제공된 건물명을 반환한다. */
    public String getBuildingName() {
        return buildingName;
    }

    /** 입점 관계 대조에 사용할 건물관리번호를 반환한다. */
    public String getBuildingManagementNumber() {
        return buildingManagementNumber;
    }

    /** 원천이 제공한 기준년월을 반환한다. */
    public String getReferenceMonth() {
        return referenceMonth;
    }

    /** 위치 미제공이면 null인 위도를 반환한다. */
    public Double getLatitude() {
        return latitude;
    }

    /** 위치 미제공이면 null인 경도를 반환한다. */
    public Double getLongitude() {
        return longitude;
    }

    /** 조회 중심까지의 거리이며 계산할 좌표가 없으면 null이다. */
    public Double getDistanceMeters() {
        return distanceMeters;
    }

    /** 제공된 장소 상세 링크를 반환하며 리뷰 본문은 포함하지 않는다. */
    public String getPlaceUrl() {
        return placeUrl;
    }
}
