package com.example.pleosstarter.kakao;

/** 실제 카메라 수학 클래스를 독립 기대값과 비교하며 Android 렌더링은 흉내 내지 않는다. */
public final class TwoFingerCameraProbe {
    private static int assertions;

    /** 이동·배율·각도·경계와 비정상 입력의 검사를 실행한다. */
    public static void main(String[] arguments) {
        translation();
        scalingAndBounds();
        rotationAndCrossing();
        composition();
        minimumSpan();
        invalidInputs();
        System.out.println("PASS CameraTransform assertions=" + assertions);
        finiteExtremeObservation();
        System.out.println("Actual TwoFingerCameraTransform; no Android, SDK camera or real touch claim.");
    }

    /** 같은 이동량은 중점만 바꾸며 배율과 방위각을 보존해야 한다. */
    private static void translation() {
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(100, 100, 300, 100, 2, 37);
        checkChange(transform.update(175, 220, 375, 220), 275, 220, 2, 37, "parallel pan");
        checkChange(transform.update(100, 100, 300, 100), 200, 100, 2, 37, "return to initial");
        checkChange(transform.update(-25, -90, 175, -90), 75, -90, 2, 37, "pure signed translation");
    }

    /** 손가락 간격에 반비례하는 배율과 SDK 허용 최소·최대값을 확인한다. */
    private static void scalingAndBounds() {
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(100, 200, 300, 200, 2, 0);
        checkChange(transform.update(0, 200, 400, 200), 200, 200, 1, 0, "pinch out 2x");
        checkChange(transform.update(150, 200, 250, 200), 200, 200, 4, 0, "pinch in half");
        near(new TwoFingerCameraTransform(0, 0, 10, 0, 0.16f, 0)
                .update(0, 0, 1000, 0).zoom(), 0.16, "minimum zoom clamp");
        near(new TwoFingerCameraTransform(0, 0, 100, 0, 5000, 0)
                .update(0, 0, 8, 0).zoom(), 5000, "maximum zoom clamp");
        near(new TwoFingerCameraTransform(0, 0, 100, 0, 0.01f, 0)
                .update(0, 0, 100, 0).zoom(), 0.16, "positive low initial clamp");
        near(new TwoFingerCameraTransform(0, 0, 100, 0, 6000, 0)
                .update(0, 0, 100, 0).zoom(), 5000, "high initial clamp");
    }

    /** 화면 시계 방향 회전의 부호 및 +180/-180 경계에서 같은 방위를 확인한다. */
    private static void rotationAndCrossing() {
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(0, 0, 100, 0, 1, 20);
        near(transform.update(0, 0, 0, 100).bearing(), -70, "screen clockwise quarter");
        near(transform.update(0, 0, 0, -100).bearing(), 110, "screen counterclockwise quarter");
        near(transform.update(0, 0, -100, 0).bearing(), -160, "half turn");

        float[] first = vector(179, 100);
        float[] next = vector(-179, 100);
        TwoFingerCameraTransform crossing = new TwoFingerCameraTransform(0, 0, first[0], first[1], 1, 40);
        near(crossing.update(0, 0, next[0], next[1]).bearing(), 38, "179 to -179");
        TwoFingerCameraTransform reverse = new TwoFingerCameraTransform(0, 0, next[0], next[1], 1, 40);
        near(reverse.update(0, 0, first[0], first[1]).bearing(), 42, "-179 to 179");
        near(new TwoFingerCameraTransform(0, 0, 100, 0, 1, 179)
                .update(0, 0, vector(-5, 100)[0], vector(-5, 100)[1]).bearing(), -176,
                "bearing wrap");
        near(new TwoFingerCameraTransform(0, 0, 100, 0, 1, 740)
                .update(0, 0, 100, 0).bearing(), 20, "initial bearing normalization");
    }

    /** 이동·배율·회전을 동시에 적용해도 최초 접촉을 기준으로 계산하는지 확인한다. */
    private static void composition() {
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(50, 90, 150, 90, 3, -35);
        float[] vector = vector(60, 250);
        checkChange(transform.update(430 - vector[0] / 2, 220 - vector[1] / 2,
                430 + vector[0] / 2, 220 + vector[1] / 2), 430, 220, 1.2, -95, "combined gesture");
        transform.update(0, 0, 1000, 0);
        checkChange(transform.update(50, 90, 150, 90), 100, 90, 3, -35,
                "intermediate updates cannot accumulate error");
    }

    /** 최초 8px 미만은 거부하고 현재 간격의 최소값 보정을 확인한다. */
    private static void minimumSpan() {
        rejects(() -> new TwoFingerCameraTransform(0, 0, 7.999f, 0, 1, 0), "initial under 8px");
        TwoFingerCameraTransform exact = new TwoFingerCameraTransform(0, 0, 8, 0, 1, 0);
        near(exact.update(0, 0, 8, 0).zoom(), 1, "initial exact 8px");
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(0, 0, 80, 0, 1, 0);
        near(transform.update(0, 0, 8, 0).zoom(), 10, "updated exact 8px");
        near(transform.update(0, 0, 7.9f, 0).zoom(), 10, "updated under 8px clamp");
        near(transform.update(0, 0, 0, 0).zoom(), 10, "coincident fingers finite zoom");
        rejects(() -> new TwoFingerCameraTransform(10, 10, 10, 10, 1, 0), "initial coincident fingers");
    }

    /** 수치가 아닌 값과 양수가 아닌 최초 배율은 계산 전에 거부해야 한다. */
    private static void invalidInputs() {
        float[] nonFinite = {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (float invalid : nonFinite) {
            for (int slot = 0; slot < 6; slot++) {
                float[] components = {10, 20, 110, 20, 1, 0};
                components[slot] = invalid;
                rejects(() -> new TwoFingerCameraTransform(components[0], components[1], components[2],
                        components[3], components[4], components[5]), "nonfinite initial component " + slot);
            }
            TwoFingerCameraTransform transform = new TwoFingerCameraTransform(10, 20, 110, 20, 1, 0);
            for (int slot = 0; slot < 4; slot++) {
                float[] components = {10, 20, 110, 20};
                components[slot] = invalid;
                rejects(() -> transform.update(components[0], components[1], components[2], components[3]),
                        "nonfinite update component " + slot);
            }
        }
        rejects(() -> new TwoFingerCameraTransform(0, 0, 100, 0, 0, 0), "zero zoom");
        rejects(() -> new TwoFingerCameraTransform(0, 0, 100, 0, -1, 0), "negative zoom");
    }

    /** 화면 경계 밖 유한 극단값은 별도 관찰로 남겨 실제 화면 입력과 구분한다. */
    private static void finiteExtremeObservation() {
        TwoFingerCameraTransform transform = new TwoFingerCameraTransform(
                -Float.MAX_VALUE, 0, Float.MAX_VALUE, 0, 1, 0);
        TwoFingerCameraTransform.Change change = transform.update(-Float.MAX_VALUE, 0, Float.MAX_VALUE, 0);
        System.out.println("OBSERVATION finite extreme zoom=" + change.zoom()
                + "; impossible in bounded Android view input, not a renderer failure.");
    }

    /** 독립한 삼각함수로 기대 손가락 벡터를 만든다. */
    private static float[] vector(double degrees, double length) {
        double radians = Math.toRadians(degrees);
        return new float[]{(float) (Math.cos(radians) * length), (float) (Math.sin(radians) * length)};
    }

    /** 변화 DTO의 네 값을 각각 기대값과 비교한다. */
    private static void checkChange(TwoFingerCameraTransform.Change change, double x, double y,
            double zoom, double bearing, String label) {
        near(change.centerX(), x, label + " centerX");
        near(change.centerY(), y, label + " centerY");
        near(change.zoom(), zoom, label + " zoom");
        near(change.bearing(), bearing, label + " bearing");
    }

    /** 유한한 결과와 부동소수점 허용 오차를 검사한다. */
    private static void near(float actual, double expected, String label) {
        assertions++;
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > 0.001) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }

    /** 입력 검증 실패가 명시적인 예외로 반환되는지 확인한다. */
    private static void rejects(Runnable action, String label) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + " expected IllegalArgumentException");
    }
}
