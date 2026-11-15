package android.content;

import java.io.File;

/** 실제 파일 저장 위치 대신 별도로 주입한 시험 파일만 사용하게 하는 경계 대역이다. */
public class Context {
    /** 시험 서비스는 설정 파일을 직접 주입하므로 기본 앱 저장 위치를 사용하지 않는다. */
    public File getFilesDir() {
        return null;
    }
}
