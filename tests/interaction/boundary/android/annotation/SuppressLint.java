package android.annotation;
/** 검사 컴파일에 필요한 주석 선언이다. */
public @interface SuppressLint {
    /** 적용 대상 경고 이름을 반환한다. */
    String[] value();
}
