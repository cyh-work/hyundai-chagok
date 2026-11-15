package android.view;
/** 포인터 구성과 시각을 보존하는 입력 경계 대역이다. */
public class MotionEvent {
    public static final int ACTION_DOWN = 0;
    public static final int ACTION_UP = 1;
    public static final int ACTION_MOVE = 2;
    public static final int ACTION_CANCEL = 3;
    public static final int ACTION_POINTER_DOWN = 5;
    public static final int ACTION_POINTER_UP = 6;
    public static final int INVALID_POINTER_ID = -1;
    public static final int ACTION_HOVER_MOVE = 7;
    public static final int ACTION_HOVER_ENTER = 9;
    public static final int ACTION_HOVER_EXIT = 10;
    public static final int ACTION_BUTTON_PRESS = 11;
    public static final int ACTION_BUTTON_RELEASE = 12;
    public static final int ACTION_POINTER_INDEX_SHIFT = 8;
    public static final int BUTTON_PRIMARY = 1;
    public static final int BUTTON_SECONDARY = 2;
    public static final int TOOL_TYPE_FINGER = 1;
    private long downTime;
    private long eventTime;
    private int action;
    private int source;
    private int buttons;
    private PointerProperties[] properties;
    private PointerCoords[] coordinates;
    private boolean recycled;
    /** 입력 포인터 식별자를 보관한다. */
    public static class PointerProperties {
        public int id;
        public int toolType;
    }
    /** 입력 포인터 좌표를 보관한다. */
    public static class PointerCoords {
        public float x;
        public float y;
        public float pressure;
        public float size;
    }
    /** 이벤트와 포인터 배열을 깊게 복사해 재활용 뒤 접근도 검사할 수 있게 한다. */
    public static MotionEvent obtain(long down, long time, int action, int count,
            PointerProperties[] properties, PointerCoords[] coords, int meta, int buttons,
            float xp, float yp, int device, int edges, int source, int flags) {
        MotionEvent event = new MotionEvent();
        event.downTime = down;
        event.eventTime = time;
        event.action = action;
        event.source = source;
        event.buttons = buttons;
        event.properties = new PointerProperties[count];
        event.coordinates = new PointerCoords[count];
        for (int index = 0; index < count; index++) {
            event.properties[index] = new PointerProperties();
            event.properties[index].id = properties[index].id;
            event.properties[index].toolType = properties[index].toolType;
            event.coordinates[index] = new PointerCoords();
            event.coordinates[index].x = coords[index].x;
            event.coordinates[index].y = coords[index].y;
            event.coordinates[index].pressure = coords[index].pressure;
            event.coordinates[index].size = coords[index].size;
        }
        return event;
    }
    /** 입력 이벤트를 동일한 값의 별도 객체로 복사한다. */
    public static MotionEvent obtain(MotionEvent event) {
        event.check();
        return obtain(event.downTime, event.eventTime, event.action, event.properties.length,
                event.properties, event.coordinates, 0, event.buttons, 1, 1, 0, 0, event.source, 0);
    }
    /** 포인터와 시간의 검사 입력을 만든다. */
    public static MotionEvent test(long down, long time, int action, int[] ids, float... xy) {
        PointerProperties[] properties = new PointerProperties[ids.length];
        PointerCoords[] coords = new PointerCoords[ids.length];
        for (int index = 0; index < ids.length; index++) {
            properties[index] = new PointerProperties();
            properties[index].id = ids[index];
            coords[index] = new PointerCoords();
            coords[index].x = xy[index * 2];
            coords[index].y = xy[index * 2 + 1];
        }
        return obtain(down, time, action, ids.length, properties, coords, 0, 0, 1, 1, 0, 0, 0, 0);
    }
    /** 재활용한 객체가 제품 입력에 다시 사용되면 실패시킨다. */
    private void check() {
        if (recycled) {
            throw new AssertionError("recycled event used");
        }
    }
    /** Android 경계의 검사 대역이다. */
    public int getActionMasked() {
        check();
        return action & 255;
    }
    /** Android 경계의 검사 대역이다. */
    public int getActionIndex() {
        return (action >> 8) & 255;
    }
    /** Android 경계의 검사 대역이다. */
    public int getPointerCount() {
        return properties.length;
    }
    /** Android 경계의 검사 대역이다. */
    public int getPointerId(int index) {
        return properties[index].id;
    }
    /** Android 경계의 검사 대역이다. */
    public int findPointerIndex(int id) {
        for (int index = 0; index < properties.length; index++) {
            if (properties[index].id == id) {
                return index;
            }
        }
        return -1;
    }
    /** Android 경계의 검사 대역이다. */
    public float getX() {
        return getX(0);
    }
    /** Android 경계의 검사 대역이다. */
    public float getY() {
        return getY(0);
    }
    /** Android 경계의 검사 대역이다. */
    public float getX(int index) {
        return coordinates[index].x;
    }
    /** Android 경계의 검사 대역이다. */
    public float getY(int index) {
        return coordinates[index].y;
    }
    /** Android 경계의 검사 대역이다. */
    public int getHistorySize() {
        return 0;
    }
    /** Android 경계의 검사 대역이다. */
    public float getHistoricalX(int index) {
        throw new AssertionError("no history");
    }
    /** Android 경계의 검사 대역이다. */
    public float getHistoricalY(int index) {
        throw new AssertionError("no history");
    }
    /** Android 경계의 검사 대역이다. */
    public long getDownTime() {
        return downTime;
    }
    /** Android 경계의 검사 대역이다. */
    public long getEventTime() {
        return eventTime;
    }
    /** Android 경계의 검사 대역이다. */
    public int getAction() {
        return action;
    }
    /** Android 경계의 검사 대역이다. */
    public void setAction(int action) {
        this.action = action;
    }
    /** Android 경계의 검사 대역이다. */
    public void getPointerProperties(int index, PointerProperties target) {
        target.id = properties[index].id;
    }
    /** Android 경계의 검사 대역이다. */
    public void getPointerCoords(int index, PointerCoords target) {
        target.x = coordinates[index].x;
        target.y = coordinates[index].y;
    }
    /** Android 경계의 검사 대역이다. */
    public int getMetaState() {
        return 0;
    }
    /** Android 경계의 검사 대역이다. */
    public int getButtonState() {
        return buttons;
    }
    /** Android 경계의 검사 대역이다. */
    public float getXPrecision() {
        return 1;
    }
    /** Android 경계의 검사 대역이다. */
    public float getYPrecision() {
        return 1;
    }
    /** Android 경계의 검사 대역이다. */
    public int getDeviceId() {
        return 0;
    }
    /** Android 경계의 검사 대역이다. */
    public int getEdgeFlags() {
        return 0;
    }
    /** Android 경계의 검사 대역이다. */
    public int getSource() {
        return source;
    }
    /** Android 경계의 검사 대역이다. */
    public int getFlags() {
        return 0;
    }
    /** Android 경계의 검사 대역이다. */
    public void recycle() {
        check();
        recycled = true;
    }
}
