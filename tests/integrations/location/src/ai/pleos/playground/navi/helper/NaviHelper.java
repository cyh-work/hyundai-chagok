package ai.pleos.playground.navi.helper;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/** 연결 지연·요청 실패·늦은 콜백을 제어하는 SDK 시험 대역이다. */
public final class NaviHelper {
    public static final List<NaviHelper> instances = new ArrayList<>();
    public static RuntimeException nextRequestFailure;
    public static RuntimeException nextInitializeFailure;
    public int requests;
    public int releases;
    private Function0<Unit> success;
    private Function1<? super Throwable, Unit> failure;
    private NaviHelperEventListener lastListener;

    /** 생성된 연결을 시험에서 조회할 수 있게 저장한다. */
    public NaviHelper(Context context) {
        instances.add(this);
    }

    /** 성공과 실패 콜백을 저장하고 시험에서 지정한 동기 예외를 던진다. */
    public void initialize(Function0<Unit> success, Function1<? super Throwable, Unit> failure) {
        this.success = success;
        this.failure = failure;
        if (nextInitializeFailure != null) {
            RuntimeException error = nextInitializeFailure;
            nextInitializeFailure = null;
            throw error;
        }
    }

    /** 연결 성공을 지연 전달한다. */
    public void connectSuccessfully() {
        success.invoke();
    }

    /** 연결 실패를 지연 전달한다. */
    public void failConnection(Throwable error) {
        failure.invoke(error);
    }

    /** 위치 수신자를 기록한다. */
    public void addListener(NaviHelperEventListener listener) {
        lastListener = listener;
    }

    /** 이미 대기열에 들어간 콜백을 재현하기 위해 마지막 수신자는 보존한다. */
    public void removeListener(NaviHelperEventListener listener) {
    }

    /** 해제 횟수를 기록한다. */
    public void release() {
        releases++;
    }

    /** 요청 횟수와 요청 실패를 재현한다. */
    public void getCurrentLocationInfo() {
        requests++;
        if (nextRequestFailure != null) {
            RuntimeException error = nextRequestFailure;
            nextRequestFailure = null;
            throw error;
        }
    }

    /** 해제된 연결에서도 늦은 결과가 오는 상황을 재현한다. */
    public void respond(CurrentLocationInfo location) {
        lastListener.onCurrentLocationInfo(location);
    }

    /** 독립 시나리오에 사용할 전역 시험 상태를 초기화한다. */
    public static void reset() {
        instances.clear();
        nextRequestFailure = null;
        nextInitializeFailure = null;
    }
}
