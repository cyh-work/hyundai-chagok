package com.example.pleosstarter.gesture;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import com.example.pleosstarter.map.GeometricGestureClassifier;
import com.example.pleosstarter.map.MapGesture;
import java.util.ArrayList;
import java.util.List;

/** 실제 입력 레이어의 자식 전달·수명 주기·분류 계약을 Android 경계 대역으로 검사한다. */
public final class FingerRoutingProbe {
    private static int assertions;
    private static int scenarios;

    /** 실행할 회귀 사례와 결과를 한 곳에서 관리한다. */
    public static void main(String[] args) {
        tapAndDoubleTap();
        lineNeverPans();
        circleAndStar();
        twoFingerAndTail();
        drawingToTwoFinger();
        cancelledAndFocusLost();
        malformedInput();
        overflowAndLongPress();
        excludedCard();
        thirdFingerAndChangedId();
        System.out.println("PASS scenarios=" + scenarios + " assertions=" + assertions);
        System.out.println("Actual MapGestureInputLayout + classifiers; Android dispatch/event/render boundary stubs.");
        System.out.println("No SDK camera, Android hit-test, microphone, or emulator claim.");
    }

    /** 두 번 탭해도 MOVE 없이 원래 DOWN/UP 시각과 좌표만 전달한다. */
    private static void tapAndDoubleTap() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 7, 200, 200);
        check(fixture.view.childEvents.isEmpty(), "tap down is buffered");
        fixture.one(2, 120, 7, 203, 201);
        check(fixture.view.childEvents.isEmpty(), "tap jitter is buffered");
        fixture.one(1, 140, 7, 203, 201);
        fixture.one(0, 230, 7, 202, 201);
        fixture.one(1, 260, 7, 202, 201);
        List<MotionEvent> events = fixture.view.childEvents;
        check(events.size() == 4, "double tap has only four edges");
        check(events.get(0).getActionMasked() == 0 && events.get(1).getActionMasked() == 1,
                "first tap down/up sequence");
        check(events.get(0).getEventTime() == 100 && events.get(1).getEventTime() == 140,
                "first tap timing preserved");
        check(events.get(2).getEventTime() == 230 && events.get(3).getEventTime() == 260,
                "second tap timing preserved");
        check(fixture.listener.pointerDown == 2 && fixture.listener.finished == 2, "tap holds paired");
        check(fixture.listener.pan == 0 && fixture.listener.started == 0, "tap never claims pan or stroke");
        noSinglePointerMoves(fixture);
        pass("tap and double tap");
    }

    /** 인식하지 못한 직선도 지도 드래그가 되지 않고 그리는 동안 선이 보인다. */
    private static void lineNeverPans() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 0, 100, 100);
        fixture.one(2, 150, 0, 130, 100);
        Canvas canvas = new Canvas();
        fixture.view.draw(canvas);
        check(canvas.paths == 2, "trail visible from first meaningful move");
        fixture.one(2, 180, 0, 500, 100);
        fixture.one(1, 200, 0, 500, 100);
        check(fixture.view.childEvents.isEmpty(), "single finger line never reaches map");
        check(fixture.listener.pan == 0, "single finger never notifies pan");
        check(fixture.listener.started == 1 && fixture.listener.finished == 1, "line lifetime paired");
        check(fixture.listener.gestures.isEmpty(), "line does not save");
        pass("line and early trail");
    }

    /** 실제 기존 분류기를 이용해 원과 교차 별만 완성 시 한 번 저장한다. */
    private static void circleAndStar() {
        Fixture circle = new Fixture();
        drawCircle(circle);
        check(circle.listener.gestures.size() == 1, "circle saved once");
        check(circle.listener.gestures.get(0).type() == MapGesture.Type.CIRCLE, "circle classified");
        check(circle.listener.pan == 0 && circle.view.childEvents.isEmpty(), "circle keeps map still");
        check(Math.abs(circle.listener.gestures.get(0).centerX() - 500) < 0.01,
                "circle original screen center retained");
        Fixture star = new Fixture();
        int[] vertices = {0, 2, 4, 1, 3, 0};
        float startX = 500;
        float startY = 400;
        star.one(0, 100, 0, startX, startY);
        long time = 100;
        for (int side = 1; side < vertices.length; side++) {
            double firstAngle = -Math.PI / 2 + vertices[side - 1] * Math.PI * 2 / 5;
            double lastAngle = -Math.PI / 2 + vertices[side] * Math.PI * 2 / 5;
            for (int step = 1; step <= 16; step++) {
                double fraction = step / 16.0;
                float x = (float) (500 + 100 * (Math.cos(firstAngle) * (1 - fraction)
                        + Math.cos(lastAngle) * fraction));
                float y = (float) (500 + 100 * (Math.sin(firstAngle) * (1 - fraction)
                        + Math.sin(lastAngle) * fraction));
                star.one(2, time += 10, 0, x, y);
            }
        }
        star.one(1, time + 10, 0, startX, startY);
        check(star.listener.gestures.size() == 1, "star saved once");
        check(star.listener.gestures.get(0).type() == MapGesture.Type.STAR, "star classified");
        check(star.view.childEvents.isEmpty() && star.listener.pan == 0, "star keeps map still");
        check(star.listener.finished == 1 && circle.listener.finished == 1, "shapes finish once");
        pass("real circle and star classification");
    }

    /** 원형 입력을 일정한 화면 좌표로 만든다. */
    private static void drawCircle(Fixture fixture) {
        fixture.one(0, 100, 0, 600, 500);
        for (int index = 1; index <= 64; index++) {
            double angle = index * Math.PI * 2 / 64;
            fixture.one(2, 100 + index * 10, 0,
                    (float) (500 + 100 * Math.cos(angle)), (float) (500 + 100 * Math.sin(angle)));
        }
        fixture.one(1, 760, 0, 600, 500);
    }

    /** 두 손가락 이동 이후 한 손가락 꼬리는 다시 지도나 도형으로 처리하지 않는다. */
    private static void twoFingerAndTail() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 7, 200, 200);
        fixture.multi(5 | (1 << 8), 180, new int[]{7, 9}, 220, 200, 400, 200);
        fixture.multi(2, 200, new int[]{7, 9}, 250, 220, 440, 210);
        fixture.multi(6, 230, new int[]{7, 9}, 250, 220, 440, 210);
        fixture.one(2, 250, 9, 700, 600);
        fixture.one(1, 270, 9, 700, 600);
        List<MotionEvent> events = fixture.view.childEvents;
        check(events.size() == 5, "map stream has down pointerdown move pointerup cancel");
        check(events.get(0).getActionMasked() == 0 && events.get(0).getPointerId(0) == 7,
                "synthetic down uses original pointer");
        check(events.get(0).getX() == 220 && events.get(0).getDownTime() == 180,
                "synthetic down uses transition position and time");
        check(events.get(1).getActionMasked() == 5 && events.get(2).getPointerCount() == 2,
                "map receives real two pointer move");
        check(events.get(4).getActionMasked() == 3 && events.get(4).getPointerId(0) == 9,
                "remaining pointer receives cancel");
        check(fixture.listener.pan == 1 && fixture.listener.gestures.isEmpty(), "multitouch is exploration only");
        check(fixture.listener.finished == 1, "multitouch lifetime ends once");
        noSinglePointerMoves(fixture);
        pass("two fingers and remaining finger");
    }

    /** 도형을 그리다 두 손가락을 더하면 도형을 폐기하고 새로운 지도 접촉으로 전환한다. */
    private static void drawingToTwoFinger() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 3, 100, 100);
        fixture.one(2, 130, 3, 180, 200);
        fixture.multi(5, 160, new int[]{8, 3}, 400, 200, 180, 200);
        fixture.multi(2, 200, new int[]{8, 3}, 420, 200, 200, 200);
        fixture.multi(3, 210, new int[]{8, 3}, 420, 200, 200, 200);
        check(fixture.listener.abandoned == 1, "drawing cancelled on second finger");
        check(fixture.listener.pan == 1 && fixture.listener.gestures.isEmpty(), "abandoned shape not saved");
        check(fixture.view.childEvents.get(0).getPointerId(0) == 3, "reordered primary id preserved");
        check(fixture.listener.finished == 1, "cancel ends hold");
        noSinglePointerMoves(fixture);
        pass("drawing to two fingers");
    }

    /** CANCEL과 포커스 이탈은 완성 저장과 늦은 UP을 모두 차단한다. */
    private static void cancelledAndFocusLost() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 0, 100, 100);
        fixture.one(2, 120, 0, 150, 150);
        fixture.one(3, 130, 0, 150, 150);
        fixture.one(1, 150, 0, 100, 100);
        check(fixture.listener.finished == 1 && fixture.listener.gestures.isEmpty(), "cancel no late save");
        Fixture focus = new Fixture();
        focus.one(0, 100, 0, 100, 100);
        focus.multi(5 | (1 << 8), 150, new int[]{0, 1}, 100, 100, 300, 100);
        focus.view.onWindowFocusChanged(false);
        focus.one(1, 200, 0, 100, 100);
        check(focus.listener.finished == 1, "focus loss releases hold");
        check(focus.view.childEvents.get(2).getActionMasked() == 3, "focus loss cancels map");
        check(focus.listener.gestures.isEmpty(), "focus loss no save");
        pass("cancel and background focus loss");
    }

    /** 잘못된 포인터·범위·NaN이 있으면 저장하지 않고 종료 시 보류를 푼다. */
    private static void malformedInput() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 0, 100, 100);
        fixture.one(2, 130, 4, 130, 150);
        fixture.one(1, 150, 4, 100, 100);
        check(fixture.listener.gestures.isEmpty() && fixture.view.childEvents.isEmpty(), "changed id rejected");
        check(fixture.listener.finished == 1, "changed id hold released");
        Fixture invalid = new Fixture();
        invalid.one(0, 100, 0, 100, 100);
        invalid.one(2, 120, 0, 200, 200);
        invalid.one(1, 150, 0, Float.NaN, 200);
        check(invalid.listener.finished == 1, "invalid up releases hold");
        check(invalid.listener.gestures.isEmpty(), "invalid up no save");
        Fixture outside = new Fixture();
        outside.one(0, 100, 0, -1, 100);
        outside.one(1, 150, 0, 100, 100);
        check(outside.listener.pointerDown == 0 && outside.view.childEvents.isEmpty(), "outside down rejected");
        pass("invalid coordinates and pointer changes");
    }

    /** 과도한 점 수와 오래 누르기가 새로운 기록이나 지도 이동을 만들지 않는다. */
    private static void overflowAndLongPress() {
        Fixture overflow = new Fixture();
        overflow.one(0, 100, 0, 100, 100);
        for (int index = 0; index < 2100; index++) {
            overflow.one(2, 110 + index, 0, index % 2 == 0 ? 200 : 220, 200);
        }
        overflow.one(1, 2300, 0, 100, 100);
        check(overflow.listener.gestures.isEmpty() && overflow.view.childEvents.isEmpty(), "point limit no save or pan");
        check(overflow.listener.finished == 1, "point limit hold released");
        Fixture hold = new Fixture();
        hold.one(0, 100, 0, 100, 100);
        hold.one(1, 900, 0, 100, 100);
        check(hold.view.childEvents.isEmpty(), "long hold does not replay late long press");
        check(hold.listener.finished == 1, "long hold ends");
        pass("point limit and long hold");
    }

    /** 카드 영역에서 시작한 입력은 자식 카드의 스크롤·클릭 경로 그대로 전달한다. */
    private static void excludedCard() {
        Fixture fixture = new Fixture();
        fixture.listener.excluded = true;
        fixture.one(0, 100, 0, 100, 100);
        fixture.one(2, 150, 0, 100, 300);
        fixture.one(1, 180, 0, 100, 300);
        check(fixture.view.childEvents.size() == 3, "excluded card full stream preserved");
        check(fixture.listener.pointerDown == 0 && fixture.listener.gestures.isEmpty(), "card bypasses drawing hold");
        fixture.listener.excluded = false;
        fixture.one(0, 200, 0, 100, 100);
        fixture.one(2, 240, 0, 300, 100);
        fixture.one(1, 280, 0, 300, 100);
        check(fixture.view.childEvents.size() == 3, "next map single stroke remains blocked");
        pass("excluded memo card and next input");
    }

    /** 두 손가락 계약을 벗어나면 자식 입력을 취소하고 한 손가락 꼬리를 소비한다. */
    private static void thirdFingerAndChangedId() {
        Fixture third = new Fixture();
        third.one(0, 100, 0, 100, 100);
        third.multi(5 | (1 << 8), 150, new int[]{0, 1}, 100, 100, 300, 100);
        third.multi(5 | (2 << 8), 180, new int[]{0, 1, 2}, 100, 100, 300, 100, 400, 100);
        third.one(2, 200, 0, 300, 400);
        third.one(1, 220, 0, 300, 400);
        check(third.view.childEvents.size() == 3, "third finger causes cancel not forwarding");
        check(third.view.childEvents.get(2).getActionMasked() == 3, "third finger map cancel");
        check(third.listener.finished == 1 && third.listener.gestures.isEmpty(), "third finger no save");
        Fixture changed = new Fixture();
        changed.one(0, 100, 0, 100, 100);
        changed.multi(5 | (1 << 8), 150, new int[]{0, 1}, 100, 100, 300, 100);
        changed.multi(2, 180, new int[]{0, 4}, 150, 100, 350, 100);
        changed.one(1, 220, 0, 150, 100);
        check(changed.view.childEvents.size() == 3, "map pointer mutation cancels");
        check(changed.listener.finished == 1, "map pointer mutation releases hold");
        noSinglePointerMoves(third);
        noSinglePointerMoves(changed);
        pass("third finger and map id change");
    }

    /** 지도 경계에 전달한 이벤트 중 단일 포인터 MOVE가 없음을 직접 검사한다. */
    private static void noSinglePointerMoves(Fixture fixture) {
        for (MotionEvent event : fixture.view.childEvents) {
            check(event.getActionMasked() != 2 || event.getPointerCount() == 2,
                    "map never receives single pointer MOVE");
        }
    }

    /** 실패 원인을 구체적인 라우팅 계약으로 보고한다. */
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 완료한 시나리오를 결과 파일에 남긴다. */
    private static void pass(String name) {
        scenarios++;
        System.out.println("PASS " + name);
    }

    /** 실제 제품 입력 뷰와 관찰 가능한 수신자를 구성한다. */
    private static final class Fixture {
        final Recorder listener = new Recorder();
        final MapGestureInputLayout view = new MapGestureInputLayout(new Context(),
                new GeometricGestureClassifier(), listener);
        private long downTime;

        /** 단일 포인터 입력을 제품 dispatch에 전달한다. */
        void one(int action, long time, int id, float x, float y) {
            multi(action, time, new int[]{id}, x, y);
        }

        /** 다중 포인터 입력과 원래 접촉 시각을 제품 dispatch에 전달한다. */
        void multi(int action, long time, int[] ids, float... xy) {
            if ((action & 255) == 0) {
                downTime = time;
            }
            MotionEvent event = MotionEvent.test(downTime, time, action, ids, xy);
            view.dispatchTouchEvent(event);
            event.recycle();
        }
    }

    /** 저장·보류·소유권 전환 콜백을 실제 호출 순서로 관찰한다. */
    private static final class Recorder implements MapGestureInputLayout.Listener {
        int pointerDown;
        int pan;
        int started;
        int abandoned;
        int finished;
        boolean excluded;
        final List<MapStrokeGesture> gestures = new ArrayList<>();

        /** 카드 제외 영역을 시나리오에서 제어한다. */
        @Override
        public boolean isDrawingExcluded(float x, float y) {
            return excluded;
        }
        /** 접촉 보류의 시작 횟수를 센다. */
        @Override
        public void onPointerDown() {
            pointerDown++;
        }
        /** 실제 두 손가락 탐색 시작을 센다. */
        @Override
        public void onMapPanStarted() {
            pan++;
        }
        /** 실제 그리기 시작을 센다. */
        @Override
        public void onStrokeStarted() {
            started++;
        }
        /** 멀티터치로 폐기한 도형을 센다. */
        @Override
        public void onShapeAbandoned() {
            abandoned++;
        }
        /** 전체 접촉 종료를 센다. */
        @Override
        public void onStrokeFinished() {
            finished++;
        }
        /** 제품 분류기가 확정한 기록만 수집한다. */
        @Override
        public void onGesture(MapStrokeGesture gesture) {
            gestures.add(gesture);
        }
    }
}
