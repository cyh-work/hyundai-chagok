package com.example.pleosstarter.records;

/** 실제 저장 기록을 히스토리에 표시하며 생성 시각 0은 과거 시각 미확인을 뜻한다. */
public record MapHistoryEntry(String id, Type type, String title, String preview, long createdAtMillis) {
    /** 장소 저장과 메모를 구분하며 위치 이동 자체는 기록 유형으로 만들지 않는다. */
    public enum Type {
        FAVORITE,
        NOTE
    }

    /** 화면 식별자와 내용, 확인된 생성 시각의 형식을 검증한다. */
    public MapHistoryEntry {
        if (id == null || id.isBlank() || type == null || title == null || title.isBlank()
                || preview == null || createdAtMillis < 0) {
            throw new IllegalArgumentException("히스토리 표시 정보가 올바르지 않습니다.");
        }
    }

    /** 실제 신규 저장 성공 시각이 남아 있는 항목인지 반환한다. */
    public boolean hasCreatedAt() {
        return createdAtMillis > 0;
    }
}
