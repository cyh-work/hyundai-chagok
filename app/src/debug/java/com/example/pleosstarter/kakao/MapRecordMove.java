package com.example.pleosstarter.kakao;

/** 드래그를 놓은 좌표와 선택 당시 저장 참조를 전달하며 기록 원문을 로그에서 숨긴다. */
public record MapRecordMove(MapRecordMarker original, double latitude, double longitude) {
    /** 저장 대상과 유효한 지리 좌표가 확인된 이동 요청만 생성한다. */
    public MapRecordMove {
        if (original == null || !original.movable() || !Double.isFinite(latitude)
                || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) {
            throw new IllegalArgumentException("기록을 옮길 위치를 확인해 주세요.");
        }
    }

    /** 민감한 메모 원문과 위치는 진단 문자열에 포함하지 않는다. */
    @Override
    public String toString() {
        return "MapRecordMove{favorite=" + original.favorite() + "}";
    }
}
