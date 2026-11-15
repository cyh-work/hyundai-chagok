package android.view;
/** Android 경계의 검사 대역이다. */
public class ViewConfiguration {

    /** Android 경계의 검사 대역이다. */
    public static ViewConfiguration get(android.content.Context context) {
        return new ViewConfiguration();
    }
    /** Android 경계의 검사 대역이다. */
    public int getScaledTouchSlop() {
        return 8;
    }
    /** Android 경계의 검사 대역이다. */
    public static int getLongPressTimeout() {
        return 500;
    }
}
