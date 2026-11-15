package android.graphics;
/** Android 경계의 검사 대역이다. */
public class Path {
    private boolean empty = true;
    /** Android 경계의 검사 대역이다. */
    public void moveTo(float x, float y) {
        empty = false;
    }
    /** Android 경계의 검사 대역이다. */
    public void lineTo(float x, float y) {
        empty = false;
    }
    /** Android 경계의 검사 대역이다. */
    public void reset() {
        empty = true;
    }
    /** Android 경계의 검사 대역이다. */
    public boolean isEmpty() {
        return empty;
    }
}
