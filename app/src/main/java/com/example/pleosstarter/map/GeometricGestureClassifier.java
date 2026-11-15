package com.example.pleosstarter.map;

import java.util.ArrayList;
import java.util.List;

/** 교차, 닫힘, 길이와 방사형 변화를 이용해 별·원·선을 구분한다. */
public final class GeometricGestureClassifier implements GestureClassifier {
    /** 너무 작은 입력을 제외하고 도형 특성을 순서대로 검사한다. */
    @Override
    public MapGesture.Type classify(List<StrokePoint> points, float minimumSize) {
        if (points.size() < 3) {
            return MapGesture.Type.UNRECOGNIZED;
        }

        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        double length = 0;
        for (int index = 0; index < points.size(); index++) {
            StrokePoint point = points.get(index);
            left = Math.min(left, point.x());
            right = Math.max(right, point.x());
            top = Math.min(top, point.y());
            bottom = Math.max(bottom, point.y());
            if (index > 0) {
                length += distance(points.get(index - 1), point);
            }
        }

        double diagonal = Math.hypot(right - left, bottom - top);
        if (diagonal < minimumSize || length < minimumSize) {
            return MapGesture.Type.UNRECOGNIZED;
        }

        List<StrokePoint> sampled = resample(points, length / 48.0);
        int intersections = countIntersections(sampled);
        double closure = distance(points.get(0), points.get(points.size() - 1)) / diagonal;
        double ratio = length / diagonal;
        if (intersections >= 3 && ratio > 2.3 && ratio < 6.5) {
            return MapGesture.Type.STAR;
        }
        if (closure < 0.35 && ratio > 2.6 && radialPeaks(sampled, (left + right) / 2, (top + bottom) / 2) >= 4) {
            return MapGesture.Type.STAR;
        }
        if (closure < 0.42 && ratio > 1.8 && ratio < 4.5 && intersections <= 2
                && Math.min(right - left, bottom - top) / Math.max(right - left, bottom - top) > 0.35) {
            return MapGesture.Type.CIRCLE;
        }
        if (closure > 0.48 && ratio < 1.8 && intersections == 0) {
            return MapGesture.Type.LINE;
        }
        return MapGesture.Type.UNRECOGNIZED;
    }

    /** 입력 속도의 영향을 줄이도록 일정 거리 간격으로 보간한다. */
    private List<StrokePoint> resample(List<StrokePoint> points, double interval) {
        List<StrokePoint> sampled = new ArrayList<>();
        sampled.add(points.get(0));
        double remaining = interval;
        StrokePoint previous = points.get(0);
        for (int index = 1; index < points.size(); index++) {
            StrokePoint current = points.get(index);
            double segment = distance(previous, current);
            while (segment >= remaining && segment > 0) {
                double fraction = remaining / segment;
                previous = new StrokePoint((float) (previous.x() + fraction * (current.x() - previous.x())),
                        (float) (previous.y() + fraction * (current.y() - previous.y())));
                sampled.add(previous);
                segment = distance(previous, current);
                remaining = interval;
            }
            remaining -= segment;
            previous = current;
        }
        sampled.add(points.get(points.size() - 1));
        return sampled;
    }

    /** 서로 인접하지 않은 선분이 내부에서 교차하는 횟수를 센다. */
    private int countIntersections(List<StrokePoint> points) {
        int intersections = 0;
        for (int first = 0; first < points.size() - 1; first++) {
            for (int second = first + 3; second < points.size() - 1; second++) {
                StrokePoint a = points.get(first);
                StrokePoint b = points.get(first + 1);
                StrokePoint c = points.get(second);
                StrokePoint d = points.get(second + 1);
                if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) {
                    intersections++;
                }
            }
        }
        return intersections;
    }

    /** 윤곽선 별의 뚜렷한 돌출부 수를 중심 거리 변화로 추정한다. */
    private int radialPeaks(List<StrokePoint> points, float centerX, float centerY) {
        double minimum = Double.MAX_VALUE;
        double maximum = 0;
        List<Double> radii = new ArrayList<>();
        for (StrokePoint point : points) {
            double radius = Math.hypot(point.x() - centerX, point.y() - centerY);
            radii.add(radius);
            minimum = Math.min(minimum, radius);
            maximum = Math.max(maximum, radius);
        }
        if (minimum > maximum * 0.65) {
            return 0;
        }
        int peaks = 0;
        boolean high = false;
        for (double radius : radii) {
            if (!high && radius > maximum * 0.8) {
                peaks++;
                high = true;
            } else if (radius < maximum * 0.6) {
                high = false;
            }
        }
        return peaks;
    }

    /** 두 점 사이 거리를 계산한다. */
    private double distance(StrokePoint first, StrokePoint second) {
        return Math.hypot(second.x() - first.x(), second.y() - first.y());
    }

    /** 선분에 대한 점의 방향을 외적으로 계산한다. */
    private double cross(StrokePoint first, StrokePoint second, StrokePoint third) {
        return (second.x() - first.x()) * (third.y() - first.y())
                - (second.y() - first.y()) * (third.x() - first.x());
    }
}
