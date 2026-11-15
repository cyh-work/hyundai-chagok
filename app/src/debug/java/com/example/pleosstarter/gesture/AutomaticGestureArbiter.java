package com.example.pleosstarter.gesture;

import com.example.pleosstarter.map.GestureClassifier;
import com.example.pleosstarter.map.MapGesture;
import com.example.pleosstarter.map.StrokePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 일반 지도 이동을 먼저 허용하고 충분한 도형 근거가 생긴 입력만 그리기로 전환한다. */
final class AutomaticGestureArbiter {
    private static final int SAMPLE_SEGMENTS = 40;
    private final GestureClassifier classifier;

    /** 완료 도형의 최종 종류는 기존 공용 분류기에 맡긴다. */
    AutomaticGestureArbiter(GestureClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier, "도형 분류기가 필요합니다.");
    }

    /** 큰 회전 뒤 시작점으로 돌아오는 원이나 반복 교차·돌출이 있는 별에서만 지도를 가로챈다. */
    boolean shouldClaim(List<StrokePoint> points, float minimumSize) {
        Geometry geometry = analyze(points, minimumSize);
        if (geometry == null) {
            return false;
        }

        boolean circle = geometry.closure() <= 0.42 && geometry.ratio() >= 1.8
                && geometry.ratio() <= 4.5 && geometry.intersections() <= 1
                && Math.abs(geometry.signedTurn()) >= 4.55 && geometry.consistency() >= 0.8
                && geometry.maximumTurn() < 1.1;
        boolean star = geometry.intersections() >= 2 && geometry.ratio() >= 2.0
                || geometry.peaks() >= 4 && geometry.closure() <= 0.5 && geometry.ratio() >= 2.6;
        return circle || star;
    }

    /** 종료 시 닫힘·크기·형태를 다시 확인하여 열린 곡선이나 흔들림에는 저장 종류를 반환하지 않는다. */
    MapGesture.Type classifyCompleted(List<StrokePoint> points, float minimumSize) {
        Geometry geometry = analyze(points, minimumSize);
        if (geometry == null || geometry.closure() > 0.28) {
            return MapGesture.Type.UNRECOGNIZED;
        }

        MapGesture.Type type = classifier.classify(points, minimumSize);
        if (type == MapGesture.Type.CIRCLE && geometry.intersections() <= 1
                && Math.abs(geometry.signedTurn()) >= 4.55
                && Math.abs(geometry.signedTurn()) <= 8.0
                && geometry.consistency() >= 0.75 && geometry.maximumTurn() < 1.1) {
            return type;
        }
        if (type == MapGesture.Type.STAR
                && (geometry.intersections() >= 3 || geometry.peaks() >= 4)) {
            return type;
        }
        return MapGesture.Type.UNRECOGNIZED;
    }

    /** 속도·이벤트 수와 관계없이 일정 거리로 표본화한 도형의 크기·회전·교차를 계산한다. */
    private Geometry analyze(List<StrokePoint> points, float minimumSize) {
        if (points == null || points.size() < 4 || !Float.isFinite(minimumSize) || minimumSize <= 0) {
            return null;
        }
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        double length = 0;
        for (int index = 0; index < points.size(); index++) {
            StrokePoint point = points.get(index);
            if (point == null || !Float.isFinite(point.x()) || !Float.isFinite(point.y())) {
                return null;
            }
            left = Math.min(left, point.x());
            top = Math.min(top, point.y());
            right = Math.max(right, point.x());
            bottom = Math.max(bottom, point.y());
            if (index > 0) {
                length += distance(points.get(index - 1), point);
            }
        }
        double width = right - left;
        double height = bottom - top;
        double diagonal = Math.hypot(width, height);
        if (Math.min(width, height) < minimumSize || length < minimumSize * 4.0
                || Math.min(width, height) / Math.max(width, height) < 0.35) {
            return null;
        }

        List<StrokePoint> sampled = resample(points, length);
        double signedTurn = 0;
        double absoluteTurn = 0;
        double maximumTurn = 0;
        for (int index = 1; index < sampled.size() - 1; index++) {
            StrokePoint first = sampled.get(index - 1);
            StrokePoint middle = sampled.get(index);
            StrokePoint last = sampled.get(index + 1);
            double firstAngle = Math.atan2(middle.y() - first.y(), middle.x() - first.x());
            double secondAngle = Math.atan2(last.y() - middle.y(), last.x() - middle.x());
            double turn = Math.atan2(Math.sin(secondAngle - firstAngle), Math.cos(secondAngle - firstAngle));
            signedTurn += turn;
            absoluteTurn += Math.abs(turn);
            maximumTurn = Math.max(maximumTurn, Math.abs(turn));
        }
        double consistency = absoluteTurn == 0 ? 0 : Math.abs(signedTurn) / absoluteTurn;
        return new Geometry(distance(points.get(0), points.get(points.size() - 1)) / diagonal,
                length / diagonal, signedTurn, consistency, maximumTurn,
                countIntersections(sampled), countPeaks(sampled, (left + right) / 2, (top + bottom) / 2));
    }

    /** 입력 점을 정해진 개수로 보간해 매 이동의 교차 계산 비용을 제한한다. */
    private List<StrokePoint> resample(List<StrokePoint> points, double length) {
        List<StrokePoint> sampled = new ArrayList<>(SAMPLE_SEGMENTS + 1);
        sampled.add(points.get(0));
        int segmentIndex = 1;
        double traversed = 0;
        double segmentLength = distance(points.get(0), points.get(1));
        for (int sampleIndex = 1; sampleIndex < SAMPLE_SEGMENTS; sampleIndex++) {
            double target = length * sampleIndex / SAMPLE_SEGMENTS;
            while (segmentIndex < points.size() - 1 && traversed + segmentLength < target) {
                traversed += segmentLength;
                segmentIndex++;
                segmentLength = distance(points.get(segmentIndex - 1), points.get(segmentIndex));
            }
            StrokePoint first = points.get(segmentIndex - 1);
            StrokePoint last = points.get(segmentIndex);
            double fraction = segmentLength == 0 ? 0 : (target - traversed) / segmentLength;
            sampled.add(new StrokePoint((float) (first.x() + (last.x() - first.x()) * fraction),
                    (float) (first.y() + (last.y() - first.y()) * fraction)));
        }
        sampled.add(points.get(points.size() - 1));
        return sampled;
    }

    /** 인접 표본과 단순 접촉을 제외한 선분 내부 교차만 센다. */
    private int countIntersections(List<StrokePoint> sampled) {
        int intersections = 0;
        for (int first = 0; first < sampled.size() - 1; first++) {
            for (int second = first + 3; second < sampled.size() - 1; second++) {
                StrokePoint a = sampled.get(first);
                StrokePoint b = sampled.get(first + 1);
                StrokePoint c = sampled.get(second);
                StrokePoint d = sampled.get(second + 1);
                if (cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0) {
                    intersections++;
                }
            }
        }
        return intersections;
    }

    /** 내부 교차가 없는 윤곽선 별의 바깥 꼭짓점 수를 중심 거리의 고저 변화로 확인한다. */
    private int countPeaks(List<StrokePoint> sampled, double centerX, double centerY) {
        double maximum = 0;
        double minimum = Double.POSITIVE_INFINITY;
        for (StrokePoint point : sampled) {
            double radius = Math.hypot(point.x() - centerX, point.y() - centerY);
            maximum = Math.max(maximum, radius);
            minimum = Math.min(minimum, radius);
        }
        if (minimum > maximum * 0.65) {
            return 0;
        }
        int peaks = 0;
        boolean high = false;
        for (StrokePoint point : sampled) {
            double radius = Math.hypot(point.x() - centerX, point.y() - centerY);
            if (!high && radius > maximum * 0.8) {
                peaks++;
                high = true;
            } else if (radius < maximum * 0.6) {
                high = false;
            }
        }
        return peaks;
    }

    /** 두 점의 화면상 거리를 구한다. */
    private double distance(StrokePoint first, StrokePoint last) {
        return Math.hypot(last.x() - first.x(), last.y() - first.y());
    }

    /** 기준 선분에 대한 점의 방향을 부호로 계산한다. */
    private double cross(StrokePoint first, StrokePoint last, StrokePoint point) {
        return (last.x() - first.x()) * (point.y() - first.y())
                - (last.y() - first.y()) * (point.x() - first.x());
    }

    /** 지도 이동과 도형을 구별하는 데 필요한 계산값만 보관한다. */
    private record Geometry(double closure, double ratio, double signedTurn, double consistency,
                            double maximumTurn, int intersections, int peaks) {
    }
}
