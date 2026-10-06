package org.investpro.exchange.binanceus;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.PollingExchangeStreamer;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BinanceUsSharedStateTest {
    private static final String ORDER = """
        {"orderId":7,"symbol":"BTCUSDT","status":"NEW","side":"BUY","type":"LIMIT",
         "price":"100","origQty":"2","executedQty":"0","time":1,"updateTime":1,"timeInForce":"GTC"}
        """;
    static class TestExchange extends BinanceUs {
        final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();
        volatile boolean rejectSync;
        volatile Runnable onOpenOrders = () -> {};
        volatile String historicalFills = "[]";
        TestExchange() {
            super(new ExchangeCredentials("binanceus", "test-key", "test-secret", null, null, null, null,
                    false, Map.of("tradingMode", "LIVE")));
        }
        @Override protected void connectPrivateSocket() { privateSubscriptionReady(); }
        @Override protected HttpResponse<String> executeHttpRequest(HttpRequest request) {
            String path = request.uri().getPath(); calls.computeIfAbsent(path, _ -> new AtomicInteger()).incrementAndGet();
            if (path.equals("/api/v3/myTrades")) return BinanceUsCooldownTest.response(200, Map.of(), historicalFills);
            if (path.equals("/api/v3/openOrders")) {
                onOpenOrders.run();
                if (rejectSync) return BinanceUsCooldownTest.response(429, Map.of("Retry-After", List.of("60")), "{\"code\":-1003}");
                return BinanceUsCooldownTest.response(200, Map.of(), "[" + ORDER + "]");
            }
            String body = path.equals("/api/v3/time") ? "{\"serverTime\":" + System.currentTimeMillis() + "}" :
                    "{\"balances\":[{\"asset\":\"USDT\",\"free\":\"100\",\"locked\":\"1\"}],\"updateTime\":1}";
            return BinanceUsCooldownTest.response(200, Map.of(), body);
        }
        int calls(String path) { return calls.getOrDefault(path, new AtomicInteger()).get(); }
    }

    @Test void startupIsSingleFlightAndAgentReadsNeverPollRest() throws Exception {
        var exchange = new TestExchange();
        try {
            try (var executor = Executors.newFixedThreadPool(16)) {
                List<Future<?>> startups = new ArrayList<>();
                for (int i = 0; i < 40; i++) startups.add(executor.submit(() -> exchange.fetchAccount().join()));
                for (var task : startups) task.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, exchange.calls("/api/v3/openOrders")); assertEquals(1, exchange.calls("/api/v3/account"));
            for (int i = 0; i < 100; i++) {
                assertEquals(1, exchange.fetchOpenOrders(null).join().size());
                assertEquals(100.0, exchange.fetchAvailableBalance("USDT").join());
                assertEquals(101.0, exchange.fetchTotalBalance("USDT").join());
                assertTrue(exchange.fetchOrder("7").join().isPresent());
            }
            exchange.streamAccount(new UiExchangeStreamConsumer()); exchange.streamOrders(new UiExchangeStreamConsumer());
            assertEquals(1, exchange.calls("/api/v3/openOrders")); assertEquals(0, exchange.calls("/api/v3/order"));
            assertEquals("LIVE", exchange.getAccountCacheHealth()); assertNotNull(exchange.getLastSuccessfulAccountSync());
            var orders = exchange.getCachedOpenOrders(null); orders.getFirst().setFilledSize(100);
            assertEquals(0.0, exchange.getCachedOpenOrders(null).getFirst().getFilledSize());
        } finally { exchange.disconnect(); }
    }

    @Test void streamFillIsDeliveredOnceAndOrderLifecycleMapsBinanceNames() throws Exception {
        var exchange = new TestExchange();
        try {
            exchange.fetchAccount().get(10, TimeUnit.SECONDS);
            var consumer = mock(org.investpro.exchange.infrastructure.ExchangeStreamConsumer.class);
            exchange.streamFills(consumer); exchange.streamOrders(consumer);
            var filled = BinanceUsAccountStateTest.event("FILLED", 2, "TRADE");
            exchange.acceptAccountEvent(filled); exchange.acceptAccountEvent(filled);
            assertTrue(exchange.fetchAllOpenOrders().join().isEmpty());
            assertEquals(1, exchange.fetchAccountTrades(null).join().size());
            verify(consumer, times(1)).onFill(anyString(), any(), any());
            assertEquals(1, exchange.calls("/api/v3/openOrders"));
        } finally { exchange.disconnect(); }
    }

    @Test void reconnectReconciliationMergesRacingFill() throws Exception {
        var exchange = new TestExchange();
        try {
            exchange.fetchAccount().get(10, TimeUnit.SECONDS);
            exchange.onOpenOrders = () -> {
                try { exchange.acceptAccountEvent(BinanceUsAccountStateTest.event("FILLED", 3, "TRADE")); }
                catch (Exception exception) { throw new CompletionException(exception); }
            };
            exchange.synchronizeAccountState().get(10, TimeUnit.SECONDS);
            assertTrue(exchange.fetchAllOpenOrders().join().isEmpty());
            assertEquals(2, exchange.calls("/api/v3/openOrders"));
            assertEquals(1, exchange.fetchAccountTrades(null).join().size());
        } finally { exchange.disconnect(); }
    }

    @Test void throttledReconciliationRetainsLiveBalancesAndOrdersAsStale() throws Exception {
        var exchange = new TestExchange();
        try {
            exchange.fetchAccount().get(10, TimeUnit.SECONDS); var last = exchange.getLastSuccessfulAccountSync();
            exchange.rejectSync = true;
            var failure = assertThrows(ExecutionException.class, () -> exchange.synchronizeAccountState().get(10, TimeUnit.SECONDS));
            assertInstanceOf(BinanceUsRequestBudget.RateLimitedException.class, failure.getCause());
            assertEquals("STALE", exchange.getAccountCacheHealth()); assertEquals(last, exchange.getLastSuccessfulAccountSync());
            assertEquals(1, exchange.fetchOpenOrders(null).join().size());
            assertFalse(exchange.fetchAccount().join().isPaperTrading()); assertEquals(100.0, exchange.fetchAvailableBalance("USDT").join());
        } finally { exchange.disconnect(); }
    }

    @Test void identicalPublicSnapshotsAreCoalesced() throws Exception {
        var cache = new BinanceUsRestCache(); var calls = new AtomicInteger();
        var request = HttpRequest.newBuilder(URI.create("https://api.binance.us/api/v3/exchangeInfo")).GET().build();
        try (var executor = Executors.newFixedThreadPool(10)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) tasks.add(executor.submit(() -> {
                try { cache.read(request, _ -> { calls.incrementAndGet(); return BinanceUsCooldownTest.response(200, Map.of(), "{}"); }); }
                catch (Exception exception) { throw new CompletionException(exception); }
            }));
            for (var task : tasks) task.get(3, TimeUnit.SECONDS);
        }
        assertEquals(1, calls.get());
    }

    @Test void reconnectRecoversMissedFillsAndDeduplicatesStreamedExecutions() throws Exception {
        var exchange = new TestExchange();
        try {
            exchange.fetchAccount().get(10, TimeUnit.SECONDS);
            exchange.acceptAccountEvent(BinanceUsAccountStateTest.event("PARTIALLY_FILLED", 2, "TRADE"));
            exchange.historicalFills = """
                [{"symbol":"BTCUSDT","orderId":7,"id":12,"price":"100","qty":"1","commission":"0.1","time":2,"isBuyer":true},
                 {"symbol":"BTCUSDT","orderId":7,"id":13,"price":"100","qty":"1","commission":"0.1","time":3,"isBuyer":true}]
                """;
            exchange.privateSubscriptionReady();
            exchange.synchronizeAccountState().get(10, TimeUnit.SECONDS);
            assertEquals(2, exchange.fetchAccountTrades(null).join().size());
            assertEquals(1, exchange.calls("/api/v3/myTrades"));
            assertEquals(2, exchange.calls("/api/v3/openOrders"));
            assertEquals("LIVE", exchange.getAccountCacheHealth());
        } finally { exchange.disconnect(); }
    }

    @Test void privatePollingFallbackDelegatesWithoutCreatingTimers() {
        var exchange = mock(BinanceUs.class); var consumer = new UiExchangeStreamConsumer();
        var streamer = new PollingExchangeStreamer(exchange);
        streamer.streamAccount(consumer); streamer.streamOrders(consumer); streamer.streamBalances(consumer); streamer.streamFills(consumer);
        verify(exchange).streamAccount(consumer); verify(exchange).streamOrders(consumer);
        verify(exchange).streamBalances(consumer); verify(exchange).streamFills(consumer);
        verify(exchange, never()).fetchAccount(); verify(exchange, never()).fetchAllOpenOrders();
        streamer.stopAll();
    }

    @Test void sharedMarketSocketRoutesByStreamInsteadOfFeedingEverySymbol() throws Exception {
        var exchange = new TestExchange();
        try {
            String wrapped = "{\"stream\":\"btcusdt@depth20@100ms\",\"data\":{\"bids\":[[\"100\",\"1\"]],\"asks\":[]}}";
            assertNotNull(exchange.marketPayload(wrapped, "btcusdt@depth20@100ms"));
            assertNull(exchange.marketPayload(wrapped, "ethusdt@depth20@100ms"));
            assertNull(exchange.marketPayload(wrapped, "btcusdt@ticker"));
            assertNull(exchange.marketPayload("{\"bids\":[]}", "btcusdt@depth20@100ms"));
            String ticker = "{\"e\":\"24hrTicker\",\"s\":\"BTCUSDT\",\"c\":\"100\"}";
            assertNotNull(exchange.marketPayload(ticker, "btcusdt@ticker"));
            assertNull(exchange.marketPayload(ticker, "ethusdt@ticker"));
            assertNull(exchange.marketPayload(ticker, "btcusdt@trade"));
        } finally { exchange.disconnect(); }
    }

    @Test void failedMetadataRefreshRetainsStaleSnapshotButQuotesDoNot() throws Exception {
        var now = new java.util.concurrent.atomic.AtomicLong(1);
        var cache = new BinanceUsRestCache(now::get);
        var metadata = HttpRequest.newBuilder(URI.create("https://api.binance.us/api/v3/exchangeInfo")).GET().build();
        var valid = BinanceUsCooldownTest.response(200, Map.of(), "{\"symbols\":[]}");
        assertSame(valid, cache.read(metadata, _ -> valid));
        now.set(600002);
        assertSame(valid, cache.read(metadata, _ -> { throw new BinanceUsRequestBudget.RateLimitedException(700000, "cooldown"); }));
        assertEquals(1, cache.staleCount());
        var quote = HttpRequest.newBuilder(URI.create("https://api.binance.us/api/v3/ticker/price?symbol=BTCUSDT")).GET().build();
        cache.read(quote, _ -> valid); now.addAndGet(1001);
        assertThrows(BinanceUsRequestBudget.RateLimitedException.class,
                () -> cache.read(quote, _ -> { throw new BinanceUsRequestBudget.RateLimitedException(700000, "cooldown"); }));
    }

    @Test void executorRejectionRetainsSnapshotAndMarksStateStale() throws Exception {
        var exchange = new TestExchange();
        try {
            exchange.fetchAccount().get(10, TimeUnit.SECONDS);
            exchange.getRestExecutor().shutdownNow();
            assertThrows(ExecutionException.class, () -> exchange.synchronizeAccountState().get(3, TimeUnit.SECONDS));
            assertEquals("STALE", exchange.getAccountCacheHealth());
            assertEquals(1, exchange.fetchAllOpenOrders().join().size());
        } finally { exchange.disconnect(); }
    }
}
