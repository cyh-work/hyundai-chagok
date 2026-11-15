package com.example.pleosstarter.stores;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;

import com.example.pleosstarter.R;
import com.example.pleosstarter.ui.MapIconDrawable;
import com.example.pleosstarter.ui.MapUiTheme;

import java.util.function.Consumer;

/** 지도에서 선택한 지점의 공통 주변 장소 패널을 독립된 창으로 표시한다. */
public final class StoreResultsDialog extends Dialog {
    private final StoreSearchService searchService;
    private final double latitude;
    private final double longitude;
    private final Consumer<StoreSummary> mapListener;
    private StoreResultsPanel panel;
    private boolean closed;

    /** 지도 이동 콜백이 없는 기존 호출자는 장소 목록만 조회할 수 있다. */
    public StoreResultsDialog(Context context, StoreSearchService searchService,
                              String anchorName, double latitude, double longitude) {
        this(context, searchService, anchorName, latitude, longitude, null);
    }

    /** 검색 중심과 앱 지도 이동 동작을 주입하며 현재 차량 좌표로 바꾸지 않는다. */
    public StoreResultsDialog(Context context, StoreSearchService searchService,
                              String anchorName, double latitude, double longitude,
                              Consumer<StoreSummary> mapListener) {
        super(context);
        this.searchService = searchService;
        this.latitude = latitude;
        this.longitude = longitude;
        this.mapListener = mapListener;
    }

    /** 공통 패널을 배치하고 기존 필터 행 끝에 창 닫기를 연결한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        Consumer<StoreSummary> selection = null;
        if (mapListener != null) {
            selection = this::selectStore;
        }
        panel = new StoreResultsPanel(getContext(), searchService, latitude, longitude, selection);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));
        panel.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 20));
        panel.addFilterAction(closeButton());
        setContentView(panel);
    }

    /** 분할 화면 안에 창을 배치하고 처음 표시할 때만 패널 조회를 시작한다. */
    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            int width = getContext().getResources().getDisplayMetrics().widthPixels;
            int height = getContext().getResources().getDisplayMetrics().heightPixels;
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.18f);
            window.setLayout(Math.min(dp(720), Math.round(width * 0.92f)),
                    Math.min(dp(480), Math.round(height * 0.80f)));
        }

        if (!closed) {
            panel.start();
        }
    }

    /** 검증된 장소를 지도에 전달하고 목록을 닫아 이동한 지점을 드러낸다. */
    private void selectStore(StoreSummary store) {
        if (closed || mapListener == null) {
            return;
        }

        mapListener.accept(store);
        dismiss();
    }

    /** 필터 행의 기존 위치와 터치 크기를 유지하는 닫기 버튼을 만든다. */
    private Button closeButton() {
        Button button = new Button(getContext());
        MapUiTheme.styleButton(button, false);
        button.setContentDescription(getContext().getString(R.string.stores_close));
        button.setPadding(dp(14), dp(14), dp(14), dp(14));
        button.setCompoundDrawables(new MapIconDrawable(
                MapIconDrawable.Symbol.CLOSE, MapUiTheme.INK, dp(20)), null, null, null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), Color.WHITE, 12), null));
        button.setOnClickListener(view -> {
            dismiss();
        });
        return button;
    }

    /** 패널과 비동기 서비스를 한 번만 닫아 종료 후 응답을 차단한다. */
    @Override
    public void dismiss() {
        if (!closed) {
            closed = true;
            if (panel != null) {
                panel.close();
            } else {
                searchService.close();
            }
        }
        super.dismiss();
    }

    /** 분할 화면의 밀도에 맞춰 논리 크기를 픽셀로 바꾼다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
