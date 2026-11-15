package com.example.pleosstarter.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.pleosstarter.R;
import com.example.pleosstarter.kakao.MapRecordInsets;
/** 지도·히스토리 진입과 선택 장소의 내비 연결·시험 이동 조작을 표시한다. */
public final class MapScreenView extends FrameLayout {
    private static final int NAVIGATION_BUTTON_WIDTH_DP = 260;
    private static final int NAVIGATION_BUTTON_HEIGHT_DP = 76;
    private static final int NAVIGATION_BUTTON_BOTTOM_MARGIN_DP = 80;
    private static final int NAVIGATION_BUTTON_TEXT_SP = 26;
    private static final int REVISIT_CARD_MAX_WIDTH_DP = 520;
    private static final int REVISIT_CARD_HEIGHT_DP = 124;
    private static final int REVISIT_CARD_SIDE_MARGIN_DP = 20;
    private static final int REVISIT_CARD_TOP_MARGIN_DP = 92;
    private final FrameLayout mapContainer;
    private final MapRevisitCardView revisitCard;
    private final Button movementButton;
    private final Button stopMovementButton;
    private final Button navigationButton;
    private final Button historyButton;
    private final TextView movementProgress;
    private final TextView banner;
    private final Runnable hideFeedback;
    private String persistentMessage = "지도를 불러오는 중…";
    private String transientMessage;
    private boolean ready;
    private boolean movementLoading;
    private boolean navigationPending;

    /** 지도를 전체 영역에 배치하고 상단 히스토리·하단 조작·일시 안내를 구성한다. */
    public MapScreenView(Context context) {
        super(context);
        setBackgroundColor(MapUiTheme.SURFACE);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);

        mapContainer = new FrameLayout(context);
        addView(mapContainer, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        revisitCard = new MapRevisitCardView(context);
        LayoutParams revisitLayout = new LayoutParams(dp(REVISIT_CARD_MAX_WIDTH_DP),
                dp(REVISIT_CARD_HEIGHT_DP), Gravity.TOP | Gravity.START);
        revisitLayout.setMarginStart(dp(REVISIT_CARD_SIDE_MARGIN_DP));
        revisitLayout.setMarginEnd(dp(REVISIT_CARD_SIDE_MARGIN_DP));
        revisitLayout.topMargin = dp(REVISIT_CARD_TOP_MARGIN_DP);
        addView(revisitCard, revisitLayout);

        historyButton = new Button(context);
        historyButton.setId(R.id.map_history_button);
        historyButton.setText(R.string.map_history_title);
        historyButton.setContentDescription(context.getString(R.string.map_history_open));
        MapUiTheme.styleButton(historyButton, false);
        GradientDrawable historyBackground = MapUiTheme.surface(context, Color.WHITE, 18);
        historyBackground.setStroke(dp(1), MapUiTheme.BORDER);
        historyButton.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x18007FA8), historyBackground, null));
        historyButton.setElevation(dp(3));
        MapActionIcon historyIcon = new MapActionIcon(MapActionIcon.Kind.SAVED, MapUiTheme.INK);
        historyIcon.setBounds(0, 0, dp(20), dp(20));
        historyButton.setCompoundDrawablesRelative(historyIcon, null, null, null);
        historyButton.setCompoundDrawablePadding(dp(10));
        LayoutParams historyLayout = new LayoutParams(dp(142), dp(56), Gravity.TOP | Gravity.END);
        historyLayout.setMargins(dp(20), dp(20), dp(20), 0);
        addView(historyButton, historyLayout);

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(dp(5), dp(5), dp(5), dp(5));
        GradientDrawable controlBackground = MapUiTheme.surface(context, Color.WHITE, 30);
        controlBackground.setStroke(dp(1), MapUiTheme.BORDER);
        controls.setBackground(controlBackground);
        controls.setElevation(dp(3));
        stopMovementButton = compactButton("종료", false);
        stopMovementButton.setContentDescription("시험 이동 종료, 차량 위치로 돌아가기");
        stopMovementButton.setVisibility(View.GONE);
        LinearLayout.LayoutParams stopLayout = new LinearLayout.LayoutParams(dp(64), dp(48));
        stopLayout.setMarginEnd(dp(4));
        controls.addView(stopMovementButton, stopLayout);
        movementButton = compactButton("시험 이동", true);
        showMovementIcon(MapIconDrawable.Symbol.PLAY);
        movementButton.setContentDescription("도로를 따라 시험 이동 시작");
        LinearLayout.LayoutParams movementLayout = new LinearLayout.LayoutParams(dp(128), dp(48));
        controls.addView(movementButton, movementLayout);
        LayoutParams controlsLayout = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(58),
                Gravity.BOTTOM | Gravity.END);
        controlsLayout.setMargins(dp(20), 0, dp(20), dp(20));
        addView(controls, controlsLayout);

        navigationButton = compactButton(context.getString(R.string.place_navigation_start), true);
        navigationButton.setTextSize(NAVIGATION_BUTTON_TEXT_SP);
        navigationButton.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        navigationButton.setVisibility(View.GONE);
        navigationButton.setElevation(dp(3));
        LayoutParams navigationLayout = new LayoutParams(dp(NAVIGATION_BUTTON_WIDTH_DP),
                dp(NAVIGATION_BUTTON_HEIGHT_DP),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        navigationLayout.setMargins(dp(20), 0, dp(20), dp(NAVIGATION_BUTTON_BOTTOM_MARGIN_DP));
        addView(navigationButton, navigationLayout);

        movementProgress = label("", 13);
        movementProgress.setSingleLine(true);
        movementProgress.setTextColor(MapUiTheme.INK);
        movementProgress.setPadding(dp(12), dp(7), dp(12), dp(7));
        movementProgress.setBackground(MapUiTheme.surface(context, 0xF7FFFFFF, 10));
        movementProgress.setVisibility(View.GONE);
        LayoutParams progressLayout = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        progressLayout.setMargins(dp(16), 0, dp(16), dp(80));
        addView(movementProgress, progressLayout);

        banner = label("", 15);
        banner.setTextColor(Color.WHITE);
        banner.setGravity(Gravity.CENTER);
        banner.setMaxLines(2);
        banner.setPadding(dp(18), dp(12), dp(18), dp(12));
        banner.setBackground(MapUiTheme.surface(context, MapUiTheme.INK, 16));
        banner.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LayoutParams bannerLayout = new LayoutParams(LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bannerLayout.setMargins(dp(16), 0, dp(16),
                dp(NAVIGATION_BUTTON_HEIGHT_DP + NAVIGATION_BUTTON_BOTTOM_MARGIN_DP + 24));
        addView(banner, bannerLayout);
        hideFeedback = () -> {
            transientMessage = null;
            renderBanner();
        };

        setReady(false);
        renderBanner();
    }

    /** 지도 준비 여부에 맞춰 히스토리·시험 조작을 활성화하고 연결 대기 안내를 해제한다. */
    public void setReady(boolean ready) {
        this.ready = ready;
        historyButton.setEnabled(ready);
        historyButton.setAlpha(ready ? 1f : 0.45f);
        movementButton.setEnabled(ready && !movementLoading);
        movementButton.setAlpha(ready && !movementLoading ? 1f : 0.45f);
        stopMovementButton.setEnabled(ready);
        stopMovementButton.setAlpha(ready ? 1f : 0.45f);
        navigationButton.setEnabled(ready && !navigationPending);
        if (ready) {
            persistentMessage = "";
            renderBanner();
        }
    }

    /** 연결·위치 오류처럼 조치가 필요한 상태를 일회성 저장 안내와 구분해 유지한다. */
    public void showStatus(String message) {
        persistentMessage = message;
        renderBanner();
    }

    /** 위치 수신이 회복되면 대기 문구를 숨기고 출처는 지도 접근성 설명에 남긴다. */
    public void showPositionSource(String source) {
        persistentMessage = "";
        mapContainer.setContentDescription("지도 · " + source);
        renderBanner();
    }

    /** 저장 결과를 잠시 표시한 뒤 아직 남은 연결 상태 안내로 되돌린다. */
    public void showFeedback(String message) {
        removeCallbacks(hideFeedback);
        transientMessage = message;
        renderBanner();
        postDelayed(hideFeedback, 4200);
    }

    /** 시험임을 표시하고 진행·일시정지·종료 조작을 작은 하단 영역에서 갱신한다. */
    public void showTestMovement(String message, boolean active, boolean paused, boolean loading) {
        movementLoading = loading;
        String title = "시험 이동";
        if (active) {
            title = paused ? "계속" : "일시정지";
        }
        if (loading) {
            title = "준비 중…";
        }
        movementButton.setText(title);
        MapIconDrawable.Symbol symbol = MapIconDrawable.Symbol.PLAY;
        if (active && !paused) {
            symbol = MapIconDrawable.Symbol.PAUSE;
        }
        showMovementIcon(symbol);
        movementButton.setContentDescription(active || loading ? "시험 이동 " + title : "도로를 따라 시험 이동 시작");
        movementButton.setEnabled(ready && !loading);
        movementButton.setAlpha(ready && !loading ? 1f : 0.45f);
        stopMovementButton.setVisibility(active ? View.VISIBLE : View.GONE);
        movementProgress.setText(message.replace("테스트 이동 · ", ""));
        movementProgress.setContentDescription(message);
        movementProgress.setVisibility(active || loading ? View.VISIBLE : View.GONE);
        updateBottomWidths(getWidth());
    }

    /** 지도 SDK와 제스처 입력을 같은 크기로 붙일 영역을 반환한다. */
    public FrameLayout getMapContainer() {
        return mapContainer;
    }

    /** 재방문 표시와 열기·닫기 입력을 연결할 카드를 반환한다. */
    public MapRevisitCardView getRevisitCard() {
        return revisitCard;
    }

    /** 화면 가장자리와 하단 시험·안내 영역을 피하도록 마커 카드의 안전 여백을 반환한다. */
    public MapRecordInsets getRecordInsets() {
        return new MapRecordInsets(0, dp(16), 0, dp(180));
    }

    /** 시험 이동 시작·일시정지 조작을 연결할 버튼을 반환한다. */
    public Button getMovementButton() {
        return movementButton;
    }

    /** 시험을 끝내고 차량 위치로 돌아갈 조작을 반환한다. */
    public Button getStopMovementButton() {
        return stopMovementButton;
    }

    /** 선택한 장소를 Pleos 기본 내비로 보낼 버튼을 반환한다. */
    public Button getNavigationButton() {
        return navigationButton;
    }

    /** 저장 메모와 즐겨찾기의 통합 목록을 열 조작을 반환한다. */
    public Button getHistoryButton() {
        return historyButton;
    }

    /** 지도로 이동한 장소가 있을 때만 설명 없는 내비 안내 버튼을 표시한다. */
    public void showNavigationAction(String destinationName) {
        navigationButton.setContentDescription(getContext().getString(
                R.string.place_navigation_destination, destinationName));
        navigationButton.setVisibility(View.VISIBLE);
        setNavigationPending(navigationPending);
    }

    /** 요청 처리 중에는 같은 목적지를 중복 전송하지 못하게 버튼을 잠근다. */
    public void setNavigationPending(boolean pending) {
        navigationPending = pending;
        navigationButton.setText(pending ? R.string.place_navigation_connecting
                : R.string.place_navigation_start);
        navigationButton.setEnabled(ready && !pending);
        navigationButton.setAlpha(pending ? 0.65f : 1f);
    }

    /** 기본 내비에서 목적지가 확인되면 해당 선택의 안내 조작을 숨긴다. */
    public void hideNavigationAction() {
        navigationButton.setVisibility(View.GONE);
    }

    /** 분할·전체 화면의 첫 측정부터 좌우 여백 안에서 재방문 카드 폭을 제한한다. */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            int availableWidth = MeasureSpec.getSize(widthMeasureSpec)
                    - dp(REVISIT_CARD_SIDE_MARGIN_DP * 2);
            revisitCard.getLayoutParams().width = Math.max(0,
                    Math.min(dp(REVISIT_CARD_MAX_WIDTH_DP), availableWidth));
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    /** 작은 분할 화면에서도 시험 진행 문구가 지도 밖으로 넘어가지 않게 조정한다. */
    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateBottomWidths(width);
    }

    /** 제거된 화면에 일회성 안내 콜백을 남기지 않는다. */
    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hideFeedback);
        super.onDetachedFromWindow();
    }

    /** 화면 폭에 맞춰 시험 중에만 나타나는 진행 문구의 최대 너비를 제한한다. */
    private void updateBottomWidths(int width) {
        if (width <= 0) {
            return;
        }
        boolean narrow = width < dp(600);
        int availableWidth = narrow ? width - dp(32) : width / 2 - dp(24);
        movementProgress.setMaxWidth(Math.max(dp(100), Math.min(dp(420), availableWidth)));
    }

    /** 일회성 결과를 우선 표시하고 없으면 보관 중인 연결 상태를 표시한다. */
    private void renderBanner() {
        String message = transientMessage != null ? transientMessage : persistentMessage;
        banner.setText(message);
        banner.setVisibility(message == null || message.isBlank() ? View.GONE : View.VISIBLE);
    }

    /** 하단 시험 조작에 48dp 터치 영역을 적용한다. */
    private Button compactButton(String title, boolean primary) {
        Button button = new Button(getContext());
        button.setText(title);
        MapUiTheme.styleButton(button, primary);
        button.setTextSize(15);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setPadding(dp(12), 0, dp(12), 0);
        int color = primary ? MapUiTheme.INK : MapUiTheme.SURFACE;
        button.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22007FA8),
                MapUiTheme.surface(getContext(), color, 24), null));
        return button;
    }

    /** 이동 조작의 현재 동작을 글자와 동일한 선 아이콘으로 표시한다. */
    private void showMovementIcon(MapIconDrawable.Symbol symbol) {
        movementButton.setCompoundDrawablesRelative(new MapIconDrawable(symbol, Color.WHITE, dp(18)),
                null, null, null);
        movementButton.setCompoundDrawablePadding(dp(6));
    }

    /** 공통 글꼴로 지도 위의 짧은 안내를 만든다. */
    private TextView label(String text, int size) {
        TextView label = new TextView(getContext());
        label.setText(text);
        label.setTextSize(size);
        label.setTextColor(MapUiTheme.INK);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return label;
    }

    /** 화면 밀도에 맞게 지도 조작 크기를 변환한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
