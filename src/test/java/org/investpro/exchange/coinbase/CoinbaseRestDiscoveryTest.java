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
        var discovery = Coinbase.class.getDeclaredMethod("fetchCoinbaseProductsRoot", boolean.class);
        discovery.setAccessible(true);
        JsonNode root = (JsonNode) discovery.invoke(exchange, true);
        assertEquals("BTC-USD", root.path("products").get(0).path("product_id").asText());
        assertEquals(4, requests.size());
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
