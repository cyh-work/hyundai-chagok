package com.example.pleosstarter.kakao;

/** 지도 위 조작 버튼을 피하기 위해 기록 카드 배치에서 제외할 각 변의 픽셀 여백이다. */
public record MapRecordInsets(int left, int top, int right, int bottom) {
    /** 음수 여백을 거부하며 지도 좌표나 마커의 투영 범위는 변경하지 않는다. */
    public MapRecordInsets {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("지도 기록 카드의 안전 여백은 음수일 수 없습니다.");
        }
    }
}
