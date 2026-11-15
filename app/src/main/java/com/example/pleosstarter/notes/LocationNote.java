package com.example.pleosstarter.notes;

/** 녹음 원본 없이 장소 좌표와 정리된 텍스트만 보관하는 위치 기록이다. */
public record LocationNote(double latitude, double longitude, String text) {
    /** 유효한 지구 좌표와 비어 있지 않은 텍스트를 검증한다. */
    public LocationNote {
        validatePosition(latitude, longitude);
        if (text == null || text.trim().isEmpty()) {
            throw new LocationNotesException(LocationNotesException.Code.INVALID_INPUT,
                    "저장할 메모 내용을 입력해 주세요.");
        }
        text = text.trim();
    }

    /** 외부 위치 입력에 유한한 위경도 범위가 전달되었는지 확인한다. */
    static void validatePosition(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new LocationNotesException(LocationNotesException.Code.INVALID_INPUT,
                    "현재 위치를 확인할 수 없습니다.");
        }
    }
}
