import com.example.pleosstarter.simulation.KakaoRouteGeometryMapper;
import com.example.pleosstarter.simulation.PlaybackClock;
import com.example.pleosstarter.simulation.PlaybackFailure;
import com.example.pleosstarter.simulation.PlaybackFrame;
import com.example.pleosstarter.simulation.PlaybackListener;
import com.example.pleosstarter.simulation.PlaybackScheduler;
import com.example.pleosstarter.simulation.PlaybackState;
import com.example.pleosstarter.simulation.RoutePath;
import com.example.pleosstarter.simulation.RoutePlaybackController;
import com.example.pleosstarter.simulation.RoutePoint;
import com.example.pleosstarter.simulation.RouteSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/** Android와 네트워크 없이 도로 보간과 일시정지 시간·요청 교체를 검증한다. */
public final class RoutePlaybackProbe {
    private static int assertions;
    private static final RoutePoint START = new RoutePoint(37.4127, 127.0947);
    private static final RoutePoint CORNER = new RoutePoint(37.4137, 127.0947);
    private static final RoutePoint END = new RoutePoint(37.4137, 127.0957);

    /** 독립된 경계 시나리오를 실행하고 실제 통과한 검사 수를 출력한다. */
    public static void main(String[] arguments) {
        verifyGeometry();
        verifyMapping();
        verifySpeedAndFinish();
        verifyUserPause();
        verifyTouchAndForeground();
        verifyLoadingHold();
        verifyStaleRequestsAndClose();
        verifyReentrantStop();
        System.out.println("PASS RoutePlaybackProbe assertions=" + assertions);
    }

    /** 굽은 도로를 가로지르지 않고 각 선분과 시작·끝을 보존하는지 확인한다. */
    private static void verifyGeometry() {
        RoutePath path = route();
        RoutePath firstLeg = new RoutePath(List.of(START, CORNER));
        double cornerDistance = firstLeg.totalMeters();
        equal(path.pointCount(), 3, "bend point count");
        equal(path.pointAt(-10), START, "start clamp");
        equal(path.pointAt(path.totalMeters() + 10), END, "end clamp");
        equal(path.pointAt(cornerDistance), CORNER, "exact corner");
        near(path.pointAt(cornerDistance / 2).longitude(), START.longitude(), 1e-12,
                "northbound leg must keep longitude");
        near(path.pointAt((path.totalMeters() + cornerDistance) / 2).latitude(), END.latitude(),
                1e-12, "eastbound leg must keep latitude");

        RoutePath duplicates = new RoutePath(List.of(START, START, CORNER, CORNER, END, END));
        equal(duplicates.pointCount(), 3, "duplicate vertices removed");
        near(duplicates.totalMeters(), path.totalMeters(), 1e-9, "duplicates keep distance");
        rejects(() -> new RoutePath(List.of()), "empty path");
        rejects(() -> new RoutePath(List.of(START)), "single vertex");
        rejects(() -> new RoutePath(List.of(START, START)), "zero length");
        rejects(() -> path.pointAt(Double.NaN), "nonfinite distance");
        rejects(() -> new RoutePoint(Double.NaN, 127), "nonfinite point");
        rejects(() -> new RoutePoint(127, 37), "swapped WGS84 coordinates");

        RoutePath dateline = new RoutePath(List.of(new RoutePoint(0, 179.9), new RoutePoint(0, -179.9)));
        near(Math.abs(dateline.pointAt(dateline.totalMeters() / 2).longitude()), 180, 1e-9,
                "dateline shortest interpolation");
    }

    /** SDK의 실제 x·y 규약을 적용하며 누락 좌표를 직선으로 건너뛰지 않는지 확인한다. */
    private static void verifyMapping() {
        KakaoRouteGeometryMapper mapper = new KakaoRouteGeometryMapper();
        RoutePath mapped = mapper.map(List.of(
                Map.of("x", START.longitude(), "y", START.latitude(), "trfSt", 1),
                Map.of("x", CORNER.longitude(), "y", CORNER.latitude(), "trfSt", 3)));
        equal(mapped.pointAt(0), START, "SDK first point uses x longitude and y latitude");
        equal(mapped.pointAt(mapped.totalMeters()), CORNER, "SDK last point preserved");
        rejects(() -> mapper.map(null), "null SDK geometry");
        rejects(() -> mapper.map(List.of(Map.of("x", 127), Map.of("x", 128, "y", 38))),
                "missing y rejects whole route");
        rejects(() -> mapper.map(List.of(Map.of("x", 127, "y", 37),
                Map.of("x", Double.POSITIVE_INFINITY, "y", 38))), "invalid SDK number");
    }

    /** 가상 시간으로 속도·100ms 갱신·끝점 정지·정지 뒤 무갱신을 확인한다. */
    private static void verifySpeedAndFinish() {
        Fixture fixture = new Fixture();
        fixture.startReady();
        equal(fixture.controller.getState(), PlaybackState.PLAYING, "ready state");
        equal(fixture.observer.frames.size(), 1, "initial frame");
        fixture.time.advance(1000);
        equal(fixture.observer.frames.size(), 11, "ten updates per second");
        near(fixture.last().distanceMeters(), 30 / 3.6, 1e-9, "30km/h movement");
        fixture.time.advance(60000);
        equal(fixture.controller.getState(), PlaybackState.FINISHED, "endpoint stops");
        equal(fixture.last().point(), END, "endpoint exact");
        near(fixture.last().distanceMeters(), fixture.last().totalMeters(), 1e-9, "end distance");
        int framesAtEnd = fixture.observer.frames.size();
        fixture.time.advance(60000);
        equal(fixture.observer.frames.size(), framesAtEnd, "finished has no timer");
        equal(fixture.time.pending(), 0, "finished queue empty");

        double previousDistance = -1;
        for (PlaybackFrame frame : fixture.observer.frames) {
            check(frame.distanceMeters() >= previousDistance, "distance monotonic");
            check(frame.distanceMeters() <= frame.totalMeters(), "distance within route");
            check(frame.point().latitude() >= START.latitude()
                    && frame.point().latitude() <= END.latitude(), "latitude within bend");
            previousDistance = frame.distanceMeters();
        }
    }

    /** 사용자가 멈춘 시간을 포함하지 않고 터치 해제로 수동 정지가 풀리지 않는지 확인한다. */
    private static void verifyUserPause() {
        Fixture fixture = new Fixture();
        fixture.startReady();
        fixture.time.advance(1000);
        double before = fixture.last().distanceMeters();
        int framesBefore = fixture.observer.frames.size();
        fixture.controller.pause();
        fixture.controller.setTouchActive(true);
        fixture.time.advance(20000);
        fixture.controller.setTouchActive(false);
        equal(fixture.controller.getState(), PlaybackState.PAUSED, "manual pause survives touch end");
        equal(fixture.observer.frames.size(), framesBefore, "paused frame remains fixed");
        equal(fixture.time.pending(), 0, "manual pause cancels timer");
        check(fixture.controller.isUserPaused(), "manual flag preserved");
        fixture.controller.resume();
        fixture.time.advance(100);
        near(fixture.last().distanceMeters(), before + 30 / 36.0, 1e-9, "paused duration excluded");
    }

    /** 손가락과 화면 가림이 겹쳐도 마지막 보류가 해제될 때까지 정지하는지 확인한다. */
    private static void verifyTouchAndForeground() {
        Fixture fixture = new Fixture();
        fixture.startReady();
        fixture.time.advance(500);
        fixture.controller.setTouchActive(true);
        int heldFrames = fixture.observer.frames.size();
        double heldDistance = fixture.last().distanceMeters();
        fixture.controller.setForeground(false);
        fixture.time.advance(100000);
        fixture.controller.setTouchActive(false);
        equal(fixture.controller.getState(), PlaybackState.PAUSED, "background still holds");
        equal(fixture.observer.frames.size(), heldFrames, "no hidden frames");
        fixture.controller.setForeground(true);
        fixture.time.advance(100);
        near(fixture.last().distanceMeters(), heldDistance + 30 / 36.0, 1e-9,
                "touch and background time excluded");
        fixture.controller.pause();
        fixture.controller.setForeground(false);
        fixture.controller.setForeground(true);
        equal(fixture.controller.getState(), PlaybackState.PAUSED, "manual pause survives foreground");
    }

    /** 터치·화면 가림 중 경로가 와도 첫 위치로 카메라를 움직이지 않는지 확인한다. */
    private static void verifyLoadingHold() {
        Fixture fixture = new Fixture();
        fixture.controller.setForeground(false);
        fixture.controller.setTouchActive(true);
        fixture.controller.start(START, END);
        fixture.time.advance(10000);
        fixture.source.callbacks.get(0).onSuccess(route());
        equal(fixture.controller.getState(), PlaybackState.PAUSED, "loaded while held");
        equal(fixture.observer.frames.size(), 0, "no initial frame under finger");
        fixture.controller.setForeground(true);
        equal(fixture.observer.frames.size(), 0, "touch still holds initial frame");
        fixture.controller.setTouchActive(false);
        equal(fixture.last().point(), START, "release starts at route start");
        fixture.time.advance(100);
        near(fixture.last().distanceMeters(), 30 / 36.0, 1e-9, "loading wait excluded");
    }

    /** 중지·새 시작·종료 뒤의 늦은 성공과 실패가 상태를 되돌리지 않는지 확인한다. */
    private static void verifyStaleRequestsAndClose() {
        Fixture fixture = new Fixture();
        fixture.controller.start(START, END);
        RouteSource.Callback old = fixture.source.callbacks.get(0);
        fixture.controller.stop();
        old.onSuccess(route());
        old.onFailure(new PlaybackFailure(PlaybackFailure.Code.ROUTE_TIMEOUT, "timeout"));
        equal(fixture.controller.getState(), PlaybackState.STOPPED, "stop ignores late request");
        equal(fixture.observer.frames.size(), 0, "stale route not displayed");
        equal(fixture.observer.failures.size(), 0, "stale failure hidden");

        fixture.controller.start(START, END);
        fixture.controller.start(START, CORNER);
        fixture.source.callbacks.get(1).onSuccess(route());
        equal(fixture.controller.getState(), PlaybackState.LOADING, "new request stays loading");
        fixture.source.callbacks.get(2).onSuccess(new RoutePath(List.of(START, CORNER)));
        fixture.time.advance(200);
        equal(fixture.controller.getState(), PlaybackState.PLAYING, "new route wins");
        int frames = fixture.observer.frames.size();
        fixture.controller.close();
        fixture.source.callbacks.get(2).onSuccess(route());
        fixture.time.advance(100000);
        fixture.controller.start(START, END);
        equal(fixture.observer.frames.size(), frames, "close blocks frames and restart");
        equal(fixture.time.pending(), 0, "close clears queue");
        check(fixture.source.closed, "source closed");
    }

    /** 상태 콜백에서 화면이 닫혀도 첫 프레임이나 타이머를 예약하지 않는지 확인한다. */
    private static void verifyReentrantStop() {
        FakeTime time = new FakeTime();
        FakeSource source = new FakeSource();
        Observer observer = new Observer();
        RoutePlaybackController[] controller = new RoutePlaybackController[1];
        observer.onPlaying = () -> controller[0].stop();
        controller[0] = new RoutePlaybackController(source, time, time, observer);
        controller[0].start(START, END);
        source.callbacks.get(0).onSuccess(route());
        equal(controller[0].getState(), PlaybackState.STOPPED, "listener stop preserved");
        equal(observer.frames.size(), 0, "listener stop prevents first frame");
        equal(time.pending(), 0, "listener stop prevents timer");
    }

    /** 북쪽으로 간 뒤 동쪽으로 도는 검증용 선분 경로를 만든다. */
    private static RoutePath route() {
        return new RoutePath(List.of(START, CORNER, END));
    }

    /** 참이어야 하는 독립적인 검증 조건을 기록한다. */
    private static void check(boolean passed, String description) {
        assertions++;
        if (!passed) {
            throw new AssertionError(description);
        }
    }

    /** 값이나 상태가 예상 결과와 일치하는지 확인한다. */
    private static void equal(Object actual, Object expected, String description) {
        check(expected.equals(actual), description + ": actual=" + actual + ", expected=" + expected);
    }

    /** 실제 계산값이 지정한 수치 오차 범위 안에 있는지 확인한다. */
    private static void near(double actual, double expected, double tolerance, String description) {
        check(Math.abs(actual - expected) <= tolerance,
                description + ": actual=" + actual + ", expected=" + expected);
    }

    /** 잘못된 외부 값이 정상 경로로 처리되지 않고 거부되는지 확인한다. */
    private static void rejects(Runnable action, String description) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, description);
    }

    /** 테스트마다 독립된 공급자·시간·관찰자를 구성한다. */
    private static final class Fixture {
        private final FakeTime time = new FakeTime();
        private final FakeSource source = new FakeSource();
        private final Observer observer = new Observer();
        private final RoutePlaybackController controller = new RoutePlaybackController(source, time, time, observer);

        /** 별도의 네트워크 시간 없이 정상 경로를 준비한다. */
        private void startReady() {
            controller.start(START, END);
            source.callbacks.get(source.callbacks.size() - 1).onSuccess(route());
        }

        /** 가장 최근에 실제 전달된 위치 프레임을 반환한다. */
        private PlaybackFrame last() {
            return observer.frames.get(observer.frames.size() - 1);
        }
    }

    /** 취소된 요청도 의도적으로 응답시켜 컨트롤러의 늦은 콜백 방어를 검사한다. */
    private static final class FakeSource implements RouteSource {
        private final List<Callback> callbacks = new ArrayList<>();
        private boolean closed;

        /** 응답 타이밍을 테스트가 제어하도록 요청을 보관한다. */
        @Override
        public void load(RoutePoint origin, RoutePoint destination, Callback callback) {
            callbacks.add(callback);
        }

        /** 네트워크에서 늦게 도착하는 응답을 재현하기 위해 보관된 콜백은 남긴다. */
        @Override
        public void cancel() {
        }

        /** 소유자가 공급자 종료를 호출했는지 기록한다. */
        @Override
        public void close() {
            closed = true;
        }
    }

    /** 같은 스레드에서 예약 순서와 경과 시간을 정확히 제어한다. */
    private static final class FakeTime implements PlaybackScheduler, PlaybackClock {
        private final PriorityQueue<Scheduled> tasks = new PriorityQueue<>(Comparator.comparingLong(Scheduled::due));
        private long now;

        /** 가상 단조 증가 시각을 반환한다. */
        @Override
        public long nowMillis() {
            return now;
        }

        /** 지정한 가상 시각에 한 번 실행할 작업을 보관한다. */
        @Override
        public void schedule(Runnable task, long delayMillis) {
            tasks.add(new Scheduled(task, now + delayMillis));
        }

        /** 같은 작업의 예약을 제거한다. */
        @Override
        public void cancel(Runnable task) {
            tasks.removeIf(scheduled -> scheduled.task() == task);
        }

        /** 지정한 시간이 흐르는 동안 도래한 예약 작업을 순서대로 실행한다. */
        private void advance(long elapsed) {
            long target = now + elapsed;
            while (!tasks.isEmpty() && tasks.peek().due() <= target) {
                Scheduled scheduled = tasks.remove();
                now = scheduled.due();
                scheduled.task().run();
            }
            now = target;
        }

        /** 종료와 정지 시 남아 있는 타이머 수를 반환한다. */
        private int pending() {
            return tasks.size();
        }
    }

    /** 가상 예약 시각과 실행 작업을 함께 보관한다. */
    private record Scheduled(Runnable task, long due) {
    }

    /** 실제 전달된 콜백만 기록해 표시 프레임과 실패를 검사한다. */
    private static final class Observer implements PlaybackListener {
        private final List<PlaybackFrame> frames = new ArrayList<>();
        private final List<PlaybackFailure> failures = new ArrayList<>();
        private Runnable onPlaying;

        /** 전달된 위치를 순서대로 기록한다. */
        @Override
        public void onPosition(PlaybackFrame frame) {
            frames.add(frame);
        }

        /** 재생 시작 중 화면 종료와 같은 재진입 상황을 재현한다. */
        @Override
        public void onStateChanged(PlaybackState state) {
            if (state == PlaybackState.PLAYING && onPlaying != null) {
                onPlaying.run();
            }
        }

        /** 실제로 전달된 실패만 보관한다. */
        @Override
        public void onFailure(PlaybackFailure failure) {
            failures.add(failure);
        }
    }
}
