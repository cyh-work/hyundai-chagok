package com.example.pleosstarter.notes;

import java.util.List;

/** 위치 기록의 저장 방식을 재진입 판단과 분리하는 저장 계약이다. */
interface LocationNoteRepository {
    /** 모든 저장 기록을 복원하며 손상된 저장본은 복원 실패로 전달한다. */
    List<LocationNote> readAll();

    /** 기록 목록 전체를 저장하고 완료 여부를 확인한 뒤 반환한다. */
    void writeAll(List<LocationNote> notes);
}
