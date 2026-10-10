package org.investpro.exchange.kraken;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KrakenAdapterTest {
    private ExchangeCredentials credentials(boolean paper) {
        return new ExchangeCredentials("kraken", "test-key", "c2VjcmV0", null, null, null, null, false,
                Map.of("tradingMode", paper ? "PAPER" : "LIVE"));
    }
    @SuppressWarnings("unchecked")
    private HttpClient client(int status, String body) throws Exception {
        var client = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
        return client;
    }
    @Test void authenticationUsesSignedPrivateBalanceAndIsCached() throws Exception {
        var client = client(200, "{\"error\":[],\"result\":{\"ZUSD\":\"10\"}}");
        var exchange = new Kraken(credentials(false), client, "https://example.invalid");
        assertFalse(exchange.checkAuthentication().isSuccess());
        assertTrue(exchange.AuthCheckResult("kraken").success());
        assertTrue(exchange.checkAuthentication().isSuccess());
        verify(client).send(argThat(request -> request.uri().getPath().equals("/0/private/Balance")
                && request.headers().firstValue("API-Sign").isPresent()), any(HttpResponse.BodyHandler.class));
        exchange.disconnect(); assertFalse(exchange.checkAuthentication().isSuccess());
    }
    @Test void rejectedCredentialsDoNotAuthenticate() throws Exception {
        var exchange = new Kraken(credentials(false), client(200, "{\"error\":[\"EAPI:Invalid key\"]}"), "https://example.invalid");
        assertFalse(exchange.AuthCheckResult("kraken").success()); assertFalse(exchange.checkAuthentication().isSuccess());
    }
    @Test void paperAccountAndOrdersNeverCallNetwork() throws Exception {
        var client = mock(HttpClient.class);
        var exchange = new Kraken(credentials(true), client, "https://example.invalid");
        var pair = new TradePair("BTC", "USD"); exchange.updateLocalPaperMarketPrice(pair, 100);
        assertTrue(exchange.fetchAccount().join().isPaperTrading());
        assertNotNull(exchange.fetchAvailableBalance("USD").join());
        assertNotNull(exchange.createMarketOrder(pair, Side.BUY, 1).join());
        assertNotNull(exchange.fetchAllOpenOrders().join());
        verifyNoInteractions(client);
    }
    @Test void tickerAndDepthFailuresDoNotRecursivelyRetry() throws Exception {
        var client = client(429, "{}");
        var exchange = new Kraken(credentials(false), client, "https://example.invalid");
        var pair = new TradePair("BTC", "USD");
        assertThrows(IllegalStateException.class, () -> exchange.getLivePrice(pair));
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.fetchOrderBook(pair).join());
        verify(client, times(2)).send(any(), any(HttpResponse.BodyHandler.class));
    }
    @Test void candlesMapOhlcAndExcludeUncommittedRow() throws Exception {
        var client = client(200, "{\"error\":[],\"result\":{\"XXBTZUSD\":[[1000,\"10\",\"12\",\"9\",\"11\",\"10\",\"5\",1],[1060,\"11\",\"13\",\"10\",\"12\",\"11\",\"7\",1]],\"last\":1060}}");
        var exchange = new Kraken(credentials(false), client, "https://example.invalid");
        var supplier = exchange.getCandleDataSupplier(60, new TradePair("BTC", "USD"));
        var candles = supplier.get().get(); assertEquals(1, candles.size());
        assertEquals(10, candles.getFirst().openPrice()); assertEquals(11, candles.getFirst().closePrice());
        assertEquals(12, candles.getFirst().highPrice()); assertEquals(5, candles.getFirst().volume());
        assertThrows(IllegalArgumentException.class, () -> exchange.getCandleDataSupplier(120, new TradePair("BTC", "USD")));
    }
    @Test void liveMarketOrderReturnsExchangeId() throws Exception {
        var exchange = new Kraken(credentials(false), client(200, "{\"error\":[],\"result\":{\"txid\":[\"actual-order\"]}}"), "https://example.invalid");
        assertEquals("actual-order", exchange.placeMarketOrder(new TradePair("BTC", "USD"), Side.BUY, 0.01).join());
        assertThrows(java.util.concurrent.CompletionException.class, () -> exchange.createLimitOrder(new TradePair("BTC", "USD"), Side.BUY, 1, Double.NaN).join());
    }
}
