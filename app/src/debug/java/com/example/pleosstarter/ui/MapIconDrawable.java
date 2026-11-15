package com.example.pleosstarter.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** 글꼴에 의존하지 않는 지도·기록 화면의 공통 선 아이콘이다. */
public final class MapIconDrawable extends Drawable {
    /** 지도와 기록 조작에서 사용하는 시각적 의미를 구분한다. */
    public enum Symbol {
        MICROPHONE, NOTE, STAR, PLAY, PAUSE, STOP, CLOSE, PIN, CHEVRON
    }

    private final Symbol symbol;
    private final int size;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /** 의미·색·픽셀 크기를 받아 같은 두께와 곡률의 아이콘을 구성한다. */
    public MapIconDrawable(Symbol symbol, int color, int size) {
        this.symbol = symbol;
        this.size = size;
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setBounds(0, 0, size, size);
    }

    /** 24단위 도안을 현재 표시 영역에 맞춰 그린다. */
    @Override
    public void draw(Canvas canvas) {
        int checkpoint = canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        path.reset();
        switch (symbol) {
            case MICROPHONE:
                canvas.drawRoundRect(9, 3, 15, 14, 3, 3, paint);
                canvas.drawArc(6, 7, 18, 19, 0, 180, false, paint);
                canvas.drawLine(12, 19, 12, 22, paint);
                canvas.drawLine(9, 22, 15, 22, paint);
                break;
            case NOTE:
                canvas.drawRoundRect(4, 3, 20, 18, 3, 3, paint);
                path.moveTo(8, 18);
                path.lineTo(8, 22);
                path.lineTo(13, 18);
                canvas.drawPath(path, paint);
                canvas.drawLine(8, 8, 16, 8, paint);
                canvas.drawLine(8, 12, 14, 12, paint);
                break;
            case STAR:
                drawStar(canvas);
                break;
            case PLAY:
                path.moveTo(8, 4);
                path.lineTo(20, 12);
                path.lineTo(8, 20);
                path.close();
                canvas.drawPath(path, paint);
                break;
            case PAUSE:
                canvas.drawLine(8, 5, 8, 19, paint);
                canvas.drawLine(16, 5, 16, 19, paint);
                break;
            case STOP:
                canvas.drawRoundRect(5, 5, 19, 19, 2, 2, paint);
                break;
            case CLOSE:
                canvas.drawLine(6, 6, 18, 18, paint);
                canvas.drawLine(18, 6, 6, 18, paint);
                break;
            case PIN:
                path.moveTo(12, 22);
                path.cubicTo(8, 17, 5, 13, 5, 9);
                path.cubicTo(5, 0, 19, 0, 19, 9);
                path.cubicTo(19, 13, 16, 17, 12, 22);
                canvas.drawPath(path, paint);
                canvas.drawCircle(12, 9, 2.4f, paint);
                break;
            case CHEVRON:
                path.moveTo(9, 5);
                path.lineTo(16, 12);
                path.lineTo(9, 19);
                canvas.drawPath(path, paint);
                break;
            default:
                break;
        }
        canvas.restoreToCount(checkpoint);
    }

    /** 글꼴별 형태 차이가 없는 다섯 꼭짓점 별을 그린다. */
    private void drawStar(Canvas canvas) {
        for (int index = 0; index < 10; index++) {
            double angle = -Math.PI / 2 + index * Math.PI / 5;
            float radius = index % 2 == 0 ? 10f : 4.6f;
            float x = 12 + (float) Math.cos(angle) * radius;
            float y = 12 + (float) Math.sin(angle) * radius;
            if (index == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        canvas.drawPath(path, paint);
    }

    /** 버튼의 표시 투명도를 아이콘에도 반영한다. */
    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    /** 호스트가 지정한 색상 필터를 적용한다. */
    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    /** 선 외의 배경이 투명함을 알린다. */
    @Override
    @SuppressWarnings("deprecation")
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** 레이아웃이 사용할 기본 가로 크기를 반환한다. */
    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    /** 레이아웃이 사용할 기본 세로 크기를 반환한다. */
    @Override
    public int getIntrinsicHeight() {
        return size;
    }
}
