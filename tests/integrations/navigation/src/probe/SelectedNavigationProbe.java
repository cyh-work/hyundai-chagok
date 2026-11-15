package probe;

import android.content.Context;
import android.os.Handler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import com.example.pleosstarter.navigation.NavigationDestination;
import com.example.pleosstarter.navigation.NavigationEvent;
import com.example.pleosstarter.navigation.PleosDestinationNavigationClient;
import static com.example.pleosstarter.navigation.NavigationEvent.Status.*;

/** 실제 SDK로 컴파일한 제품 클래스의 외부 연결·시간 경계를 검증한다. */
public final class SelectedNavigationProbe {
    private static final NavigationDestination FIRST =
            new NavigationDestination("카페 하나", "경기 성남시 수정구", 37.4, 127.10);
    private static final NavigationDestination SECOND =
            new NavigationDestination("공원 둘", "경기 성남시 분당구", 37.42, 127.12);
    private static int assertions;
    private static int scenarios;

    /** 대역을 공유하는 하나의 실행을 독립적으로 구성한다. */
    private static final class Run {
        final List<NavigationEvent> events = new ArrayList<>();
        final PleosDestinationNavigationClient client;

        /** 시간과 연결 상태를 초기화하고 제품 관찰자를 연결한다. */
        Run() {
            Handler.reset();
            NaviHelper.reset();
            client = new PleosDestinationNavigationClient(new Context(), events::add);
        }

        /** 시작과 연결 완료를 진행하여 경로 요청 상태를 만든다. */
        NaviHelper request(NavigationDestination destination) {
            client.start(destination);
            NaviHelper helper = lastHelper();
            helper.connected.invoke();
            Handler.flush();
            return helper;
        }

        /** 마지막 관찰 상태를 반환한다. */
        NavigationEvent.Status lastStatus() {
            return events.get(events.size() - 1).status();
        }
    }

    /** 서로 독립적인 성공·오류·수명·시간 경계를 실행한다. */
    public static void main(String[] arguments) {
        inputValidation();
        arbitraryDestinationAndDuplicates();
        duplicateConnectionSuccessDoesNotRequestAnotherRoute();
        sequentialDestinationsAndStaleCallbacks();
        connectionFailureRetry();
        synchronousConnectionFailures();
        listenerRegistrationFailure();
        requestAndDestinationExceptions();
        sdkErrorRetry();
        connectionTimeoutAndLateCallback();
        requestTimeoutResetsAfterConnection();
        closeWhileConnecting();
        closeWithQueuedCallbacks();
        observerClosesImmediately();
        cleanupExceptionsStillPermitRetry();
        System.out.println("PASS scenarios=" + scenarios + " assertions=" + assertions);
        System.out.println("Actual product bytecode compiled against installed SDK; Android/service/time are controlled doubles.");
    }

    /** 같은 SDK 연결 완료 이벤트가 반복돼도 안내 요청과 요청 상태는 한 번만 발생한다. */
    private static void duplicateConnectionSuccessDoesNotRequestAnotherRoute() {
        Run run = new Run();
        NaviHelper helper = run.request(FIRST);

        helper.connected.invoke();
        Handler.flush();

        check(helper.requestCount == 1, "duplicate connection success cannot request a second route");
        check(run.events.stream().filter(event -> event.status() == REQUESTED).count() == 1,
                "duplicate connection success cannot publish a second request status");
        helper.listener.onRouteStarted(start(FIRST));
        Handler.flush();
        helper.listener.onDestinationInfo(destination(FIRST, 100));
        Handler.flush();
        check(run.lastStatus() == CONFIRMED, "first route still confirms after duplicate connection callback");
        done(run, "duplicate connection success");
    }

    /** 빈 이름·비유한 값·좌표 범위를 SDK 호출 전에 거부하는지 확인한다. */
    private static void inputValidation() {
        Run run = new Run();
        NavigationDestination normalized = new NavigationDestination(" 카페 ", null, -90, 180);
        check(normalized.name().equals("카페") && normalized.address().isEmpty()
                && normalized.isValid(), "DTO normalization and inclusive bounds");
        NavigationDestination[] invalid = {
                null, new NavigationDestination(null, "", 37, 127),
                new NavigationDestination(" ", "", 37, 127),
                new NavigationDestination("가게", "", Double.NaN, 127),
                new NavigationDestination("가게", "", 37, Double.POSITIVE_INFINITY),
                new NavigationDestination("가게", "", 90.01, 127),
                new NavigationDestination("가게", "", -90.01, 127),
                new NavigationDestination("가게", "", 37, 180.01),
                new NavigationDestination("가게", "", 37, -180.01)
        };
        for (NavigationDestination destination : invalid) {
            run.client.start(destination);
            check(run.lastStatus() == REQUEST_FAILED, "invalid destination rejected");
        }
        check(NaviHelper.INSTANCES.isEmpty(), "invalid input never creates helper");
        run.request(FIRST);
        check(run.lastStatus() == REQUESTED, "valid retry after invalid input");
        done(run, "input validation");
    }

    /** 임의 장소 전달과 연결·요청 중 중복 차단 및 이름·좌표 대조를 확인한다. */
    private static void arbitraryDestinationAndDuplicates() {
        Run run = new Run();
        run.client.start(FIRST);
        run.client.start(SECOND);
        check(NaviHelper.INSTANCES.size() == 1 && run.events.size() == 1, "duplicate connecting ignored");
        NaviHelper helper = lastHelper();
        helper.connected.invoke();
        Handler.flush();
        run.client.start(SECOND);
        check(helper.requestCount == 1, "duplicate requested ignored");
        RouteInfo route = helper.requestedRoute;
        check(route.getLatitude() == FIRST.latitude() && route.getLongitude() == FIRST.longitude()
                && route.getPoiName().equals(FIRST.name()) && route.getAddress().equals(FIRST.address())
                && route.getRouteOption() == RouteOption.RECOMMENDED, "arbitrary route fields preserved");

        helper.listener.onDestinationInfo(destination(FIRST, 10));
        Handler.flush();
        check(run.lastStatus() == REQUESTED, "destination alone cannot confirm");
        helper.listener.onRouteStarted(start(SECOND));
        Handler.flush();
        check(run.lastStatus() == REQUESTED, "unrelated route start ignored");
        helper.listener.onRouteStarted(start(FIRST));
        Handler.flush();
        check(run.lastStatus() == STARTED && helper.queryCount == 1, "route start queries destination");
        helper.listener.onDestinationInfo(destination(SECOND, 10));
        helper.listener.onDestinationInfo(destination(new NavigationDestination(FIRST.name(), "", 37.5, 127.1), 10));
        Handler.flush();
        check(run.lastStatus() == STARTED, "wrong name or coordinate cannot confirm");
        helper.listener.onDestinationInfo(destination(FIRST, 0));
        Handler.flush();
        check(run.lastStatus() == CONFIRMED && run.events.get(run.events.size() - 1).distanceMeters() == 0,
                "matching destination confirms even at zero distance");
        check(helper.releaseCount == 1 && helper.removeCount == 1, "success detaches connection");
        done(run, "arbitrary destination and duplicate guard");
    }

    /** 두 번째 목적지로 재사용할 때 이전 연결 이벤트가 끼어들지 못하는지 확인한다. */
    private static void sequentialDestinationsAndStaleCallbacks() {
        Run run = new Run();
        NaviHelper first = run.request(FIRST);
        first.listener.onRoutingCompleted();
        first.listener.onDestinationInfo(destination(FIRST, 100));
        Handler.flush();
        check(run.lastStatus() == CONFIRMED, "calculated route has separate confirmed destination");
        NaviHelper second = run.request(SECOND);
        check(second != first && second.requestedRoute.getPoiName().equals(SECOND.name())
                && second.requestedRoute.getLatitude() == SECOND.latitude(), "second route not hardcoded");
        int count = run.events.size();
        first.listener.onRouteStarted(start(SECOND));
        first.listener.onDestinationInfo(destination(SECOND, 1));
        first.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        first.failed.invoke(new IllegalStateException("late error"));
        Handler.flush();
        check(run.events.size() == count && second.releaseCount == 0, "old callback cannot complete or fail new request");
        second.listener.onRoutingCompleted();
        second.listener.onDestinationInfo(destination(SECOND, 42));
        Handler.flush();
        NavigationEvent result = run.events.get(run.events.size() - 1);
        check(result.status() == CONFIRMED && result.destination().equals(SECOND.name())
                && result.distanceMeters() == 42, "second confirmed payload preserved");
        done(run, "sequential destinations and stale callbacks");
    }

    /** 비동기 연결 실패 후 새 서비스 연결로 재시도하는지 확인한다. */
    private static void connectionFailureRetry() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper first = lastHelper();
        first.failed.invoke(new IllegalStateException("connection unavailable"));
        Handler.flush();
        check(run.lastStatus() == CONNECTION_FAILED && first.releaseCount == 1, "connection failure released");
        NaviHelper retry = run.request(SECOND);
        check(retry != first && run.lastStatus() == REQUESTED, "connection failure retry");
        done(run, "connection failure retry");
    }

    /** 생성 및 초기화의 동기 예외도 재시도 가능한 실패로 변환하는지 확인한다. */
    private static void synchronousConnectionFailures() {
        Run run = new Run();
        NaviHelper.failConstructor = true;
        run.client.start(FIRST);
        check(run.lastStatus() == CONNECTION_FAILED, "constructor exception converted");
        NaviHelper.failInitialize = true;
        run.client.start(FIRST);
        check(run.lastStatus() == CONNECTION_FAILED && lastHelper().releaseCount == 1,
                "initialize exception released");
        run.request(SECOND);
        check(run.lastStatus() == REQUESTED, "synchronous failure retry");
        done(run, "synchronous connection failures");
    }

    /** 연결 후 이벤트 구독 실패도 정리되는지 확인한다. */
    private static void listenerRegistrationFailure() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper helper = lastHelper();
        helper.failAdd = true;
        helper.connected.invoke();
        Handler.flush();
        check(run.lastStatus() == CONNECTION_FAILED && helper.requestCount == 0 && helper.releaseCount == 1,
                "listener registration failure released");
        done(run, "listener registration failure");
    }

    /** 경로 요청과 목적지 조회 예외를 실패로 마치고 다시 요청할 수 있는지 확인한다. */
    private static void requestAndDestinationExceptions() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper helper = lastHelper();
        helper.failRequest = true;
        helper.connected.invoke();
        Handler.flush();
        check(run.lastStatus() == REQUEST_FAILED && helper.releaseCount == 1, "request exception released");
        NaviHelper retry = run.request(SECOND);
        retry.failQuery = true;
        retry.listener.onRouteStarted(start(SECOND));
        Handler.flush();
        check(run.lastStatus() == REQUEST_FAILED && retry.releaseCount == 1, "destination exception released");
        run.request(FIRST);
        check(run.lastStatus() == REQUESTED, "request and query retry");
        done(run, "request and destination exceptions");
    }

    /** SDK 오류 이후 요청 상태가 해제되고 재시도되는지 확인한다. */
    private static void sdkErrorRetry() {
        Run run = new Run();
        NaviHelper helper = run.request(FIRST);
        helper.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        helper.listener.onRouteStarted(start(FIRST));
        Handler.flush();
        check(run.lastStatus() == REQUEST_FAILED && helper.queryCount == 0 && helper.releaseCount == 1,
                "SDK error wins over queued late route");
        run.request(SECOND);
        check(run.lastStatus() == REQUESTED, "SDK error retry");
        done(run, "SDK error retry");
    }

    /** 연결 제한 시간의 직전·도달 시점과 뒤늦은 연결 완료를 확인한다. */
    private static void connectionTimeoutAndLateCallback() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper helper = lastHelper();
        Handler.advance(29999);
        check(run.lastStatus() == CONNECTING, "connection timeout not early");
        Handler.advance(1);
        check(run.lastStatus() == TIMED_OUT && helper.releaseCount == 1, "connection timeout at thirty seconds");
        helper.connected.invoke();
        Handler.flush();
        check(helper.requestCount == 0 && run.lastStatus() == TIMED_OUT, "late connection ignored after timeout");
        run.request(SECOND);
        check(run.lastStatus() == REQUESTED, "timeout retry");
        done(run, "connection timeout and retry");
    }

    /** 연결 소요 시간과 독립적으로 경로 요청에 새 제한 시간을 부여하는지 확인한다. */
    private static void requestTimeoutResetsAfterConnection() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper helper = lastHelper();
        Handler.advance(25000);
        helper.connected.invoke();
        Handler.flush();
        Handler.advance(29999);
        check(run.lastStatus() == REQUESTED, "request timer reset after connection");
        Handler.advance(1);
        check(run.lastStatus() == TIMED_OUT && helper.releaseCount == 1, "request timeout releases");
        done(run, "separate request timeout");
    }

    /** 연결 대기 중 화면이 닫힌 뒤 콜백·재요청이 발생하지 않는지 확인한다. */
    private static void closeWhileConnecting() {
        Run run = new Run();
        run.client.start(FIRST);
        NaviHelper helper = lastHelper();
        run.client.close();
        run.client.close();
        helper.connected.invoke();
        helper.failed.invoke(new IllegalStateException("late after close"));
        run.client.start(SECOND);
        Handler.advance(60000);
        check(run.events.size() == 1 && helper.requestCount == 0 && helper.releaseCount == 1
                && NaviHelper.INSTANCES.size() == 1, "closed client ignores callbacks and reuse");
        done(run, "close while connecting");
    }

    /** 이미 메인 큐에 있는 이벤트와 종료 이후 들어오는 이벤트를 함께 차단한다. */
    private static void closeWithQueuedCallbacks() {
        Run run = new Run();
        NaviHelper helper = run.request(FIRST);
        helper.listener.onRouteStarted(start(FIRST));
        run.client.close();
        helper.listener.onRoutingCompleted();
        helper.listener.onDestinationInfo(destination(FIRST, 10));
        helper.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        Handler.advance(60000);
        check(run.events.size() == 2 && helper.queryCount == 0 && helper.releaseCount == 1,
                "close removes queued and rejects later callbacks");
        done(run, "close with queued callbacks");
    }

    /** 관찰자가 진행 알림 안에서 바로 종료해도 뒤따르는 SDK 호출이 없는지 확인한다. */
    private static void observerClosesImmediately() {
        for (NavigationEvent.Status closeAt : List.of(CONNECTING, REQUESTED, STARTED, CALCULATED)) {
            Handler.reset();
            NaviHelper.reset();
            List<NavigationEvent> events = new ArrayList<>();
            PleosDestinationNavigationClient[] holder = new PleosDestinationNavigationClient[1];
            Consumer<NavigationEvent> observer = event -> {
                events.add(event);
                if (event.status() == closeAt) {
                    holder[0].close();
                }
            };
            holder[0] = new PleosDestinationNavigationClient(new Context(), observer);
            holder[0].start(FIRST);
            if (closeAt == CONNECTING) {
                check(NaviHelper.INSTANCES.isEmpty(), "close in CONNECTING prevents creation");
                continue;
            }
            NaviHelper helper = lastHelper();
            helper.connected.invoke();
            Handler.flush();
            if (closeAt == REQUESTED) {
                check(helper.requestCount == 0 && helper.releaseCount == 1, "close in REQUESTED prevents request");
                continue;
            }
            if (closeAt == STARTED) {
                helper.listener.onRouteStarted(start(FIRST));
            } else {
                helper.listener.onRoutingCompleted();
            }
            Handler.flush();
            check(helper.queryCount == 0 && helper.releaseCount == 1, "close in route event prevents query");
        }
        scenarios++;
        System.out.println("PASS observer-triggered close");
    }

    /** 해제 예외가 실패 응답이나 다음 요청을 막지 않는지 확인한다. */
    private static void cleanupExceptionsStillPermitRetry() {
        Run run = new Run();
        NaviHelper helper = run.request(FIRST);
        helper.failRemove = true;
        helper.failRelease = true;
        helper.listener.onError(NaviErrorCode.CAUSE_SEARCH_ROUTE);
        Handler.flush();
        check(run.lastStatus() == REQUEST_FAILED && helper.removeCount == 1 && helper.releaseCount == 1,
                "release attempted despite remove exception");
        run.request(SECOND);
        check(run.lastStatus() == REQUESTED, "cleanup exception does not block retry");
        done(run, "cleanup exception recovery");
    }

    /** 마지막으로 생성된 대역 SDK 연결을 반환한다. */
    private static NaviHelper lastHelper() {
        return NaviHelper.INSTANCES.get(NaviHelper.INSTANCES.size() - 1);
    }

    /** SDK의 실제 목적지 DTO로 응답을 생성한다. */
    private static DestinationInfo destination(NavigationDestination destination, int distance) {
        return new DestinationInfo(destination.latitude(), destination.longitude(), null, null,
                destination.name(), destination.address(), distance, 60, 0, "", "", "");
    }

    /** SDK의 실제 안내 시작 DTO로 응답을 생성한다. */
    private static RouteStartInfo start(NavigationDestination destination) {
        return new RouteStartInfo(60, 100, 0, destination.name(), 80);
    }

    /** 실패 시 원인을 드러내고 확인한 단언 수를 누적한다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    /** 시나리오의 남은 자원을 닫고 성공을 기록한다. */
    private static void done(Run run, String description) {
        run.client.close();
        scenarios++;
        System.out.println("PASS " + description);
    }
}
