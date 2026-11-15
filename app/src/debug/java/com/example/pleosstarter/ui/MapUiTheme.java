package com.example.pleosstarter.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.Button;

/** 지도 화면과 장소·메모 카드에서 공유하는 색상과 기본 터치 스타일이다. */
public final class MapUiTheme {
    public static final int INK = Color.rgb(0, 44, 95);
    public static final int MUTED = Color.rgb(104, 117, 133);
    public static final int SURFACE = Color.rgb(244, 246, 248);
    public static final int ACCENT = Color.rgb(0, 127, 168);
    public static final int ACCENT_SOFT = Color.rgb(231, 243, 248);
    public static final int BORDER = Color.rgb(223, 229, 235);

    /** 상태 없이 화면 스타일만 제공하므로 인스턴스 생성을 제한한다. */
    private MapUiTheme() {
    }

    /** 표시 중인 화면의 밀도로 논리 크기를 픽셀로 변환한다. */
    public static int dp(Context context, int size) {
        return Math.round(size * context.getResources().getDisplayMetrics().density);
    }

    /** 카드·입력란에 사용할 둥근 단색 배경을 새로 만든다. */
    public static GradientDrawable surface(Context context, int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    /** 주 동작과 보조 동작을 구분하면서 최소 52dp 터치 영역과 누름 피드백을 적용한다. */
    public static void styleButton(Button button, boolean primary) {
        Context context = button.getContext();
        int backgroundColor = primary ? INK : SURFACE;
        int textColor = primary ? Color.WHITE : INK;
        int pressedColor = primary ? 0x33FFFFFF : 0x18007FA8;
        GradientDrawable background = surface(context, backgroundColor, 12);
        button.setBackgroundTintList(null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(pressedColor),
                background, null));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{MUTED, textColor}));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 52));
        button.setMinimumHeight(dp(context, 52));
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
    }
}
