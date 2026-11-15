package com.example.pleosstarter.kakao;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.pleosstarter.BuildConfig;
import com.kakaomobility.knsdk.KNLanguageType;
import com.kakaomobility.knsdk.KNSDK;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import kotlin.Unit;

/** 앱 키 인증과 SDK 설치를 한 번 수행하고 화면 재생성에도 결과를 재사용한다. */
public final class KakaoSdkRuntime {
    private static final String TAG = "KakaoNavigationTest";
    private final Application application;
    private final String nativeAppKey;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Completion> pendingCompletions = new ArrayList<>();
    private boolean installed;
    private boolean initialized;
    private boolean initializing;
    private boolean foreground;

    /** 인증 결과와 실패를 서로 다른 경로로 전달한다. */
    public interface Completion {
        /** 인증이 끝나 지도·경로 API를 사용할 수 있음을 알린다. */
        void onReady();

        /** 키 원문을 제외한 초기화 실패를 알린다. */
        void onFailure(KakaoTestFailure failure);
    }

    /** Application과 중앙 빌드 설정에서 읽은 키를 주입한다. */
    public KakaoSdkRuntime(Application application, String nativeAppKey) {
        this.application = application;
        this.nativeAppKey = nativeAppKey;
    }

    /** 키 유무 확인 뒤 설치·인증을 실행하며 중복 요청은 같은 완료 결과를 받는다. */
    public void initialize(Completion completion) {
        if (nativeAppKey.isEmpty()) {
            completion.onFailure(new KakaoTestFailure(
                    KakaoTestError.MISSING_APP_KEY, "카카오 앱 키 등록이 필요합니다."));
            return;
        }
        if (initialized) {
            completion.onReady();
            return;
        }

        pendingCompletions.add(completion);
        if (initializing) {
            return;
        }
        initializing = true;

        try {
            // 이 시험에 필요한 저장 공간과 SDK만 앱 내부에 설치한다.
            if (!installed) {
                File storageDirectory = new File(application.getFilesDir(), "kakao-navigation");
                if (!storageDirectory.isDirectory() && !storageDirectory.mkdirs()) {
                    fail(new KakaoTestFailure(KakaoTestError.SDK_LOAD_FAILED,
                            "카카오 지도 저장 공간을 준비하지 못했습니다."));
                    return;
                }
                KNSDK.INSTANCE.install(application, storageDirectory.getAbsolutePath());
                installed = true;
                Log.i(TAG, "SDK_INSTALLED version=" + KNSDK.INSTANCE.sdkVersion());
            }

            // 최신 AAR의 Java 서명은 appKey, clientVersion, csId, appUserId 순서다.
            KNSDK.INSTANCE.initializeWithAppKey(nativeAppKey, BuildConfig.VERSION_NAME, null, null,
                    KNLanguageType.KNLanguageType_KOREAN, error -> {
                        mainHandler.post(() -> {
                            if (error != null) {
                                KakaoSdkErrorReporter.report("SDK_INITIALIZATION_FAILED", error);
                                fail(new KakaoTestFailure(KakaoTestError.SDK_INITIALIZATION_FAILED,
                                        "카카오 인증 또는 초기화에 실패했습니다. 앱 등록과 연결 상태를 확인하세요."));
                                return;
                            }
                            initialized = true;
                            initializing = false;
                            applyForegroundState();
                            Log.i(TAG, "SDK_INITIALIZED");
                            List<Completion> completions = new ArrayList<>(pendingCompletions);
                            pendingCompletions.clear();
                            for (Completion pending : completions) {
                                pending.onReady();
                            }
                        });
                        return Unit.INSTANCE;
                    });
        } catch (LinkageError error) {
            Log.e(TAG, "SDK_LOAD_FAILED type=" + error.getClass().getSimpleName());
            fail(new KakaoTestFailure(KakaoTestError.SDK_LOAD_FAILED,
                    "이 기기에서 카카오 SDK 라이브러리를 불러오지 못했습니다."));
        } catch (RuntimeException error) {
            Log.e(TAG, "SDK_INITIALIZATION_EXCEPTION type=" + error.getClass().getSimpleName());
            fail(new KakaoTestFailure(KakaoTestError.SDK_INITIALIZATION_FAILED,
                    "카카오 SDK 초기화 중 오류가 발생했습니다."));
        }
    }

    /** 앱 전체의 전경 상태가 바뀌면 초기화된 SDK에 전달한다. */
    public void setForeground(boolean foreground) {
        this.foreground = foreground;
        if (initialized) {
            applyForegroundState();
        }
    }

    /** SDK가 요구하는 전경·배경 수명주기 순서로 상태를 전달한다. */
    private void applyForegroundState() {
        if (foreground) {
            KNSDK.INSTANCE.handleWillEnterForeground();
            KNSDK.INSTANCE.handleDidBecomeActive();
        } else {
            KNSDK.INSTANCE.handleWillResignActive();
            KNSDK.INSTANCE.handleDidEnterBackground();
        }
    }

    /** 대기 중인 호출자 모두에게 같은 실패를 전달하고 재시도할 수 있게 한다. */
    private void fail(KakaoTestFailure failure) {
        initializing = false;
        List<Completion> completions = new ArrayList<>(pendingCompletions);
        pendingCompletions.clear();
        for (Completion pending : completions) {
            pending.onFailure(failure);
        }
    }
}
