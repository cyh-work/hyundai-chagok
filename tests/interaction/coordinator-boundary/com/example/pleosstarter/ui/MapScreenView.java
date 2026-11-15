package com.example.pleosstarter.ui;
/** Android UI 대신 화면에 전달한 상태만 보관한다. */
public final class MapScreenView {
    public String message = "";
    public boolean active;
    public boolean paused;
    public int failures;
    private final Button button = new Button();
    /** 피드백 전달 횟수를 기록한다. */
    public void showFeedback(String message) {
        failures++;
    }
    /** 시험 표시 상태를 기록한다. */
    public void showTestMovement(String message, boolean active, boolean paused, boolean loading) {
        this.message = message;
        this.active = active;
        this.paused = paused;
    }
    /** 완료 표시용 버튼 경계를 반환한다. */
    public Button getMovementButton() {
        return button;
    }
    /** 버튼 텍스트 설정만 허용하는 대역이다. */
    public static final class Button {
        /** 렌더링 없이 표시 요청을 허용한다. */
        public void setText(String text) {
        }
    }
}
