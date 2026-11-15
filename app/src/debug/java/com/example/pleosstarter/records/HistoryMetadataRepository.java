package com.example.pleosstarter.records;

import java.util.Map;

/** 원문·좌표를 복제하지 않고 기록 식별자별 실제 신규 생성 시각만 보관한다. */
public interface HistoryMetadataRepository {
    /** 이미 저장된 시각만 반환하며 과거 기록에 현재 시각을 채우지 않는다. */
    Map<String, Long> readCreatedAt();

    /** 처음 확인한 신규 저장 시각만 기록하고 기존 값은 갱신하지 않는다. */
    void rememberCreatedAt(String historyId, long createdAtMillis);

    /** 본 기록이 삭제된 뒤 해당 시각 정보만 정리한다. */
    void removeCreatedAt(String historyId);

    /** 좌표 이동으로 달라진 메모 식별자와 기존 생성 시각의 전체 대응을 한 번에 저장한다. */
    void replaceCreatedAt(Map<String, Long> timestamps);
}
