package com.example.pleosstarter.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.kakao.MapRecordMarker;

import java.util.Objects;

/** 다시 방문한 장소의 저장 글자를 보여 주고 열기·닫기 입력만 외부에 전달한다. */
public final class MapRevisitCardView extends FrameLayout implements MapRevisitDisplay {
    private final ImageView typeIcon;
    private final TextView title;
    private final TextView body;
    private final ImageButton closeButton;

    /** 기존 지도 색상과 선 아이콘으로 기본 숨김 상태의 재방문 카드를 만든다. */
    public MapRevisitCardView(Context context) {
        super(context);
        setId(R.id.map_revisit_card);
        setVisibility(View.GONE);
        setFocusable(true);
        setDefaultFocusHighlightEnabled(false);
        setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        setElevation(dp(4));

        GradientDrawable background = MapUiTheme.surface(context, Color.WHITE, 20);
        background.setStroke(dp(1), MapUiTheme.BORDER);
        setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8), background, null));

        // 본문과 닫기 터치 영역을 분리해 기록 열기 입력과 겹치지 않게 한다.
        LinearLayout content = new LinearLayout(context);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPaddingRelative(dp(18), dp(16), dp(8), dp(16));
        LayoutParams contentLayout = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        contentLayout.setMarginEnd(dp(60));
        addView(content, contentLayout);

        typeIcon = new ImageView(context);
        typeIcon.setId(R.id.map_revisit_type_icon);
        typeIcon.setPadding(dp(11), dp(11), dp(11), dp(11));
        typeIcon.setBackground(MapUiTheme.surface(context, MapUiTheme.ACCENT_SOFT, 14));
        typeIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(dp(48), dp(48));
        iconLayout.setMarginEnd(dp(14));
        content.addView(typeIcon, iconLayout);

        LinearLayout textColumn = new LinearLayout(context);
        textColumn.setOrientation(LinearLayout.VERTICAL);
        textColumn.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(textColumn, new LinearLayout.LayoutParams(0,
                LayoutParams.MATCH_PARENT, 1f));

        title = label(13, MapUiTheme.MUTED);
        title.setId(R.id.map_revisit_title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        textColumn.addView(title, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));

        body = label(20, MapUiTheme.INK);
        body.setId(R.id.map_revisit_body);
        body.setMaxLines(2);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bodyLayout = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bodyLayout.topMargin = dp(6);
        textColumn.addView(body, bodyLayout);

        closeButton = new ImageButton(context);
        closeButton.setId(R.id.map_revisit_close);
        closeButton.setContentDescription(context.getString(R.string.map_revisit_close));
        closeButton.setImageDrawable(new MapIconDrawable(MapIconDrawable.Symbol.CLOSE,
                MapUiTheme.MUTED, dp(24)));
        closeButton.setPadding(dp(14), dp(14), dp(14), dp(14));
        closeButton.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(context, Color.TRANSPARENT, 16), null));
        LayoutParams closeLayout = new LayoutParams(dp(52), dp(52), Gravity.TOP | Gravity.END);
        closeLayout.topMargin = dp(8);
        closeLayout.setMarginEnd(dp(8));
        addView(closeButton, closeLayout);
    }

    /** 저장 원문과 종류를 표시하며 탭·닫기 동작의 수명 관리는 호출자에게 맡긴다. */
    @Override
    public void showRecord(MapRecordMarker marker, Runnable open, Runnable dismiss) {
        Objects.requireNonNull(marker, "재방문 기록이 필요합니다.");
        Objects.requireNonNull(open, "기록 열기 동작이 필요합니다.");
        Objects.requireNonNull(dismiss, "카드 닫기 동작이 필요합니다.");

        String recordType = getContext().getString(marker.favorite()
                ? R.string.map_record_read_favorite : R.string.map_record_read_note);
        String savedText = marker.note() == null
                ? getContext().getString(R.string.map_record_read_favorite) : marker.note().text();
        title.setText(getContext().getString(R.string.map_revisit_title, recordType));
        body.setText(savedText);
        MapIconDrawable.Symbol symbol = marker.favorite()
                ? MapIconDrawable.Symbol.STAR : MapIconDrawable.Symbol.NOTE;
        typeIcon.setImageDrawable(new MapIconDrawable(symbol, MapUiTheme.ACCENT, dp(26)));
        setContentDescription(getContext().getString(R.string.map_revisit_open,
                recordType, savedText));

        setOnClickListener(view -> {
            open.run();
        });
        closeButton.setOnClickListener(view -> {
            dismiss.run();
        });
        setVisibility(View.VISIBLE);
    }

    /** 카드와 연결 콜백을 해제하며 지도·위치·재방문 판단 상태는 변경하지 않는다. */
    @Override
    public void hideRecord() {
        setVisibility(View.GONE);
        setOnClickListener(null);
        closeButton.setOnClickListener(null);
        setContentDescription(null);
        title.setText("");
        body.setText("");
        typeIcon.setImageDrawable(null);
    }

    /** 카드 본문과 보조 문구를 같은 글꼴로 만들고 접근성 읽기는 카드에 모은다. */
    private TextView label(int sizeSp, int color) {
        TextView text = new TextView(getContext());
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setIncludeFontPadding(false);
        text.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return text;
    }

    /** 공통 지도 밀도 변환으로 카드 내부 간격과 터치 영역을 계산한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
