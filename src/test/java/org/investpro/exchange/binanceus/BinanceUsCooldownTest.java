package org.investpro.exchange.binanceus;

import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BinanceUsCooldownTest {
    static HttpResponse<String> response(int status, Map<String, List<String>> headers, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (_, _) -> true));
        when(response.body()).thenReturn(body);
        return response;
    }
    @Test void cooldownIsGlobalAndHonorsRetryAfter() throws Exception {
        var budget = new BinanceUsRequestBudget();
        long before = System.currentTimeMillis();
        var exception = assertThrows(BinanceUsRequestBudget.RateLimitedException.class,
                () -> budget.observe(response(429, Map.of("Retry-After", List.of("60")), "{}")));
        assertTrue(exception.retryAt() >= before + 60000);
        assertThrows(BinanceUsRequestBudget.RateLimitedException.class, budget::checkCooldown);
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<Boolean>> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) tasks.add(executor.submit(() -> {
                assertThrows(BinanceUsRequestBudget.RateLimitedException.class, () -> budget.acquire(3));
                return true;
            }));
            for (var task : tasks) assertTrue(task.get(3, TimeUnit.SECONDS));
        }
    }
    @Test void noBurstAfterCooldown() throws Exception {
        var budget = new BinanceUsRequestBudget();
        assertThrows(BinanceUsRequestBudget.RateLimitedException.class,
                () -> budget.observe(response(429, Map.of("Retry-After", List.of("0")), "{}")));
        Thread.sleep(Math.max(0, budget.cooldownUntil() - System.currentTimeMillis()) + 10);
        var arrivals = new CopyOnWriteArrayList<Long>();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> {
                try { budget.acquire(1); arrivals.add(System.nanoTime()); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }));
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
        }
        var sorted = arrivals.stream().sorted().toList();
        for (int i = 1; i < sorted.size(); i++) assertTrue(sorted.get(i) - sorted.get(i - 1) > TimeUnit.MILLISECONDS.toNanos(30));
    }
    @Test void headerUsageBlocksFurtherReservationsAtSafetyBudget() throws Exception {
        var budget = new BinanceUsRequestBudget();
        budget.observe(response(200, Map.of("X-MBX-USED-WEIGHT-1M", List.of("4800")), "{}"));
        try (var executor = Executors.newSingleThreadExecutor()) {
            Future<?> task = executor.submit(() -> {
                try { budget.acquire(20); } catch (Exception exception) { throw new CompletionException(exception); }
            });
            assertThrows(TimeoutException.class, () -> task.get(50, TimeUnit.MILLISECONDS));
            task.cancel(true);
        }
    }
    @Test void endpointWeightsDistinguishAccountAndSymbolRequests() {
        assertEquals(40, weight("openOrders")); assertEquals(3, weight("openOrders?symbol=BTCUSDT"));
        assertEquals(20, weight("account")); assertEquals(20, weight("exchangeInfo"));
        assertEquals(10, weight("allOrders?symbol=BTCUSDT"));
        assertEquals(5, weight("myTrades?symbol=BTCUSDT&orderId=1"));
        assertEquals(25, weight("trades?symbol=BTCUSDT"));
        assertEquals(5, weight("depth?symbol=BTCUSDT&limit=20"));
        assertEquals(25, weight("depth?limit=500")); assertEquals(50, weight("depth?limit=1000"));
        assertEquals(250, weight("depth?limit=5000")); assertEquals(2, weight("klines?symbol=BTCUSDT"));
    }
    private static int weight(String endpoint) {
        return BinanceUsRequestBudget.weight(HttpRequest.newBuilder(URI.create("https://api.binance.us/api/v3/" + endpoint)).GET().build());
    }
    @Test void banDeadlineInBodyIsHonored() {
        var budget = new BinanceUsRequestBudget(); long until = System.currentTimeMillis() + 300000;
        assertThrows(BinanceUsRequestBudget.RateLimitedException.class,
                () -> budget.observe(response(418, Map.of(), "{\"msg\":\"IP banned until " + until + ".\"}")));
        assertTrue(budget.cooldownUntil() >= until);
    }
}
