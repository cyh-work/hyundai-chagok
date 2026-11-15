package com.example.pleosstarter.kakao;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import com.example.pleosstarter.BuildConfig;

/** 기존 앱 시작에는 SDK를 실행하지 않고 카카오 시험에만 공용 수명주기를 제공한다. */
public final class KakaoTestApplication extends Application implements Application.ActivityLifecycleCallbacks {
    private KakaoSdkRuntime sdkRuntime;
    private int startedActivities;

    /** 앱의 전경·배경 전환을 관찰하되 카카오 초기화는 시험 화면까지 미룬다. */
    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    /** 비밀값을 노출하지 않고 빌드 설정에 앱 키가 있는지만 반환한다. */
    public boolean hasKakaoAppKey() {
        return !BuildConfig.KAKAO_NATIVE_APP_KEY.isEmpty();
    }

    /** 카카오 시험 화면이 요청할 때만 SDK 실행 객체를 구성한다. */
    public KakaoSdkRuntime getSdkRuntime() {
        if (sdkRuntime == null) {
            sdkRuntime = new KakaoSdkRuntime(this, BuildConfig.KAKAO_NATIVE_APP_KEY);
            sdkRuntime.setForeground(startedActivities > 0);
        }
        return sdkRuntime;
    }

    /** 첫 Activity가 보이면 앱 전체가 전경으로 전환됐음을 전달한다. */
    @Override
    public void onActivityStarted(Activity activity) {
        startedActivities++;
        if (startedActivities == 1 && sdkRuntime != null) {
            sdkRuntime.setForeground(true);
        }
    }

    /** 마지막 Activity가 사라지면 앱 전체가 배경으로 전환됐음을 전달한다. */
    @Override
    public void onActivityStopped(Activity activity) {
        startedActivities = Math.max(0, startedActivities - 1);
        if (startedActivities == 0 && sdkRuntime != null) {
            sdkRuntime.setForeground(false);
        }
    }

    /** Activity 생성 자체에는 지도나 안내를 시작하지 않는다. */
    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    /** 지도 렌더링 재개는 해당 화면의 세션이 담당한다. */
    @Override
    public void onActivityResumed(Activity activity) {
    }

    /** 지도 렌더링 중단은 해당 화면의 세션이 담당한다. */
    @Override
    public void onActivityPaused(Activity activity) {
    }

    /** 이 시험 앱은 SDK 상태를 Activity 저장 상태에 복사하지 않는다. */
    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    /** 지도 자원 해제는 해당 화면의 세션이 담당한다. */
    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
