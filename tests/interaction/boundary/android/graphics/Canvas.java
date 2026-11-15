package android.graphics;
/** Android 경계의 검사 대역이다. */
public class Canvas {
    public int paths;
    /** Android 경계의 검사 대역이다. */
    public void drawPath(Path path, Paint paint) {
        paths++;
    }
}
