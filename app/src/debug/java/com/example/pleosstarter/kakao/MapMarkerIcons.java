package com.example.pleosstarter.kakao;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import com.example.pleosstarter.ui.MapUiTheme;
import com.example.pleosstarter.ui.MapIconDrawable;

/** 현재 위치·저장 기록·선택 장소의 지도 아이콘을 생성한다. */
final class MapMarkerIcons {
    private static final int SELECTED_PLACE_HEIGHT = 104;
    private static final int SELECTED_PLACE_MAX_TEXT_WIDTH = 240;
    /** 외부 생성이 필요 없는 아이콘 팩터리다. */
    private MapMarkerIcons() {
    }

    /** 이동 방향을 추측하지 않는 파란 위치 점을 만든다. */
    static Bitmap currentPosition() {
        Bitmap icon = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.argb(65, 30, 120, 255));
        canvas.drawCircle(28, 28, 27, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(28, 28, 18, paint);
        paint.setColor(Color.rgb(24, 116, 245));
        canvas.drawCircle(28, 28, 13, paint);
        return icon;
    }

    /** 시험 좌표는 네이비 점으로 표시해 차량 위치와 구분한다. */
    static Bitmap testPosition() {
        Bitmap icon = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x33002C5F);
        canvas.drawCircle(28, 28, 27, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(28, 28, 18, paint);
        paint.setColor(MapUiTheme.INK);
        canvas.drawCircle(28, 28, 13, paint);
        return icon;
    }

    /** 짧은 장소명과 끝점이 아래 중앙인 핀을 한 장의 정면 아이콘으로 만든다. */
    static Bitmap selectedPlace(String name) {
        TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(MapUiTheme.INK);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextSize(17);
        String placeName = name == null || name.isBlank() ? "선택한 장소" : name.trim();
        String title = TextUtils.ellipsize(placeName, textPaint,
                SELECTED_PLACE_MAX_TEXT_WIDTH, TextUtils.TruncateAt.END).toString();
        int width = Math.max(112, (int) Math.ceil(textPaint.measureText(title)) + 32);

        // 이름표는 지도 바탕색과 관계없이 읽을 수 있게 흰 배경과 외곽선을 사용한다.
        Bitmap icon = Bitmap.createBitmap(width, SELECTED_PLACE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x26002C5F);
        canvas.drawRoundRect(1, 4, width - 1, 42, 12, 12, paint);
        paint.setColor(Color.WHITE);
        canvas.drawRoundRect(1, 1, width - 1, 39, 12, 12, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(MapUiTheme.BORDER);
        canvas.drawRoundRect(1, 1, width - 1, 39, 12, 12, paint);
        float textBaseline = 20 - (textPaint.ascent() + textPaint.descent()) / 2;
        canvas.drawText(title, (width - textPaint.measureText(title)) / 2, textBaseline, textPaint);

        // 핀 끝은 아래 두 픽셀 여백에 두며 지도 마커의 오프셋으로 정확히 보정한다.
        float centerX = width / 2.0f;
        Path pin = new Path();
        pin.moveTo(centerX, SELECTED_PLACE_HEIGHT - 2);
        pin.cubicTo(centerX - 5, 93, centerX - 24, 79, centerX - 24, 65);
        pin.cubicTo(centerX - 24, 34, centerX + 24, 34, centerX + 24, 65);
        pin.cubicTo(centerX + 24, 79, centerX + 5, 93, centerX, SELECTED_PLACE_HEIGHT - 2);
        pin.close();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(MapUiTheme.ACCENT);
        canvas.drawPath(pin, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(Color.WHITE);
        canvas.drawPath(pin, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, 64, 9, paint);
        return icon;
    }

    /** 같은 크기의 흰 원 안에서 별과 기록 아이콘을 색·형태로 구분한다. */
    static Bitmap record(boolean favorite) {
        Bitmap icon = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(icon);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x20002C5F);
        canvas.drawCircle(30, 32, 27, paint);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(30, 29, 27, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.4f);
        paint.setColor(MapUiTheme.BORDER);
        canvas.drawCircle(30, 29, 26.5f, paint);

        MapIconDrawable.Symbol symbol = favorite
                ? MapIconDrawable.Symbol.STAR : MapIconDrawable.Symbol.NOTE;
        int color = favorite ? Color.rgb(176, 113, 16) : MapUiTheme.ACCENT;
        MapIconDrawable glyph = new MapIconDrawable(symbol, color, 30);
        glyph.setBounds(15, 14, 45, 44);
        glyph.draw(canvas);
        return icon;
    }
}
