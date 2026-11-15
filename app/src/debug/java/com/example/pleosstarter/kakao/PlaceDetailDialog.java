package com.example.pleosstarter.kakao;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.ui.MapUiTheme;

/** 지도·안내 세션을 변경하지 않고 선택한 장소의 조회 상태와 상세 정보를 보여준다. */
public final class PlaceDetailDialog extends Dialog {
    /** 사용자가 선택한 장소로 지도 카메라를 옮기도록 화면에 요청한다. */
    public interface Listener {
        /** 상세창에서 확인한 장소를 지도 화면에 전달한다. */
        void onShowOnMap(KakaoPlaceSelection selection);
    }

    private final Listener listener;
    private final java.util.function.Consumer<KakaoPlaceSelection> nearbyListener;
    private PlaceDetails details;
    private LinearLayout content;
    private TextView nameView;
    private TextView categoryView;
    private TextView addressView;
    private TextView roadAddressView;
    private TextView phoneView;
    private TextView stateView;

    /** 조회 전에도 지도 또는 검색 결과에서 받은 기본 장소 정보를 표시할 수 있게 한다. */
    public PlaceDetailDialog(Context context, PlaceDetails initialDetails, Listener listener) {
        this(context, initialDetails, listener, null);
    }

    /** 선택 지점의 주변 매장 조회 동작을 추가하면서 기존 상세 표시 계약을 유지한다. */
    public PlaceDetailDialog(Context context, PlaceDetails initialDetails, Listener listener,
                             java.util.function.Consumer<KakaoPlaceSelection> nearbyListener) {
        super(context);
        this.details = initialDetails;
        this.listener = listener;
        this.nearbyListener = nearbyListener;
    }

    /** 상세 필드와 주변 매장 조회·닫기·지도 이동 동작을 구성한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(20));
        content.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 24));

        TextView heading = createText("장소 정보", 14);
        heading.setTextColor(MapUiTheme.ACCENT);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setPadding(0, 0, 0, dp(4));
        content.addView(heading);
        nameView = createText("", 28);
        nameView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        nameView.setAccessibilityHeading(true);
        content.addView(nameView);

        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(8), 0, dp(4));
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(dp(16), dp(6), dp(16), dp(6));
        fields.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 16));
        categoryView = createText("", 19);
        roadAddressView = createText("", 19);
        addressView = createText("", 19);
        phoneView = createText("", 19);
        stateView = createText("", 17);
        stateView.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        fields.addView(categoryView);
        fields.addView(roadAddressView);
        fields.addView(addressView);
        fields.addView(phoneView);
        fields.addView(stateView);
        scroll.addView(fields);
        content.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f));

        if (nearbyListener != null) {
            Button nearbyButton = createButton("주변 매장", true);
            nearbyButton.setOnClickListener(view -> {
                nearbyListener.accept(details.toSelection());
            });
            LinearLayout.LayoutParams nearbyLayout = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
            nearbyLayout.topMargin = dp(16);
            content.addView(nearbyButton, nearbyLayout);
        }

        LinearLayout actions = new LinearLayout(getContext());
        actions.setGravity(Gravity.END);
        Button closeButton = createButton("닫기", false);
        closeButton.setOnClickListener(view -> dismiss());
        Button mapButton = createButton("지도에서 보기", true);
        mapButton.setOnClickListener(view -> {
            listener.onShowOnMap(details.toSelection());
            dismiss();
        });
        LinearLayout.LayoutParams closeLayout = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        closeLayout.setMarginEnd(dp(12));
        actions.addView(closeButton, closeLayout);
        actions.addView(mapButton, new LinearLayout.LayoutParams(0, dp(56), 1.0f));
        LinearLayout.LayoutParams actionsLayout = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        actionsLayout.topMargin = dp(16);
        content.addView(actions, actionsLayout);
        setContentView(content);
        renderFields();
    }

    /** 가로 화면에서도 지도와 함께 읽을 수 있는 크기로 상세창을 제한한다. */
    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window == null) {
            return;
        }

        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0.3f);
        resizeToContent();
    }

    /** 이미 확인한 기본 정보는 유지하면서 추가 상세 정보를 불러오는 상태를 알린다. */
    public void showLoading() {
        stateView.setVisibility(View.VISIBLE);
        stateView.setText("상세 정보를 불러오는 중입니다.");
        stateView.setTextColor(MapUiTheme.MUTED);
        resizeToContent();
    }

    /** 실제 상세 응답에 포함된 필드만 표시하고 누락된 정보를 만들지 않는다. */
    public void showDetails(PlaceDetails updatedDetails) {
        details = updatedDetails;
        renderFields();
        if (details.getAddress().isEmpty() && details.getRoadAddress().isEmpty()
                && details.getPhone().isEmpty() && details.getCategory().isEmpty()) {
            stateView.setVisibility(View.VISIBLE);
            stateView.setText("이 장소에는 제공되는 주소·분류·전화 정보가 없습니다.");
        } else {
            stateView.setText("");
            stateView.setVisibility(View.GONE);
        }
        stateView.setTextColor(MapUiTheme.MUTED);
        resizeToContent();
    }

    /** 상세 조회 실패를 장소 선택 성공과 구분하고 기존 확인 정보는 보존한다. */
    public void showFailure(KakaoTestFailure failure) {
        stateView.setVisibility(View.VISIBLE);
        stateView.setText(failure.getMessage());
        stateView.setTextColor(Color.rgb(154, 58, 38));
        resizeToContent();
    }

    /** 실제 내용 높이로 창을 줄이고 최대 높이를 넘는 필드만 고정 제목·버튼 사이에서 스크롤한다. */
    private void resizeToContent() {
        Window window = getWindow();
        if (window == null || content == null) {
            return;
        }

        int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        int dialogWidth = Math.min(dp(680), Math.round(screenWidth * 0.9f));
        int maximumHeight = Math.min(dp(460), Math.round(screenHeight * 0.82f));

        // 폭을 먼저 확정해 주소·오류의 줄바꿈을 포함한 자연 높이를 측정한다.
        content.measure(View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int dialogHeight = Math.min(maximumHeight, content.getMeasuredHeight());
        window.setLayout(dialogWidth, dialogHeight);
        content.requestLayout();
    }

    /** 장소명과 제공된 선택 필드를 화면에 반영한다. */
    private void renderFields() {
        nameView.setText(details.getName());
        setOptionalField(categoryView, "분류", details.getCategory());
        setOptionalField(roadAddressView, "도로명 주소", details.getRoadAddress());
        String address = details.getAddress();
        if (address.equals(details.getRoadAddress())) {
            address = "";
        }
        setOptionalField(addressView, "주소", address);
        setOptionalField(phoneView, "전화", details.getPhone());
    }

    /** 값이 없는 필드는 숨기고 제공된 값에는 읽기 쉬운 이름을 붙인다. */
    private void setOptionalField(TextView view, String label, String text) {
        if (text.isEmpty()) {
            view.setVisibility(View.GONE);
            return;
        }

        view.setVisibility(View.VISIBLE);
        SpannableString field = new SpannableString(label + "\n" + text);
        field.setSpan(new ForegroundColorSpan(MapUiTheme.MUTED), 0, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        field.setSpan(new RelativeSizeSpan(0.78f), 0, label.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        view.setText(field);
    }

    /** 상세 정보의 글자 크기와 여백을 일관되게 적용한다. */
    private TextView createText(String text, int size) {
        TextView view = new TextView(getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(MapUiTheme.INK);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(4), 1.0f);
        view.setPadding(0, dp(8), 0, dp(8));
        return view;
    }

    /** 닫기와 지도 이동 버튼에 충분한 터치 영역을 제공한다. */
    private Button createButton(String text, boolean primary) {
        Button button = new Button(getContext());
        button.setText(text);
        MapUiTheme.styleButton(button, primary);
        return button;
    }

    /** 화면 밀도에 맞춰 상세창 크기와 여백을 변환한다. */
    private int dp(int pixels) {
        return MapUiTheme.dp(getContext(), pixels);
    }
}
