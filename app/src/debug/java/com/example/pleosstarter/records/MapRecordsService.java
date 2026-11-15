package com.example.pleosstarter.records;

import com.example.pleosstarter.notes.LocationNote;

import java.util.List;
import java.util.Optional;

/** 지도 좌표의 즐겨찾기·메모 업무 규칙과 저장 결과를 제공한다. */
public interface MapRecordsService {
    /** 그린 위치를 추가하고 같은 위치의 기존 즐겨찾기는 중복 생성하지 않는다. */
    SavedMapPoint addFavorite(double latitude, double longitude, String name);

    /** 확인한 메모를 저장하며 8m 이내 기존 즐겨찾기는 식별자·위치를 유지한 채 메모만 갱신한다. */
    SavedMapPoint saveFavorite(double latitude, double longitude, String name, String note);

    /** 유효한 좌표에서 8m 이내 기존 즐겨찾기를 찾아 편집 전 내용을 제공한다. */
    Optional<SavedMapPoint> findFavoriteNear(double latitude, double longitude);

    /** 화면과 지도 마커에 쓸 변경 불가능한 즐겨찾기 목록을 반환한다. */
    List<SavedMapPoint> favorites();

    /** 선택한 즐겨찾기의 좌표만 바꾸며 다른 항목과 합치거나 새 항목을 만들지 않는다. */
    SavedMapPoint moveFavorite(String id, double latitude, double longitude);

    /** 사용자가 명시적으로 고른 식별자의 즐겨찾기만 삭제한다. */
    void removeFavorite(String id);

    /** 확정 텍스트와 그린 위치를 저장하고 저장된 메모를 반환한다. */
    LocationNote saveNote(double latitude, double longitude, String text);

    /** 지도 마커와 기록 목록에 쓸 메모 스냅샷을 반환한다. */
    List<LocationNote> notes();

    /** 선택 당시의 순번·원문이 일치하는 메모 한 건의 좌표만 바꾼다. */
    LocationNote moveNote(int index, LocationNote expected, double latitude, double longitude);

    /** 선택 당시의 순번·원문이 일치하는 메모 한 건을 영속 저장 성공 후 삭제한다. */
    void removeNote(int index, LocationNote expected);

    /** 기존 재진입 판단을 적용하며 이번 위치에 새 알림이 없으면 null을 반환한다. */
    LocationNote onPosition(double latitude, double longitude);
}
