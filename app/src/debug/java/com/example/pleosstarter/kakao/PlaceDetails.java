package com.example.pleosstarter.kakao;

/** 장소 검색·상세 응답에서 확인한 표시 정보와 위치만 담는다. */
public final class PlaceDetails {
    private final KakaoPlaceSelection selection;
    private final String address;
    private final String roadAddress;
    private final String phone;
    private final String category;

    /** SDK에서 제공하지 않은 표시 필드는 빈 문자열로 보관한다. */
    public PlaceDetails(KakaoPlaceSelection selection, String address, String roadAddress,
                        String phone, String category) {
        this.selection = selection;
        this.address = clean(address);
        this.roadAddress = clean(roadAddress);
        this.phone = clean(phone);
        this.category = clean(category);
    }

    /** 지도 선택 직후 상세 응답을 기다리는 동안 표시할 기본 정보를 만든다. */
    public static PlaceDetails fromSelection(KakaoPlaceSelection selection) {
        return new PlaceDetails(selection, "", "", "", "");
    }

    /** 지도 이동에 필요한 장소 식별자와 좌표를 반환한다. */
    public KakaoPlaceSelection toSelection() {
        return selection;
    }

    /** 공급자의 장소 식별자를 반환한다. */
    public long getPoiId() {
        return selection.getPoiId();
    }

    /** 지도 또는 검색 응답에서 받은 장소 이름을 반환한다. */
    public String getName() {
        return selection.getName();
    }

    /** 지번 주소가 제공된 경우 반환한다. */
    public String getAddress() {
        return address;
    }

    /** 도로명 주소가 제공된 경우 반환한다. */
    public String getRoadAddress() {
        return roadAddress;
    }

    /** 연락처가 제공된 경우 반환하며 전화를 자동으로 걸지 않는다. */
    public String getPhone() {
        return phone;
    }

    /** 공급자가 반환한 업종·장소 분류를 반환한다. */
    public String getCategory() {
        return category;
    }

    /** 지도 표시용 WGS84 위도를 반환한다. */
    public double getLatitude() {
        return selection.getLatitude();
    }

    /** 지도 표시용 WGS84 경도를 반환한다. */
    public double getLongitude() {
        return selection.getLongitude();
    }

    /** 누락된 선택 필드를 화면에서 안전하게 처리하도록 정리한다. */
    private static String clean(String text) {
        if (text == null) {
            return "";
        }
        return text.trim();
    }
}
