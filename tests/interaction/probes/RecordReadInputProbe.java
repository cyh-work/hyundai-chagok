package com.example.pleosstarter.gesture;

import android.content.Context;
import android.view.MotionEvent;
import com.example.pleosstarter.map.GeometricGestureClassifier;
import com.example.pleosstarter.map.MapGesture;
import java.util.ArrayList;
import java.util.List;

/** 실제 입력 뷰에서 기록 모달의 짧은 탭 소비와 기존 입력 분리를 검사한다. */
public final class RecordReadInputProbe {
    private static int assertions;
    private static int scenarios;

    /** SDK 선택 대역과 모달 포커스 재진입을 포함한 회귀 사례를 실행한다. */
    public static void main(String[] args) {
        consumedRecordTaps();
        unconsumedTapTiming();
        modalFocusReentry();
        longPressDoesNotTap();
        shapesDoNotTap();
        multiTouchAndCancelDoNotTap();
        System.out.println("PASS modal scenarios=" + scenarios + " assertions=" + assertions);
        System.out.println("Actual MapGestureInputLayout + classifiers; Android event/dispatch boundary stubs.");
        System.out.println("Record hit-testing, Dialog display and emulator evidence are outside this probe.");
    }

    /** 메모·즐겨찾기로 소비한 한 번 탭은 SDK 이벤트 없이 한 번만 완료한다. */
    private static void consumedRecordTaps() {
        for (int record = 0; record < 2; record++) {
            Fixture fixture = new Fixture();
            fixture.listener.consume = true;
            float x = 200 + record * 200;
            fixture.one(MotionEvent.ACTION_DOWN, 100, x, 300);
            check(fixture.listener.taps == 0, "no record open on DOWN");
            fixture.one(MotionEvent.ACTION_MOVE, 120, x + 2, 301);
            fixture.one(MotionEvent.ACTION_UP, 145, x + 2, 301);
            check(fixture.listener.taps == 1, "record opens exactly once on short UP");
            check(fixture.listener.tapX == x && fixture.listener.tapY == 300,
                    "record hit test uses stable DOWN position");
            check(fixture.view.childEvents.isEmpty(), "consumed record emits no SDK DOWN/UP");
            check(fixture.listener.finished == 1, "consumed record completes one interaction");
            check(fixture.listener.gestures.isEmpty(), "consumed record creates no drawing");
        }
        pass("single note and favorite taps consumed before SDK");
    }

    /** 기록이 없는 두 번 탭은 원래 시각과 좌표를 그대로 SDK에 전달한다. */
    private static void unconsumedTapTiming() {
        Fixture fixture = new Fixture();
        fixture.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        fixture.one(MotionEvent.ACTION_UP, 140, 202, 301);
        fixture.one(MotionEvent.ACTION_DOWN, 230, 201, 300);
        fixture.one(MotionEvent.ACTION_UP, 260, 201, 300);
        List<MotionEvent> events = fixture.view.childEvents;
        check(fixture.listener.taps == 2, "unconsumed callback once per tap");
        check(events.size() == 4, "SDK has two complete taps");
        check(events.get(0).getActionMasked() == 0 && events.get(1).getActionMasked() == 1
                && events.get(2).getActionMasked() == 0 && events.get(3).getActionMasked() == 1,
                "SDK DOWN/UP order unchanged");
        check(events.get(0).getEventTime() == 100 && events.get(1).getEventTime() == 140
                && events.get(2).getEventTime() == 230 && events.get(3).getEventTime() == 260,
                "SDK double tap event timing unchanged");
        check(events.get(0).getDownTime() == 100 && events.get(1).getDownTime() == 100
                && events.get(2).getDownTime() == 230 && events.get(3).getDownTime() == 230,
                "SDK per-contact DOWN timing unchanged");
        check(events.get(0).getX() == 200 && events.get(1).getX() == 202,
                "SDK original coordinates unchanged");
        check(fixture.listener.finished == 2, "two ordinary taps complete twice");
        pass("unconsumed double tap timing");
    }

    /** 모달 표시 중 포커스가 재진입해 입력을 초기화해도 재활용 이벤트를 사용하지 않는다. */
    private static void modalFocusReentry() {
        Fixture fixture = new Fixture();
        fixture.listener.consume = true;
        fixture.listener.openModal = () -> {
            fixture.view.onWindowFocusChanged(false);
        };
        fixture.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        fixture.one(MotionEvent.ACTION_UP, 140, 200, 300);
        check(fixture.listener.taps == 1, "focus reentry opens modal once");
        check(fixture.listener.finished == 1, "focus reentry does not double-finish interaction");
        check(fixture.view.childEvents.isEmpty(), "focus reentry leaks no SDK events");
        check(fixture.view.callbacks.isEmpty(), "focus reentry removes held callbacks");

        fixture.view.onWindowFocusChanged(true);
        fixture.listener.openModal = null;
        fixture.listener.consume = false;
        fixture.one(MotionEvent.ACTION_DOWN, 400, 500, 300);
        fixture.one(MotionEvent.ACTION_UP, 440, 500, 300);
        check(fixture.listener.taps == 2 && fixture.listener.finished == 2,
                "first tap after dismiss starts a fresh interaction");
        check(fixture.view.childEvents.size() == 2,
                "fresh unconsumed tap forwards one complete stream after modal");
        pass("modal focus reentry and first subsequent tap");
    }

    /** 긴 누름과 기록 끌기는 짧은 탭 모달을 열지 않는다. */
    private static void longPressDoesNotTap() {
        Fixture unclaimed = new Fixture();
        unclaimed.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        unclaimed.one(MotionEvent.ACTION_UP, 900, 200, 300);
        check(unclaimed.listener.taps == 0 && unclaimed.view.childEvents.isEmpty(),
                "long hold does not become a short tap");

        Fixture dragged = new Fixture();
        dragged.listener.claimDrag = true;
        dragged.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        for (Runnable callback : new ArrayList<>(dragged.view.callbacks)) {
            callback.run();
        }
        dragged.one(MotionEvent.ACTION_MOVE, 720, 350, 400);
        dragged.one(MotionEvent.ACTION_UP, 800, 350, 400);
        check(dragged.listener.dragStarts == 1 && dragged.listener.dragEnds == 1,
                "claimed long press still has one drag lifecycle");
        check(dragged.listener.taps == 0 && dragged.view.childEvents.isEmpty(),
                "record drag does not open modal or send SDK tap");
        check(dragged.listener.finished == 1, "drag completes once");
        pass("long hold and marker drag exclusion");
    }

    /** 실제 분류기의 원·별 입력에는 짧은 탭 콜백을 보내지 않는다. */
    private static void shapesDoNotTap() {
        Fixture circle = new Fixture();
        circle.one(MotionEvent.ACTION_DOWN, 100, 600, 500);
        for (int index = 1; index <= 64; index++) {
            double angle = index * Math.PI * 2 / 64;
            circle.one(MotionEvent.ACTION_MOVE, 100 + index * 10,
                    (float) (500 + 100 * Math.cos(angle)), (float) (500 + 100 * Math.sin(angle)));
        }
        circle.one(MotionEvent.ACTION_UP, 760, 600, 500);
        check(circle.listener.taps == 0 && circle.listener.gestures.size() == 1,
                "circle creates one gesture and no modal tap");
        check(circle.listener.gestures.get(0).type() == MapGesture.Type.CIRCLE,
                "circle classifier still recognizes original path");

        Fixture star = new Fixture();
        int[] vertices = {0, 2, 4, 1, 3, 0};
        star.one(MotionEvent.ACTION_DOWN, 100, 500, 400);
        long time = 100;
        for (int side = 1; side < vertices.length; side++) {
            double first = -Math.PI / 2 + vertices[side - 1] * Math.PI * 2 / 5;
            double last = -Math.PI / 2 + vertices[side] * Math.PI * 2 / 5;
            for (int step = 1; step <= 16; step++) {
                double fraction = step / 16.0;
                float x = (float) (500 + 100 * (Math.cos(first) * (1 - fraction)
                        + Math.cos(last) * fraction));
                float y = (float) (500 + 100 * (Math.sin(first) * (1 - fraction)
                        + Math.sin(last) * fraction));
                star.one(MotionEvent.ACTION_MOVE, time += 10, x, y);
            }
        }
        star.one(MotionEvent.ACTION_UP, time + 10, 500, 400);
        check(star.listener.taps == 0 && star.listener.gestures.size() == 1,
                "star creates one gesture and no modal tap");
        check(star.listener.gestures.get(0).type() == MapGesture.Type.STAR,
                "star classifier still recognizes original path");
        pass("circle and star exclusion");
    }

    /** 두 손가락 이동과 취소·본문 제외 입력은 새로운 기록 모달을 만들지 않는다. */
    private static void multiTouchAndCancelDoNotTap() {
        Fixture multi = new Fixture();
        multi.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        multi.multi(5 | (1 << 8), 160, new int[]{0, 1}, 200, 300, 400, 300);
        multi.multi(2, 200, new int[]{0, 1}, 250, 350, 450, 350);
        multi.multi(6 | (1 << 8), 230, new int[]{0, 1}, 250, 350, 450, 350);
        multi.one(MotionEvent.ACTION_UP, 260, 250, 350);
        check(multi.listener.taps == 0 && multi.listener.finished == 1,
                "two fingers and remaining tail never open modal");
        check(multi.view.childEvents.size() == 5, "two finger map stream unchanged");

        Fixture cancelled = new Fixture();
        cancelled.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        cancelled.one(MotionEvent.ACTION_CANCEL, 140, 200, 300);
        cancelled.one(MotionEvent.ACTION_UP, 160, 200, 300);
        check(cancelled.listener.taps == 0 && cancelled.view.childEvents.isEmpty(),
                "cancel and late UP never open modal");
        check(cancelled.listener.finished == 1, "cancel finishes only once");

        Fixture excluded = new Fixture();
        excluded.listener.excluded = true;
        excluded.one(MotionEvent.ACTION_DOWN, 100, 200, 300);
        excluded.one(MotionEvent.ACTION_MOVE, 140, 200, 400);
        excluded.one(MotionEvent.ACTION_UP, 160, 200, 400);
        check(excluded.listener.taps == 0 && excluded.view.childEvents.size() == 3,
                "excluded text scrolling remains with original child");
        pass("two finger cancellation and excluded input");
    }

    /** 실제 검사가 실패한 입력 계약을 즉시 드러낸다. */
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /** 검증을 마친 독립 입력 시나리오를 기록한다. */
    private static void pass(String name) {
        scenarios++;
        System.out.println("PASS " + name);
    }

    /** 실제 입력 뷰와 제어 가능한 모달 선택 경계를 결합한다. */
    private static final class Fixture {
        final Recorder listener = new Recorder();
        final MapGestureInputLayout view = new MapGestureInputLayout(new Context(),
                new GeometricGestureClassifier(), listener);
        private long downTime;

        /** 단일 포인터를 원래 시각으로 제품 입력 경로에 전달한다. */
        void one(int action, long time, float x, float y) {
            multi(action, time, new int[]{0}, x, y);
        }

        /** 경계 대역의 재활용 검사를 활성화한 채 다중 포인터도 전달한다. */
        void multi(int action, long time, int[] ids, float... coordinates) {
            if ((action & 255) == MotionEvent.ACTION_DOWN) {
                downTime = time;
            }
            MotionEvent event = MotionEvent.test(downTime, time, action, ids, coordinates);
            view.dispatchTouchEvent(event);
            event.recycle();
        }
    }

    /** 선택 소비·포커스 재진입·드래그 결과만 제어하고 입력 라우팅은 실제 구현에 맡긴다. */
    private static final class Recorder implements MapGestureInputLayout.Listener {
        int taps;
        int finished;
        int dragStarts;
        int dragEnds;
        float tapX;
        float tapY;
        boolean consume;
        boolean claimDrag;
        boolean excluded;
        Runnable openModal;
        final List<MapStrokeGesture> gestures = new ArrayList<>();

        /** 저장 마커의 소비 결과와 동기적인 모달 포커스 이동을 모의한다. */
        @Override
        public boolean onMapTapped(float x, float y) {
            taps++;
            tapX = x;
            tapY = y;
            if (openModal != null) {
                openModal.run();
            }
            return consume;
        }

        /** 입력 보류의 종료 횟수를 확인한다. */
        @Override
        public void onStrokeFinished() {
            finished++;
        }

        /** 긴 누름이 기록 드래그로 인계되는 경우를 제어한다. */
        @Override
        public boolean onRecordDragStart(float x, float y) {
            dragStarts++;
            return claimDrag;
        }

        /** 기록 이동이 한 번만 끝났는지 확인한다. */
        @Override
        public void onRecordDragEnd(float x, float y, boolean cancelled) {
            dragEnds++;
        }

        /** 읽기 영역에 속한 입력을 원래 자식에게 맡기는 기존 계약을 제어한다. */
        @Override
        public boolean isDrawingExcluded(float x, float y) {
            return excluded;
        }

        /** 실제 도형 분류 결과를 그대로 수집한다. */
        @Override
        public void onGesture(MapStrokeGesture gesture) {
            gestures.add(gesture);
        }
    }
}
