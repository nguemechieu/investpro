package org.investpro.exchange.alpaca;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
import org.investpro.utils.MARKET_TYPES;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlpacaTest {
    static class TestExchange extends Alpaca {
        volatile int accountStatus = 200;
        volatile int dataStatus = 200;
        volatile int assetsStatus = 200;
        volatile String account = "{\"id\":\"test-account\",\"status\":\"ACTIVE\",\"cash\":\"5000\",\"equity\":\"5500\",\"buying_power\":\"10000\"}";
        final AtomicInteger assetCalls = new AtomicInteger();
        final List<HttpRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
        TestExchange() {
            super(new ExchangeCredentials("alpaca", "test-key", "test-secret", null, null, null, null,
                    false, Map.of("tradingMode", "LIVE")));
        }
        @Override protected HttpResponse<String> executeHttpRequest(HttpRequest request) {
            requests.add(request);
            String path = request.uri().getPath();
            int status = path.equals("/v2/account") ? accountStatus
                    : path.equals("/v2/assets") ? assetsStatus
                    : request.uri().getHost().equals("data.alpaca.markets") ? dataStatus : 200;
            String body = switch (path) {
                case "/v2/account" -> account;
                case "/v2/assets" -> {
                    assetCalls.incrementAndGet();
                    yield "[{\"symbol\":\"AAPL\",\"class\":\"us_equity\",\"status\":\"active\",\"tradable\":true,\"fractionable\":true}]";
                }
                case "/v2/orders" -> request.method().equals("POST") ? "{\"id\":\"order-1\"}" : "[]";
                case "/v2/positions" -> "[{\"symbol\":\"AAPL\",\"asset_id\":\"asset-1\",\"side\":\"long\",\"qty\":\"2\",\"avg_entry_price\":\"100\",\"current_price\":\"110\",\"unrealized_pl\":\"20\"}]";
                case "/v2/stocks/AAPL/snapshot" -> "{\"latestTrade\":{\"p\":110,\"t\":\"2026-10-08T12:00:00Z\"},\"latestQuote\":{\"bp\":109,\"ap\":111},\"dailyBar\":{\"o\":100,\"h\":112,\"l\":99,\"v\":1000}}";
                case "/v2/stocks/AAPL/bars" -> "{\"bars\":[{\"t\":\"2026-10-08T12:00:00Z\",\"o\":100,\"c\":110,\"h\":112,\"l\":99,\"v\":1000}]}";
                default -> "{}";
            };
            @SuppressWarnings("unchecked") HttpResponse<String> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(status);
            when(response.body()).thenReturn(body);
            return response;
        }
    }

    @Test void authenticationActuallyContactsLiveAccountAndSetsConnectionState() {
        var exchange = new TestExchange();
        assertFalse(exchange.isConnected());
        assertTrue(exchange.checkAuthentication().isSuccess());
        assertTrue(exchange.isConnected());
        var request = exchange.requests.getFirst();
        assertEquals("api.alpaca.markets", request.uri().getHost());
        assertEquals("test-key", request.headers().firstValue("APCA-API-KEY-ID").orElseThrow());
        assertEquals("test-secret", request.headers().firstValue("APCA-API-SECRET-KEY").orElseThrow());
        assertTrue(request.timeout().isPresent());
        exchange.disconnect();
        assertFalse(exchange.isConnected());
    }

    @Test void rejectedCredentialsAndRateLimitsAreDistinguished() {
        var exchange = new TestExchange();
        exchange.accountStatus = 401;
        var rejected = exchange.checkAuthentication();
        assertFalse(rejected.isSuccess());
        assertTrue(rejected.isCredentialIssue());
        assertFalse(exchange.isConnected());
        exchange.accountStatus = 429;
        assertFalse(exchange.checkAuthentication().isCredentialIssue());
    }

    @Test void marketsAreDiscoveredAndBulkTradabilitySharesAssetMetadata() throws Exception {
        var exchange = new TestExchange();
        exchange.connect();
        var pairs = exchange.getTradePairSymbol();
        assertEquals("AAPL", pairs.getFirst().getNativeSymbol());
        assertTrue(exchange.fetchTradabilityStatus(pairs).join().getFirst().isFullyTradable());
        assertEquals(1, exchange.getTradablePairs().size());
        assertEquals(1, exchange.assetCalls.get());
        assertTrue(exchange.supportsMarketType(MARKET_TYPES.STOCKS));
        assertFalse(exchange.supportsMarketType(MARKET_TYPES.FUTURES));
    }

    @Test void connectedButBlockedAccountCannotSubmitOrders() throws Exception {
        var exchange = new TestExchange();
        exchange.account = "{\"status\":\"ACTIVE\",\"trading_blocked\":true}";
        exchange.connect();
        var pair = new TradePair("AAPL", "USD");
        var result = exchange.fetchTradabilityStatus(pair).join();
        assertTrue(result.canBeDisplayedInMarketWatch());
        assertFalse(result.orderSubmissionAllowed());
        assertFalse(exchange.canSubmitLiveOrders());
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createMarketOrder(pair, Side.BUY, 1).join());
        assertTrue(exchange.requests.stream().noneMatch(request -> request.method().equals("POST")));
    }

    @Test void genericMarketAndLimitOrdersUseRealEndpointAndNeverReturnNull() throws Exception {
        var exchange = new TestExchange();
        exchange.connect();
        var order = mock(Order.class);
        when(order.getSymbol()).thenReturn("AAPL/USD");
        when(order.getSide()).thenReturn(Side.BUY);
        when(order.getQuantity()).thenReturn(0.5);
        when(order.getType()).thenReturn("MARKET");
        assertEquals("order-1", exchange.createOrder(order).join());
        when(order.getType()).thenReturn("LIMIT");
        when(order.getPrice()).thenReturn(100.0);
        assertEquals("order-1", exchange.createOrder(order).join());
        when(order.getType()).thenReturn("STOP");
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createOrder(order).join());
        assertEquals(2, exchange.requests.stream().filter(request -> request.method().equals("POST")).count());
    }

    @Test void accountBalancesAndPositionsAreMappedFromBrokerValues() {
        var exchange = new TestExchange();
        assertEquals(5500.0, exchange.fetchEquity().join());
        assertEquals(10000.0, exchange.fetchAvailableBalance("USD").join());
        assertEquals(5500.0, exchange.fetchTotalBalance("USD").join());
        var position = exchange.fetchAllPositions().join().getFirst();
        assertEquals(2, position.getQuantity());
        assertEquals(20, position.getUnrealizedPnl());
        assertEquals("asset-1", position.getPositionId());
    }

    @Test void chartsAndPricesUseDataHostAndConfiguredFeed() throws Exception {
        var exchange = new TestExchange();
        var pair = new TradePair("AAPL", "USD");
        assertEquals(110, exchange.fetchTicker(pair).join().getLastPrice());
        var candles = exchange.getCandleDataSupplier(60, pair).get().get();
        assertEquals(110, candles.getFirst().closePrice());
        assertEquals(1000, candles.getFirst().volume());
        assertTrue(exchange.requests.stream().allMatch(request -> "data.alpaca.markets".equals(request.uri().getHost())));
        assertTrue(exchange.requests.getFirst().uri().getQuery().contains("feed="));
        assertThrows(IllegalArgumentException.class, () -> exchange.getCandleDataSupplier(7, pair));
    }

    @Test void everyAccountBlockPreventsLiveTrading() throws Exception {
        var json = new ObjectMapper();
        assertTrue(Alpaca.accountCanTrade(json.readTree("{\"status\":\"ACTIVE\"}")));
        for (String flag : List.of("trading_blocked", "account_blocked", "trade_suspended_by_user")) {
            assertFalse(Alpaca.accountCanTrade(json.readTree("{\"status\":\"ACTIVE\",\"" + flag + "\":true}")));
        }
        assertFalse(Alpaca.accountCanTrade(json.readTree("{\"status\":\"ONBOARDING\"}")));
    }

    @Test void trailingStopsCannotBypassBlockedAccountChecks() throws Exception {
        var exchange = new TestExchange();
        exchange.connect();
        exchange.account = "{\"status\":\"ACTIVE\",\"account_blocked\":true}";
        var pair = new TradePair("AAPL", "USD");
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> exchange.createTrailingStopOrder(pair, Side.SELL, 1, 2, false).join());
        assertTrue(exchange.requests.stream().noneMatch(request -> request.method().equals("POST")));
    }

    @Test void invalidNumbersAndDisconnectedSessionsNeverSubmitOrders() throws Exception {
        var exchange = new TestExchange();
        var pair = new TradePair("AAPL", "USD");
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createMarketOrder(pair, Side.BUY, Double.NaN).join());
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createLimitOrder(pair, Side.BUY, 1, 0).join());
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createMarketOrder(pair, Side.BUY, 1).join());
        assertTrue(exchange.requests.stream().noneMatch(request -> request.method().equals("POST")));
    }

    @Test void discoveryAndSubscriptionFailuresNeverFabricateEmptySuccess() throws Exception {
        var exchange = new TestExchange();
        exchange.assetsStatus = 429;
        assertThrows(IllegalStateException.class, exchange::getTradePairSymbol);
        exchange.connect();
        exchange.dataStatus = 403;
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> exchange.fetchTicker(new TradePair("AAPL", "USD")).join());
        assertTrue(exchange.isConnected(), "A market-data subscription error must not invalidate trading authentication");
    }

    @Test void cancellationUsesDeleteAndDisconnectClearsLiveOrderPermission() {
        var exchange = new TestExchange();
        exchange.connect();
        assertEquals("order-1", exchange.cancelOrder("order-1").join());
        assertTrue(exchange.requests.stream().anyMatch(request -> request.method().equals("DELETE")
                && request.uri().getPath().equals("/v2/orders/order-1")));
        exchange.disconnect();
        assertFalse(exchange.canSubmitLiveOrders());
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.cancelOrder("order-1").join());
    }
}
