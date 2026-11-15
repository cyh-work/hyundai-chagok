package com.example.pleosstarter.kakao;

import android.util.Log;

import com.kakaomobility.knsdk.common.objects.KNError;

/** 인증정보가 섞일 수 있는 오류 본문 대신 SDK 오류 코드만 검증 로그에 남긴다. */
public final class KakaoSdkErrorReporter {
    /** 정적 오류 기록만 허용하는 전용 클래스다. */
    private KakaoSdkErrorReporter() {
    }

    /** 숫자 또는 대문자 오류 식별자만 허용하고 메시지·추가정보는 출력하지 않는다. */
    public static void report(String event, KNError error) {
        String safeCode = "unclassified";
        if (error != null && error.getCode() != null
                && error.getCode().matches("-?[0-9]{1,10}|[A-Z][A-Z_0-9]{0,39}")) {
            safeCode = error.getCode();
        }
        Log.w("KakaoNavigationTest", event + " sdkCode=" + safeCode);
    }
}
