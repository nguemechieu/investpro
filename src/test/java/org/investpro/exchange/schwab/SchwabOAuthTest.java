package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchwabOAuthTest {
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final SchwabApiConfig CONFIG = new SchwabApiConfig("client", "secret", "", "",
            "https://api.example.test", "https://api.example.test/trader/v1",
            "https://api.example.test/marketdata/v1", "https://api.example.test/token", false);
    @TempDir Path directory;

    private static SchwabTokenState state(long expires) {
        return new SchwabTokenState("access-sensitive", "refresh-sensitive", NOW.plusSeconds(expires), NOW,
                NOW.plusSeconds(7200), "Bearer", "read trade");
    }
    private static class Store implements SchwabTokenStore {
        SchwabTokenState value;
        int saves;
        Store(SchwabTokenState value) { this.value = value; }
        public Optional<SchwabTokenState> load() { return Optional.ofNullable(value); }
        public void save(SchwabTokenState value) { this.value = value; saves++; }
        public void clear() { value = null; }
    }
    private static HttpResponse<String> response(int code, String body) {
        return new Response(code, body, HttpHeaders.of(Map.of(), (_, _) -> true));
    }
    private record Response(int statusCode, String body, HttpHeaders headers) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return URI.create("https://api.example.test"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }
    private static SchwabTokenManager manager(SchwabOAuthClient oauth, Store store) {
        return new SchwabTokenManager(oauth, store, CLOCK);
    }
    @Test void authorizationUrlEncodesRedirectAndState() {
        String oldUrl = System.getProperty("SCHWAB_AUTHORIZATION_URL"), oldRedirect = System.getProperty("SCHWAB_REDIRECT_URI");
        try {
            System.setProperty("SCHWAB_AUTHORIZATION_URL", "https://auth.example.test/authorize");
            System.setProperty("SCHWAB_REDIRECT_URI", "https://127.0.0.1:8443/oauth/callback");
            var client = new SchwabOAuthClient(CONFIG, mock(HttpClient.class), JSON, CLOCK);
            String uri = client.buildAuthorizationUri("state+/=").toString();
            assertTrue(uri.contains("response_type=code&client_id=client"));
            assertTrue(uri.contains("redirect_uri=https%3A%2F%2F127.0.0.1%3A8443%2Foauth%2Fcallback"));
            assertTrue(uri.endsWith("state=state%2B%2F%3D"));
        } finally { restore("SCHWAB_AUTHORIZATION_URL", oldUrl); restore("SCHWAB_REDIRECT_URI", oldRedirect); }
    }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }
    private SchwabOAuthCallbackServer callback() throws Exception {
        return new SchwabOAuthCallbackServer(URI.create("https://127.0.0.1:0/oauth/callback"), SSLContext.getDefault(), Runnable::run);
    }
    @Test void statesAreRandomAndSuccessIsSingleUse() throws Exception {
        try (var first = callback(); var second = callback()) {
            assertNotEquals(first.state(), second.state()); assertTrue(first.state().length() >= 43);
            String query = "state=" + first.state() + "&code=code%2B%2F%3D";
            assertEquals("code+/=", first.validateCallback(query));
            assertThrows(SchwabAuthenticationException.class, () -> first.validateCallback(query));
        }
    }
    @Test void malformedMissingMismatchedAndDeniedCallbacksAreRejected() throws Exception {
        try (var server = callback()) {
            for (String query : Arrays.asList(null, "code=x", "state=wrong&code=x", "state=" + server.state(),
                    "state=" + server.state() + "&code=x&error=access_denied&error_description=private",
                    "state=" + server.state() + "&code=%ZZ", "state=" + server.state() + "&code=x&code=y")) {
                var error = assertThrows(SchwabAuthenticationException.class, () -> server.validateCallback(query));
                assertFalse(error.toString().contains("private"));
            }
        }
    }
    @Test void nonHttpsOrNonLoopbackCallbackIsRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> SchwabApiConfig.requireHttps("http://localhost/callback"));
        assertThrows(SchwabAuthenticationException.class, () -> new SchwabOAuthCallbackServer(
                URI.create("https://remote.example.test/callback"), SSLContext.getDefault(), Runnable::run));
    }
    @Test void parsesTokensAndAbsoluteExpiryAndUsesBasicFormEncoding() throws Exception {
        var http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(CompletableFuture.completedFuture(response(200,
                        "{\"access_token\":\"new-access\",\"refresh_token\":\"rotated\",\"expires_in\":1800,\"token_type\":\"Bearer\",\"scope\":\"trade\"}")));
        var result = new SchwabOAuthClient(CONFIG, http, JSON, CLOCK).refresh(state(10)).get();
        assertEquals("rotated", result.refreshToken()); assertEquals(NOW.plusSeconds(1800), result.accessTokenExpiresAt());
        assertEquals(NOW, result.obtainedAt()); assertEquals(state(10).refreshTokenExpiresAt(), result.refreshTokenExpiresAt());
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(capture.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertEquals("Basic " + Base64.getEncoder().encodeToString("client:secret".getBytes(StandardCharsets.UTF_8)), capture.getValue().headers().firstValue("Authorization").orElseThrow());
        assertEquals("application/x-www-form-urlencoded", capture.getValue().headers().firstValue("Content-Type").orElseThrow());
        assertNull(capture.getValue().uri().getQuery());
    }
    @Test void invalidTokenResponsesAndTimeoutsFailSafely() {
        for (String body : List.of("not-json-sensitive", "{}", "{\"access_token\":\"sensitive\",\"expires_in\":0}")) {
            var http = mock(HttpClient.class);
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(CompletableFuture.completedFuture(response(200, body)));
            var error = assertThrows(CompletionException.class, () -> new SchwabOAuthClient(CONFIG, http, JSON, CLOCK).refresh(state(10)).join());
            assertInstanceOf(SchwabAuthenticationException.class, error.getCause());
            assertFalse(error.toString().contains("sensitive"));
        }
        var http = mock(HttpClient.class);
        when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("timed out")));
        assertInstanceOf(HttpTimeoutException.class, assertThrows(CompletionException.class,
                () -> new SchwabOAuthClient(CONFIG, http, JSON, CLOCK).refresh(state(10)).join()).getCause());
    }
    @Test void validAccessTokenIsReusedWithoutHttp() {
        var oauth = mock(SchwabOAuthClient.class); var store = new Store(state(1800));
        assertEquals("access-sensitive", manager(oauth, store).getValidAccessToken().join()); verifyNoInteractions(oauth);
        assertFalse(state(90).accessValid(NOW)); assertTrue(state(91).accessValid(NOW));
    }
    @Test void twentyConcurrentCallersShareOneRefreshAndPersistRotation() throws Exception {
        var oauth = mock(SchwabOAuthClient.class); var store = new Store(state(10));
        var pending = new CompletableFuture<SchwabTokenState>(); when(oauth.refresh(any())).thenReturn(pending);
        var tokens = manager(oauth, store);
        try (var workers = Executors.newFixedThreadPool(20)) {
            var callers = new ArrayList<Future<CompletableFuture<String>>>();
            for (int i = 0; i < 20; i++) callers.add(workers.submit(tokens::getValidAccessToken));
            var waiting = new ArrayList<CompletableFuture<String>>();
            for (var caller : callers) waiting.add(caller.get(5, TimeUnit.SECONDS));
            assertEquals("Token Refreshing", tokens.status()); verify(oauth, times(1)).refresh(any());
            var rotated = new SchwabTokenState("new", "rotated", NOW.plusSeconds(1800), NOW, NOW.plusSeconds(7200), "Bearer", "trade");
            pending.complete(rotated);
            for (var result : waiting) assertEquals("new", result.get(5, TimeUnit.SECONDS));
            assertEquals(rotated, store.value); assertEquals(1, store.saves);
        }
    }
    @Test void refreshFailureCanRetryButFatalFailureRequiresAuthorization() {
        var oauth = mock(SchwabOAuthClient.class); var store = new Store(state(1));
        when(oauth.refresh(any())).thenReturn(CompletableFuture.failedFuture(new IOException("offline")),
                CompletableFuture.failedFuture(new SchwabAuthenticationException("rejected")));
        var tokens = manager(oauth, store);
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join());
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join());
        assertEquals("Authorization Required", tokens.status());
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join()); verify(oauth, times(2)).refresh(any());
    }
    @Test void disconnectPreventsLateRefreshAndUnlinkClearsStore() throws Exception {
        var oauth = mock(SchwabOAuthClient.class); var store = new Store(state(1));
        var pending = new CompletableFuture<SchwabTokenState>(); when(oauth.refresh(any())).thenReturn(pending);
        var tokens = manager(oauth, store); var future = tokens.getValidAccessToken(); tokens.disconnect();
        pending.complete(state(1800));
        assertThrows(CompletionException.class, future::join); assertEquals(0, store.saves); assertEquals("Disconnected", tokens.status());
        assertNotNull(store.value); tokens.unlink(); assertNull(store.value);
    }
    @Test void authorizationPersistsAndRejectsCancelledGeneration() throws Exception {
        var store = new Store(null); var tokens = manager(mock(SchwabOAuthClient.class), store);
        long generation = tokens.beginAuthorization(); tokens.authorize(state(1800), generation);
        assertEquals("Connected", tokens.status()); assertEquals(1, store.saves);
        generation = tokens.beginAuthorization(); tokens.disconnect(); final long old = generation;
        assertThrows(SchwabAuthenticationException.class, () -> tokens.authorize(state(1800), old));
        assertEquals(1, store.saves);
    }
    @Test void encryptedStoreRoundTripsRotatesAndClearsWithoutPlaintext() throws Exception {
        Path path = directory.resolve("tokens"); var store = new SchwabEncryptedTokenStore(path, () -> "separate-unlock-password", JSON);
        assertTrue(store.load().isEmpty()); store.save(state(1800)); byte[] first = Files.readAllBytes(path);
        assertFalse(new String(first, StandardCharsets.UTF_8).contains("sensitive"));
        assertEquals(state(1800), store.load().orElseThrow()); store.save(state(1800));
        assertFalse(Arrays.equals(first, Files.readAllBytes(path))); store.clear(); assertTrue(store.load().isEmpty());
    }
    @Test void corruptedStoreWrongPasswordAndMissingPasswordFailClosed() throws Exception {
        Path path = directory.resolve("tokens"); var store = new SchwabEncryptedTokenStore(path, () -> "separate-unlock-password", JSON);
        store.save(state(1800)); var wrong = new SchwabEncryptedTokenStore(path, () -> "different-unlock-password", JSON);
        assertThrows(SchwabAuthenticationException.class, wrong::load);
        byte[] corrupt = Files.readAllBytes(path); corrupt[corrupt.length - 1] ^= 1; Files.write(path, corrupt);
        assertThrows(SchwabAuthenticationException.class, store::load);
        assertThrows(SchwabAuthenticationException.class, () -> new SchwabEncryptedTokenStore(directory.resolve("other"), () -> "", JSON).save(state(1800)));
        assertFalse(state(1800).toString().contains("sensitive")); assertFalse(CONFIG.toString().contains("secret"));
    }
    @Test void api401RefreshesOnceAndRepeated401RequiresAuthorization() throws Exception {
        var oauth = mock(SchwabOAuthClient.class); var store = new Store(state(1800)); var tokens = manager(oauth, store);
        when(oauth.refresh(any())).thenReturn(CompletableFuture.completedFuture(state(1900)));
        var http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(401, "private"), response(200, "[]"), response(401, "private"), response(401, "private"));
        var api = new SchwabApiClient(CONFIG, tokens, http); assertTrue(api.fetchAccountNumbers().isArray());
        verify(oauth, times(1)).refresh(any());
        assertThrows(SchwabAuthenticationException.class, api::fetchAccountNumbers); assertEquals("Authorization Required", tokens.status());
        verify(http, times(4)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
    @Test void api403DoesNotRefreshAnd429DoesNotRetryOrders() throws Exception {
        var oauth = mock(SchwabOAuthClient.class); var tokens = manager(oauth, new Store(state(1800))); var http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(403, "private"), response(429, "private"));
        var api = new SchwabApiClient(CONFIG, tokens, http);
        assertEquals(403, assertThrows(SchwabApiException.class, api::fetchAccountNumbers).status());
        assertEquals(429, assertThrows(SchwabApiException.class, () -> api.placeOrder("hash", JSON.createObjectNode())).status());
        verifyNoInteractions(oauth); verify(http, times(2)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
    @Test void api429GetRetriesAreBoundedAndSecretsOnlyUseAuthorizationHeader() throws Exception {
        var http = mock(HttpClient.class); var tokens = manager(mock(SchwabOAuthClient.class), new Store(state(1800)));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                .thenReturn(response(429, "private"));
        var api = new SchwabApiClient(CONFIG, tokens, http);
        assertEquals(429, assertThrows(SchwabApiException.class, api::fetchAccountNumbers).status());
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(capture.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        for (var request : capture.getAllValues()) {
            assertEquals("Bearer access-sensitive", request.headers().firstValue("Authorization").orElseThrow());
            assertFalse(request.uri().toString().contains("sensitive"));
        }
    }

    @Test void cancellationOfOneWaiterDoesNotCancelSharedRefresh() {
        var oauth = mock(SchwabOAuthClient.class); var pending = new CompletableFuture<SchwabTokenState>();
        when(oauth.refresh(any())).thenReturn(pending);
        var tokens = manager(oauth, new Store(state(1))); var first = tokens.getValidAccessToken(); var second = tokens.getValidAccessToken();
        first.cancel(true); pending.complete(state(1800));
        assertEquals("access-sensitive", second.join()); verify(oauth, times(1)).refresh(any());
    }
    @Test void synchronousRefreshFailureDoesNotLeaveStuckFlight() {
        var oauth = mock(SchwabOAuthClient.class);
        when(oauth.refresh(any())).thenThrow(new IllegalArgumentException("invalid config"));
        var tokens = manager(oauth, new Store(state(1)));
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join());
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join()); verify(oauth, times(2)).refresh(any());
    }
    @Test void expiredRefreshRequiresConsentAndCannotRefresh() {
        var oauth = mock(SchwabOAuthClient.class);
        var expired = new SchwabTokenState("access", "refresh", NOW.plusSeconds(1), NOW, NOW.minusSeconds(1), "Bearer", "");
        var tokens = manager(oauth, new Store(expired));
        assertThrows(CompletionException.class, () -> tokens.getValidAccessToken().join());
        assertEquals("Authorization Required", tokens.status()); verifyNoInteractions(oauth);
    }
    @Test void logMessagesNeverContainTokens() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(SchwabTokenManager.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            var oauth = mock(SchwabOAuthClient.class);
            when(oauth.refresh(any())).thenReturn(CompletableFuture.completedFuture(state(1800)));
            var tokens = manager(oauth, new Store(state(1))); tokens.getValidAccessToken().join(); tokens.invalidate();
            assertFalse(appender.list.isEmpty());
            for (var event : appender.list) assertFalse(event.getFormattedMessage().contains("sensitive"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void longRetryAfterRetainsSharedPauseWithoutSleepingOrSendingAgain() throws Exception {
        var http = mock(HttpClient.class); var tokens = manager(mock(SchwabOAuthClient.class), new Store(state(1800)));
        var limited = new Response(429, "private", HttpHeaders.of(Map.of("Retry-After", List.of("120")), (_, _) -> true));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(limited);
        var api = new SchwabApiClient(CONFIG, tokens, http);
        assertThrows(SchwabApiException.class, api::fetchAccountNumbers);
        assertThrows(SchwabApiException.class, api::fetchAccountNumbers);
        verify(http, times(1)).send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
    }
    @Test void acceptedOrderUsesNumericLocationIdAndEncodedAccountHash() throws Exception {
        var http = mock(HttpClient.class); var tokens = manager(mock(SchwabOAuthClient.class), new Store(state(1800)));
        var accepted = new Response(201, "", HttpHeaders.of(Map.of("Location", List.of("https://api.example.test/trader/v1/accounts/hash/orders/12345")), (_, _) -> true));
        when(http.send(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(accepted);
        assertEquals("12345", new SchwabApiClient(CONFIG, tokens, http).placeOrder("hash/+", JSON.createObjectNode()));
        var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        assertTrue(capture.getValue().uri().getRawPath().contains("hash%2F%2B"));
    }

    @Test void realHttpsCallbackDeliversCodeAndReturnsNoSecretsToBrowser() throws Exception {
        Path keys = directory.resolve("callback.p12");
        String fixturePassword = "test-certificate-password";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        var process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "callback", "-keyalg", "RSA",
                "-storetype", "PKCS12", "-keystore", keys.toString(), "-storepass", fixturePassword,
                "-keypass", fixturePassword, "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-validity", "1")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        var tls = SchwabOAuthCallbackServer.loadTls(keys, fixturePassword.toCharArray());
        var trustStore = java.security.KeyStore.getInstance("PKCS12");
        try (var input = Files.newInputStream(keys)) { trustStore.load(input, fixturePassword.toCharArray()); }
        var trusts = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
        trusts.init(trustStore); var clientTls = SSLContext.getInstance("TLS"); clientTls.init(null, trusts.getTrustManagers(), null);
        try (var workers = Executors.newFixedThreadPool(2);
             var server = new SchwabOAuthCallbackServer(URI.create("https://127.0.0.1:0/callback"), tls, workers);
             var client = HttpClient.newBuilder().sslContext(clientTls).connectTimeout(Duration.ofSeconds(5)).build()) {
            var code = server.start();
            var request = HttpRequest.newBuilder(URI.create("https://127.0.0.1:" + server.boundPort()
                    + "/callback?state=" + server.state() + "&code=sensitive-code%2B"))
                    .timeout(Duration.ofSeconds(10)).GET().build();
            var reply = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, reply.statusCode()); assertEquals("sensitive-code+", code.get(5, TimeUnit.SECONDS));
            assertFalse(reply.body().contains("sensitive")); assertEquals("no-store", reply.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals(400, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }
    @Test void authorizationCodeBodyIsFormEncodedAndUsesExactRedirect() throws Exception {
        String old = System.getProperty("SCHWAB_REDIRECT_URI");
        try {
            System.setProperty("SCHWAB_REDIRECT_URI", "https://127.0.0.1:8443/callback");
            var http = mock(HttpClient.class);
            when(http.sendAsync(any(HttpRequest.class), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(CompletableFuture.completedFuture(response(200,
                            "{\"access_token\":\"access\",\"refresh_token\":\"refresh\",\"token_type\":\"Bearer\",\"expires_in\":1800}")));
            new SchwabOAuthClient(CONFIG, http, JSON, CLOCK).exchangeAuthorizationCode("code+/=").get();
            var capture = org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
            verify(http).sendAsync(capture.capture(), org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
            var bytes = new java.io.ByteArrayOutputStream(); var done = new CompletableFuture<Void>();
            capture.getValue().bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer>() {
                public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { subscription.request(Long.MAX_VALUE); }
                public void onNext(java.nio.ByteBuffer buffer) { byte[] part = new byte[buffer.remaining()]; buffer.get(part); bytes.writeBytes(part); }
                public void onError(Throwable error) { done.completeExceptionally(error); }
                public void onComplete() { done.complete(null); }
            });
            done.get(5, TimeUnit.SECONDS);
            assertEquals("grant_type=authorization_code&code=code%2B%2F%3D&redirect_uri=https%3A%2F%2F127.0.0.1%3A8443%2Fcallback", bytes.toString(StandardCharsets.UTF_8));
        } finally { restore("SCHWAB_REDIRECT_URI", old); }
    }
    @Test void malformedTokensAndInsecureEndpointsAreRejectedWithoutEchoingValues() {
        assertThrows(IllegalArgumentException.class, () -> new SchwabTokenState("token\r\nsecret", "refresh", NOW.plusSeconds(1800), NOW, null, "Bearer", ""));
        var error = assertThrows(IllegalArgumentException.class, () -> new SchwabApiConfig("client", "secret", "", "",
                "http://api.example.test?secret=value", CONFIG.traderApiBaseUrl(), CONFIG.marketDataBaseUrl(), CONFIG.oauthTokenUrl(), false));
        assertFalse(error.toString().contains("value"));
    }
}
