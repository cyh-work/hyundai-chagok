package com.example.pleosstarter.ui;

import com.example.pleosstarter.kakao.MapRecordMarker;

/** 재방문 카드의 표시를 위치 판단·예약·저장 흐름과 분리한다. */
public interface MapRevisitDisplay {
    /** 한 기록과 명시적인 열기·닫기 동작만 지도 위에 표시한다. */
    void showRecord(MapRecordMarker marker, Runnable open, Runnable dismiss);

    /** 카드와 이전 기록을 여는 동작을 함께 제거한다. */
    void hideRecord();
}
