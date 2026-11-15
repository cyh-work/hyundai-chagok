package com.example.pleosstarter.stores;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.ui.MapUiTheme;
import com.example.pleosstarter.ui.MapIconDrawable;

import java.util.function.Consumer;

/** 선택한 실제 매장의 상세 필드를 앱 안에서 표시하고 목록 또는 지도에 돌아간다. */
public final class StoreDetailDialog extends Dialog {
    private final StoreSummary store;
    private final Consumer<StoreSummary> mapListener;
    private final boolean mapActionAvailable;

    /** 검색 응답과 앱 내부 지도 이동 동작을 받아 외부 서비스 이동 없이 표시한다. */
    public StoreDetailDialog(Context context, StoreSummary store,
                             Consumer<StoreSummary> mapListener, boolean mapActionAvailable) {
        super(context);
        this.store = store;
        this.mapListener = mapListener;
        this.mapActionAvailable = mapActionAvailable;
    }

    /** 매장 이름과 업종을 먼저 보여주고 주소·전화는 구분된 행으로 읽을 수 있게 한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = verticalLayout();
        content.setPadding(dp(26), dp(24), dp(26), dp(22));
        content.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 20));
        LinearLayout details = verticalLayout();
        addIdentity(details);
        addField(details, R.string.store_detail_road_address, store.getRoadAddress());
        if (!store.getAddress().equals(store.getRoadAddress())) {
            addField(details, R.string.store_detail_address, store.getAddress());
        }
        addField(details, R.string.store_detail_phone, store.getPhone());

        ScrollView scroll = new ScrollView(getContext());
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(8));
        scroll.addView(details);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        TextView source = text(getContext().getString(R.string.store_detail_source), 12, MapUiTheme.MUTED);
        source.setPadding(0, dp(12), 0, dp(14));
        content.addView(source);
        if (mapActionAvailable && !canShowOnMap()) {
            TextView missingLocation = text(getContext().getString(R.string.store_detail_no_location),
                    14, MapUiTheme.MUTED);
            missingLocation.setPadding(0, 0, 0, dp(12));
            content.addView(missingLocation);
        }
        addActions(content);
        setContentView(content);
    }

    /** 매장 이름·원천 업종·선택 지점까지 거리를 상세 화면의 첫 묶음으로 표시한다. */
    private void addIdentity(LinearLayout content) {
        TextView heading = text(getContext().getString(R.string.store_detail_title), 13, MapUiTheme.ACCENT);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setCompoundDrawablePadding(dp(7));
        heading.setCompoundDrawables(new MapIconDrawable(
                MapIconDrawable.Symbol.PIN, MapUiTheme.ACCENT, dp(17)), null, null, null);
        content.addView(heading);

        TextView nameView = text(store.getName(), 28, MapUiTheme.INK);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setAccessibilityHeading(true);
        nameView.setPadding(0, dp(12), 0, dp(8));
        content.addView(nameView);
        if (!store.getCategory().isEmpty()) {
            TextView categoryView = text(store.getCategory(), 14, MapUiTheme.MUTED);
            categoryView.setPadding(0, 0, 0, dp(14));
            content.addView(categoryView);
        }

        Double distance = store.getDistanceMeters();
        if (distance != null && Double.isFinite(distance)) {
            TextView distanceView = text(getContext().getString(R.string.store_detail_distance_value,
                    Math.round(distance)), 13, MapUiTheme.ACCENT);
            distanceView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            distanceView.setPadding(dp(12), dp(7), dp(12), dp(7));
            distanceView.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 8));
            content.addView(distanceView, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        View divider = new View(getContext());
        divider.setBackgroundColor(MapUiTheme.BORDER);
        LinearLayout.LayoutParams dividerLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        dividerLayout.topMargin = dp(22);
        content.addView(divider, dividerLayout);
    }

    /** 목록 복귀는 목록 상태를 유지하고 지도 이동은 유효한 좌표가 있을 때만 허용한다. */
    private void addActions(LinearLayout content) {
        LinearLayout actions = new LinearLayout(getContext());
        Button backButton = new Button(getContext());
        backButton.setText(R.string.store_detail_back);
        MapUiTheme.styleButton(backButton, false);
        backButton.setOnClickListener(view -> {
            dismiss();
        });
        LinearLayout.LayoutParams backLayout = new LinearLayout.LayoutParams(0, dp(54), 0.7f);
        backLayout.setMarginEnd(dp(8));
        actions.addView(backButton, backLayout);

        if (mapActionAvailable) {
            Button mapButton = new Button(getContext());
            mapButton.setText(R.string.store_detail_map);
            MapUiTheme.styleButton(mapButton, true);
            mapButton.setEnabled(canShowOnMap());
            mapButton.setOnClickListener(view -> {
                if (canShowOnMap()) {
                    mapListener.accept(store);
                    dismiss();
                }
            });
            actions.addView(mapButton, new LinearLayout.LayoutParams(0, dp(54), 1.3f));
        }
        content.addView(actions);
    }

    /** 조회 중이던 목록 위에 분할 화면 크기를 지키는 상세창을 배치한다. */
    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) {
            return;
        }

        int width = getContext().getResources().getDisplayMetrics().widthPixels;
        int height = getContext().getResources().getDisplayMetrics().heightPixels;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.12f);
        window.setLayout(Math.min(dp(680), Math.round(width * 0.92f)),
                Math.min(dp(560), Math.round(height * 0.84f)));
    }

    /** 누락되거나 WGS84 범위를 벗어난 좌표를 지도 이동에 사용하지 않는다. */
    public boolean canShowOnMap() {
        Double latitude = store.getLatitude();
        Double longitude = store.getLongitude();
        return mapActionAvailable && mapListener != null && latitude != null && longitude != null
                && Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** 제공된 필드의 이름과 값을 좌우로 정렬하고 각 행에 얇은 구분선을 둔다. */
    private void addField(LinearLayout parent, int labelResource, String value) {
        if (value.isEmpty()) {
            return;
        }

        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.TOP);
        row.setPadding(0, dp(18), 0, dp(18));
        TextView label = text(getContext().getString(labelResource), 13, MapUiTheme.MUTED);
        label.setPadding(0, dp(2), dp(12), 0);
        row.addView(label, new LinearLayout.LayoutParams(dp(88),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView field = text(value, 18, MapUiTheme.INK);
        row.addView(field, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));
        parent.addView(row);

        View divider = new View(getContext());
        divider.setBackgroundColor(MapUiTheme.BORDER);
        parent.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    /** 상세 내용의 세로 배치 컨테이너를 만든다. */
    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /** 상세 화면의 글자 크기와 색을 일관되게 적용한다. */
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(4), 1.0f);
        return view;
    }

    /** 화면 밀도에 맞춰 여백과 크기를 변환한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
