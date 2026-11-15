package com.example.pleosstarter.map;

import java.util.List;

/** 화면이나 저장소 없이 한 번의 연속 스트로크를 도형으로 분류하는 계약이다. */
public interface GestureClassifier {
    /** 픽셀 좌표의 스트로크와 최소 길이를 받아 인식 결과를 반환한다. */
    MapGesture.Type classify(List<StrokePoint> points, float minimumSize);
}
