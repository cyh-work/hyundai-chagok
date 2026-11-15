package com.example.pleosstarter.gesture;

import android.content.Context;
import android.graphics.Canvas;
import android.view.InputDevice;
import android.view.MotionEvent;

import com.example.pleosstarter.map.GeometricGestureClassifier;

import java.util.ArrayList;
import java.util.List;

/** 실제 제품 입력 계층의 기록 끌기와 마우스 버튼 조합을 경계 대역에서 검사한다. */
public final class DragMouseRoutingProbe {
    private static int assertions;

    /** 새로운 입력 계약과 취소·복귀 경계를 순서대로 검사한다. */
    public static void main(String[] args) {
        recordDragCommitsOnce();
        rejectedHoldKeepsDrawing();
        movementBeforeHoldAndExcludedCard();
        cancelledHoldsAndDrags();
        changedPointerReleaseCancels();
        dragToTwoFingerMap();
        mousePrimaryTapAndDrag();
        mouseChordAndBlockedTail();
        sameSynMouseButtonSequence();
        cancelledMouseChordRequiresRelease();
        outsideReleaseAllowsFreshMouseStream();
        secondaryFirstAndEdgePan();
        mouseCancellationAndUnsupportedActions();
        reentrantClaimCancellation();
        System.out.println("PASS drag-mouse assertions=" + assertions);
        System.out.println("Actual product input classes; Android boundary stubs only, no PC-to-emulator claim.");
    }

    /** 누르고 끈 기록은 도형이나 지도 입력 없이 마지막 좌표로 한 번 끝난다. */
    private static void recordDragCommitsOnce() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.one(0, 100, 200, 200);
        fixture.one(2, 200, 203, 202);
        fixture.fireTimers();
        check(fixture.recorder.starts == 1 && fixture.recorder.startX == 200,
                "hold claims original down position once");
        fixture.one(2, 650, 340, 370);
        fixture.one(1, 700, 350, 380);
        fixture.one(3, 710, 350, 380);
        check(fixture.recorder.moves == 1, "drag movement preview once");
        check(fixture.recorder.ends == 1 && !fixture.recorder.cancelled, "drag commits once");
        check(fixture.recorder.endX == 350 && fixture.recorder.endY == 380, "up coordinates used");
        check(fixture.recorder.pointerDown == 1 && fixture.recorder.finished == 1, "drag hold balanced");
        check(fixture.recorder.gestures == 0 && fixture.view.childEvents.isEmpty(), "drag never draws or pans");
        check(fixture.paths() == 0, "drag leaves no drawing trail");
    }

    /** 기록이 없는 곳의 긴 누름은 이후 도형 입력을 막지 않는다. */
    private static void rejectedHoldKeepsDrawing() {
        Fixture fixture = new Fixture();
        fixture.one(0, 100, 200, 200);
        fixture.fireTimers();
        fixture.one(2, 700, 400, 200);
        check(fixture.paths() == 2, "rejected hold keeps live stroke");
        fixture.one(1, 800, 500, 200);
        check(fixture.recorder.starts == 1 && fixture.recorder.ends == 0, "unclaimed record never ends");
        check(fixture.recorder.strokeStarted == 1 && fixture.recorder.finished == 1, "normal stroke survives hold");
    }

    /** 도형 이동과 카드 내부 입력은 긴 누름 예약을 만들거나 실행하지 않는다. */
    private static void movementBeforeHoldAndExcludedCard() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.one(0, 100, 200, 200);
        fixture.one(2, 150, 209, 200);
        fixture.fireTimers();
        check(fixture.recorder.starts == 0, "slop cancels long hold");
        fixture.one(3, 200, 209, 200);

        Fixture excluded = new Fixture();
        excluded.recorder.excluded = true;
        excluded.recorder.claim = true;
        excluded.one(0, 100, 200, 200);
        excluded.fireTimers();
        excluded.one(2, 700, 240, 300);
        excluded.one(1, 750, 240, 300);
        check(excluded.recorder.starts == 0, "memo card never claims drag");
        check(excluded.view.childEvents.size() == 3, "memo card receives original input");
        check(excluded.recorder.pointerDown == 0 && excluded.recorder.finished == 0, "excluded card has no input hold");
    }

    /** 취소·포커스·비활성·분리·가림은 예약과 활성 끌기를 각각 정확히 끝낸다. */
    private static void cancelledHoldsAndDrags() {
        for (int reason = 0; reason < 5; reason++) {
            Fixture pending = new Fixture();
            pending.recorder.claim = true;
            pending.one(0, 100, 200, 200);
            pending.cancel(reason);
            pending.fireTimers();
            check(pending.recorder.starts == 0, "cancelled timer never claims " + reason);
            check(pending.recorder.finished == 1, "cancelled pending hold ends " + reason);

            Fixture active = new Fixture();
            active.recorder.claim = true;
            active.one(0, 100, 200, 200);
            active.fireTimers();
            active.one(2, 650, 300, 300);
            active.cancel(reason);
            active.cancel(reason);
            check(active.recorder.ends == 1 && active.recorder.cancelled, "active drag cancelled once " + reason);
            check(active.recorder.finished == 1, "active drag hold balanced " + reason);
        }
    }

    /** 잡은 포인터가 바뀐 종료 입력도 저장하지 않고 보류를 해제한다. */
    private static void changedPointerReleaseCancels() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.one(0, 100, 200, 200);
        fixture.fireTimers();
        fixture.multi(1, 700, new int[]{9}, 300, 300);
        check(fixture.recorder.ends == 1 && fixture.recorder.cancelled, "changed pointer drop cancels record");
        check(fixture.recorder.finished == 1, "changed pointer final up releases input hold");
    }

    /** 두 번째 손가락은 기록을 원복한 뒤 기존 지도 접촉과 꼬리 소비로 전환한다. */
    private static void dragToTwoFingerMap() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.one(0, 100, 200, 200);
        fixture.fireTimers();
        fixture.one(2, 650, 300, 300);
        fixture.multi(5 | 256, 700, new int[]{0, 1}, 300, 300, 400, 300);
        check(fixture.recorder.ends == 1 && fixture.recorder.cancelled, "second finger cancels record drag");
        check(fixture.recorder.pan == 1 && fixture.recorder.finished == 0, "map keeps same input hold");
        fixture.multi(2, 720, new int[]{0, 1}, 330, 310, 430, 310);
        fixture.multi(6 | 256, 740, new int[]{0, 1}, 330, 310, 430, 310);
        int childCount = fixture.view.childEvents.size();
        fixture.one(2, 760, 500, 400);
        check(fixture.view.childEvents.size() == childCount && fixture.paths() == 0, "remaining finger cannot draw or pan");
        fixture.one(1, 780, 500, 400);
        check(fixture.recorder.finished == 1 && fixture.recorder.ends == 1, "release ends hold without second drag end");
        check(fixture.view.childEvents.stream().anyMatch(event -> event.getPointerCount() == 2
                && event.getActionMasked() == 2), "two finger move forwarded");
    }

    /** 마우스 왼쪽 입력의 중복 버튼 이벤트는 탭과 기록 끌기를 중복시키지 않는다. */
    private static void mousePrimaryTapAndDrag() {
        Fixture tap = new Fixture();
        tap.mouse(0, 100, 1, 200, 200, false);
        tap.mouse(11, 101, 1, 200, 200, true);
        tap.mouse(12, 180, 0, 200, 200, true);
        tap.mouse(1, 181, 0, 200, 200, false);
        check(tap.view.childEvents.size() == 2, "mouse duplicate press/release yields one tap");
        check(tap.recorder.pointerDown == 1 && tap.recorder.finished == 1, "mouse tap hold balanced");
        check(tap.view.childEvents.get(0).getSource() == InputDevice.SOURCE_TOUCHSCREEN,
                "mouse normalized as touch");

        Fixture drag = new Fixture();
        drag.recorder.claim = true;
        drag.mouse(0, 100, 1, 200, 200, false);
        drag.fireTimers();
        drag.mouse(2, 650, 1, 350, 400, false);
        drag.mouse(1, 700, 0, 360, 420, false);
        check(drag.recorder.starts == 1 && drag.recorder.moves == 1, "mouse primary holds and drags record");
        check(drag.recorder.ends == 1 && !drag.recorder.cancelled, "mouse record drop commits once");
        check(drag.recorder.endX == 360 && drag.recorder.endY == 420, "mouse drop coordinates preserved");
    }

    /** 양 버튼으로 전환하면 기록 이동을 취소하고 한 버튼 꼬리를 모두 소비한다. */
    private static void mouseChordAndBlockedTail() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.mouse(0, 100, 1, 300, 300, false);
        fixture.fireTimers();
        fixture.mouse(11, 650, 3, 320, 300, true);
        check(fixture.recorder.ends == 1 && fixture.recorder.cancelled, "mouse chord cancels held marker");
        fixture.mouse(2, 700, 3, 400, 350, false);
        MotionEvent move = fixture.view.childEvents.get(fixture.view.childEvents.size() - 1);
        check(move.getPointerCount() == 2 && move.getActionMasked() == 2, "mouse chord forwards two pointer move");
        check(Math.abs(move.getX(1) - move.getX(0)) == 64 && move.getY(0) == move.getY(1),
                "mouse pair has fixed span without rotate or scale");
        fixture.mouse(12, 730, 1, 400, 350, true);
        int afterRelease = fixture.view.childEvents.size();
        fixture.mouse(2, 750, 1, 450, 400, false);
        fixture.mouse(11, 760, 3, 460, 410, true);
        fixture.mouse(2, 770, 3, 470, 420, false);
        check(fixture.view.childEvents.size() == afterRelease, "button tail and re-chord blocked until both up");
        check(fixture.recorder.finished == 0 && fixture.paths() == 0, "tail holds session without drawing");
        fixture.mouse(1, 800, 0, 470, 420, false);
        check(fixture.recorder.finished == 1 && fixture.recorder.ends == 1, "all mouse buttons up ends once");
        fixture.mouse(0, 900, 1, 200, 200, false);
        fixture.mouse(1, 950, 0, 200, 200, false);
        check(fixture.recorder.pointerDown == 2 && fixture.recorder.finished == 2, "next mouse tap works");
    }

    /** 오른쪽 먼저 누르기와 화면 모서리 이동도 유효한 고정 간격을 유지한다. */
    private static void secondaryFirstAndEdgePan() {
        Fixture fixture = new Fixture();
        fixture.mouse(0, 100, 2, 990, 200, false);
        check(fixture.recorder.pointerDown == 0, "secondary alone does not draw");
        fixture.mouse(11, 130, 3, 990, 200, true);
        fixture.mouse(2, 150, 3, 999, 210, false);
        fixture.mouse(2, 170, 3, 0, 220, false);
        for (MotionEvent event : fixture.view.childEvents) {
            if (event.getPointerCount() == 2) {
                check(Math.abs(event.getX(1) - event.getX(0)) == 64, "edge keeps pair span");
                check(event.getX(0) >= 0 && event.getX(1) >= 0
                        && event.getX(0) <= 1000 && event.getX(1) <= 1000, "edge pair stays inside");
            }
        }
        fixture.mouse(1, 200, 0, 0, 220, false);
        check(fixture.recorder.pan == 1 && fixture.recorder.finished == 1, "right first chord ends cleanly");
        check(fixture.recorder.gestures == 0, "right first never saves gesture");
    }

    /** 같은 SYN의 DOWN 최종 상태 뒤 중간 BUTTON_PRESS는 버튼 해제가 아니다. */
    private static void sameSynMouseButtonSequence() {
        for (int firstButton : new int[]{1, 2}) {
            Fixture fixture = new Fixture();
            fixture.mouse(0, 100, 3, 300, 300, false);
            int initialChildren = fixture.view.childEvents.size();
            fixture.mouse(11, 101, firstButton, 300, 300, true);
            check(fixture.view.childEvents.size() == initialChildren,
                    "same SYN intermediate button press cannot release chord " + firstButton);
            fixture.mouse(11, 102, 3, 300, 300, true);
            fixture.mouse(2, 150, 3, 400, 350, false);
            MotionEvent last = fixture.view.childEvents.get(fixture.view.childEvents.size() - 1);
            check(last.getActionMasked() == 2 && last.getPointerCount() == 2,
                    "same SYN sequence keeps subsequent two-pointer move " + firstButton);
            check(fixture.recorder.pan == 1 && fixture.recorder.pointerDown == 1,
                    "same SYN press sequence starts only once " + firstButton);

            fixture.mouse(12, 180, firstButton, 400, 350, true);
            int releasedChildren = fixture.view.childEvents.size();
            fixture.mouse(12, 181, firstButton, 400, 350, true);
            fixture.mouse(2, 185, firstButton, 420, 350, false);
            check(fixture.view.childEvents.size() == releasedChildren,
                    "partial release duplicate and remaining tail stay blocked " + firstButton);
            fixture.mouse(12, 190, 0, 420, 350, true);
            fixture.mouse(1, 191, 0, 420, 350, false);
            fixture.mouse(12, 192, 0, 420, 350, true);
            check(fixture.recorder.finished == 1, "same SYN release and UP close once " + firstButton);
        }

        Fixture upFirst = new Fixture();
        upFirst.mouse(0, 100, 3, 300, 300, false);
        upFirst.mouse(1, 200, 0, 300, 300, false);
        upFirst.mouse(12, 201, 2, 300, 300, true);
        upFirst.mouse(12, 202, 0, 300, 300, true);
        check(upFirst.recorder.finished == 1, "duplicate release after UP does not reopen contact");
        upFirst.mouse(0, 300, 1, 200, 200, false);
        upFirst.mouse(1, 350, 0, 200, 200, false);
        check(upFirst.recorder.pointerDown == 2 && upFirst.recorder.finished == 2,
                "fresh tap follows duplicated release sequence");
    }

    /** 화면 취소 뒤 누른 버튼의 MOVE·PRESS로 접촉을 되살리지 않는다. */
    private static void cancelledMouseChordRequiresRelease() {
        for (int reason = 0; reason < 5; reason++) {
            Fixture fixture = new Fixture();
            fixture.mouse(0, 100, 3, 300, 300, false);
            if (reason == 0) {
                fixture.mouse(3, 140, 3, 300, 300, false);
            } else {
                fixture.cancel(reason);
            }
            if (reason == 2) {
                fixture.view.setDrawingEnabled(true);
            }
            int cancelledChildren = fixture.view.childEvents.size();
            fixture.mouse(2, 800, 3, 400, 400, false);
            fixture.mouse(11, 810, 3, 400, 400, true);
            check(fixture.view.childEvents.size() == cancelledChildren,
                    "cancelled chord cannot restart while buttons held " + reason);
            check(fixture.recorder.pan == 1 && fixture.recorder.finished == 1,
                    "cancelled chord hold stays closed " + reason);
            fixture.mouse(12, 820, 0, 400, 400, true);
            fixture.mouse(0, 900, 3, 300, 300, false);
            fixture.mouse(1, 1000, 0, 300, 300, false);
            check(fixture.recorder.pan == 2 && fixture.recorder.finished == 2,
                    "all buttons released enables fresh chord " + reason);
        }
        Fixture missingDown = new Fixture();
        missingDown.mouse(2, 100, 3, 400, 400, false);
        check(missingDown.recorder.pointerDown == 0, "unowned mouse MOVE cannot synthesize new DOWN");
    }

    /** 앱 밖의 해제를 못 받아도 새 DOWN과 무버튼 hover는 복귀 클릭을 살린다. */
    private static void outsideReleaseAllowsFreshMouseStream() {
        for (int buttons : new int[]{1, 3}) {
            Fixture fixture = new Fixture();
            fixture.mouse(0, 100, 3, 300, 300, false);
            fixture.view.onWindowFocusChanged(false);
            int beforeReturn = fixture.view.childEvents.size();
            fixture.mouse(2, 600, 3, 400, 400, false);
            check(fixture.view.childEvents.size() == beforeReturn,
                    "held-button MOVE remains blocked before returning fresh DOWN " + buttons);
            fixture.mouse(0, 700, buttons, 200, 200, false);
            check(fixture.recorder.pointerDown == 2, "first returning DOWN starts new stream " + buttons);
            fixture.mouse(1, 780, 0, 200, 200, false);
            check(fixture.recorder.finished == 2, "returning stream closes balanced " + buttons);
        }

        Fixture hover = new Fixture();
        hover.mouse(0, 100, 3, 300, 300, false);
        hover.view.onWindowFocusChanged(false);
        hover.mouse(7, 500, 0, 250, 250, true);
        check(hover.view.genericEvents.size() == 1, "release-observing hover still passes through");
        hover.mouse(11, 600, 1, 250, 250, true);
        hover.mouse(1, 700, 0, 250, 250, false);
        check(hover.recorder.pointerDown == 2 && hover.recorder.finished == 2,
                "hover-observed release enables next mouse press");

        Fixture missingUp = new Fixture();
        missingUp.mouse(0, 100, 3, 300, 300, false);
        missingUp.mouse(12, 150, 1, 300, 300, true);
        missingUp.mouse(0, 500, 1, 200, 200, false);
        check(missingUp.recorder.finished == 1 && missingUp.recorder.pointerDown == 2,
                "new DOWN closes stale held tail before starting new contact");
        missingUp.mouse(1, 550, 0, 200, 200, false);
        check(missingUp.recorder.finished == 2, "new stream after missed UP ends once");
    }

    /** 마우스 취소와 포커스 손실은 저장하지 않고 hover·휠은 해석하지 않는다. */
    private static void mouseCancellationAndUnsupportedActions() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.mouse(0, 100, 1, 200, 200, false);
        fixture.fireTimers();
        fixture.mouse(3, 650, 1, 250, 250, false);
        check(fixture.recorder.ends == 1 && fixture.recorder.cancelled, "mouse cancel does not drop record");
        fixture.mouse(1, 660, 0, 250, 250, false);
        fixture.mouse(0, 700, 1, 200, 200, false);
        fixture.fireTimers();
        fixture.view.onWindowFocusChanged(false);
        fixture.mouse(1, 1300, 0, 300, 300, false);
        check(fixture.recorder.ends == 2 && fixture.recorder.finished == 2, "focus loss resets mouse state");
        fixture.mouse(7, 1400, 0, 400, 400, true);
        fixture.mouse(8, 1500, 0, 400, 400, true);
        check(fixture.view.genericEvents.size() == 2, "hover and wheel remain generic events");
        check(fixture.recorder.pointerDown == 2, "unsupported generic events do not start input");
    }

    /** 대상 선택 콜백에서 화면이 가려져도 승인된 이동은 한 번 취소된다. */
    private static void reentrantClaimCancellation() {
        Fixture fixture = new Fixture();
        fixture.recorder.claim = true;
        fixture.recorder.duringClaim = () -> fixture.view.onWindowFocusChanged(false);
        fixture.one(0, 100, 200, 200);
        fixture.fireTimers();
        check(fixture.recorder.ends == 1 && fixture.recorder.cancelled, "reentrant claim cancelled once");
        check(fixture.recorder.finished == 1, "reentrant hold released once");
    }

    /** 틀린 결과를 즉시 실패시키고 실행한 단언 수를 남긴다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    /** 실제 입력 클래스에 정해진 포인터·버튼 시퀀스를 공급한다. */
    private static final class Fixture {
        final Recorder recorder = new Recorder();
        final MapGestureInputLayout view = new MapGestureInputLayout(new Context(),
                new GeometricGestureClassifier(), recorder);

        /** 단일 손가락의 실제 입력 경로를 호출한다. */
        void one(int action, long time, float x, float y) {
            multi(action, time, new int[]{0}, x, y);
        }

        /** 실제 포인터 식별자와 좌표를 보존한 다중 입력을 전달한다. */
        void multi(int action, long time, int[] ids, float... xy) {
            MotionEvent event = MotionEvent.test(100, time, action, ids, xy);
            view.dispatchTouchEvent(event);
            event.recycle();
        }

        /** Android 마우스 소스와 버튼 상태를 touch 또는 generic 경로에 전달한다. */
        void mouse(int action, long time, int buttons, float x, float y, boolean generic) {
            MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
            properties.id = 0;
            MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
            coordinates.x = x;
            coordinates.y = y;
            MotionEvent event = MotionEvent.obtain(100, time, action, 1,
                    new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
                    0, buttons, 1, 1, 0, 0, InputDevice.SOURCE_MOUSE, 0);
            if (generic) {
                view.dispatchGenericMotionEvent(event);
            } else {
                view.dispatchTouchEvent(event);
            }
            event.recycle();
        }

        /** 예약된 Android 콜백을 만료 시점의 단일 UI 루프처럼 실행한다. */
        void fireTimers() {
            List<Runnable> pending = new ArrayList<>(view.callbacks);
            view.callbacks.clear();
            for (Runnable task : pending) {
                task.run();
            }
        }

        /** 활성 입력을 서로 다른 Android 수명 주기 경계에서 취소한다. */
        void cancel(int reason) {
            switch (reason) {
                case 0:
                    one(3, 700, 300, 300);
                    break;
                case 1:
                    view.onWindowFocusChanged(false);
                    break;
                case 2:
                    view.setDrawingEnabled(false);
                    break;
                case 3:
                    view.onDetachedFromWindow();
                    break;
                case 4:
                    view.onWindowVisibilityChanged(8);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown cancellation");
            }
        }

        /** 표시 레이어가 그린 획 수를 확인한다. */
        int paths() {
            Canvas canvas = new Canvas();
            view.draw(canvas);
            return canvas.paths;
        }
    }

    /** 이동·취소·자동 추적 보류의 실제 콜백 횟수와 좌표를 관찰한다. */
    private static final class Recorder implements MapGestureInputLayout.Listener {
        boolean claim;
        boolean excluded;
        boolean cancelled;
        int starts;
        int moves;
        int ends;
        int pointerDown;
        int finished;
        int pan;
        int strokeStarted;
        int gestures;
        float startX;
        float endX;
        float endY;
        Runnable duringClaim;

        /** 카드 내부 입력 제외 여부를 반환한다. */
        @Override
        public boolean isDrawingExcluded(float x, float y) {
            return excluded;
        }

        /** 기록 선택 요청과 시작 위치를 기록한다. */
        @Override
        public boolean onRecordDragStart(float x, float y) {
            starts++;
            startX = x;
            if (duringClaim != null) {
                duringClaim.run();
            }
            return claim;
        }

        /** 위치 미리보기 요청 수를 기록한다. */
        @Override
        public void onRecordDragMove(float x, float y) {
            moves++;
        }

        /** 최종 좌표와 취소 여부를 기록한다. */
        @Override
        public void onRecordDragEnd(float x, float y, boolean cancelled) {
            ends++;
            endX = x;
            endY = y;
            this.cancelled = cancelled;
        }

        /** 입력 보류 시작을 센다. */
        @Override
        public void onPointerDown() {
            pointerDown++;
        }

        /** 입력 보류 해제를 센다. */
        @Override
        public void onStrokeFinished() {
            finished++;
        }

        /** 지도 이동 시작을 센다. */
        @Override
        public void onMapPanStarted() {
            pan++;
        }

        /** 실시간 도형 시작을 센다. */
        @Override
        public void onStrokeStarted() {
            strokeStarted++;
        }

        /** 분류된 도형 수를 센다. */
        @Override
        public void onGesture(MapStrokeGesture gesture) {
            gestures++;
        }
    }
}
