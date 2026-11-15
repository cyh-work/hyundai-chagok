package com.example.pleosstarter.ui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** 지도 조작에 필요한 선 아이콘을 해상도에 맞춰 그린다. */
public final class MapActionIcon extends Drawable {
    /** 지도 조작과 기록의 종류를 선 아이콘으로 구분한다. */
    public enum Kind {
        SEARCH, LOCATION, SAVED, DRAW, NOTE, FAVORITE
    }

    private final Kind kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 버튼 색상과 아이콘 종류를 받아 24단위 벡터를 준비한다. */
    public MapActionIcon(Kind kind, int color) {
        this.kind = kind;
        paint.setColor(color);
        paint.setStrokeWidth(1.8f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    /** 주어진 경계에 벡터를 배율 적용해 그린다. */
    @Override
    public void draw(Canvas canvas) {
        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        switch (kind) {
            case SEARCH:
                canvas.drawCircle(10.5f, 10.5f, 6.5f, paint);
                canvas.drawLine(15.5f, 15.5f, 21, 21, paint);
                break;
            case LOCATION:
                canvas.drawCircle(12, 12, 6, paint);
                canvas.drawCircle(12, 12, 1.5f, paint);
                canvas.drawLine(12, 2, 12, 6, paint);
                canvas.drawLine(12, 18, 12, 22, paint);
                canvas.drawLine(2, 12, 6, 12, paint);
                canvas.drawLine(18, 12, 22, 12, paint);
                break;
            case SAVED:
                Path bookmark = new Path();
                bookmark.moveTo(6, 3);
                bookmark.lineTo(18, 3);
                bookmark.lineTo(18, 21);
                bookmark.lineTo(12, 17);
                bookmark.lineTo(6, 21);
                bookmark.close();
                canvas.drawPath(bookmark, paint);
                break;
            case DRAW:
                Path stroke = new Path();
                stroke.moveTo(3, 18);
                stroke.cubicTo(3, 5, 17, 1, 17, 10);
                stroke.cubicTo(17, 19, 5, 22, 8, 14);
                stroke.cubicTo(11, 6, 21, 5, 21, 14);
                canvas.drawPath(stroke, paint);
                break;
            case NOTE:
                Path note = new Path();
                note.moveTo(4, 4);
                note.lineTo(20, 4);
                note.lineTo(20, 16);
                note.lineTo(10, 16);
                note.lineTo(5, 21);
                note.lineTo(5, 16);
                note.lineTo(4, 16);
                note.close();
                canvas.drawPath(note, paint);
                canvas.drawLine(8, 8, 16, 8, paint);
                canvas.drawLine(8, 12, 13, 12, paint);
                break;
            case FAVORITE:
                Path star = new Path();
                for (int point = 0; point < 10; point++) {
                    double angle = -Math.PI / 2 + point * Math.PI / 5;
                    float radius = point % 2 == 0 ? 10 : 4.5f;
                    float x = 12 + radius * (float) Math.cos(angle);
                    float y = 12 + radius * (float) Math.sin(angle);
                    if (point == 0) {
                        star.moveTo(x, y);
                    } else {
                        star.lineTo(x, y);
                    }
                }
                star.close();
                canvas.drawPath(star, paint);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    /** 버튼 비활성화 등에 사용되는 투명도를 반영한다. */
    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    /** 호스트가 요청한 색상 필터를 반영한다. */
    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    /** 선 아이콘의 투명 배경을 Android에 알린다. */
    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
