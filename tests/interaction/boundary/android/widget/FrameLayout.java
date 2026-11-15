package android.widget;
/** Android 경계의 검사 대역이다. */
public class FrameLayout extends android.view.View {
    public final java.util.List<android.view.MotionEvent> childEvents = new java.util.ArrayList<>();
    public final java.util.List<android.view.MotionEvent> genericEvents = new java.util.ArrayList<>();
    private final java.util.List<android.view.View> children = new java.util.ArrayList<>();
    /** 자식 표시 크기의 경계 값이다. */
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        /** Android 경계의 검사 대역이다. */
        public LayoutParams(int width, int height) {
        }
    }
    /** Android 경계의 검사 대역이다. */
    public FrameLayout(android.content.Context context) {
        super(context);
    }
    /** Android 경계의 검사 대역이다. */
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        childEvents.add(android.view.MotionEvent.obtain(event));
        return true;
    }
    /** Android 경계의 검사 대역이다. */
    public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        genericEvents.add(android.view.MotionEvent.obtain(event));
        return true;
    }
    /** Android 경계의 검사 대역이다. */
    public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
        return false;
    }
    /** Android 경계의 검사 대역이다. */
    public void onViewAdded(android.view.View child) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void addView(android.view.View child, LayoutParams layout) {
        children.add(child);
        onViewAdded(child);
    }
    /** Android 경계의 검사 대역이다. */
    public void bringChildToFront(android.view.View child) {
        children.remove(child);
        children.add(child);
    }
    /** Android 경계의 검사 대역이다. */
    protected void dispatchDraw(android.graphics.Canvas canvas) {
        for (android.view.View child : children) {
            child.draw(canvas);
        }
    }
    /** Android 경계의 검사 대역이다. */
    public void draw(android.graphics.Canvas canvas) {
        dispatchDraw(canvas);
    }
}
