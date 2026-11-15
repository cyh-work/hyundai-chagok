package com.example.pleosstarter.gesture;

import com.example.pleosstarter.map.MapGesture;

/** 그린 도형과 그 영역의 화면 중심을 지도 좌표 변환 계층에 전달한다. */
public record MapStrokeGesture(MapGesture.Type type, float centerX, float centerY) {
}
