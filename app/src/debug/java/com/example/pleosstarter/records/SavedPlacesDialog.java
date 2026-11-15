package com.example.pleosstarter.records;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.ui.MapActionIcon;
import com.example.pleosstarter.ui.MapIconDrawable;
import com.example.pleosstarter.ui.MapUiTheme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** 실제 메모와 즐겨찾기를 생성 시각이 구분되는 통합 히스토리로 보여준다. */
public final class SavedPlacesDialog extends Dialog {
    private final List<MapHistoryEntry> entries;
    private final Consumer<MapHistoryEntry> listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("MM.dd · HH:mm", Locale.KOREA);

    /** 저장 서비스에서 받은 목록 스냅샷과 선택 동작을 연결한다. */
    public SavedPlacesDialog(Context context, List<MapHistoryEntry> entries,
            Consumer<MapHistoryEntry> listener) {
        super(context);
        this.entries = entries;
        this.listener = listener;
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    /** 빈 상태 또는 재활용 가능한 목록을 둥근 카드 안에 구성한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(28), dp(28), dp(20));
        content.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 24));
        LinearLayout.LayoutParams headerLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
        headerLayout.bottomMargin = dp(20);
        content.addView(createHeader(), headerLayout);

        if (entries.isEmpty()) {
            TextView empty = text(getContext().getString(R.string.map_history_empty),
                    18, MapUiTheme.MUTED);
            empty.setGravity(android.view.Gravity.CENTER);
            empty.setPadding(dp(12), dp(24), dp(12), dp(24));
            empty.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 16));
            content.addView(empty, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        } else {
            ListView list = new ListView(getContext());
            list.setId(R.id.map_history_list);
            list.setBackgroundColor(Color.WHITE);
            list.setDivider(new ColorDrawable(MapUiTheme.BORDER));
            list.setDividerHeight(dp(1));
            list.setAdapter(new SavedPlacesAdapter());
            list.setOnItemClickListener((parent, view, position, id) -> {
                dismiss();
                listener.accept(entries.get(position));
            });
            content.addView(list, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        }

        setContentView(content);
    }

    /** 공통 모달의 제목과 닫기 조작을 같은 높이에 배치한다. */
    private LinearLayout createHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getContext().getString(R.string.map_history_title), 24, MapUiTheme.INK);
        title.setId(R.id.map_history_title);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageButton close = new ImageButton(getContext());
        close.setId(R.id.map_history_close);
        close.setImageDrawable(new MapIconDrawable(MapIconDrawable.Symbol.CLOSE, MapUiTheme.INK, dp(24)));
        close.setPadding(dp(14), dp(14), dp(14), dp(14));
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 16), null));
        close.setContentDescription(getContext().getString(R.string.map_history_close));
        close.setOnClickListener(view -> {
            dismiss();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(52), dp(52)));
        return header;
    }

    /** 현재 분할 창 안에서 목록과 상단 닫기가 함께 보이게 크기를 제한한다. */
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
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setDimAmount(0.28f);
        window.setLayout(Math.min(dp(720), Math.round(availableWidth * 0.90f)),
                Math.min(dp(720), Math.round(availableHeight * 0.86f)));
    }

    /** 표시 문구의 색상과 행간을 공통 지도 스타일에 맞춘다. */
    private TextView text(String content, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(content);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    /** 공통 화면 밀도 변환을 사용한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }

    /** 긴 저장 목록에서도 행을 재사용하고 원문 모델은 수정하지 않는다. */
    private final class SavedPlacesAdapter extends BaseAdapter {
        /** 두 저장 유형의 전체 항목 수를 반환한다. */
        @Override
        public int getCount() {
            return entries.size();
        }

        /** 목록 위치에 대응하는 원래 저장 모델을 반환한다. */
        @Override
        public MapHistoryEntry getItem(int position) {
            return entries.get(position);
        }

        /** 현재 목록 스냅샷에서 사용하는 행 식별자를 반환한다. */
        @Override
        public long getItemId(int position) {
            return position;
        }

        /** 두 줄 미리보기만 표시하고 상세 화면에 전달할 원문은 유지한다. */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            SavedEntryView row;
            if (convertView instanceof SavedEntryView savedRow) {
                row = savedRow;
            } else {
                row = new SavedEntryView();
            }
            row.bind(entries.get(position));
            return row;
        }
    }

    /** 유형 표시와 두 줄 원문 미리보기를 갖춘 재사용 목록 카드다. */
    private final class SavedEntryView extends LinearLayout {
        private final TextView typeView;
        private final TextView timeView;
        private final TextView previewView;
        private final ImageView iconView;

        /** 항목 전체를 누를 수 있는 충분한 크기의 카드를 구성한다. */
        private SavedEntryView() {
            super(SavedPlacesDialog.this.getContext());
            setOrientation(HORIZONTAL);
            setMinimumHeight(dp(92));
            setPadding(0, dp(18), 0, dp(18));
            setBackground(new RippleDrawable(ColorStateList.valueOf(MapUiTheme.ACCENT_SOFT),
                    MapUiTheme.surface(getContext(), Color.WHITE, 16), null));
            iconView = new ImageView(getContext());
            iconView.setPadding(dp(10), dp(10), dp(10), dp(10));
            iconView.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 12));
            iconView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams iconLayout = new LinearLayout.LayoutParams(dp(44), dp(44));
            iconLayout.setMarginEnd(dp(14));
            addView(iconView, iconLayout);

            LinearLayout body = new LinearLayout(getContext());
            body.setOrientation(VERTICAL);
            addView(body, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            LinearLayout meta = new LinearLayout(getContext());
            typeView = text("", 13, MapUiTheme.ACCENT);
            typeView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            meta.addView(typeView, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
            timeView = text("", 12, MapUiTheme.MUTED);
            meta.addView(timeView);
            body.addView(meta);
            previewView = text("", 18, MapUiTheme.INK);
            previewView.setMaxLines(2);
            previewView.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams previewLayout = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            previewLayout.topMargin = dp(7);
            body.addView(previewView, previewLayout);
        }

        /** 실제 생성 시각만 표시하며 과거 미확인 시각은 이전 기록으로 구분한다. */
        private void bind(MapHistoryEntry entry) {
            boolean favorite = entry.type() == MapHistoryEntry.Type.FAVORITE;
            typeView.setText(favorite ? "즐겨찾기" : "메모");
            iconView.setImageDrawable(new MapActionIcon(favorite
                    ? MapActionIcon.Kind.FAVORITE : MapActionIcon.Kind.NOTE, MapUiTheme.ACCENT));
            previewView.setText(entry.preview().isEmpty() ? entry.title() : entry.preview());
            if (entry.hasCreatedAt()) {
                timeView.setText(timeFormat.format(new Date(entry.createdAtMillis())));
            } else {
                timeView.setText("이전 기록");
            }
        }
    }
}
