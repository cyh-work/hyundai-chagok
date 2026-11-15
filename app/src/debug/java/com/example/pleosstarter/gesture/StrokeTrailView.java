package com.example.pleosstarter.gesture;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import com.example.pleosstarter.ui.MapUiTheme;

/** 지도 SurfaceView와 별도인 화면 레이어에 현재 손가락의 획만 표시한다. */
final class StrokeTrailView extends View {
    private final Path stroke = new Path();
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean trailVisible;

    /** 터치·접근성은 입력 레이어에 맡기고 지도 위의 투명한 표시만 구성한다. */
    StrokeTrailView(Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        configurePaint(strokePaint, MapUiTheme.ACCENT, dp(5));
        configurePaint(outlinePaint, Color.argb(160, 255, 255, 255), dp(9));
    }

    /** 다음 획의 시작점을 보관하되 짧은 탭은 화면에 남기지 않는다. */
    void beginStroke(float x, float y) {
        clearStroke();
        stroke.moveTo(x, y);
    }

    /** 수신한 실제 이동점을 기존 획에 추가하고 표시 레이어만 갱신한다. */
    void appendPoint(float x, float y) {
        stroke.lineTo(x, y);
        invalidate();
    }

    /** 탭 범위를 벗어나면 보관한 첫 점부터 획을 표시한다. */
    void showTrail() {
        trailVisible = true;
        invalidate();
    }

    /** 완료·취소·다중 터치 전환 시 잔여 획을 지운다. */
    void clearStroke() {
        trailVisible = false;
        stroke.reset();
        invalidate();
    }

    /** 지도와 독립된 표시 레이어에 외곽선과 색상 선을 차례로 그린다. */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (trailVisible && !stroke.isEmpty()) {
            canvas.drawPath(stroke, outlinePaint);
            canvas.drawPath(stroke, strokePaint);
        }
    }

    /** 모서리와 끝점이 끊기지 않는 둥근 선을 구성한다. */
    private void configurePaint(Paint paint, int color, float width) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(color);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** 화면 밀도에 맞게 선 너비를 변환한다. */
    private float dp(float size) {
        return size * getResources().getDisplayMetrics().density;
    }
}
