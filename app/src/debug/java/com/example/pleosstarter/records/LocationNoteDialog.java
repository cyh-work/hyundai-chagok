package com.example.pleosstarter.records;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.pleosstarter.notes.LocationNote;
import com.example.pleosstarter.ui.MapUiTheme;
import com.example.pleosstarter.ui.MapIconDrawable;

import java.util.Objects;
import java.util.function.Consumer;

/** 지도에서 원·별을 그린 위치의 음성 메모를 검토하고 명시적으로 저장하는 화면이다. */
public final class LocationNoteDialog extends Dialog {
    /** 영속 저장을 마친 메모만 화면에 반영하는 계약이다. */
    public interface Listener {
        /** 저장 완료한 메모를 지도 마커와 목록에 반영한다. */
        void onSaved(LocationNote note);
    }

    private static final String LOG_TAG = "PlaceMap";
    private static final int ERROR_COLOR = Color.rgb(174, 45, 51);
    private final MemoSpeechInput speech;
    private final String title;
    private final String placeName;
    private final String initialText;
    private final Consumer<String> saveAction;
    private EditText editor;
    private TextView speechStatus;
    private int speechGeneration;
    private boolean listening;
    private boolean started;
    private boolean closed;

    /** 좌표 변환을 끝낸 지도 위치와 서비스·음성 입력·저장 수신자를 주입한다. */
    public LocationNoteDialog(Context context, MapRecordsService records, MemoSpeechInput speech,
                              double latitude, double longitude, String placeName, Listener listener) {
        this(context, speech, "위치 메모", placeName, "",
                noteSaveAction(records, latitude, longitude, listener));
    }

    /** 기록 종류와 저장 동작을 주입해 원·별이 같은 음성 입력 수명 주기를 사용하게 한다. */
    public LocationNoteDialog(Context context, MemoSpeechInput speech, String title,
                              String placeName, String initialText, Consumer<String> saveAction) {
        super(context);
        this.speech = Objects.requireNonNull(speech, "음성 입력이 필요합니다.");
        this.title = Objects.requireNonNull(title, "기록 제목이 필요합니다.");
        this.initialText = Objects.requireNonNull(initialText, "기존 기록 내용이 필요합니다.");
        this.saveAction = Objects.requireNonNull(saveAction, "기록 저장 동작이 필요합니다.");
        this.placeName = placeName == null || placeName.isBlank() ? "지도에서 그린 위치" : placeName;
    }

    /** 기존 원 메모 생성자의 입력 조건과 저장 완료 알림 계약을 보존한다. */
    private static Consumer<String> noteSaveAction(MapRecordsService records, double latitude,
                                                   double longitude, Listener listener) {
        Objects.requireNonNull(records, "위치 기록 서비스가 필요합니다.");
        Objects.requireNonNull(listener, "메모 저장 수신자가 필요합니다.");
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                || Math.abs(latitude) > 90.0 || Math.abs(longitude) > 180.0) {
            throw new MapRecordFailure(MapRecordFailure.Code.INVALID_INPUT,
                    "메모를 남길 지도 위치를 확인하지 못했습니다.");
        }
        return text -> listener.onSaved(records.saveNote(latitude, longitude, text));
    }

    /** 입력·오류·저장 버튼을 구성하며 키보드는 직접 편집할 때만 표시한다. */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = new LinearLayout(getContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(28), dp(28), dp(24));
        content.setBackground(MapUiTheme.surface(getContext(), Color.WHITE, 20));

        // 기록 종류와 대상 위치를 한 헤더 안에서 구분한다.
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView recordIcon = new ImageView(getContext());
        MapIconDrawable.Symbol symbol = title.contains("즐겨찾기")
                ? MapIconDrawable.Symbol.STAR : MapIconDrawable.Symbol.NOTE;
        recordIcon.setImageDrawable(new MapIconDrawable(symbol, MapUiTheme.ACCENT, dp(26)));
        recordIcon.setPadding(dp(12), dp(12), dp(12), dp(12));
        recordIcon.setBackground(MapUiTheme.surface(getContext(), MapUiTheme.ACCENT_SOFT, 14));
        recordIcon.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(recordIcon, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout titles = new LinearLayout(getContext());
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView heading = text(title, 25, MapUiTheme.INK);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setAccessibilityHeading(true);
        titles.addView(heading);
        TextView target = text(placeName, 14, MapUiTheme.MUTED);
        LinearLayout.LayoutParams targetLayout = fullWidth();
        targetLayout.topMargin = dp(4);
        titles.addView(target, targetLayout);
        LinearLayout.LayoutParams titlesLayout = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titlesLayout.setMarginStart(dp(16));
        header.addView(titles, titlesLayout);
        content.addView(header, fullWidth());

        // 키보드가 열리거나 분할 화면이 좁아져도 편집 영역을 스크롤할 수 있게 한다.
        ScrollView scroll = new ScrollView(getContext());
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        LinearLayout fields = new LinearLayout(getContext());
        fields.setOrientation(LinearLayout.VERTICAL);
        fields.setPadding(0, dp(24), 0, dp(4));
        scroll.addView(fields);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        speechStatus = text("음성 입력 준비 중", 15, MapUiTheme.ACCENT);
        speechStatus.setCompoundDrawablesRelative(new MapIconDrawable(
                MapIconDrawable.Symbol.MICROPHONE, MapUiTheme.ACCENT, dp(20)), null, null, null);
        speechStatus.setCompoundDrawablePadding(dp(10));
        speechStatus.setGravity(Gravity.CENTER_VERTICAL);
        speechStatus.setPadding(dp(2), dp(8), dp(2), dp(12));
        speechStatus.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        fields.addView(speechStatus, fullWidth());

        editor = new EditText(getContext());
        editor.setTextColor(MapUiTheme.INK);
        editor.setHintTextColor(MapUiTheme.MUTED);
        editor.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        editor.setTextSize(20);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editor.setHint("이곳에서 기억할 내용을 남겨 보세요");
        editor.setPadding(dp(18), dp(18), dp(18), dp(18));
        editor.setBackgroundTintList(null);
        GradientDrawable editorBackground = MapUiTheme.surface(getContext(), MapUiTheme.SURFACE, 12);
        editorBackground.setStroke(dp(1), MapUiTheme.BORDER);
        editor.setBackground(editorBackground);
        editor.setLineSpacing(dp(3), 1.0f);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(4);
        editor.setMaxLines(6);
        editor.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        editor.setText(initialText);
        editor.setSelection(editor.length());
        configureManualInputCancellation();
        LinearLayout.LayoutParams editorLayout = fullWidth();
        editorLayout.topMargin = dp(2);
        fields.addView(editor, editorLayout);

        Button retry = button("음성 다시 입력", false);
        retry.setCompoundDrawablesRelative(new MapIconDrawable(
                MapIconDrawable.Symbol.MICROPHONE, MapUiTheme.INK, dp(20)), null, null, null);
        retry.setCompoundDrawablePadding(dp(10));
        retry.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x18007FA8),
                MapUiTheme.surface(getContext(), Color.TRANSPARENT, 12), null));
        retry.setGravity(Gravity.CENTER);
        retry.setOnClickListener(view -> startSpeech());
        LinearLayout.LayoutParams retryLayout = new LinearLayout.LayoutParams(dp(190), dp(52));
        retryLayout.gravity = Gravity.END;
        retryLayout.topMargin = dp(6);
        fields.addView(retry, retryLayout);

        // 저장과 취소는 편집 내용의 스크롤과 관계없이 항상 같은 자리에 둔다.
        LinearLayout buttons = new LinearLayout(getContext());
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        Button cancel = button("취소", false);
        cancel.setOnClickListener(view -> dismiss());
        LinearLayout.LayoutParams cancelLayout = new LinearLayout.LayoutParams(0, dp(56), 1.0f);
        cancelLayout.setMarginEnd(dp(12));
        buttons.addView(cancel, cancelLayout);
        Button save = button("저장", true);
        save.setOnClickListener(view -> saveRecord());
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(56), 1.0f));
        LinearLayout.LayoutParams buttonsLayout = fullWidth();
        buttonsLayout.topMargin = dp(20);
        content.addView(buttons, buttonsLayout);
        setContentView(content);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /** 대화상자를 표시한 직후 원·별을 그린 위치에 대한 음성 인식을 시작한다. */
    @Override
    protected void onStart() {
        super.onStart();
        Window window = getWindow();
        if (window != null) {
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            int screenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
            window.setDimAmount(0.22f);
            window.setLayout(Math.min(dp(640), screenWidth - dp(48)),
                    Math.min(dp(480), Math.round(screenHeight * 0.86f)));
        }
        if (!started && !closed) {
            started = true;
            editor.post(() -> {
                if (!closed && isShowing()) {
                    startSpeech();
                }
            });
        }
    }

    /** 다른 화면으로 전환될 때 인식을 취소하며 편집 중인 텍스트는 유지한다. */
    public void cancelSpeech() {
        boolean wasListening = listening;
        speechGeneration++;
        listening = false;
        speech.cancel();
        if (wasListening) {
            Log.i(LOG_TAG, "speech code=CANCELLED");
        }
        if (wasListening && speechStatus != null && isShowing()) {
            showStatus("음성 입력이 중지되었습니다. 다시 시도하거나 직접 입력하세요.", false);
        }
    }

    /** 닫힌 대화상자의 인식을 취소하며 공유 음성 입력의 최종 종료는 소유 화면에 맡긴다. */
    @Override
    protected void onStop() {
        cancelSpeech();
        closed = true;
        super.onStop();
    }

    /** 이전 인식만 취소하고 이번 요청의 결과가 편집기에 한 번 반영되도록 한다. */
    private void startSpeech() {
        if (closed) {
            return;
        }
        cancelSpeech();
        int generation = ++speechGeneration;
        listening = true;
        Log.i(LOG_TAG, "speech code=START");
        showStatus("듣고 있어요. 말씀해 주세요.", false);
        speech.start(new MemoSpeechInput.Callback() {
            /** 살아 있는 요청의 확정 문장만 편집기에 채우며 아직 저장하지 않는다. */
            @Override
            public void onText(String text) {
                if (!acceptsResult(generation)) {
                    return;
                }
                listening = false;
                if (text == null || text.isBlank()) {
                    Log.i(LOG_TAG, "speech code=EMPTY_RESULT");
                    showStatus("음성을 인식하지 못했습니다. 다시 시도하거나 직접 입력하세요.", true);
                    return;
                }
                editor.setText(text);
                editor.setSelection(editor.length());
                Log.i(LOG_TAG, "speech code=TEXT_READY");
                showStatus("입력 완료 · 확인 후 저장해 주세요.", false);
            }

            /** 인식 실패를 그대로 안내하고 직접 편집·재시도·취소를 계속 허용한다. */
            @Override
            public void onFailure(MapRecordFailure failure) {
                if (!acceptsResult(generation)) {
                    return;
                }
                listening = false;
                Log.i(LOG_TAG, "speech code=" + failure.getCode().name());
                showStatus(failure.getMessage() + " 다시 시도하거나 직접 입력하세요.", true);
            }
        });
    }

    /** 수동 편집을 시작하면 인식을 취소하고 터치·접근성 클릭은 EditText 기본 처리에 맡긴다. */
    @SuppressLint("ClickableViewAccessibility")
    private void configureManualInputCancellation() {
        editor.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                cancelSpeech();
            }
            return false;
        });
    }

    /** 취소·닫기 이후나 이전 인식 요청의 늦은 결과를 반영하지 않는다. */
    private boolean acceptsResult(int generation) {
        return !closed && isShowing() && listening && generation == speechGeneration;
    }

    /** 사용자가 확인한 텍스트를 영속 저장한 뒤에만 성공 콜백과 닫기를 수행한다. */
    private void saveRecord() {
        cancelSpeech();
        try {
            saveAction.accept(editor.getText().toString());
        } catch (MapRecordFailure failure) {
            showStatus(failure.getMessage(), true);
            return;
        }
        dismiss();
    }

    /** 음성·저장 상태를 사용자에게 알리며 원문이나 녹음을 로그에 남기지 않는다. */
    private void showStatus(String message, boolean error) {
        speechStatus.setText(message);
        speechStatus.setTextColor(error ? ERROR_COLOR : MapUiTheme.ACCENT);
    }

    /** 역할별 색과 글자 크기를 지정한 안내 문구를 만든다. */
    private TextView text(String message, int size, int color) {
        TextView view = new TextView(getContext());
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        view.setLineSpacing(dp(3), 1.0f);
        return view;
    }

    /** 터치 목표가 충분한 대화상자 버튼을 만든다. */
    private Button button(String label, boolean primary) {
        Button button = new Button(getContext());
        button.setText(label);
        MapUiTheme.styleButton(button, primary);
        return button;
    }

    /** 세로 컨테이너에서 사용하는 전체 너비 배치 값을 만든다. */
    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /** 화면 밀도에 맞춰 대화상자 크기를 계산한다. */
    private int dp(int size) {
        return MapUiTheme.dp(getContext(), size);
    }
}
