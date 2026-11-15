package com.example.pleosstarter.stores;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.ui.MapUiTheme;
import com.example.pleosstarter.ui.MapIconDrawable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 고정된 기록·선택 좌표의 주변 장소 필터와 조회 목록을 공통 화면으로 제공한다.
 * 조회 서비스와 좌표 주입이 필수이므로 XML 생성 없이 코드에서만 구성한다.
 */
@SuppressLint("ViewConstructor")
public final class StoreResultsPanel extends LinearLayout {
    private static final String TAG = "StoreResults";
    private static final int NEARBY_RADIUS_METERS = KakaoStoreQuery.DEFAULT_RADIUS_METERS;
    private final StoreSearchService searchService;
    private final double latitude;
    private final double longitude;
    private final Consumer<StoreSummary> mapListener;
    private final Map<KakaoStoreCategory, Button> categoryButtons =
            new EnumMap<>(KakaoStoreCategory.class);
    private KakaoStoreCategory selectedCategory = KakaoStoreCategory.RESTAURANT;
    private LinearLayout filters;
    private LinearLayout resultList;
    private ScrollView resultScroll;
    private TextView stateView;
    private Button retryButton;
    private ProgressBar loadingIndicator;
    private long generation;
    private boolean closed;
    private boolean started;

    /** 좌표와 선택 동작을 고정하고 패널이 닫힐 때 주입한 조회 서비스도 해제한다. */
    public StoreResultsPanel(Context context, StoreSearchService searchService,
                             double latitude, double longitude,
                             Consumer<StoreSummary> onSelect) {
        super(context);
        this.searchService = searchService;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mapListener = onSelect;

        setOrientation(LinearLayout.VERTICAL);
        addFilters();

        // 조회 중에는 진행 표시만, 빈 결과와 실패 때만 필요한 상태를 표시한다.
        loadingIndicator = new ProgressBar(getContext());
        loadingIndicator.setIndeterminateTintList(ColorStateList.valueOf(MapUiTheme.ACCENT));
        loadingIndicator.setContentDescription(getContext().getString(R.string.stores_loading));
        LinearLayout.LayoutParams loadingLayout = new LinearLayout.LayoutParams(dp(28), dp(28));
        loadingLayout.gravity = Gravity.CENTER_HORIZONTAL;
        loadingLayout.topMargin = dp(20);
        addView(loadingIndicator, loadingLayout);

        stateView = text("", 16, MapUiTheme.MUTED);
        stateView.setPadding(0, dp(24), 0, dp(12));
        stateView.setGravity(Gravity.CENTER);
        stateView.setVisibility(View.GONE);
        stateView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addView(stateView);
        retryButton = button(getContext().getString(R.string.stores_retry), false);
        retryButton.setVisibility(View.GONE);
        retryButton.setOnClickListener(view -> {
            loadStores();
        });
        addView(retryButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));

        resultList = verticalLayout();
        resultScroll = new ScrollView(getContext());
        resultScroll.setFillViewport(false);
        resultScroll.setClipToPadding(false);
        resultScroll.setPadding(0, dp(14), 0, 0);
        resultScroll.addView(resultList);
        addView(resultScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));
        updateSelections();
    }

    /** 고정 100m 안에서 조회할 네 가지 장소 분류를 한 행에 배치한다. */
    private void addFilters() {
        filters = new LinearLayout(getContext());
        filters.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout categories = new LinearLayout(getContext());
        categories.setPadding(dp(4), dp(4), dp(4), dp(4));
        categories.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 14));
        for (KakaoStoreCategory category : KakaoStoreCategory.values()) {
            Button categoryButton = button(categoryLabel(category), false);
            styleCategoryButton(categoryButton, category == selectedCategory);
            categoryButton.setOnClickListener(view -> {
                selectedCategory = category;
                loadStores();
            });
            categoryButtons.put(category, categoryButton);
            LinearLayout.LayoutParams tab = new LinearLayout.LayoutParams(0, dp(48), 1.0f);
            if (category != KakaoStoreCategory.TOURIST_ATTRACTION) {
                tab.setMarginEnd(dp(3));
            }
            categories.addView(categoryButton, tab);
        }
        filters.addView(categories, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        addView(filters);
    }

    /** 선택된 업종은 블루 배경과 흰 글자로 표시하고 나머지는 같은 바탕을 유지한다. */
    private void styleCategoryButton(Button button, boolean selected) {
        MapUiTheme.styleButton(button, false);
        int background = selected ? MapUiTheme.ACCENT : MapUiTheme.SURFACE;
        int foreground = selected ? Color.WHITE : MapUiTheme.MUTED;
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), background, 10), null));
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(4), dp(4), dp(4), dp(4));
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setSelected(selected);
    }

    /** 독립 장소창이 기존 필터 행 끝에 닫기 버튼을 배치할 수 있게 한다. */
    void addFilterAction(View action) {
        filters.addView(action, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    /** 화면을 처음 표시할 때만 고정 좌표의 기본 분류를 조회한다. */
    public void start() {
        if (closed || started) {
            return;
        }

        started = true;
        loadStores();
    }

    /** 같은 중심의 100m 안에서 선택한 업종을 조회하고 이전 결과는 폐기한다. */
    private void loadStores() {
        if (closed || !started) {
            return;
        }

        long request = ++generation;
        searchService.cancel();
        updateSelections();
        stateView.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        loadingIndicator.setVisibility(View.VISIBLE);
        resultList.removeAllViews();
        resultScroll.scrollTo(0, 0);

        KakaoStoreQuery query = new KakaoStoreQuery(
                latitude, longitude, selectedCategory, NEARBY_RADIUS_METERS);
        searchService.search(query, new StoreSearchCallback() {
            /** 현재 선택 조건의 실제 응답만 목록에 반영한다. */
            @Override
            public void onSuccess(StoreSearchResult result) {
                if (isCurrentRequest(request)) {
                    showResult(result);
                }
            }

            /** 인증·통신 실패를 매장이 없는 결과로 바꾸지 않는다. */
            @Override
            public void onFailure(StoreSearchFailure failure) {
                if (isCurrentRequest(request)) {
                    loadingIndicator.setVisibility(View.GONE);
                    stateView.setVisibility(View.VISIBLE);
                    retryButton.setVisibility(View.VISIBLE);
                    stateView.setTextColor(Color.rgb(154, 58, 38));
                    stateView.setText(failureMessage(failure));
                    Log.w(TAG, "STORE_UI_FAILURE category=" + selectedCategory.name()
                            + " radius=" + NEARBY_RADIUS_METERS + " code=" + failure.getCode()
                            + " http=" + failure.getHttpStatus());
                }
            }
        });
    }

    /** 선택한 장소 분류를 해당 필터에 표시한다. */
    private void updateSelections() {
        for (Map.Entry<KakaoStoreCategory, Button> entry : categoryButtons.entrySet()) {
            styleCategoryButton(entry.getValue(), entry.getKey() == selectedCategory);
        }
    }

    /** 닫히거나 새로운 조건으로 조회한 패널에는 이전 응답을 적용하지 않는다. */
    private boolean isCurrentRequest(long request) {
        return started && !closed && request == generation;
    }

    /** 결과가 있으면 목록만 표시하고 빈 결과는 짧은 상태로 구분한다. */
    private void showResult(StoreSearchResult result) {
        loadingIndicator.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        stateView.setTextColor(MapUiTheme.MUTED);
        if (result.getStores().isEmpty()) {
            stateView.setText(R.string.stores_empty);
            stateView.setVisibility(View.VISIBLE);
        } else {
            stateView.setVisibility(View.GONE);
        }

        resultList.removeAllViews();
        for (StoreSummary store : result.getStores()) {
            addStore(store);
        }
        Log.i(TAG, "STORE_UI_RESULT category=" + selectedCategory.name()
                + " radius=" + result.getRadiusMeters() + " displayed=" + result.getStores().size()
                + " truncated=" + result.isTruncated());
    }

    /** 장소 이름과 거리만 표시하고 선택하면 상세창 없이 지도에 표시한다. */
    private void addStore(StoreSummary store) {
        if (resultList.getChildCount() > 0) {
            addDivider(resultList, 52);
        }
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(16));
        row.setMinimumHeight(dp(76));
        row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                new ColorDrawable(Color.WHITE), null));
        boolean canFocus = canFocusStore(store);
        row.setClickable(canFocus);
        row.setFocusable(canFocus);
        row.setEnabled(canFocus);
        row.setAlpha(canFocus ? 1.0f : 0.5f);
        row.setContentDescription(getContext().getString(R.string.stores_show_on_map, store.getName()));
        row.setOnClickListener(view -> {
            focusStore(store);
        });

        TextView marker = text("", 14, MapUiTheme.ACCENT);
        marker.setPadding(dp(10), dp(10), dp(10), dp(10));
        marker.setCompoundDrawables(new MapIconDrawable(
                MapIconDrawable.Symbol.PIN, MapUiTheme.ACCENT, dp(20)), null, null, null);
        marker.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 12));
        marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams markerLayout = new LinearLayout.LayoutParams(dp(40), dp(40));
        markerLayout.setMarginEnd(dp(12));
        row.addView(marker, markerLayout);

        LinearLayout description = verticalLayout();
        TextView name = text(store.getName(), 19, MapUiTheme.INK);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        description.addView(name);
        row.addView(description, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

        LinearLayout end = verticalLayout();
        end.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        end.setPadding(dp(12), 0, 0, 0);
        Double distance = store.getDistanceMeters();
        if (distance != null && Double.isFinite(distance)) {
            TextView distanceView = text(getContext().getString(
                    R.string.stores_distance, Math.round(distance)), 14, MapUiTheme.INK);
            distanceView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            end.addView(distanceView);
        }
        TextView chevron = text("", 14, MapUiTheme.MUTED);
        chevron.setCompoundDrawables(new MapIconDrawable(
                MapIconDrawable.Symbol.CHEVRON, MapUiTheme.MUTED, dp(16)), null, null, null);
        chevron.setPadding(0, dp(8), 0, 0);
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        end.addView(chevron);
        row.addView(end);
        resultList.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** 행 사이의 가는 선으로 정보 경계를 구분한다. */
    private void addDivider(LinearLayout parent, int insetDp) {
        View divider = new View(getContext());
        divider.setBackgroundColor(MapUiTheme.BORDER);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        layout.setMarginStart(dp(insetDp));
        parent.addView(divider, layout);
    }

    /** 실제 지도 이동에 사용할 수 있는 WGS84 좌표와 연결된 동작인지 확인한다. */
    private boolean canFocusStore(StoreSummary store) {
        Double targetLatitude = store.getLatitude();
        Double targetLongitude = store.getLongitude();
        return mapListener != null && targetLatitude != null && targetLongitude != null
                && Double.isFinite(targetLatitude) && Double.isFinite(targetLongitude)
                && targetLatitude >= -90 && targetLatitude <= 90
                && targetLongitude >= -180 && targetLongitude <= 180;
    }

    /** 유효한 장소 선택을 부모 화면에 전달하며 화면 닫기는 호출자가 결정한다. */
    private void focusStore(StoreSummary store) {
        if (closed || !canFocusStore(store)) {
            return;
        }
        mapListener.accept(store);
        Log.i(TAG, "STORE_MAP_SELECTED category=" + selectedCategory.name());
    }

    /** 공급자 내부 용어 대신 필요한 사용자 행동을 안내한다. */
    private int failureMessage(StoreSearchFailure failure) {
        switch (failure.getCode()) {
            case MISSING_KEY:
            case INVALID_CONFIGURATION:
            case AUTHORIZATION_DENIED:
                return R.string.stores_unavailable;
            case INVALID_COORDINATE:
                return R.string.stores_invalid_location;
            case QUOTA_EXCEEDED:
                return R.string.stores_limit;
            default:
                return R.string.stores_retry_later;
        }
    }

    /** 장소 분류 필터의 이름을 화면 리소스에서 제공한다. */
    private String categoryLabel(KakaoStoreCategory category) {
        switch (category) {
            case RESTAURANT:
                return getContext().getString(R.string.stores_restaurant);
            case CAFE:
                return getContext().getString(R.string.stores_cafe);
            case CONVENIENCE:
                return getContext().getString(R.string.stores_convenience);
            case TOURIST_ATTRACTION:
                return getContext().getString(R.string.stores_tourist_attraction);
            default:
                return category.getDisplayName();
        }
    }

    /** 진행 중인 조회를 취소·해제하고 닫힌 뒤 도착한 결과와 중복 종료를 무시한다. */
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        generation++;
        searchService.close();
    }

    /** 주변 매장창의 세로 배치 컨테이너를 만든다. */
    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    /** 글자 크기와 색을 적용한 표시 전용 문구를 만든다. */
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    /** 기존 지도 카드와 동일한 터치 버튼 스타일을 적용한다. */
    private Button button(String label, boolean primary) {
        Button view = new Button(getContext());
        view.setText(label);
        MapUiTheme.styleButton(view, primary);
        return view;
    }

    /** 분할 화면의 밀도에 맞춰 논리 크기를 픽셀로 바꾼다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
