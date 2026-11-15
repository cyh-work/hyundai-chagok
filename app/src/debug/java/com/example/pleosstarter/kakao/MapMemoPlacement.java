package com.example.pleosstarter.kakao;

/** 투영된 메모 지점 가까이에 화면 정면 카드를 배치하는 순수 계산을 담당한다. */
final class MapMemoPlacement {
    private static final MapRecordInsets NO_INSETS = new MapRecordInsets(0, 0, 0, 0);
    /** 지도의 세계 좌표를 현재 화면에 투영한 픽셀 지점이다. */
    record ScreenPoint(float x, float y) {
    }

    /** 카드의 화면 위치와 지도 지점을 연결할 카드 경계 좌표다. */
    record Placement(boolean visible, float left, float top, float connectorX, float connectorY) {
    }

    /** 인스턴스 상태 없이 화면 좌표만 계산한다. */
    private MapMemoPlacement() {
    }

    /** 지도 밖 지점은 숨기며 보이는 지점의 위·아래 중 카드가 들어가는 쪽에 놓는다. */
    static Placement calculate(ScreenPoint point, int viewportWidth, int viewportHeight,
                               int cardWidth, int cardHeight, float margin, float gap) {
        return calculate(point, viewportWidth, viewportHeight, cardWidth, cardHeight,
                margin, gap, NO_INSETS);
    }

    /** 지점의 가시성은 전체 지도 기준으로 판단하고 카드는 조작 영역을 제외한 안전 영역에 놓는다. */
    static Placement calculate(ScreenPoint point, int viewportWidth, int viewportHeight,
                               int cardWidth, int cardHeight, float margin, float gap,
                               MapRecordInsets insets) {
        if (point == null || !Float.isFinite(point.x()) || !Float.isFinite(point.y())
                || insets == null || !Float.isFinite(margin) || margin < 0
                || !Float.isFinite(gap) || gap < 0
                || viewportWidth <= 0 || viewportHeight <= 0 || cardWidth <= 0 || cardHeight <= 0
                || point.x() < 0 || point.x() > viewportWidth
                || point.y() < 0 || point.y() > viewportHeight) {
            return new Placement(false, 0, 0, 0, 0);
        }

        // 여백 합산은 실수 좌표로 수행해 큰 정수 여백의 덧셈 넘침을 피한다.
        float safeLeft = insets.left() + margin;
        float safeTop = insets.top() + margin;
        float safeRight = viewportWidth - (float) insets.right() - margin;
        float safeBottom = viewportHeight - (float) insets.bottom() - margin;
        if (cardWidth > safeRight - safeLeft || cardHeight > safeBottom - safeTop) {
            return new Placement(false, 0, 0, 0, 0);
        }

        float left = clamp(point.x() - cardWidth / 2.0f, safeLeft, safeRight - cardWidth);
        float above = point.y() - gap - cardHeight;
        float top;
        if (above >= safeTop) {
            top = clamp(above, safeTop, safeBottom - cardHeight);
        } else {
            top = clamp(point.y() + gap, safeTop, safeBottom - cardHeight);
        }
        float connectorX = clamp(point.x(), left + Math.min(20, cardWidth / 2.0f),
                left + cardWidth - Math.min(20, cardWidth / 2.0f));
        float connectorY = top > point.y() ? top : top + cardHeight;
        if (point.y() >= top && point.y() <= top + cardHeight) {
            if (point.x() < left) {
                connectorX = left;
            } else if (point.x() > left + cardWidth) {
                connectorX = left + cardWidth;
            }
            if (point.x() < left || point.x() > left + cardWidth) {
                connectorY = clamp(point.y(), top + Math.min(20, cardHeight / 2.0f),
                        top + cardHeight - Math.min(20, cardHeight / 2.0f));
            }
        }
        return new Placement(true, left, top, connectorX, connectorY);
    }

    /** 화면 경계 안으로 한 축의 위치를 제한한다. */
    private static float clamp(float coordinate, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, coordinate));
    }
}
