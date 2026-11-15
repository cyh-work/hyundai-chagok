package com.example.pleosstarter.records;

import com.example.pleosstarter.kakao.MapRecordMarker;
import java.util.List;
import java.util.Optional;

/** 저장 기록 스냅샷과 유효 위치를 받아 현재 통과에서 보여 줄 재방문 한 건을 고른다. */
public interface RecordRevisitService {
    /** 최초 목록은 복원 기록으로 받고 이후 추가·이동·수정은 억제하며 잘못된 참조는 MapRecordFailure로 거부한다. */
    void replaceRecords(List<MapRecordMarker> records);

    /** 100m 안의 가장 가까운 한 건을 반환하고 표시를 허용할 때만 동시에 근접한 모든 기록의 진입을 소비한다. */
    Optional<MapRecordMarker> onPosition(double latitude, double longitude, boolean presentationAllowed);

    /** 이전 위치에서 보류한 표시를 버리며 기록별 이미 알림·이탈 상태는 유지한다. */
    void resetPosition();
}
