package com.example.pleosstarter.records;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.ui.MapIconDrawable;
import com.example.pleosstarter.ui.MapUiTheme;
import com.example.pleosstarter.stores.StoreResultsPanel;
import com.example.pleosstarter.stores.StoreSearchService;
import com.example.pleosstarter.stores.StoreSummary;

import java.util.Objects;
import java.util.function.Consumer;

/** 원·별의 저장 글자와 주변 가게를 보여 주고 즉시 삭제·해당 기록 길안내를 전달한다. */
public final class MapRecordReadDialog extends Dialog {
    private static final int PREFERRED_WIDTH_DP = 720;
    private static final int MAXIMUM_HEIGHT_DP = 720;
    private static final int CONTENT_PADDING_DP = 28;
    private static final int HEADER_HEIGHT_DP = 52;
    private static final int BODY_GAP_DP = 24;
    private static final int MINIMUM_BODY_HEIGHT_DP = 64;
    private static final int MAXIMUM_BODY_HEIGHT_DP = 144;
    private static final int NAVIGATION_BUTTON_WIDTH_DP = 260;
    private static final int NAVIGATION_BUTTON_HEIGHT_DP = 76;
    private final boolean favorite;
    private final String savedText;
    private final StoreResultsPanel stores;
    private final Consumer<MapRecordReadDialog> deleteAction;
    private final Consumer<MapRecordReadDialog> navigationAction;
    private LinearLayout content;
    private TextView actionError;
    private Button deleteButton;
    private Button navigationButton;
    private boolean actionPending;
    private ScrollView scroll;
    private TextView body;
    private View ownerView;
    private final View.OnLayoutChangeListener ownerLayoutChanged = this::onOwnerLayoutChanged;

    /** 기록 좌표를 조회 패널에 고정하고 가게 선택·삭제·길안내를 소유 화면에 위임한다. */
    public MapRecordReadDialog(Context context, boolean favorite, String text,
            double latitude, double longitude, StoreSearchService searchService,
            Consumer<StoreSummary> onStoreSelected, Consumer<MapRecordReadDialog> onDelete,
            Consumer<MapRecordReadDialog> onNavigate) {
        super(context);
        this.favorite = favorite;
        savedText = Objects.requireNonNull(text, "표시할 기록 글자가 필요합니다.");
        deleteAction = Objects.requireNonNull(onDelete, "삭제 처리가 필요합니다.");
        navigationAction = Objects.requireNonNull(onNavigate, "길안내 처리가 필요합니다.");
        stores = new StoreResultsPanel(context, searchService, latitude, longitude, store -> {
            if (actionPending) {
                return;
            }
            onStoreSelected.accept(store);
            dismiss();
        });
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    /** 글자와 주변 목록을 각각 스크롤할 수 있게 구성해 긴 메모도 가게 목록을 가리지 않는다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP),
                dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP));
        content.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 24));
        content.addView(createHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(HEADER_HEIGHT_DP)));

        body = new TextView(getContext());
        body.setId(R.id.map_record_read_body);
        body.setText(savedText);
        body.setTextSize(24);
        body.setTextColor(MapUiTheme.INK);
        body.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        body.setLineSpacing(dp(8), 1.0f);
        body.setTextIsSelectable(false);
        body.setPadding(0, 0, 0, dp(4));
        if (savedText.isBlank()) {
            body.setText(R.string.map_record_read_empty);
            body.setTextColor(MapUiTheme.MUTED);
        }

        scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams readingLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        readingLayout.topMargin = dp(BODY_GAP_DP);
        content.addView(scroll, readingLayout);

        actionError = new TextView(getContext());
        actionError.setId(R.id.map_record_action_error);
        actionError.setTextColor(0xFFB3261E);
        actionError.setTextSize(16);
        actionError.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        actionError.setVisibility(View.GONE);
        content.addView(actionError);

        View divider = new View(getContext());
        divider.setBackgroundColor(MapUiTheme.BORDER);
        LinearLayout.LayoutParams dividerLayout = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerLayout.topMargin = dp(20);
        dividerLayout.bottomMargin = dp(20);
        content.addView(divider, dividerLayout);
        stores.setId(R.id.map_record_nearby_stores);
        content.addView(stores, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        navigationButton = createNavigationButton();
        LinearLayout.LayoutParams navigationLayout = new LinearLayout.LayoutParams(
                dp(NAVIGATION_BUTTON_WIDTH_DP), dp(NAVIGATION_BUTTON_HEIGHT_DP));
        navigationLayout.gravity = Gravity.CENTER_HORIZONTAL;
        navigationLayout.topMargin = dp(20);
        content.addView(navigationButton, navigationLayout);
        setContentView(content);
    }

    /** 기록 종류만 바꾸는 공통 아이콘·제목과 삭제·닫기 조작을 만든다. */
    private LinearLayout createHeader() {
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(getContext());
        MapIconDrawable.Symbol symbol = favorite
                ? MapIconDrawable.Symbol.STAR : MapIconDrawable.Symbol.NOTE;
        icon.setImageDrawable(new MapIconDrawable(symbol, MapUiTheme.ACCENT, dp(26)));
        icon.setPadding(dp(13), dp(13), dp(13), dp(13));
        icon.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 16));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(icon, new LinearLayout.LayoutParams(dp(HEADER_HEIGHT_DP), dp(HEADER_HEIGHT_DP)));

        TextView title = new TextView(getContext());
        title.setId(R.id.map_record_read_title);
        title.setText(favorite ? R.string.map_record_read_favorite : R.string.map_record_read_note);
        title.setTextSize(22);
        title.setTextColor(MapUiTheme.INK);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setAccessibilityHeading(true);
        LinearLayout.LayoutParams titleLayout = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        titleLayout.setMarginStart(dp(16));
        titleLayout.setMarginEnd(dp(12));
        header.addView(title, titleLayout);

        deleteButton = actionButton(R.string.map_record_read_delete);
        deleteButton.setId(R.id.map_record_read_delete);
        deleteButton.setContentDescription(getContext().getString(R.string.map_record_read_delete_description));
        deleteButton.setOnClickListener(view -> {
            if (beginAction()) {
                deleteAction.accept(this);
            }
        });
        LinearLayout.LayoutParams deleteLayout = new LinearLayout.LayoutParams(dp(64), dp(52));
        deleteLayout.setMarginEnd(dp(12));
        header.addView(deleteButton, deleteLayout);

        ImageButton close = new ImageButton(getContext());
        close.setId(R.id.map_record_read_close);
        close.setImageDrawable(new MapIconDrawable(MapIconDrawable.Symbol.CLOSE, MapUiTheme.INK, dp(24)));
        close.setPadding(dp(14), dp(14), dp(14), dp(14));
        close.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 16), null));
        close.setContentDescription(getContext().getString(R.string.map_record_read_close));
        close.setOnClickListener(view -> {
            dismiss();
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(HEADER_HEIGHT_DP), dp(HEADER_HEIGHT_DP)));
        return header;
    }

    /** 지도 화면과 같은 크기·색의 기록 위치 길안내 버튼을 만든다. */
    private Button createNavigationButton() {
        Button button = new Button(getContext());
        button.setId(R.id.map_record_read_navigation);
        button.setText(R.string.place_navigation_start);
        button.setContentDescription(getContext().getString(R.string.map_record_read_navigation));
        MapUiTheme.styleButton(button, true);
        button.setTextSize(26);
        button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                MapUiTheme.surface(getContext(), MapUiTheme.INK, 24), null));
        button.setOnClickListener(view -> {
            if (beginAction()) {
                navigationAction.accept(this);
            }
        });
        return button;
    }

    /** 삭제·길안내 중 하나만 요청하고 완료 전 같은 기록의 중복 동작을 막는다. */
    private boolean beginAction() {
        if (actionPending) {
            return false;
        }
        actionPending = true;
        setActionsEnabled(false);
        actionError.setVisibility(View.GONE);
        return true;
    }

    /** 처리 중인 두 동작을 함께 잠그거나 실패 후 함께 다시 사용할 수 있게 한다. */
    private void setActionsEnabled(boolean enabled) {
        deleteButton.setEnabled(enabled);
        navigationButton.setEnabled(enabled);
        deleteButton.setAlpha(enabled ? 1f : 0.45f);
        navigationButton.setAlpha(enabled ? 1f : 0.45f);
    }

    /** 공통 색·글자·최소 터치 영역으로 텍스트 동작 버튼을 만든다. */
    private Button actionButton(int label) {
        Button button = new Button(getContext());
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setTextColor(MapUiTheme.MUTED);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 12), null));
        return button;
    }

    /** 저장 실패 때 원문과 목록을 유지하고 안전한 오류 문구와 재시도를 제공한다. */
    public void showFailure(MapRecordFailure failure) {
        showActionFailure(failure.getMessage());
    }

    /** 길안내 실패 때 모달을 유지하고 같은 기록으로 다시 요청할 수 있게 한다. */
    public void showNavigationFailure(String message) {
        showActionFailure(message);
    }

    /** 동작 실패의 사용자 안내를 같은 영역에 표시하며 버튼의 중복 차단을 해제한다. */
    private void showActionFailure(String message) {
        actionPending = false;
        setActionsEnabled(true);
        actionError.setText(message);
        actionError.setVisibility(View.VISIBLE);
    }

    /** 소유 앱 창 안에 모달을 가운데 배치하고 분할 크기 변경도 반영한다. */
    @Override
    protected void onStart() {
        super.onStart();
        if (getOwnerActivity() != null) {
            ownerView = getOwnerActivity().getWindow().getDecorView();
            ownerView.addOnLayoutChangeListener(ownerLayoutChanged);
        }
        updateWindowSize();
        stores.start();
    }

    /** 닫힌 모달이 앱 창의 크기 변경을 계속 참조하지 않도록 연결을 해제한다. */
    @Override
    protected void onStop() {
        stores.close();
        if (ownerView != null) {
            ownerView.removeOnLayoutChangeListener(ownerLayoutChanged);
            ownerView = null;
        }
        super.onStop();
    }

    /** 앱 창의 실제 크기가 바뀐 경우에만 본문 높이와 패널 크기를 다시 계산한다. */
    private void onOwnerLayoutChanged(View view, int left, int top, int right, int bottom,
            int oldLeft, int oldTop, int oldRight, int oldBottom) {
        if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
            updateWindowSize();
        }
    }

    /** 메모 높이를 제한하고 나머지 공간을 주변 가게 목록에 배정한다. */
    private void updateWindowSize() {
        Window window = getWindow();
        if (window == null || content == null) {
            return;
        }

        int availableWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int availableHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        if (ownerView != null && ownerView.getWidth() > 0 && ownerView.getHeight() > 0) {
            availableWidth = ownerView.getWidth();
            availableHeight = ownerView.getHeight();
        }

        int width = Math.min(dp(PREFERRED_WIDTH_DP), Math.round(availableWidth * 0.9f));
        int maximumHeight = Math.min(dp(MAXIMUM_HEIGHT_DP), Math.round(availableHeight * 0.86f));
        int bodyWidth = Math.max(1, width - dp(CONTENT_PADDING_DP * 2));
        body.measure(View.MeasureSpec.makeMeasureSpec(bodyWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int preferredBodyHeight = Math.max(dp(MINIMUM_BODY_HEIGHT_DP), body.getMeasuredHeight());
        int maximumBodyHeight = Math.min(dp(MAXIMUM_BODY_HEIGHT_DP), maximumHeight / 5);
        int bodyHeight = Math.min(preferredBodyHeight, Math.max(1, maximumBodyHeight));
        ViewGroup.LayoutParams bodyLayout = scroll.getLayoutParams();
        bodyLayout.height = bodyHeight;
        scroll.setLayoutParams(bodyLayout);

        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.28f);
        window.setGravity(Gravity.CENTER);
        window.setLayout(width, maximumHeight);
    }

    /** 기존 지도 화면과 같은 밀도로 간격과 터치 영역을 변환한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
