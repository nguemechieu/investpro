package org.investpro.exchange.coinbase;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CoinbaseHttpErrorTest {
    @Test void rejectedOrderPropagatesSafeGuidanceWithoutRetryingOrRevokingAuthentication() throws Exception {
        var exchange = org.mockito.Mockito.spy(new Coinbase(new org.investpro.exchange.credentials.ExchangeCredentials(
                "coinbase", "", "", "", "", "", "", false)));
        org.mockito.Mockito.doReturn(false).when(exchange).isPaperTrading();
        field(exchange, "apiSecret", "Bearer ey.test.signature");
        var client = org.mockito.Mockito.mock(java.net.http.HttpClient.class);
        field(exchange, "httpClient", client);
        @SuppressWarnings("unchecked")
        java.net.http.HttpResponse<byte[]> response = org.mockito.Mockito.mock(java.net.http.HttpResponse.class);
        org.mockito.Mockito.when(response.statusCode()).thenReturn(403);
        org.mockito.Mockito.when(response.headers()).thenReturn(java.net.http.HttpHeaders.of(java.util.Map.of(), (_, _) -> true));
        org.mockito.Mockito.when(response.body()).thenReturn(
                "{\"error\":\"PERMISSION_DENIED\",\"message\":\"User is not allowed to trade futures\"}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        org.mockito.Mockito.when(client.sendAsync(org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(response));
        var order = new org.investpro.models.trading.Order();
        order.setSymbol("GOL-25NOV26-CDE"); order.setType("MARKET");
        order.setSide(org.investpro.utils.Side.BUY); order.setQuantity(1);
        var error = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> exchange.createOrder(order).get(5, java.util.concurrent.TimeUnit.SECONDS));
        assertTrue(error.getCause().getMessage().contains("derivatives access"));
        assertTrue(exchange.hasPrivateAuthentication());
        assertFalse(exchange.isAuthenticationRejected());
        org.mockito.Mockito.verify(client, org.mockito.Mockito.times(1)).sendAsync(
                org.mockito.ArgumentMatchers.any(java.net.http.HttpRequest.class),
                org.mockito.ArgumentMatchers.any(java.net.http.HttpResponse.BodyHandler.class));
    }

    private static void field(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value); return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }
    @Test void orderPermissionDenialExplainsPortfolioAndTradeAccessWithoutLeakingResponse() {
        String message = CoinbaseHttpError.message(403, "/api/v3/brokerage/orders",
                "{\"error\":\"PERMISSION_DENIED\",\"message\":\"secret-account@example.com\",\"token\":\"secret-token\"}");
        assertTrue(message.contains("PERMISSION_DENIED"));
        assertTrue(message.contains("Trade permission"));
        assertTrue(message.contains("portfolio"));
        assertFalse(message.contains("secret"));
        assertFalse(message.contains("authentication"));
    }

    @Test void derivativesAndIpRestrictionsHaveSpecificGuidance() {
        assertTrue(CoinbaseHttpError.message(403, "/api/v3/brokerage/orders",
                "{\"message\":\"User is not allowed to trade futures\"}").contains("derivatives access"));
        String ip = CoinbaseHttpError.message(403, "/api/v3/brokerage/orders",
                "{\"error_details\":\"IP address 1.2.3.4 is not whitelisted\"}");
        assertTrue(ip.contains("IP allowlist"));
        assertFalse(ip.contains("1.2.3.4"));
    }

    @Test void malformedAndOversizedBodiesReceiveSafeFallbacksAndUnknownCodesAreNotEchoed() {
        for (String body : new String[]{null, "", "<html>secret</html>", "secret".repeat(12000),
                "{\"error\":\"private-token\"}"}) {
            String message = CoinbaseHttpError.message(403, "/api/v3/brokerage/orders", body);
            assertTrue(message.contains("Coinbase HTTP 403"));
            assertTrue(message.contains("Trade permission"));
            assertFalse(message.contains("secret"));
            assertFalse(message.contains("private-token"));
        }
        assertTrue(CoinbaseHttpError.message(401, "/api/v3/brokerage/accounts", "").contains("rejected authentication"));
    }
}
