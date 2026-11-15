package com.example.pleosstarter.gesture;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

import com.example.pleosstarter.map.GestureClassifier;
import com.example.pleosstarter.map.MapGesture;
import com.example.pleosstarter.map.StrokePoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 한 손가락 도형과 두 손가락 지도 조작을 입력 시작부터 분리하는 투명 레이어다. */
@SuppressLint("ViewConstructor")
public final class MapGestureInputLayout extends FrameLayout {
    /** 도형 좌표와 입력 수명 주기를 지도 화면에 전달하는 계약이다. */
    public interface Listener {
        /** 메모 카드 내부에서 시작한 입력은 원래 카드의 스크롤·닫기에 맡긴다. */
        default boolean isDrawingExcluded(float x, float y) {
            return false;
        }

        /** 첫 손가락부터 자동 이동과 위치 추적을 보류하도록 알린다. */
        default void onPointerDown() {
        }

        /** 짧은 탭을 먼저 처리하며 저장 마커가 소비한 경우 SDK에 다시 전달하지 않는다. */
        default boolean onMapTapped(float x, float y) {
            return false;
        }

        /** 두 손가락 지도 탐색을 시작해 추적 복귀 정책과 구분하도록 알린다. */
        default void onMapPanStarted() {
        }

        /** 한 손가락이 탭 범위를 벗어나 그리기를 시작했음을 알린다. */
        default void onStrokeStarted() {
        }

        /** 도형 도중 두 손가락 조작으로 전환되어 저장 후보를 버렸음을 알린다. */
        default void onShapeAbandoned() {
        }

        /** 전체 손가락의 종료·취소 때 입력 보류를 해제하도록 알린다. */
        default void onStrokeFinished() {
        }

        /** 누른 위치의 기록을 이동 대상으로 잡을 수 있을 때만 true를 반환한다. */
        default boolean onRecordDragStart(float x, float y) {
            return false;
        }

        /** 잡은 기록의 화면 위치 미리보기를 갱신하며 여기서는 저장하지 않는다. */
        default void onRecordDragMove(float x, float y) {
        }

        /** 기록 이동을 한 번 종료하며 취소이면 원래 위치를 복원한다. */
        default void onRecordDragEnd(float x, float y, boolean cancelled) {
        }

        /** 완성한 원·별과 화면 중심만 전달한다. */
        void onGesture(MapStrokeGesture gesture);
    }

    /** 한 번의 접촉에서 입력의 소유권이 바뀌는 경우를 명시한다. */
    private enum InputOwner {
        IDLE, DRAWING, DRAGGING, MAP, EXCLUDED, BLOCKED
    }

    private static final int MAXIMUM_POINTS = 2000;
    private static final float MINIMUM_GESTURE_SIZE_DP = 32.0f;
    private static final long TRAIL_LIFETIME_MILLIS = 450L;
    private final AutomaticGestureArbiter arbiter;
    private final Listener listener;
    private final List<StrokePoint> points = new ArrayList<>();
    private final StrokeTrailView strokeTrail;
    private final MouseChordAdapter mouseChord;
    private final float touchSlop;
    private final Runnable clearTrail = this::clearStroke;
    private final Runnable claimRecordDrag = this::startRecordDragWhenHeld;
    private InputOwner owner = InputOwner.IDLE;
    private boolean drawingEnabled = true;
    private boolean interactionActive;
    private boolean strokeStarted;
    private boolean forwarding;
    private boolean recordDragActive;
    private int pointerId = MotionEvent.INVALID_POINTER_ID;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private long mapDownTime;
    private int firstMapPointerId = MotionEvent.INVALID_POINTER_ID;
    private int secondMapPointerId = MotionEvent.INVALID_POINTER_ID;
    private MotionEvent heldDown;
    private MotionEvent lastMapEvent;

    /** 기존 분류기와 수신자를 주입하고 지도 위의 그리기 선만 구성한다. */
    public MapGestureInputLayout(Context context, GestureClassifier classifier, Listener listener) {
        super(context);
        arbiter = new AutomaticGestureArbiter(classifier);
        this.listener = Objects.requireNonNull(listener, "제스처 수신자가 필요합니다.");
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mouseChord = new MouseChordAdapter(this::dispatchPointerInput, dp(64));
        strokeTrail = new StrokeTrailView(context);
        addView(strokeTrail, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        setContentDescription("한 손가락으로 원과 별 기록, 두 손가락으로 지도 이동·확대·회전");
    }

    /** 감지를 중지할 때 진행 중인 입력을 취소하고 원래 지도 입력으로 전환한다. */
    public void setDrawingEnabled(boolean enabled) {
        resetInput();
        mouseChord.reset();
        drawingEnabled = enabled;
        setContentDescription(enabled
                ? "한 손가락으로 원과 별 기록, 두 손가락으로 지도 이동·확대·회전" : "지도 이동");
    }

    /** 한 손가락 기록 입력을 받는지 반환한다. */
    public boolean isDrawingEnabled() {
        return drawingEnabled;
    }

    /** 한 손가락 이동은 자식에게 보내지 않고 확정된 탭과 두 손가락 조작만 전달한다. */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (forwarding || !drawingEnabled) {
            return super.dispatchTouchEvent(event);
        }
        if (mouseChord.dispatch(event, getWidth(), getHeight())) {
            return true;
        }
        return dispatchPointerInput(event);
    }

    /** 우클릭의 generic 버튼 전환도 터치 이벤트와 같은 마우스 상태기로 전달한다. */
    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (!forwarding && drawingEnabled && mouseChord.dispatch(event, getWidth(), getHeight())) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    /** 실제 터치와 정규화한 마우스를 공통 입력 소유권 규칙으로 처리한다. */
    private boolean dispatchPointerInput(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            beginInput(event);
        }
        if (owner == InputOwner.EXCLUDED) {
            boolean handled = forward(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                resetInput();
            }
            return handled;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            resetInput();
            return true;
        }
        if (owner == InputOwner.IDLE) {
            return true;
        }
        if (owner == InputOwner.BLOCKED) {
            if (action == MotionEvent.ACTION_UP) {
                resetInput();
            }
            return true;
        }
        if (!validCoordinates(event)) {
            blockRemainder();
            if (action == MotionEvent.ACTION_UP) {
                resetInput();
            }
            return true;
        }

        if (owner == InputOwner.MAP) {
            handleMapInput(event);
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            beginMapInput(event);
        } else if (owner == InputOwner.DRAGGING) {
            handleRecordDrag(event);
        } else if (action == MotionEvent.ACTION_MOVE) {
            appendEvent(event);
        } else if (action == MotionEvent.ACTION_UP) {
            finishSinglePointer(event);
        }
        return true;
    }

    /** 입력 소유권은 dispatch 단계에서 결정하므로 전달한 자식 입력을 다시 가로채지 않는다. */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    /** 별도의 터치 소비를 만들지 않고 접근성 클릭 계약만 제공한다. */
    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }

    /** 나중에 지도 SDK가 추가돼도 획 표시 레이어는 항상 지도 위에 유지한다. */
    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (strokeTrail != null && child != strokeTrail) {
            bringChildToFront(strokeTrail);
        }
    }

    /** 창이 가려지면 진행 중인 도형을 저장하지 않고 지도 입력도 취소한다. */
    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != View.VISIBLE) {
            resetInput();
            mouseChord.reset();
        }
    }

    /** 포커스를 잃은 접촉을 나중에 완성 도형으로 처리하지 않는다. */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            resetInput();
            mouseChord.reset();
        }
    }

    /** 제거된 화면의 포인터·이벤트 복사본·표시 선을 모두 정리한다. */
    @Override
    protected void onDetachedFromWindow() {
        resetInput();
        mouseChord.reset();
        super.onDetachedFromWindow();
    }

    /** 첫 접촉을 보관하되 카드 영역은 처음부터 원래 자식에게 맡긴다. */
    private void beginInput(MotionEvent event) {
        resetInput();
        if (event.getPointerCount() != 1 || !validCoordinates(event)) {
            owner = InputOwner.BLOCKED;
            return;
        }
        if (listener.isDrawingExcluded(event.getX(), event.getY())) {
            owner = InputOwner.EXCLUDED;
            return;
        }
        owner = InputOwner.DRAWING;
        pointerId = event.getPointerId(0);
        downX = event.getX();
        downY = event.getY();
        lastX = downX;
        lastY = downY;
        heldDown = MotionEvent.obtain(event);
        points.add(new StrokePoint(downX, downY));
        strokeTrail.beginStroke(downX, downY);
        interactionActive = true;
        listener.onPointerDown();
        postDelayed(claimRecordDrag, ViewConfiguration.getLongPressTimeout());
    }

    /** 탭 범위를 벗어나지 않고 누른 기록만 이동 소유권을 요청한다. */
    private void startRecordDragWhenHeld() {
        if (owner != InputOwner.DRAWING || strokeStarted || !interactionActive
                || Math.hypot(lastX - downX, lastY - downY) > touchSlop) {
            return;
        }
        if (!listener.onRecordDragStart(downX, downY)) {
            return;
        }
        if (owner != InputOwner.DRAWING || !interactionActive) {
            listener.onRecordDragEnd(lastX, lastY, true);
            return;
        }

        recordDragActive = true;
        owner = InputOwner.DRAGGING;
        points.clear();
        releaseHeldDown();
        clearStroke();
    }

    /** 기록을 잡은 한 포인터의 이동·종료만 전달하고 잘못된 입력은 취소한다. */
    private void handleRecordDrag(MotionEvent event) {
        if (event.getPointerCount() != 1 || event.getPointerId(0) != pointerId) {
            blockRemainder();
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                resetInput();
            }
            return;
        }
        lastX = event.getX();
        lastY = event.getY();
        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            listener.onRecordDragMove(lastX, lastY);
        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            finishRecordDrag(false);
            resetInput();
        }
    }

    /** 재진입과 중복 취소에도 잡은 기록마다 종료를 정확히 한 번 전달한다. */
    private void finishRecordDrag(boolean cancelled) {
        if (!recordDragActive) {
            return;
        }
        recordDragActive = false;
        listener.onRecordDragEnd(lastX, lastY, cancelled);
    }

    /** Android의 합쳐진 이동 이력도 분류기에 전달하되 잘못된 포인터는 전체 취소한다. */
    private void appendEvent(MotionEvent event) {
        if (event.getPointerCount() != 1 || event.getPointerId(0) != pointerId) {
            blockRemainder();
            return;
        }
        for (int index = 0; index < event.getHistorySize(); index++) {
            if (!appendPoint(event.getHistoricalX(index), event.getHistoricalY(index))) {
                return;
            }
        }
        appendPoint(event.getX(), event.getY());
    }

    /** 선의 점을 추가하고 탭 범위를 벗어난 첫 순간부터 선을 표시한다. */
    private boolean appendPoint(float x, float y) {
        if (!validPoint(x, y) || points.size() >= MAXIMUM_POINTS) {
            blockRemainder();
            return false;
        }
        lastX = x;
        lastY = y;
        if (Math.hypot(x - downX, y - downY) > touchSlop) {
            removeCallbacks(claimRecordDrag);
        }
        StrokePoint previous = points.get(points.size() - 1);
        if (Math.hypot(x - previous.x(), y - previous.y()) < dp(1)) {
            return true;
        }
        points.add(new StrokePoint(x, y));
        strokeTrail.appendPoint(x, y);
        if (!strokeStarted && Math.hypot(x - downX, y - downY) > touchSlop) {
            strokeStarted = true;
            strokeTrail.showTrail();
            listener.onStrokeStarted();
        }
        return true;
    }

    /** 저장 마커 탭은 즉시 처리하고 나머지 짧은 탭만 원래 시간으로 SDK에 전달한다. */
    private void finishSinglePointer(MotionEvent event) {
        removeCallbacks(claimRecordDrag);
        appendEvent(event);
        if (owner != InputOwner.DRAWING) {
            resetInput();
            return;
        }
        if (!strokeStarted) {
            if (heldDown != null && event.getEventTime() - heldDown.getEventTime()
                    <= ViewConfiguration.getLongPressTimeout()) {
                if (!listener.onMapTapped(downX, downY)) {
                    forward(heldDown);
                    forward(event);
                }
                performClick();
            }
            resetInput();
            return;
        }

        MapGesture.Type type = arbiter.classifyCompleted(points, dp(MINIMUM_GESTURE_SIZE_DP));
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (StrokePoint point : points) {
            left = Math.min(left, point.x());
            right = Math.max(right, point.x());
            top = Math.min(top, point.y());
            bottom = Math.max(bottom, point.y());
        }
        owner = InputOwner.IDLE;
        releaseHeldDown();
        points.clear();
        interactionActive = false;
        postDelayed(clearTrail, TRAIL_LIFETIME_MILLIS);
        try {
            if (type == MapGesture.Type.CIRCLE || type == MapGesture.Type.STAR) {
                listener.onGesture(new MapStrokeGesture(type, (left + right) / 2, (top + bottom) / 2));
            }
        } finally {
            listener.onStrokeFinished();
        }
    }

    /** 두 번째 손가락의 현재 좌표로 새 지도 접촉을 시작하고 도형 후보는 완전히 폐기한다. */
    private void beginMapInput(MotionEvent event) {
        removeCallbacks(claimRecordDrag);
        if (event.getPointerCount() != 2 || event.findPointerIndex(pointerId) < 0) {
            blockRemainder();
            return;
        }
        int primaryIndex = event.findPointerIndex(pointerId);
        if (primaryIndex == event.getActionIndex()) {
            blockRemainder();
            return;
        }
        finishRecordDrag(true);
        if (strokeStarted) {
            listener.onShapeAbandoned();
        }
        listener.onMapPanStarted();
        owner = InputOwner.MAP;
        points.clear();
        releaseHeldDown();
        clearStroke();
        mapDownTime = event.getEventTime();
        firstMapPointerId = event.getPointerId(0);
        secondMapPointerId = event.getPointerId(1);

        MotionEvent down = singlePointer(event, primaryIndex, MotionEvent.ACTION_DOWN);
        try {
            forward(down);
            forwardMap(event);
        } finally {
            down.recycle();
        }
    }

    /** 두 손가락만 지도에 보내고 한 손가락이 남으면 CANCEL 뒤 전체 종료를 기다린다. */
    private void handleMapInput(MotionEvent event) {
        int action = event.getActionMasked();
        if (event.getPointerCount() != 2 || event.findPointerIndex(firstMapPointerId) < 0
                || event.findPointerIndex(secondMapPointerId) < 0) {
            blockRemainder();
            if (action == MotionEvent.ACTION_UP) {
                resetInput();
            }
            return;
        }
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerCount() == 2) {
            forwardMap(event);
            int remainingIndex = event.getActionIndex() == 0 ? 1 : 0;
            MotionEvent cancel = singlePointer(event, remainingIndex, MotionEvent.ACTION_CANCEL);
            try {
                forward(cancel);
            } finally {
                cancel.recycle();
            }
            releaseMapEvent();
            owner = InputOwner.BLOCKED;
        } else if (action == MotionEvent.ACTION_MOVE && event.getPointerCount() == 2) {
            forwardMap(event);
        } else {
            blockRemainder();
            if (action == MotionEvent.ACTION_UP) {
                resetInput();
            }
        }
    }

    /** 두 손가락 시작 시각으로 맞춘 이벤트 복사본을 전달하고 취소용 마지막 상태를 보관한다. */
    private void forwardMap(MotionEvent event) {
        int pointerCount = event.getPointerCount();
        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coordinates = new MotionEvent.PointerCoords[pointerCount];
        for (int index = 0; index < pointerCount; index++) {
            properties[index] = new MotionEvent.PointerProperties();
            coordinates[index] = new MotionEvent.PointerCoords();
            event.getPointerProperties(index, properties[index]);
            event.getPointerCoords(index, coordinates[index]);
        }
        MotionEvent copy = MotionEvent.obtain(mapDownTime, event.getEventTime(), event.getAction(),
                pointerCount, properties, coordinates, event.getMetaState(), event.getButtonState(),
                event.getXPrecision(), event.getYPrecision(), event.getDeviceId(), event.getEdgeFlags(),
                event.getSource(), event.getFlags());
        releaseMapEvent();
        lastMapEvent = MotionEvent.obtain(copy);
        try {
            forward(copy);
        } finally {
            copy.recycle();
        }
    }

    /** 지정 포인터만 포함한 시작·취소 이벤트를 같은 지도 접촉 시각으로 만든다. */
    private MotionEvent singlePointer(MotionEvent event, int index, int action) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        event.getPointerProperties(index, properties);
        event.getPointerCoords(index, coordinates);
        return MotionEvent.obtain(mapDownTime, event.getEventTime(), action, 1,
                new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coordinates},
                event.getMetaState(), event.getButtonState(), event.getXPrecision(), event.getYPrecision(),
                event.getDeviceId(), event.getEdgeFlags(), event.getSource(), event.getFlags());
    }

    /** 부모의 입력 라우터를 우회해 지도 또는 카드의 원래 dispatch를 호출한다. */
    private boolean forward(MotionEvent event) {
        forwarding = true;
        try {
            return super.dispatchTouchEvent(event);
        } finally {
            forwarding = false;
        }
    }

    /** 잘못된 입력과 세 손가락 전환에서는 저장 없이 남은 접촉을 모두 소비한다. */
    private void blockRemainder() {
        removeCallbacks(claimRecordDrag);
        finishRecordDrag(true);
        cancelMapInput();
        owner = InputOwner.BLOCKED;
        points.clear();
        releaseHeldDown();
        clearStroke();
    }

    /** 지도에 시작한 입력이 있으면 마지막 포인터 구성으로 CANCEL을 전달한다. */
    private void cancelMapInput() {
        if (lastMapEvent != null) {
            MotionEvent cancel = MotionEvent.obtain(lastMapEvent);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            try {
                forward(cancel);
            } finally {
                cancel.recycle();
                releaseMapEvent();
            }
        }
    }

    /** 전체 입력을 초기화하며 시작했던 보류를 정확히 한 번 해제한다. */
    private void resetInput() {
        removeCallbacks(claimRecordDrag);
        finishRecordDrag(true);
        cancelMapInput();
        boolean hadInput = interactionActive;
        interactionActive = false;
        owner = InputOwner.IDLE;
        strokeStarted = false;
        pointerId = MotionEvent.INVALID_POINTER_ID;
        firstMapPointerId = MotionEvent.INVALID_POINTER_ID;
        secondMapPointerId = MotionEvent.INVALID_POINTER_ID;
        points.clear();
        releaseHeldDown();
        removeCallbacks(clearTrail);
        clearStroke();
        if (hadInput) {
            listener.onStrokeFinished();
        }
    }

    /** SDK에 보낼 수 있는 현재 포인터 좌표인지 화면 경계에서 확인한다. */
    private boolean validCoordinates(MotionEvent event) {
        if (event.getPointerCount() < 1) {
            return false;
        }
        for (int index = 0; index < event.getPointerCount(); index++) {
            if (!validPoint(event.getX(index), event.getY(index))) {
                return false;
            }
        }
        return true;
    }

    /** 유한한 화면 내부 좌표만 허용한다. */
    private boolean validPoint(float x, float y) {
        return Float.isFinite(x) && Float.isFinite(y) && x >= 0 && y >= 0
                && (getWidth() <= 0 || x <= getWidth()) && (getHeight() <= 0 || y <= getHeight());
    }

    /** 저장한 단일 접촉 이벤트를 반환하고 참조를 제거한다. */
    private void releaseHeldDown() {
        if (heldDown != null) {
            heldDown.recycle();
            heldDown = null;
        }
    }

    /** 취소에 사용한 지도 이벤트 복사본을 반환한다. */
    private void releaseMapEvent() {
        if (lastMapEvent != null) {
            lastMapEvent.recycle();
            lastMapEvent = null;
        }
    }

    /** 표시 선만 지우고 저장된 기록에는 관여하지 않는다. */
    private void clearStroke() {
        strokeTrail.clearStroke();
    }

    /** 화면 밀도에 맞춰 도형 크기와 선 너비를 변환한다. */
    private float dp(float pixels) {
        return pixels * getResources().getDisplayMetrics().density;
    }
}
