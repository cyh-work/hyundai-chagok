package com.example.pleosstarter.records;

import java.util.List;

/** 즐겨찾기 저장 방식과 메모·지도 화면을 분리한다. */
public interface FavoriteRepository {
    /** 저장된 위치 목록을 복원하며 손상은 오류로 전달한다. */
    List<SavedMapPoint> readAll();

    /** 전체 목록의 저장 완료를 확인하며 실패하면 예외를 전달한다. */
    void writeAll(List<SavedMapPoint> favorites);
}
