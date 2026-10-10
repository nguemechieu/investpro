package org.investpro.exchange.binanceus;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.credentials.ExchangeSigning;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import static org.junit.jupiter.api.Assertions.*;

class BinanceUsAuthenticationTest {
    static class Venue extends BinanceUs {
        final List<HttpRequest> signedRequests = new CopyOnWriteArrayList<>();
        int accountStatus = 200;
        int orderStatus = 200;
        String accountBody = "{\"canTrade\":true,\"permissions\":[\"SPOT\"],\"balances\":[]}";
        Venue(String key, String secret) {
            super(new ExchangeCredentials("binanceus", key, secret, null, null, null, null,
                    false, Map.of("tradingMode", "LIVE")));
        }
        @Override protected HttpResponse<String> executeHttpRequest(HttpRequest request) {
            String path = request.uri().getPath();
            if (request.headers().firstValue("X-MBX-APIKEY").isPresent()) signedRequests.add(request);
            if (path.equals("/api/v3/time")) return response(200, "{\"serverTime\":" + System.currentTimeMillis() + "}");
            if (path.equals("/api/v3/account")) return response(accountStatus, accountBody);
            if (path.equals("/api/v3/exchangeInfo")) return response(200, """
                {"symbols":[{"symbol":"BTCUSDT","status":"TRADING","isSpotTradingAllowed":true,
                "permissionSets":[["SPOT"]],"orderTypes":["MARKET","LIMIT"],"filters":[]}]}
                """);
            if (path.equals("/api/v3/order")) return response(orderStatus, orderStatus == 200
                    ? "{\"orderId\":7}" : "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}");
            throw new AssertionError("Unexpected endpoint: " + path);
        }
        private HttpResponse<String> response(int status, String body) {
            return BinanceUsCooldownTest.response(status, Map.of(), body);
        }
    }
    @Test void configuredKeyDoesNotAuthenticateRejectedAccount() {
        var venue = new Venue("key", "secret");
        try {
            venue.setAuthenticatedSessionConnected(true);
            venue.accountStatus = 401;
            venue.accountBody = "{\"code\":-2015,\"msg\":\"Invalid API-key, IP, or permissions for action.\"}";
            var result = venue.checkAuthentication();
            assertFalse(result.isSuccess()); assertTrue(result.isCredentialIssue());
            assertEquals(401, result.getHttpStatus()); assertFalse(venue.isAuthenticatedSessionConnected());
            assertFalse(venue.AuthCheckResult("BINANCE US").success());
        } finally { venue.disconnect(); }
    }
    @Test void missingSecretNeverClaimsAuthenticationOrSendsRequest() {
        var venue = new Venue("key", " ");
        try { assertFalse(venue.checkAuthentication().isSuccess()); assertTrue(venue.signedRequests.isEmpty()); }
        finally { venue.disconnect(); }
    }
    @Test void readOnlyAccountRemainsAuthenticatedWithExplicitTradingStatus() {
        var venue = new Venue("key", "secret");
        try {
            venue.accountBody = "{\"canTrade\":false,\"balances\":[]}";
            var result = venue.checkAuthentication();
            assertTrue(result.isSuccess()); assertEquals("false", result.getMetadata().get("canTrade"));
            assertTrue(result.getMessage().contains("trading is disabled"));
        } finally { venue.disconnect(); }
    }
    @Test void accountAndManualOrderUseSameNormalizedCredentialsAndSignedPayload() throws Exception {
        var venue = new Venue(" key\n", " secret\r\n");
        try {
            assertTrue(venue.checkAuthentication().isSuccess());
            assertEquals("7", venue.createMarketOrder(new TradePair("BTC", "USDT"), Side.BUY, 1).get(10, TimeUnit.SECONDS));
            for (var request : venue.signedRequests) {
                assertEquals("api.binance.us", request.uri().getHost());
                assertEquals("key", request.headers().firstValue("X-MBX-APIKEY").orElseThrow());
                String payload = request.method().equals("GET") ? request.uri().getRawQuery() : body(request);
                int signature = payload.lastIndexOf("&signature=");
                assertTrue(signature > 0);
                assertEquals(ExchangeSigning.hmacHex("HmacSHA256", "secret", payload.substring(0, signature)),
                        payload.substring(signature + "&signature=".length()));
            }
            assertEquals(1, venue.signedRequests.stream().filter(r -> r.method().equals("POST")).count());
        } finally { venue.disconnect(); }
    }
    @Test void orderPermissionFailureExplainsReadAccessWithoutRetryOrFakeFill() throws Exception {
        var venue = new Venue("key", "secret");
        try {
            assertTrue(venue.checkAuthentication().isSuccess()); venue.orderStatus = 401;
            var error = assertThrows(ExecutionException.class,
                    () -> venue.createMarketOrder(new TradePair("BTC", "USDT"), Side.BUY, 1).get(10, TimeUnit.SECONDS));
            Throwable root = error; while (root.getCause() != null) root = root.getCause();
            assertTrue(root.getMessage().contains("enable Spot Trading"));
            assertTrue(venue.isAuthenticatedSessionConnected());
            assertEquals(1, venue.signedRequests.stream().filter(r -> r.method().equals("POST")).count());
        } finally { venue.disconnect(); }
    }
    private static String body(HttpRequest request) {
        var completion = new CompletableFuture<String>(); var text = new StringBuilder();
        request.bodyPublisher().orElseThrow().subscribe(new Flow.Subscriber<ByteBuffer>() {
            public void onSubscribe(Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
            public void onNext(ByteBuffer buffer) { text.append(StandardCharsets.UTF_8.decode(buffer)); }
            public void onError(Throwable error) { completion.completeExceptionally(error); }
            public void onComplete() { completion.complete(text.toString()); }
        });
        return completion.join();
    }
}