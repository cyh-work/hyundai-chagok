package com.example.pleosstarter.gesture;

import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;

import java.util.Objects;
import java.util.function.Consumer;

/** 마우스 왼쪽 입력과 좌우 동시 입력을 기존 한·두 포인터 경로로 변환한다. */
final class MouseChordAdapter {
    /** 실제 마우스 버튼과 합성 접촉의 현재 소유권을 구분한다. */
    private enum State {
        IDLE, PRIMARY, SECONDARY_ONLY, CHORD, BLOCKED
    }

    private static final int BOTH_BUTTONS = MotionEvent.BUTTON_PRIMARY | MotionEvent.BUTTON_SECONDARY;
    private final Consumer<MotionEvent> receiver;
    private final float pointerSpacing;
    private State state = State.IDLE;
    private boolean contactOpen;
    private boolean chordCoordinates;
    private long downTime;
    private float currentX;
    private float currentY;
    private float offsetX;
    private int viewWidth;
    private int viewHeight;

    /** 합성 이벤트 수신자와 두 포인터 사이의 고정 화면 간격을 주입한다. */
    MouseChordAdapter(Consumer<MotionEvent> receiver, float pointerSpacing) {
        this.receiver = Objects.requireNonNull(receiver, "마우스 입력 수신자가 필요합니다.");
        this.pointerSpacing = pointerSpacing;
    }

    /** 마우스 접촉·버튼 이벤트만 소비하고 휠·hover와 실제 터치는 그대로 둔다. */
    boolean dispatch(MotionEvent event, int width, int height) {
        if ((event.getSource() & InputDevice.SOURCE_MOUSE) != InputDevice.SOURCE_MOUSE) {
            return false;
        }
        viewWidth = width;
        viewHeight = height;
        if (isHoverAction(event.getActionMasked())) {
            observeReleasedHover(event);
            return false;
        }
        if (!supportedAction(event.getActionMasked())) {
            return false;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_CANCEL
                || !Float.isFinite(event.getX()) || !Float.isFinite(event.getY())) {
            cancelContact(event);
            return true;
        }
        currentX = event.getX();
        currentY = event.getY();
        int buttons = event.getButtonState() & BOTH_BUTTONS;

        if (state == State.CHORD) {
            handleChord(event, buttons);
        } else if (state == State.PRIMARY) {
            handlePrimary(event, buttons);
        } else if (state == State.BLOCKED) {
            handleBlockedContact(event, buttons);
        } else {
            beginContact(event, buttons);
        }
        return true;
    }

    /** 화면 취소 뒤 남은 이동을 막고 버튼 해제 또는 명확한 새 접촉을 기다린다. */
    void reset() {
        if (state == State.CHORD) {
            Log.i("PlaceMap", "MOUSE_CHORD code=CANCEL");
        }
        boolean hadPressedButtons = state != State.IDLE;
        clearState();
        if (hadPressedButtons) {
            state = State.BLOCKED;
        }
    }

    /** 실제 모든 버튼 해제를 확인했을 때 합성 접촉 기억을 비운다. */
    private void clearState() {
        state = State.IDLE;
        contactOpen = false;
        chordCoordinates = false;
        offsetX = 0;
    }

    /** 왼쪽 누름은 한 포인터, 오른쪽 먼저 누름은 동시 입력 대기로 시작한다. */
    private void beginContact(MotionEvent event, int buttons) {
        if (buttons == BOTH_BUTTONS) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                startPrimary(event);
                startChord(event);
            }
        } else if (buttons == MotionEvent.BUTTON_PRIMARY) {
            if (state == State.SECONDARY_ONLY) {
                state = State.BLOCKED;
            } else if (event.getActionMasked() == MotionEvent.ACTION_DOWN
                    || event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
                startPrimary(event);
            }
        } else if (buttons == MotionEvent.BUTTON_SECONDARY) {
            state = State.SECONDARY_ONLY;
        } else {
            clearState();
        }
    }

    /** 왼쪽만 누른 동안 기존 탭·도형·기록 이동에 같은 단일 접촉을 전달한다. */
    private void handlePrimary(MotionEvent event, int buttons) {
        if (buttons == BOTH_BUTTONS) {
            startChord(event);
        } else if (buttons == MotionEvent.BUTTON_PRIMARY) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                send(event, MotionEvent.ACTION_MOVE, 1);
            }
        } else if (buttons == 0) {
            send(event, MotionEvent.ACTION_UP, 1);
            clearState();
        } else {
            send(event, MotionEvent.ACTION_CANCEL, 1);
            contactOpen = false;
            state = State.BLOCKED;
        }
    }

    /** 좌우를 함께 누를 때 고정 간격의 두 포인터를 기존 지도 조작에 전달한다. */
    private void handleChord(MotionEvent event, int buttons) {
        // 같은 SYN의 DOWN은 최종 버튼 상태, 뒤따르는 PRESS는 중간 상태일 수 있다.
        if (event.getActionMasked() == MotionEvent.ACTION_BUTTON_PRESS) {
            return;
        }
        if (buttons == BOTH_BUTTONS) {
            if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                send(event, MotionEvent.ACTION_MOVE, 2);
            }
            return;
        }

        send(event, MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2);
        Log.i("PlaceMap", "MOUSE_CHORD code=END");
        state = State.BLOCKED;
        finishBlockedContact(event, buttons);
    }

    /** 한 버튼이 남은 꼬리는 그리지 않고 둘 다 해제될 때 접촉을 끝낸다. */
    private void finishBlockedContact(MotionEvent event, int buttons) {
        if (buttons != 0) {
            return;
        }
        if (contactOpen) {
            send(event, MotionEvent.ACTION_UP, 1);
        }
        clearState();
    }

    /** 남은 MOVE는 막되 새 DOWN은 앱 밖에서 종료된 이전 스트림과 구분한다. */
    private void handleBlockedContact(MotionEvent event, int buttons) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            finishBlockedContact(event, buttons);
            return;
        }
        if (contactOpen) {
            send(event, MotionEvent.ACTION_CANCEL, 1);
        }
        clearState();
        beginContact(event, buttons);
    }

    /** 앱 밖에서 놓은 버튼을 hover 상태로 확인하되 hover 동작 자체는 소비하지 않는다. */
    private void observeReleasedHover(MotionEvent event) {
        if (state != State.BLOCKED || (event.getButtonState() & BOTH_BUTTONS) != 0
                || !Float.isFinite(event.getX()) || !Float.isFinite(event.getY())) {
            return;
        }
        currentX = event.getX();
        currentY = event.getY();
        finishBlockedContact(event, 0);
    }

    /** 버튼 이벤트 중복과 관계없이 한 접촉의 DOWN을 한 번만 만든다. */
    private void startPrimary(MotionEvent event) {
        downTime = event.getEventTime();
        state = State.PRIMARY;
        contactOpen = true;
        send(event, MotionEvent.ACTION_DOWN, 1);
    }

    /** 첫 손가락 위치를 유지하면서 화면 안쪽으로 두 번째 포인터를 붙인다. */
    private void startChord(MotionEvent event) {
        float spacing = Math.min(pointerSpacing, Math.max(1, viewWidth - 1));
        offsetX = currentX <= viewWidth / 2.0f ? spacing : -spacing;
        chordCoordinates = true;
        state = State.CHORD;
        Log.i("PlaceMap", "MOUSE_CHORD code=START");
        send(event, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2);
    }

    /** 취소된 합성 접촉은 기록 저장이나 탭을 만들지 않는다. */
    private void cancelContact(MotionEvent event) {
        if (contactOpen) {
            send(event, MotionEvent.ACTION_CANCEL, state == State.CHORD ? 2 : 1);
        }
        reset();
    }

    /** 버튼 상태를 제거한 고정 ID 포인터를 만들고 동기 전달 후 복사본을 해제한다. */
    private void send(MotionEvent source, int action, int count) {
        float firstX = currentX;
        if (chordCoordinates && viewWidth > 0) {
            float minimumX = Math.max(0, -offsetX);
            float maximumX = viewWidth - Math.max(0, offsetX);
            firstX = Math.max(minimumX, Math.min(maximumX, firstX));
        }
        float y = currentY;
        if (chordCoordinates && viewHeight > 0) {
            y = Math.max(0, Math.min(viewHeight, y));
        }
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[count];
        for (int index = 0; index < count; index++) {
            properties[index] = new MotionEvent.PointerProperties();
            properties[index].id = index;
            properties[index].toolType = MotionEvent.TOOL_TYPE_FINGER;
            coordinates[index] = new MotionEvent.PointerCoords();
            coordinates[index].x = firstX + (index == 0 ? 0 : offsetX);
            coordinates[index].y = y;
            coordinates[index].pressure = 1;
            coordinates[index].size = 1;
        }
        MotionEvent synthetic = MotionEvent.obtain(downTime, source.getEventTime(), action, count,
                properties, coordinates, source.getMetaState(), 0, source.getXPrecision(),
                source.getYPrecision(), source.getDeviceId(), source.getEdgeFlags(),
                InputDevice.SOURCE_TOUCHSCREEN, source.getFlags());
        try {
            receiver.accept(synthetic);
        } finally {
            synthetic.recycle();
        }
    }

    /** 이번 기능에서 해석하는 접촉·버튼 액션만 허용한다. */
    private boolean supportedAction(int action) {
        return action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP
                || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_CANCEL
                || action == MotionEvent.ACTION_BUTTON_PRESS || action == MotionEvent.ACTION_BUTTON_RELEASE;
    }

    /** 접촉으로 바꾸지 않고 버튼 해제 여부만 관측할 hover 종류를 구분한다. */
    private boolean isHoverAction(int action) {
        return action == MotionEvent.ACTION_HOVER_MOVE || action == MotionEvent.ACTION_HOVER_ENTER
                || action == MotionEvent.ACTION_HOVER_EXIT;
    }
}
