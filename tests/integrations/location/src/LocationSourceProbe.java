import android.content.Context;
import android.os.Handler;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import com.example.pleosstarter.location.CurrentLocationListener;
import com.example.pleosstarter.location.CurrentPosition;
import com.example.pleosstarter.location.LocationError;
import com.example.pleosstarter.location.LocationFailure;
import com.example.pleosstarter.location.PleosCurrentLocationSource;
import java.util.ArrayList;
import java.util.List;

/** 위치 공급자의 좌표 검증·시간 제한·생명주기 경계를 SDK 대역으로 실행한다. */
public final class LocationSourceProbe {
    private static int assertions;

    /** 독립 시나리오를 모두 실행하고 검증한 단언 수를 출력한다. */
    public static void main(String[] args) {
        verifyCoordinates();
        verifyPolling();
        verifyRequestTimeout();
        verifyConnectionTimeout();
        verifyStopAndClose();
        verifyReentrantStop();
        verifyPermissions();
        verifyInvalidResponses();
        System.out.println("PASS location source assertions=" + assertions);
    }

    /** 유한값·범위·영점 좌표와 수신 시각, 환경 구분을 확인한다. */
    private static void verifyCoordinates() {
        double[][] invalid = {{0, 0}, {Double.NaN, 127}, {37, Double.POSITIVE_INFINITY},
                {90.1, 1}, {-90.1, 1}, {1, 180.1}, {1, -180.1}};
        for (double[] point : invalid) {
            boolean rejected = false;
            try {
                new CurrentPosition(point[0], point[1], 0, true);
            } catch (IllegalArgumentException expected) {
                rejected = true;
            }
            check(rejected, "invalid coordinate rejected");
        }
        CurrentPosition position = new CurrentPosition(90, -180, 42L, true);
        check(position.getLatitude() == 90 && position.getLongitude() == -180, "boundary retained");
        check(position.getReceivedElapsedRealtimeMillis() == 42L, "receipt time retained");
        check(position.isEmulatedVehicle() && !position.hasFixTimestamp(), "no fake GPS fix time");
        check(position.getSourceLabel().contains("에뮬레이터"), "emulator label");
        check(!new CurrentPosition(37, 127, 0, false).isEmulatedVehicle(), "real environment label");
    }

    /** 응답을 기다리는 동안 중복 요청하지 않고 완료 후 2초에만 재조회하는지 확인한다. */
    private static void verifyPolling() {
        reset();
        Recorder recorder = new Recorder();
        PleosCurrentLocationSource source = start(recorder);
        NaviHelper helper = connected();
        Handler.advance(3000L);
        check(helper.requests == 1, "no overlapping poll");
        helper.respond(new CurrentLocationInfo(37.5, 127.1));
        Handler.advance(0L);
        check(recorder.positions.size() == 1, "response delivered");
        check(recorder.positions.get(0).getReceivedElapsedRealtimeMillis() == 3000L, "actual receipt timestamp");
        helper.respond(new CurrentLocationInfo(37.6, 127.2));
        Handler.advance(1999L);
        check(recorder.positions.size() == 1 && helper.requests == 1, "unsolicited response ignored");
        Handler.advance(1L);
        check(helper.requests == 2, "next poll at 2 seconds");
        source.close();
    }

    /** 타임아웃 이후 이전 연결의 응답이 새 조회를 완료하지 않는지 확인한다. */
    private static void verifyRequestTimeout() {
        reset();
        Recorder recorder = new Recorder();
        PleosCurrentLocationSource source = start(recorder);
        NaviHelper old = connected();
        Handler.advance(5000L);
        check(recorder.failures.get(0).getCode() == LocationError.REQUEST_TIMEOUT, "request timeout typed");
        check(old.releases == 1, "timed out connection released");
        Handler.advance(2000L);
        NaviHelper current = connected();
        old.respond(new CurrentLocationInfo(1, 1));
        Handler.advance(0L);
        check(recorder.positions.isEmpty(), "late old response discarded");
        current.respond(new CurrentLocationInfo(37, 127));
        Handler.advance(0L);
        check(recorder.positions.size() == 1, "replacement response delivered");
        source.close();
    }

    /** 연결이 응답하지 않아도 제한 시간 후 실패하며 늦은 초기화는 무시하는지 확인한다. */
    private static void verifyConnectionTimeout() {
        reset();
        Recorder recorder = new Recorder();
        PleosCurrentLocationSource source = start(recorder);
        NaviHelper old = NaviHelper.instances.get(0);
        Handler.advance(8000L);
        check(recorder.failures.get(0).getCode() == LocationError.CONNECTION_TIMEOUT, "connection timeout typed");
        old.connectSuccessfully();
        Handler.advance(0L);
        check(old.requests == 0, "late connection cannot request");
        source.close();
    }

    /** 중단·재시작·영구 종료 후 대기 중 콜백과 타이머가 전달되지 않는지 확인한다. */
    private static void verifyStopAndClose() {
        reset();
        Recorder recorder = new Recorder();
        PleosCurrentLocationSource source = start(recorder);
        NaviHelper old = connected();
        old.respond(new CurrentLocationInfo(37, 127));
        source.stop();
        Handler.advance(10000L);
        check(recorder.positions.isEmpty() && recorder.failures.isEmpty(), "stop cancels queued results");
        check(NaviHelper.instances.size() == 1, "stop cancels reconnect");
        source.start(recorder);
        NaviHelper current = connected();
        source.close();
        current.respond(new CurrentLocationInfo(37, 127));
        Handler.advance(10000L);
        check(recorder.positions.isEmpty(), "close rejects late result");
        boolean rejected = false;
        try {
            source.start(recorder);
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        check(rejected, "closed source cannot restart");
    }

    /** 수신자가 결과 처리 중 중단하더라도 다음 폴링이 예약되지 않는지 확인한다. */
    private static void verifyReentrantStop() {
        reset();
        PleosCurrentLocationSource source = new PleosCurrentLocationSource(new Context(), true);
        source.start(new CurrentLocationListener() {
            /** 성공 콜백 안에서 화면 중단을 재현한다. */
            @Override
            public void onLocation(CurrentPosition position) {
                source.stop();
            }

            /** 실패 콜백 안에서 화면 중단을 재현한다. */
            @Override
            public void onUnavailable(LocationFailure failure) {
                source.stop();
            }
        });
        NaviHelper helper = connected();
        helper.respond(new CurrentLocationInfo(37, 127));
        Handler.advance(10000L);
        check(helper.requests == 1 && NaviHelper.instances.size() == 1, "reentrant stop prevents poll");
        source.close();
    }

    /** 권한 오류를 구분하며 승인되지 않은 호출을 자동 반복하지 않는지 확인한다. */
    private static void verifyPermissions() {
        reset();
        Recorder recorder = new Recorder();
        NaviHelper.nextRequestFailure = new SecurityException("private SDK detail");
        PleosCurrentLocationSource source = start(recorder);
        connected();
        Handler.advance(10000L);
        check(recorder.failures.get(0).getCode() == LocationError.PERMISSION_DENIED, "permission typed");
        check(!recorder.failures.get(0).getMessage().contains("private"), "internal detail hidden");
        check(NaviHelper.instances.size() == 1, "permission denied does not retry");
        source.close();
    }

    /** 빈 응답과 잘못된 좌표를 성공 또는 대체 위치로 표시하지 않는지 확인한다. */
    private static void verifyInvalidResponses() {
        CurrentLocationInfo[] responses = {null, new CurrentLocationInfo(0, 0),
                new CurrentLocationInfo(Double.NaN, 127)};
        for (CurrentLocationInfo response : responses) {
            reset();
            Recorder recorder = new Recorder();
            PleosCurrentLocationSource source = start(recorder);
            connected().respond(response);
            Handler.advance(0L);
            check(recorder.positions.isEmpty(), "invalid response never becomes location");
            check(recorder.failures.get(0).getCode() == LocationError.INVALID_COORDINATE, "invalid response typed");
            source.close();
        }
    }

    /** 각 시나리오의 위치 공급자를 시작한다. */
    private static PleosCurrentLocationSource start(Recorder recorder) {
        PleosCurrentLocationSource source = new PleosCurrentLocationSource(new Context(), true);
        source.start(recorder);
        return source;
    }

    /** 가장 최근 모의 SDK 연결을 성공시킨다. */
    private static NaviHelper connected() {
        NaviHelper helper = NaviHelper.instances.get(NaviHelper.instances.size() - 1);
        helper.connectSuccessfully();
        Handler.advance(0L);
        return helper;
    }

    /** 모의 시간과 연결 상태를 초기화한다. */
    private static void reset() {
        Handler.reset();
        NaviHelper.reset();
    }

    /** 기대 조건을 만족하지 않으면 시험을 즉시 실패시킨다. */
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    /** 외부 수신자가 받은 성공·실패 결과를 보관한다. */
    private static final class Recorder implements CurrentLocationListener {
        private final List<CurrentPosition> positions = new ArrayList<>();
        private final List<LocationFailure> failures = new ArrayList<>();

        /** 성공 위치를 기록한다. */
        @Override
        public void onLocation(CurrentPosition position) {
            positions.add(position);
        }

        /** typed 실패를 기록한다. */
        @Override
        public void onUnavailable(LocationFailure failure) {
            failures.add(failure);
        }
    }
}
