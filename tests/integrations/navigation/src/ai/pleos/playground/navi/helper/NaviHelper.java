package ai.pleos.playground.navi.helper;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/** 실제 SDK와 같은 호출 계약으로 연결·요청·해제 실패를 주입한다. */
public final class NaviHelper {
    public static final List<NaviHelper> INSTANCES = new ArrayList<>();
    public static boolean failConstructor;
    public static boolean failInitialize;
    public Function0<Unit> connected;
    public Function1<? super Throwable, Unit> failed;
    public NaviHelperEventListener listener;
    public RouteInfo requestedRoute;
    public int requestCount;
    public int queryCount;
    public int releaseCount;
    public int removeCount;
    public boolean failAdd;
    public boolean failRequest;
    public boolean failQuery;
    public boolean failRemove;
    public boolean failRelease;

    /** 생성 오류 또는 새 연결 인스턴스를 기록한다. */
    public NaviHelper(Context context) {
        if (failConstructor) {
            failConstructor = false;
            throw new IllegalStateException("injected constructor failure");
        }
        INSTANCES.add(this);
    }

    /** 완료 콜백을 보관하여 테스트가 연결 완료 시각을 결정하게 한다. */
    public void initialize(Function0<Unit> connected, Function1<? super Throwable, Unit> failed) {
        if (failInitialize) {
            failInitialize = false;
            throw new IllegalStateException("injected initialize failure");
        }
        this.connected = connected;
        this.failed = failed;
    }

    /** 이벤트 구독 계약과 구독 실패를 재현한다. */
    public void addListener(NaviHelperEventListener listener) {
        if (failAdd) {
            throw new IllegalStateException("injected add failure");
        }
        this.listener = listener;
    }

    /** 해제 호출 수를 기록하며 과거 콜백 참조는 시험을 위해 보존한다. */
    public void removeListener(NaviHelperEventListener listener) {
        removeCount++;
        if (failRemove) {
            throw new IllegalStateException("injected remove failure");
        }
    }

    /** 실제 SDK DTO를 보관하여 전달 좌표와 표시 이름을 검증한다. */
    public void requestRoute(RouteInfo route) {
        requestCount++;
        if (failRequest) {
            throw new IllegalStateException("injected route failure");
        }
        requestedRoute = route;
    }

    /** 목적지 조회 횟수와 조회 예외를 재현한다. */
    public void getDestinationInfo() {
        queryCount++;
        if (failQuery) {
            throw new IllegalStateException("injected destination failure");
        }
    }

    /** 서비스 해제 횟수를 기록하고 해제 중 예외도 시험한다. */
    public void release() {
        releaseCount++;
        if (failRelease) {
            throw new IllegalStateException("injected release failure");
        }
    }

    /** 테스트 간 주입 상태와 연결 인스턴스를 초기화한다. */
    public static void reset() {
        INSTANCES.clear();
        failConstructor = false;
        failInitialize = false;
    }
}
