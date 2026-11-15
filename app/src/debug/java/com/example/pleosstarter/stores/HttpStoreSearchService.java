package com.example.pleosstarter.stores;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** 원천별 작업을 병행하고 닫힌 화면이나 이전 요청의 늦은 응답을 차단한다. */
public final class HttpStoreSearchService implements StoreSearchService {
    private static final String TAG = "StoreSearch";
    private final StoreRequestCredentials configuration;
    private final Map<StoreProvider, StoreSearchClient> clients = new EnumMap<>(StoreProvider.class);
    private final Map<StoreProvider, Long> generations = new EnumMap<>(StoreProvider.class);
    private final Map<StoreProvider, Future<?>> running = new EnumMap<>(StoreProvider.class);
    private final ExecutorService workers;
    private final Executor callbacks;
    private boolean closed;

    /** 앱 전용 인증 파일과 두 HTTPS 클라이언트를 화면 스레드 콜백에 연결한다. */
    public HttpStoreSearchService(Context context) {
        this(new StoreApiConfiguration(new File(context.getFilesDir(), StoreApiConfiguration.FILE_NAME)),
                new KakaoLocalStoreClient(new HttpsStoreTransport()),
                new PublicDataStoreClient(new HttpsStoreTransport()),
                Executors.newFixedThreadPool(2), new Handler(Looper.getMainLooper())::post);
    }

    /** 제출 앱은 중계 주소만 사용하며 로컬 REST 키 파일로 되돌아가지 않는다. */
    public HttpStoreSearchService(Context context, String placesProxyUrl) {
        this(new PlacesProxyConfiguration(placesProxyUrl));
    }

    /** 중계 설정과 무인증 HTTPS 클라이언트를 기존 비동기 처리에 연결한다. */
    private HttpStoreSearchService(PlacesProxyConfiguration configuration) {
        this(configuration, new PlacesProxyStoreClient(configuration, new HttpsStoreTransport()),
                new PublicDataStoreClient(new HttpsStoreTransport()),
                Executors.newFixedThreadPool(2), new Handler(Looper.getMainLooper())::post);
    }

    /** 네트워크·작업 실행·결과 전달 경계를 주입해 핵심 흐름을 독립 검증한다. */
    HttpStoreSearchService(StoreRequestCredentials configuration, StoreSearchClient kakaoClient,
                           StoreSearchClient publicDataClient, ExecutorService workers,
                           Executor callbacks) {
        this.configuration = configuration;
        this.clients.put(StoreProvider.KAKAO_LOCAL, kakaoClient);
        this.clients.put(StoreProvider.PUBLIC_DATA, publicDataClient);
        this.workers = workers;
        this.callbacks = callbacks;
    }

    /** 새 원천 요청만 대체하며 다른 원천의 병행 요청은 유지한다. */
    @Override
    public synchronized void search(StoreProvider provider, double latitude, double longitude,
                                    StoreSearchCallback callback) {
        enqueueSearch(provider, latitude, longitude, null, callback);
    }

    /** 카카오 분류·반경 변경도 같은 원천의 이전 조회와 결과 전달을 취소한다. */
    @Override
    public synchronized void search(KakaoStoreQuery query, StoreSearchCallback callback) {
        Objects.requireNonNull(query, "query");
        enqueueSearch(StoreProvider.KAKAO_LOCAL, query.getLatitude(), query.getLongitude(), query, callback);
    }

    /** 동기화된 공개 진입점에서 검색 조건을 검증하고 해당 원천 작업만 교체한다. */
    private void enqueueSearch(StoreProvider provider, double latitude, double longitude,
                                KakaoStoreQuery query, StoreSearchCallback callback) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(callback, "callback");
        if (closed) {
            callbacks.execute(() -> callback.onFailure(new StoreSearchFailure(
                    StoreSearchError.CLOSED, "매장 조회 화면을 다시 열어 주세요.", 0)));
            return;
        }

        long generation = generations.getOrDefault(provider, 0L) + 1;
        generations.put(provider, generation);
        Future<?> previous = running.remove(provider);
        if (previous != null) {
            previous.cancel(true);
        }
        if (!validCoordinate(latitude, longitude)) {
            deliverFailure(provider, generation, callback, new StoreSearchFailure(
                    StoreSearchError.INVALID_COORDINATE, "지도에서 조회할 위치를 다시 선택해 주세요.", 0));
            return;
        }
        if (query != null && !query.hasSupportedScope()) {
            deliverFailure(provider, generation, callback, new StoreSearchFailure(
                    StoreSearchError.INVALID_QUERY, "매장 분류와 검색 범위를 다시 선택해 주세요.", 0));
            return;
        }

        running.put(provider, workers.submit(() -> executeSearch(
                provider, generation, latitude, longitude, query, callback)));
    }

    /** 인증 설정부터 호출·변환까지 처리하고 내부 예외 원문은 출력하지 않는다. */
    private void executeSearch(StoreProvider provider, long generation, double latitude,
                               double longitude, KakaoStoreQuery query, StoreSearchCallback callback) {
        try {
            String key = configuration.readKey(provider);
            StoreSearchResult result;
            if (query == null) {
                result = clients.get(provider).search(latitude, longitude, key);
            } else {
                result = clients.get(provider).search(query, key);
            }
            callbacks.execute(() -> {
                if (isCurrent(provider, generation)) {
                    Log.i(TAG, "STORE_SEARCH_SUCCESS provider=" + provider.name()
                            + " rows=" + result.getStores().size() + " total=" + result.getTotalCount()
                            + " radius=" + result.getRadiusMeters()
                            + " categories=" + result.getCategoryCounts().size()
                            + " truncated=" + result.isTruncated());
                    callback.onSuccess(result);
                }
            });
        } catch (StoreSearchException failure) {
            deliverFailure(provider, generation, callback, failure.getFailure());
        } catch (SocketTimeoutException failure) {
            deliverFailure(provider, generation, callback, new StoreSearchFailure(
                    StoreSearchError.TIMEOUT, "매장 정보 응답이 늦어지고 있어요. 다시 시도해 주세요.", 0));
        } catch (IOException failure) {
            deliverFailure(provider, generation, callback, new StoreSearchFailure(
                    StoreSearchError.NETWORK_FAILURE, "매장 정보를 연결하지 못했어요. 네트워크를 확인해 주세요.", 0));
        } catch (RuntimeException failure) {
            deliverFailure(provider, generation, callback, new StoreSearchFailure(
                    StoreSearchError.REMOTE_FAILURE, "매장 정보를 처리하지 못했어요. 다시 시도해 주세요.", 0));
        }
    }

    /** 살아 있는 최신 요청에만 안전한 실패 코드와 HTTP 상태를 전달한다. */
    private void deliverFailure(StoreProvider provider, long generation, StoreSearchCallback callback,
                                 StoreSearchFailure failure) {
        callbacks.execute(() -> {
            if (isCurrent(provider, generation)) {
                Log.w(TAG, "STORE_SEARCH_FAILURE provider=" + provider.name()
                        + " code=" + failure.getCode().name() + " http=" + failure.getHttpStatus());
                callback.onFailure(failure);
            }
        });
    }

    /** 다른 요청 또는 닫힌 화면에 도착한 결과를 제외한다. */
    private synchronized boolean isCurrent(StoreProvider provider, long generation) {
        return !closed && generations.getOrDefault(provider, 0L) == generation;
    }

    /** 비유한 수와 WGS84 범위 밖 입력을 외부 서비스로 보내지 않는다. */
    private boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && Double.isFinite(longitude)
                && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    /** 진행 중인 작업을 중단하고 이미 대기열에 든 콜백도 무효화한다. */
    @Override
    public synchronized void cancel() {
        for (StoreProvider provider : StoreProvider.values()) {
            generations.put(provider, generations.getOrDefault(provider, 0L) + 1);
        }
        for (Future<?> future : running.values()) {
            future.cancel(true);
        }
        running.clear();
    }

    /** 화면 종료 후 요청이 재사용되지 않도록 작업 자원을 해제한다. */
    @Override
    public synchronized void close() {
        closed = true;
        cancel();
        workers.shutdownNow();
    }
}
