package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.coinbase.Coinbase.ExchangeCapabilityStatus;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinbaseRestDiscoveryTest {
    @Test void publicDerivativeCatalogSurvivesDeniedPrivateDiscoveryAndRemainsViewOnly() throws Exception {
        var exchange = spy(new Coinbase(new ExchangeCredentials("coinbase", "", "", "", "", "", "", false)));
        doReturn(false).when(exchange).isPaperTrading(); field(exchange, "apiSecret", "Bearer ey.test.signature");
        var client = mock(HttpClient.class); field(exchange, "httpClient", client);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            boolean publicRequest = request.uri().getPath().contains("/market/products");
            String query = java.util.Objects.toString(request.uri().getQuery(), "");
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(publicRequest ? 200 : 403);
            when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
            String body = !publicRequest ? "Denied" : query.contains("product_type=FUTURE") ? """
                    {"products":[{"product_id":"GOL-25NOV26-CDE","product_type":"FUTURE"},
                    {"product_id":"BIP-20DEC30-CDE","product_type":"FUTURE","future_product_details":{"contract_expiry_type":"EXPIRING","funding_rate":"0"}}]}
                    """ : "{\"products\":[{\"product_id\":\"BTC-USD\",\"product_type\":\"SPOT\"}]}";
            if (publicRequest) assertTrue(request.headers().firstValue("Authorization").isEmpty());
            when(response.body()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)); return response;
        });
        var products = exchange.fetchMarketInstruments().get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(3, products.size());
        var gold = products.stream().filter(p -> p.nativeSymbol().startsWith("GOL-")).findFirst().orElseThrow();
        var perp = products.stream().filter(p -> p.nativeSymbol().startsWith("BIP-")).findFirst().orElseThrow();
        assertTrue(gold.isFuture()); assertTrue(perp.isPerpetual()); assertTrue(perp.tradePair().isPerpetual());
        assertEquals(org.investpro.enums.ContractType.PERPETUAL, perp.tradePair().getContractType());
        assertTrue(gold.canShowInMarketWatch()); assertTrue(perp.canShowInMarketWatch());
        assertEquals(org.investpro.trading.tradability.TradabilityStatus.VIEW_ONLY, perp.tradability().status());
        assertFalse(perp.tradability().isFullyTradable());
        assertEquals(ExchangeCapabilityStatus.PERMISSION_REQUIRED, exchange.getPerpetualsAccess());
        assertEquals(ExchangeCapabilityStatus.PERMISSION_REQUIRED, exchange.getExpiringFuturesAccess());
    }
    @Test void marketWatchQuotesUseExactDatedContractIdsOffCallingThread() throws Exception {
        Coinbase exchange = new Coinbase(new ExchangeCredentials("coinbase", "", "", "", "", "", "", false));
        HttpClient client = mock(HttpClient.class);
        field(exchange, "httpClient", client);
        Thread caller = Thread.currentThread();
        List<String> paths = new java.util.concurrent.CopyOnWriteArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            assertNotSame(caller, Thread.currentThread());
            HttpRequest request = call.getArgument(0);
            paths.add(request.uri().getPath());
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
            when(response.body()).thenReturn("{\"best_bid\":\"100\",\"best_ask\":\"101\",\"trades\":[]}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return response;
        });
        var pairs = List.of(org.investpro.models.trading.TradePair.fromSymbol("GOL-25NOV26-CDE"),
                org.investpro.models.trading.TradePair.fromSymbol("BIP-20DEC30-CDE"));
        var quotes = exchange.fetchTickers(pairs).get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals(2, quotes.size());
        assertEquals(List.of("/api/v3/brokerage/market/products/GOL-25NOV26-CDE/ticker",
                "/api/v3/brokerage/market/products/BIP-20DEC30-CDE/ticker"), paths);
        assertEquals(100, quotes.getFirst().getBidPrice());
    }
    @Test
    void rejectsOpaqueSecretsBeforeAnyNetworkCall() {
        assertThrows(IllegalArgumentException.class, () -> new Coinbase(new ExchangeCredentials(
                "coinbase", "organizations/test/apiKeys/test", "invalid-secret", "", "", "", "", false)));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field f = type.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) { type = type.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    @Test
    void publicSpotSurvives401() throws Exception { verifyPrivateDiscoveryDenial(401); }

    @Test
    void publicSpotSurvives403() throws Exception { verifyPrivateDiscoveryDenial(403); }

    private void verifyPrivateDiscoveryDenial(int status) throws Exception {
        Coinbase exchange = spy(new Coinbase(new ExchangeCredentials("coinbase", "", "", "", "", "", "", false)));
        doReturn(false).when(exchange).isPaperTrading();
        field(exchange, "apiSecret", "Bearer ey.test.signature");
        HttpClient client = mock(HttpClient.class);
        field(exchange, "httpClient", client);
        List<HttpRequest> requests = new ArrayList<>();
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
            HttpRequest request = call.getArgument(0);
            requests.add(request);
            boolean publicRequest = request.uri().getPath().contains("/market/products");
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(publicRequest ? 200 : status);
            when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (a, b) -> true));
            when(response.body()).thenReturn((publicRequest
                    ? "{\"products\":[{\"product_id\":\"BTC-USD\",\"product_type\":\"SPOT\"}]}"
                    : "Unauthorized").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return response;
        });
        var products = exchange.fetchMarketInstruments().get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertEquals("BTC-USD", products.getFirst().nativeSymbol());
        assertEquals(6, requests.size());
        assertEquals("https://api.coinbase.com/api/v3/brokerage/market/products", requests.getFirst().uri().toString());
        assertTrue(requests.getFirst().headers().firstValue("Authorization").isEmpty());
        assertEquals(ExchangeCapabilityStatus.PERMISSION_REQUIRED, exchange.getPerpetualsAccess());
        assertEquals(ExchangeCapabilityStatus.PERMISSION_REQUIRED, exchange.getExpiringFuturesAccess());
        assertEquals(ExchangeCapabilityStatus.AVAILABLE, exchange.getSpotProductsAccess());

        List<Coinbase.RestAuthDiagnostic> diagnosis = exchange.diagnoseRestAuthentication();
        assertEquals(List.of(new Coinbase.RestAuthDiagnostic("accounts without query", false)), diagnosis);
        assertEquals("/api/v3/brokerage/accounts", requests.getLast().uri().getRawPath());
        assertNull(requests.getLast().uri().getRawQuery());
    }
}
