package com.example.pleosstarter.kakao;

import android.graphics.Matrix;
import android.util.Log;
import android.view.MotionEvent;

import com.kakaomobility.knsdk.common.util.FloatPoint;
import com.kakaomobility.knsdk.map.knmaprenderer.objects.KNMapCameraUpdate;
import com.kakaomobility.knsdk.map.knmapview.KNMapView;

/** 두 손가락 조작을 카카오 기본 기울기 제스처와 분리해 이동·확대·회전으로 적용한다. */
final class TwoFingerMapCamera {
    private final KNMapView map;
    private final Runnable onStarted;
    private TwoFingerCameraTransform transform;
    private TwoFingerCameraTransform.Change previous;
    private FloatPoint initialFocus;
    private Matrix initialProjection;
    private float initialZoom;
    private float initialBearing;
    private Runnable pendingValidation;
    private float tilt;
    private int firstPointerId;
    private int secondPointerId;
    private boolean active;

    /** 지도와 조작 시작 알림을 주입하며 위치 공급이나 저장 기능에는 관여하지 않는다. */
    TwoFingerMapCamera(KNMapView map, Runnable onStarted) {
        this.map = map;
        this.onStarted = onStarted;
    }

    /** 두 손가락 스트림만 소비하고 짧은 단일 탭은 기존 SDK 선택기로 보낸다. */
    boolean onTouch(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() == 2 && !active) {
            begin(event);
            return true;
        }
        if (!active) {
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 2) {
            update(event);
        } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_POINTER_UP || event.getPointerCount() != 2) {
            // Android의 MOVE 보간보다 실제 손을 뗀 좌표를 마지막 위치로 확정한다.
            if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() == 2) {
                update(event);
            }
            Log.i("PlaceMap", "TWO_FINGER_CAMERA ended zoom=" + map.getZoom()
                    + " bearing=" + map.getBearing() + " tilt=" + map.getTilt());
            FloatPoint focus = initialFocus;
            TwoFingerCameraTransform.Change expected = previous;
            reset();
            if (focus != null && expected != null) {
                pendingValidation = () -> {
                    pendingValidation = null;
                    FloatPoint projected = map.katecToScreen(focus);
                    if (validCoordinate(projected)) {
                        Log.i("PlaceMap", "TWO_FINGER_FOCUS expected=" + expected.centerX() + "," + expected.centerY()
                                + " actual=" + projected.getX() + "," + projected.getY()
                                + " zoom=" + map.getZoom() + " expectedZoom=" + expected.zoom()
                                + " bearing=" + map.getBearing() + " expectedBearing=" + expected.bearing()
                                + " tilt=" + map.getTilt());
                    }
                };
                map.postDelayed(pendingValidation, 150);
            }
        }
        return true;
    }

    /** 화면 종료나 손가락 해제 뒤에는 이전 입력을 다음 조작에 이어 붙이지 않는다. */
    void reset() {
        if (pendingValidation != null) {
            map.removeCallbacks(pendingValidation);
            pendingValidation = null;
        }
        active = false;
        transform = null;
        previous = null;
        initialFocus = null;
        initialProjection = null;
    }

    /** 두 포인터의 최초 배율·각도·중심을 보관하고 SDK의 단일 탭 대기를 취소한다. */
    private void begin(MotionEvent event) {
        // 첫 포인터 DOWN을 받은 SDK의 탭·길게 누르기 대기를 명시적으로 종료한다.
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        try {
            map.onTouchEvent(cancel);
        } finally {
            cancel.recycle();
        }

        active = true;
        onStarted.run();
        firstPointerId = event.getPointerId(0);
        secondPointerId = event.getPointerId(1);
        float firstX = event.getX(0);
        float firstY = event.getY(0);
        float secondX = event.getX(1);
        float secondY = event.getY(1);
        if (!validPoint(firstX, firstY) || !validPoint(secondX, secondY)
                || Math.hypot(secondX - firstX, secondY - firstY) < 8.0) {
            return;
        }

        FloatPoint projected = map.screenToKatec(new FloatPoint(
                (firstX + secondX) / 2, (firstY + secondY) / 2));
        if (projected == null || !Float.isFinite(projected.getX()) || !Float.isFinite(projected.getY())) {
            Log.w("PlaceMap", "TWO_FINGER_CAMERA projection_unavailable");
            return;
        }
        tilt = map.getTilt();
        initialFocus = new FloatPoint(projected.getX(), projected.getY());
        initialZoom = map.getZoom();
        initialBearing = map.getBearing();
        initialProjection = captureProjection();
        if (initialProjection == null) {
            Log.w("PlaceMap", "TWO_FINGER_CAMERA projection_unavailable");
            return;
        }
        transform = new TwoFingerCameraTransform(firstX, firstY, secondX, secondY,
                initialZoom, initialBearing);
        previous = transform.update(firstX, firstY, secondX, secondY);
        Log.i("PlaceMap", "TWO_FINGER_CAMERA started zoom=" + map.getZoom()
                + " bearing=" + map.getBearing() + " tilt=" + tilt);
    }

    /** 포인터 순서가 바뀌어도 ID로 추적하며 공개 카메라 API에 한 번에 반영한다. */
    private void update(MotionEvent event) {
        if (transform == null || map.getWidth() <= 0 || map.getHeight() <= 0) {
            return;
        }
        int firstIndex = event.findPointerIndex(firstPointerId);
        int secondIndex = event.findPointerIndex(secondPointerId);
        if (firstIndex < 0 || secondIndex < 0) {
            return;
        }
        float firstX = event.getX(firstIndex);
        float firstY = event.getY(firstIndex);
        float secondX = event.getX(secondIndex);
        float secondY = event.getY(secondIndex);
        if (!validPoint(firstX, firstY) || !validPoint(secondX, secondY)) {
            return;
        }

        TwoFingerCameraTransform.Change change = transform.update(firstX, firstY, secondX, secondY);
        float[] offset = { change.centerX(), change.centerY() };
        initialProjection.mapPoints(offset);
        if (!Float.isFinite(offset[0]) || !Float.isFinite(offset[1])) {
            return;
        }

        // 최초 화면의 역투영에 전체 변환을 적용하므로 렌더링 지연 중 입력도 누락되지 않는다.
        double ratio = change.zoom() / initialZoom;
        double angle = Math.toRadians(initialBearing - change.bearing());
        double offsetX = offset[0] * ratio;
        double offsetY = offset[1] * ratio;
        FloatPoint corrected = new FloatPoint(
                (float) (initialFocus.getX() - offsetX * Math.cos(angle) + offsetY * Math.sin(angle)),
                (float) (initialFocus.getY() - offsetX * Math.sin(angle) - offsetY * Math.cos(angle)));
        if (!validCoordinate(corrected)) {
            return;
        }

        // 앵커와 기울기는 유지하고 이동·배율·방향을 하나의 명령으로 반영한다.
        map.moveCamera(KNMapCameraUpdate.Creator.targetTo(corrected)
                .zoomTo(change.zoom()).bearingTo(change.bearing()).tiltTo(tilt), false, false);
        previous = change;
    }

    /** 기울어진 지도 평면의 역투영을 고정해 비동기 SDK 카메라 값을 다시 누적하지 않는다. */
    private Matrix captureProjection() {
        FloatPoint anchor = map.getAnchor();
        FloatPoint origin = map.screenToKatec(new FloatPoint(
                map.getWidth() * anchor.getX(), map.getHeight() * (1 - anchor.getY())));
        if (!validCoordinate(origin)) {
            return null;
        }

        // 큰 절대 지도 좌표 대신 카메라 중심 상대값을 사용해 단정밀도 오차를 줄인다.
        float left = map.getWidth() * 0.2f;
        float right = map.getWidth() * 0.8f;
        float top = map.getHeight() * 0.2f;
        float bottom = map.getHeight() * 0.8f;
        float[] screen = { left, top, right, top, right, bottom, left, bottom };
        float[] ground = new float[8];
        for (int index = 0; index < 4; index++) {
            FloatPoint sample = map.screenToKatec(new FloatPoint(screen[index * 2], screen[index * 2 + 1]));
            if (!validCoordinate(sample)) {
                return null;
            }
            ground[index * 2] = sample.getX() - origin.getX();
            ground[index * 2 + 1] = sample.getY() - origin.getY();
        }

        Matrix projection = new Matrix();
        if (!projection.setPolyToPoly(screen, 0, ground, 0, 4)) {
            return null;
        }
        return projection;
    }

    /** SDK 역투영 결과가 실제 카메라 명령에 사용할 수 있는지 확인한다. */
    private boolean validCoordinate(FloatPoint coordinate) {
        return coordinate != null && Float.isFinite(coordinate.getX()) && Float.isFinite(coordinate.getY());
    }

    /** 지도 밖 또는 유효하지 않은 좌표를 카메라 명령으로 사용하지 않는다. */
    private boolean validPoint(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y)
                && x >= 0 && y >= 0 && x <= map.getWidth() && y <= map.getHeight();
    }
}
