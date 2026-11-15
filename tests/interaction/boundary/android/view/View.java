package android.view;
/** Android 경계의 검사 대역이다. */
public class View {
    public static final int VISIBLE = 0;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 2;
    protected final android.content.Context context;
    private int width = 1000;
    private int height = 1000;
    public java.util.List<Runnable> callbacks = new java.util.ArrayList<>();
    /** Android 경계의 검사 대역이다. */
    public View(android.content.Context context) {
        this.context = context;
    }
    /** Android 경계의 검사 대역이다. */
    public android.content.res.Resources getResources() {
        return context.getResources();
    }
    /** Android 경계의 검사 대역이다. */
    public int getWidth() {
        return width;
    }
    /** Android 경계의 검사 대역이다. */
    public int getHeight() {
        return height;
    }
    /** Android 경계의 검사 대역이다. */
    public void setContentDescription(String description) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void invalidate() {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void setWillNotDraw(boolean skip) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void setClickable(boolean clickable) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void setFocusable(boolean focusable) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void setImportantForAccessibility(int mode) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public boolean isHardwareAccelerated() {
        return false;
    }
    /** Android 경계의 검사 대역이다. */
    protected void onDraw(android.graphics.Canvas canvas) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void draw(android.graphics.Canvas canvas) {
        onDraw(canvas);
    }
    /** Android 경계의 검사 대역이다. */
    public boolean postDelayed(Runnable task, long delay) {
        callbacks.add(task);
        return true;
    }
    /** Android 경계의 검사 대역이다. */
    public boolean removeCallbacks(Runnable task) {
        return callbacks.remove(task);
    }
    /** Android 경계의 검사 대역이다. */
    public boolean performClick() {
        return true;
    }
    /** Android 경계의 검사 대역이다. */
    protected void onWindowVisibilityChanged(int visibility) {
        
    }
    /** Android 경계의 검사 대역이다. */
    public void onWindowFocusChanged(boolean focused) {
        
    }
    /** Android 경계의 검사 대역이다. */
    protected void onDetachedFromWindow() {
        
    }
}
