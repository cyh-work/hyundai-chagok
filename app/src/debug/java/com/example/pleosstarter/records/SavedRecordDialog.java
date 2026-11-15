package com.example.pleosstarter.records;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.ui.MapUiTheme;

/** 저장한 장소 또는 메모 원문을 읽고 명시적인 지도 이동·삭제를 요청한다. */
public final class SavedRecordDialog extends Dialog {
    private final String title;
    private final String body;
    private final int favoriteNumber;
    private final Runnable showOnMap;
    private final Runnable deleteFavorite;
    private TextView errorView;

    /** 저장 동작은 조정자에 맡기고 표시 정보와 사용자 동작만 보관한다. */
    private SavedRecordDialog(Context context, String title, String body, int favoriteNumber,
            Runnable showOnMap, Runnable deleteFavorite) {
        super(context);
        this.title = title;
        this.body = body;
        this.favoriteNumber = favoriteNumber;
        this.showOnMap = showOnMap;
        this.deleteFavorite = deleteFavorite;
    }

    /** 목록 순번으로 기본 이름을 구분하되 저장 모델과 구체적인 장소 이름은 보존한다. */
    public static SavedRecordDialog forFavorite(Context context, SavedMapPoint point,
            int displayNumber, Runnable showOnMap, Runnable deleteFavorite) {
        String displayName = point.name();
        if ("지도에 기록한 위치".equals(displayName)) {
            displayName = "저장한 위치 " + displayNumber;
        }
        return new SavedRecordDialog(context, displayName, point.note(), displayNumber,
                showOnMap, deleteFavorite);
    }

    /** 메모 원문을 자르지 않고 전체 스크롤 영역으로 전달한다. */
    public static SavedRecordDialog forNote(Context context, LocationNote note, Runnable showOnMap) {
        return new SavedRecordDialog(context, "메모", note.text(), 0, showOnMap, null);
    }

    /** 긴 내용과 하단 동작을 분리해 원문 스크롤 중에도 버튼을 유지한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(20));
        content.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 24));

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout reading = new LinearLayout(getContext());
        reading.setOrientation(LinearLayout.VERTICAL);
        if (deleteFavorite != null) {
            TextView type = text("★  즐겨찾기 " + favoriteNumber, 14, MapUiTheme.ACCENT);
            type.setPadding(0, 0, 0, dp(10));
            reading.addView(type);
        }
        TextView heading = text(title, 24, MapUiTheme.INK);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setAccessibilityHeading(true);
        reading.addView(heading);
        if (!body.isEmpty()) {
            TextView memo = text(body, 19, MapUiTheme.INK);
            memo.setTextIsSelectable(false);
            memo.setPadding(0, dp(20), 0, dp(12));
            reading.addView(memo);
        }
        scroll.addView(reading);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        errorView = text("", 15, MapUiTheme.INK);
        errorView.setVisibility(View.GONE);
        errorView.setPadding(dp(12), dp(10), dp(12), dp(10));
        errorView.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 12));
        errorView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        content.addView(errorView);

        LinearLayout actions = new LinearLayout(getContext());
        Button close = button("닫기", false);
        close.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams closeLayout = new LinearLayout.LayoutParams(0, dp(52), 1.0f);
        closeLayout.setMarginEnd(dp(8));
        actions.addView(close, closeLayout);
        Button map = button("지도에서 보기", true);
        map.setOnClickListener(view -> {
            showOnMap.run();
            dismiss();
        });
        actions.addView(map, new LinearLayout.LayoutParams(0, dp(52), 1.6f));
        LinearLayout.LayoutParams actionsLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLayout.topMargin = dp(16);
        content.addView(actions, actionsLayout);

        if (deleteFavorite != null) {
            Button delete = button("즐겨찾기 삭제", false);
            delete.setTextColor(MapUiTheme.MUTED);
            delete.setOnClickListener(view -> deleteFavorite.run());
            LinearLayout.LayoutParams deleteLayout = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            deleteLayout.topMargin = dp(8);
            content.addView(delete, deleteLayout);
        }
        setContentView(content);
    }

    /** 분할 창 안에서 메모 스크롤 영역과 모든 동작 버튼이 보이게 배치한다. */
    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) {
            return;
        }
        int availableWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int availableHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        if (getOwnerActivity() != null) {
            View owner = getOwnerActivity().getWindow().getDecorView();
            if (owner.getWidth() > 0 && owner.getHeight() > 0) {
                availableWidth = owner.getWidth();
                availableHeight = owner.getHeight();
            }
        }
        int preferredHeight = deleteFavorite == null ? dp(560) : dp(320);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.28f);
        window.setLayout(Math.min(dp(560), Math.round(availableWidth * 0.92f)),
                Math.min(preferredHeight, Math.round(availableHeight * 0.86f)));
    }

    /** 삭제에 실패하면 기록 창을 유지하고 안전한 오류 문구를 표시한다. */
    public void showFailure(MapRecordFailure failure) {
        errorView.setText(failure.getMessage());
        errorView.setVisibility(View.VISIBLE);
    }

    /** 제목과 원문에 읽기 쉬운 색상과 행간을 적용한다. */
    private TextView text(String content, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1.0f);
        return view;
    }

    /** 지도 화면과 같은 주·보조 버튼 스타일을 사용한다. */
    private Button button(String label, boolean primary) {
        Button button = new Button(getContext());
        button.setText(label);
        MapUiTheme.styleButton(button, primary);
        return button;
    }

    /** 공통 화면 밀도 변환을 사용한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
