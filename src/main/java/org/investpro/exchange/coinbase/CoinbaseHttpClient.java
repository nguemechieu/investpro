package org.investpro.exchange.coinbase;

import lombok.extern.slf4j.Slf4j;
import org.investpro.utils.CoinbaseJwtSigner;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Handles all HTTP communication for Coinbase.
 * Provides a dedicated executor for async operations.
 */
@Slf4j
public class CoinbaseHttpClient {

    private static final String REST_BASE_URL = "https://api.coinbase.com/api/v3/brokerage";

    private final HttpClient httpClient;
    private final CoinbaseJwtSigner jwtSigner;
    private final String apiKey;
    private final String apiSecret;
    private final ExecutorService httpExecutor;

    public CoinbaseHttpClient(String apiKey, String apiSecret, CoinbaseJwtSigner jwtSigner) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();
        this.jwtSigner = jwtSigner;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        // Create dedicated executor for Coinbase HTTP operations
        this.httpExecutor = Executors.newFixedThreadPool(
                4,
                r -> {
                    Thread t = new Thread(r, "coinbase-http-worker-" + System.nanoTime());
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Send synchronous HTTP request and return response body.
     */
    public @NotNull String send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 400) {
                log.warn("Coinbase HTTP error {}: {}", statusCode, response.body());
                return "";
            }

            return decompressIfNeeded(response);
        } catch (IOException e) {
            log.error("IO error during Coinbase HTTP request: {}", e.getMessage(), e);
            return "";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Coinbase HTTP request interrupted: {}", e.getMessage());
            return "";
        } catch (Exception e) {
            log.error("Unexpected error during Coinbase HTTP request: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * Send asynchronous HTTP request and return response body future.
     */
    public @NotNull CompletableFuture<String> sendAsync(HttpRequest request) {
        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(response -> {
                    int statusCode = response.statusCode();
                    if (statusCode >= 400) {
                        log.warn("Coinbase HTTP error {}: {}", statusCode, response.body());
                        return "";
                    }
                    return decompressIfNeeded(response);
                }, httpExecutor)
                .exceptionally(e -> {
                    if (e instanceof java.net.http.HttpTimeoutException) {
                        log.warn("Coinbase HTTP timeout: {}", e.getMessage());
                    } else {
                        log.error("Error during Coinbase async HTTP request: {}", e.getMessage(), e);
                    }
                    return "";
                });
    }

    /**
     * Create base HTTP request builder with standard headers.
     */
    public HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "InvestPro/1.0")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Cache-Control", "no-cache");
    }

    /**
     * Create authenticated request builder with proper authorization header.
     * Does NOT set request body or method - caller decides .GET(), .POST(...), etc.
     */
    public HttpRequest.Builder authenticatedRequest(String method, String url) {
        HttpRequest.Builder builder = baseRequest(url);
        String authorization = authorizationHeader(method, url);

        if (!authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }

        return builder;
    }

    /**
     * Get authorization header for REST request.
     */
    private String authorizationHeader(String method, String url) {
        if (jwtSigner != null) {
            return jwtSigner.buildAuthorizationHeaderForUrl(method, url);
        }

        String token = bearerToken();
        if (!token.isBlank()) {
            return "Bearer " + token;
        }

        return "";
    }

    /**
     * Extract bearer token from apiSecret if provided.
     */
    private @NotNull String bearerToken() {
        if (apiSecret == null || apiSecret.isBlank()) {
            return "";
        }

        String token = apiSecret.trim();

        // If it's a private key, don't use as bearer token
        if (token.contains("BEGIN") && token.contains("PRIVATE KEY")) {
            return "";
        }

        // Strip "Bearer " prefix if present
        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return token.substring("Bearer ".length()).trim();
        }

        return token;
    }

    /**
     * URL encode a string for query parameters.
     */
    public String encode(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to encode URL parameter: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Check if connection has private endpoint authentication.
     */
    public boolean hasPrivateEndpointAuth() {
        return jwtSigner != null || !bearerToken().isBlank();
    }

    /**
     * Decompress response if it's gzip or deflate encoded.
     */
    private String decompressIfNeeded(HttpResponse<String> response) {
        String contentEncoding = response.headers()
                .firstValue("content-encoding")
                .orElse("")
                .toLowerCase();

        try {
            if ("gzip".equals(contentEncoding)) {
                byte[] compressed = response.body().getBytes(StandardCharsets.UTF_8);
                ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                GZIPInputStream gis = new GZIPInputStream(bais);
                return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
            } else if ("deflate".equals(contentEncoding)) {
                byte[] compressed = response.body().getBytes(StandardCharsets.UTF_8);
                ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                InflaterInputStream iis = new InflaterInputStream(bais);
                return new String(iis.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to decompress response: {}", e.getMessage());
        }

        return response.body();
    }

    /**
     * Shutdown the HTTP executor gracefully.
     */
    public void shutdown() {
        try {
            httpExecutor.shutdown();
            if (!httpExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                httpExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            httpExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get REST base URL.
     */
    public static String getRestBaseUrl() {
        return REST_BASE_URL;
    }
}
