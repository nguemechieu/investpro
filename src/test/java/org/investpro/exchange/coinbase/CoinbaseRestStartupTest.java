package org.investpro.exchange.coinbase;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinbaseRestStartupTest {
    @Test void cancellingCandleFetchCancelsQueuedHttpRequest() throws Exception {
        var pending = new CompletableFuture<String>();
        var supplier = new CoinbaseCandleDataSupplier(60, new TradePair("BTC", "USD"), _ -> pending);
        assertTrue(supplier.get().cancel(true));
        assertTrue(pending.isCancelled());
    }
    @Test void fortyStartupRequestsQueueWithoutExceedingTwoHttpRequests() throws Exception {
        Coinbase exchange = exchange();
        HttpClient client = mock(HttpClient.class);
        field(exchange, "httpClient", client);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(_ -> {
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            try { Thread.sleep(200); return response(200, "{}"); }
            finally { active.decrementAndGet(); }
        });
        List<CompletableFuture<String>> requests = new ArrayList<>();
        for (int index = 0; index < 40; index++) requests.add(sendAsync(exchange, "ASSET" + index));
        CompletableFuture.allOf(requests.toArray(CompletableFuture[]::new)).get(20, TimeUnit.SECONDS);
        assertEquals(2, peak.get());
        assertTrue(requests.stream().allMatch(request -> "{}".equals(request.join())));
        verify(client, times(40)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(client, never()).sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test void simultaneousNetworkRetriesReleasePermitsBeforeRetrying() throws Exception {
        Coinbase exchange = exchange();
        HttpClient client = mock(HttpClient.class);
        field(exchange, "httpClient", client);
        var attempts = new ConcurrentHashMap<String, AtomicInteger>();
        CountDownLatch firstAttempts = new CountDownLatch(2);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            if (attempts.computeIfAbsent(request.uri().getPath(), _ -> new AtomicInteger()).incrementAndGet() == 1) {
                firstAttempts.countDown();
                assertTrue(firstAttempts.await(3, TimeUnit.SECONDS));
                throw new IOException("temporary connection reset");
            }
            return response(200, "{}");
        });
        var first = sendAsync(exchange, "FIRST");
        var second = sendAsync(exchange, "SECOND");
        CompletableFuture.allOf(first, second).get(5, TimeUnit.SECONDS);
        assertEquals("{}", first.join());
        assertEquals("{}", second.join());
        assertEquals(2, permits(exchange));
    }

    @Test void rateLimitedProductDefersWithoutBlockingUnrelatedProduct() throws Exception {
        Coinbase exchange = exchange();
        HttpClient client = mock(HttpClient.class);
        field(exchange, "httpClient", client);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            return response(request.uri().getPath().contains("LIMITED") ? 429 : 200, "{}");
        });
        var limited = sendAsync(exchange, "LIMITED");
        var error = assertThrows(ExecutionException.class, () -> limited.get(3, TimeUnit.SECONDS));
        assertInstanceOf(CoinbaseRestRateLimiter.RateLimitBlockedException.class, error.getCause());
        assertEquals("{}", sendAsync(exchange, "OTHER").get(3, TimeUnit.SECONDS));
        assertEquals(2, permits(exchange));
    }

    @Test void interruptedPacingReturnsItsPermit() throws Exception {
        var limiter = new CoinbaseRestRateLimiter();
        field(limiter, "nextAllowedAtMs", System.currentTimeMillis() + 30_000);
        var failure = new CompletableFuture<Throwable>();
        Thread worker = new Thread(() -> {
            try { limiter.acquirePermit("/candles", "BTC-USD"); }
            catch (Throwable error) { failure.complete(error); }
        });
        worker.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (worker.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.onSpinWait();
        worker.interrupt(); worker.join(2_000);
        assertFalse(worker.isAlive());
        assertInstanceOf(RuntimeException.class, failure.get(1, TimeUnit.SECONDS));
        assertEquals(2, ((Semaphore) value(limiter, "permits")).availablePermits());
    }

    @Test void candleSupplierUsesAdapterTransportAndExactContractSymbol() throws Exception {
        var paths = new CopyOnWriteArrayList<String>();
        long opened = Instant.now().minusSeconds(600).getEpochSecond();
        var supplier = new CoinbaseCandleDataSupplier(60, TradePair.fromSymbol("BIP-20DEC30-CDE"), request -> {
            paths.add(request.uri().getPath());
            return CompletableFuture.completedFuture("{\"candles\":[{\"start\":\"" + opened
                    + "\",\"open\":\"1\",\"close\":\"2\",\"high\":\"2\",\"low\":\"1\",\"volume\":\"3\"}]}");
        });
        assertEquals(1, supplier.get().get(1, TimeUnit.SECONDS).size());
        assertEquals("/api/v3/brokerage/market/products/BIP-20DEC30-CDE/candles", paths.getFirst());
        assertEquals(1, supplier.getCandleDataSupplier(60, TradePair.fromSymbol("BIP-20DEC30-CDE"))
                .get().get(1, TimeUnit.SECONDS).size());
        assertEquals(2, paths.size());
    }

    private Coinbase exchange() throws Exception {
        return new Coinbase(new ExchangeCredentials("coinbase", "", "", "", "", "", "", false));
    }

    @SuppressWarnings("unchecked")
    private CompletableFuture<String> sendAsync(Coinbase exchange, String symbol) throws Exception {
        var method = Coinbase.class.getDeclaredMethod("sendAsync", HttpRequest.class);
        method.setAccessible(true);
        return (CompletableFuture<String>) method.invoke(exchange, HttpRequest.newBuilder(URI.create(
                "https://api.coinbase.com/api/v3/brokerage/market/products/" + symbol + "/ticker")).GET().build());
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<byte[]> response(int status, String body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (_, _) -> true));
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        return response;
    }

    private int permits(Coinbase exchange) throws Exception {
        return ((Semaphore) value(value(exchange, "marketRestLimiter"), "permits")).availablePermits();
    }
    private Object value(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); return field.get(target);
    }
    private void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
}
