package org.investpro.ai.local.grpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.local.grpc.generated.BacktestReviewRequest;
import org.investpro.ai.local.grpc.generated.BacktestReviewResponse;
import org.investpro.ai.local.grpc.generated.HealthResponse;
import org.investpro.ai.local.grpc.generated.SignalReviewRequest;
import org.investpro.ai.local.grpc.generated.SignalReviewResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

/**
 * HTTP/JSON client for the local Python AI advisory service.
 *
 * <p>This client replaces the previous gRPC implementation to eliminate the
 * compile-time dependency on protobuf code generation. The Python AI service
 * ({@code ai-service/}) should expose three REST endpoints:</p>
 * <ul>
 *   <li>{@code GET  /health}           — returns {@link HealthResponse} JSON</li>
 *   <li>{@code POST /analyze-signal}   — body: {@link SignalReviewRequest} JSON,
 *       response: {@link SignalReviewResponse} JSON</li>
 *   <li>{@code POST /review-backtest}  — body: {@link BacktestReviewRequest} JSON,
 *       response: {@link BacktestReviewResponse} JSON</li>
 * </ul>
 *
 * <p><strong>CRITICAL:</strong> This client only requests advisory signals from the
 * Python AI service. It NEVER places, submits, or influences order execution directly.
 * All decisions are advisory; the RiskEngine and ExecutionEngine retain final authority.</p>
 *
 * <p>I/O errors throw {@link RuntimeException}, which the caller
 * ({@link LocalAiRuntimeService}) routes to the fallback policy via its circuit breaker.</p>
 */
@Slf4j
public class PythonAiGrpcClient implements AutoCloseable {

    /** Advisory result returned after the signal review endpoint responds. */
    public record SignalReviewResult(
            boolean approved,
            double aiConfidence,
            String recommendation,
            double sizeAdjustment,
            String details) {}

    /** Advisory result returned after the backtest review endpoint responds. */
    public record BacktestReviewResult(
            boolean accepted,
            double aiScore,
            double overfitRisk,
            String warnings) {}

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final long timeoutMs;
    private final HttpClient httpClient;

    /**
     * Creates a client pointing at the Python AI service.
     *
     * @param host      hostname or IP of the Python AI service
     * @param port      HTTP port of the service
     * @param timeoutMs per-request timeout in milliseconds (minimum 200)
     */
    public PythonAiGrpcClient(String host, int port, long timeoutMs) {
        this.timeoutMs = Math.max(200L, timeoutMs);
        this.baseUrl   = "http://" + host + ":" + port;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    // =========================================================================
    // Health check
    // =========================================================================

    /**
     * Requests a health check from the AI service.
     *
     * @return a populated {@link AiGrpcHealthStatus}; returns an
     *         {@linkplain AiGrpcHealthStatus#unavailable unavailable} status if the
     *         service cannot be reached (never throws).
     */
    public AiGrpcHealthStatus health(boolean conservativeMode, String circuitState,
            long requestsPerMinute, String lastError) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/health"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("X-Source", "investpro-java")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                HealthResponse hr = MAPPER.readValue(resp.body(), HealthResponse.class);
                return new AiGrpcHealthStatus(
                        hr.getOk(),
                        hr.getStatus(),
                        hr.getServiceName(),
                        hr.getAvgLatencyMs(),
                        circuitState,
                        conservativeMode,
                        lastError != null ? lastError : "",
                        requestsPerMinute,
                        Instant.now());
            }
            return AiGrpcHealthStatus.unavailable("HTTP " + resp.statusCode());
        } catch (Exception e) {
            log.debug("AI health check failed: {}", e.getMessage());
            return AiGrpcHealthStatus.unavailable(e.getMessage());
        }
    }

    // =========================================================================
    // Signal review
    // =========================================================================

    /**
     * Requests an advisory review of a trading signal.
     *
     * @param request the signal details to review (built via
     *                {@link SignalReviewRequest#newBuilder()})
     * @return advisory result
     * @throws RuntimeException if the HTTP call fails or the response cannot be parsed
     */
    public SignalReviewResult analyzeSignal(SignalReviewRequest request) {
        try {
            String body = MAPPER.writeValueAsString(request);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/analyze-signal"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            SignalReviewResponse response = MAPPER.readValue(resp.body(), SignalReviewResponse.class);
            return new SignalReviewResult(
                    response.getApproved(),
                    response.getAiConfidence(),
                    response.getRecommendation(),
                    response.getSizeAdjustment(),
                    String.join(";", response.getReasonsList()));
        } catch (Exception e) {
            log.debug("analyzeSignal HTTP call failed: {}", e.getMessage());
            throw new RuntimeException("AI signal review service unavailable: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Backtest review
    // =========================================================================

    /**
     * Requests an advisory review of a backtest result.
     *
     * @param request the backtest details to review (built via
     *                {@link BacktestReviewRequest#newBuilder()})
     * @return advisory result
     * @throws RuntimeException if the HTTP call fails or the response cannot be parsed
     */
    public BacktestReviewResult reviewBacktest(BacktestReviewRequest request) {
        try {
            String body = MAPPER.writeValueAsString(request);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/review-backtest"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            BacktestReviewResponse response = MAPPER.readValue(resp.body(), BacktestReviewResponse.class);
            return new BacktestReviewResult(
                    response.getAccepted(),
                    response.getAiScore(),
                    response.getOverfitRisk(),
                    String.join(";", response.getWarningsList()));
        } catch (Exception e) {
            log.debug("reviewBacktest HTTP call failed: {}", e.getMessage());
            throw new RuntimeException("AI backtest review service unavailable: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        // java.net.http.HttpClient does not require explicit shutdown.
    }
}
