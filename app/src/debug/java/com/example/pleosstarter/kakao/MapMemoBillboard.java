package com.example.pleosstarter.kakao;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.ui.MapUiTheme;

import java.util.Objects;
import java.util.function.Supplier;

/** 저장 원문만 지도 지점 위에 표시하며 글자는 항상 화면 정면으로 읽힌다. */
final class MapMemoBillboard {
    private final FrameLayout host;
    private final TextView body;
    private final ScrollView scroll;
    private final Runnable trackFrame = this::updatePosition;
    private MapRecordInsets insets = new MapRecordInsets(0, 0, 0, 0);
    private Supplier<MapMemoPlacement.ScreenPoint> projection;
    private boolean active;
    private boolean closed;
    private boolean selected;
    private int measuredMaximumWidth;
    private int measuredViewportHeight;

    /** 배경·제목·버튼 없이 텍스트와 긴 원문을 읽을 스크롤 영역만 구성한다. */
    MapMemoBillboard(FrameLayout host) {
        this.host = Objects.requireNonNull(host, "메모를 표시할 지도 컨테이너가 필요합니다.");
        Context context = host.getContext();

        body = new TextView(context);
        body.setTextColor(MapUiTheme.INK);
        body.setTextSize(22);
        body.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setLineSpacing(dp(4), 1.0f);
        body.setPadding(dp(6), dp(6), dp(6), dp(6));
        body.setShadowLayer(dp(3), 0, 0, Color.WHITE);

        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClickable(true);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll.setVisibility(View.GONE);
        host.addView(scroll, new FrameLayout.LayoutParams(1, 1));
    }

    /** 원문 전체를 축약 없이 표시하고 매 프레임 지점만 투영해 위치를 맞춘다. */
    void show(LocationNote note, Supplier<MapMemoPlacement.ScreenPoint> projection) {
        if (closed) {
            return;
        }
        this.projection = Objects.requireNonNull(projection, "지도 투영 함수가 필요합니다.");
        selected = true;
        body.setText(note.text());
        scroll.scrollTo(0, 0);
        measuredMaximumWidth = 0;
        measuredViewportHeight = 0;
        host.removeCallbacks(trackFrame);
        if (active) {
            updatePosition();
        }
    }

    /** 지도 화면 생명주기에 맞춰 추적을 시작하거나 중지한다. */
    void setActive(boolean active) {
        this.active = active;
        host.removeCallbacks(trackFrame);
        if (active && selected && !closed) {
            updatePosition();
        } else {
            hideViews();
        }
    }

    /** 지도 좌표는 유지하고 하단 조작 영역을 피하도록 텍스트만 다시 배치한다. */
    void setInsets(MapRecordInsets insets) {
        Objects.requireNonNull(insets, "지도 기록 텍스트의 안전 여백이 필요합니다.");
        if (closed || this.insets.equals(insets)) {
            return;
        }
        this.insets = insets;
        measuredMaximumWidth = 0;
        measuredViewportHeight = 0;
        host.removeCallbacks(trackFrame);
        if (active && selected) {
            updatePosition();
        }
    }

    /** 본문 스크롤을 도형 입력에서 제외할 수 있게 현재 텍스트 영역을 검사한다. */
    boolean contains(float x, float y) {
        return scroll.getVisibility() == View.VISIBLE
                && x >= scroll.getX() && x <= scroll.getX() + scroll.getWidth()
                && y >= scroll.getY() && y <= scroll.getY() + scroll.getHeight();
    }

    /** 화면 밖에 숨겨진 선택과 실제로 읽을 수 있는 텍스트를 구분한다. */
    boolean isVisible() {
        return !closed && active && selected && scroll.getVisibility() == View.VISIBLE;
    }

    /** 선택을 해제하고 원문 및 지연 프레임 참조를 정리한다. */
    void hide() {
        selected = false;
        projection = null;
        host.removeCallbacks(trackFrame);
        body.setText("");
        hideViews();
    }

    /** 지도 종료 시 추가한 뷰와 프레임 콜백을 모두 제거한다. */
    void close() {
        closed = true;
        hide();
        host.removeView(scroll);
    }

    /** 분할 화면의 현재 크기로 측정하며 화면 밖 지점은 숨기고 다시 들어오면 복원한다. */
    private void updatePosition() {
        if (closed || !active || !selected || projection == null) {
            return;
        }
        int width = host.getWidth();
        int height = host.getHeight();
        long availableWidth = (long) width - insets.left() - insets.right() - dp(32);
        long availableHeight = (long) height - insets.top() - insets.bottom() - dp(32);
        if (availableWidth < dp(80) || availableHeight < dp(32)) {
            hideViews();
            host.postOnAnimation(trackFrame);
            return;
        }
        int maximumWidth = (int) Math.min(dp(420), availableWidth);
        int maximumHeight = (int) Math.min(Math.min(dp(360), height / 2 - dp(48)), availableHeight);
        if (maximumWidth < dp(80) || maximumHeight < dp(32)) {
            hideViews();
            host.postOnAnimation(trackFrame);
            return;
        }

        // 짧은 원문은 내용만큼만 차지하고 긴 원문은 너비 안에서 줄바꿈·스크롤한다.
        if (measuredMaximumWidth != maximumWidth || measuredViewportHeight != height) {
            body.measure(View.MeasureSpec.makeMeasureSpec(maximumWidth, View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int textWidth = Math.max(1, body.getMeasuredWidth());
            int textHeight = Math.min(body.getMeasuredHeight(), maximumHeight);
            scroll.setLayoutParams(new FrameLayout.LayoutParams(textWidth, textHeight));
            scroll.measure(View.MeasureSpec.makeMeasureSpec(textWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(textHeight, View.MeasureSpec.EXACTLY));
            measuredMaximumWidth = maximumWidth;
            measuredViewportHeight = height;
        }

        // 매 프레임 세계 좌표만 투영하며 본문의 회전·기울기는 적용하지 않는다.
        MapMemoPlacement.ScreenPoint point = projection.get();
        MapMemoPlacement.Placement placement = MapMemoPlacement.calculate(point, width, height,
                scroll.getMeasuredWidth(), scroll.getMeasuredHeight(), dp(16), dp(32), insets);
        if (placement.visible()) {
            scroll.setX(placement.left());
            scroll.setY(placement.top());
            scroll.setVisibility(View.VISIBLE);
        } else {
            hideViews();
        }
        host.postOnAnimation(trackFrame);
    }

    /** 지도 화면에서 텍스트만 숨기며 보관한 선택은 바꾸지 않는다. */
    private void hideViews() {
        scroll.setVisibility(View.GONE);
    }

    /** 실제 지도 뷰의 화면 밀도를 기준으로 텍스트 영역 치수를 정한다. */
    private int dp(int size) {
        return MapUiTheme.dp(host.getContext(), size);
    }

}
