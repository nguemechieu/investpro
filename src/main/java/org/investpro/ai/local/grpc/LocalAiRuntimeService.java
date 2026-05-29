package org.investpro.ai.local.grpc;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiDecision;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.ai.local.grpc.generated.BacktestReviewRequest;
import org.investpro.ai.local.grpc.generated.SignalReviewRequest;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;
import org.investpro.strategy.lab.StrategyPerformanceReport;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integrates the local Python AI advisory service into the InvestPro decision pipeline.
 *
 * <p>This service wraps {@link PythonAiGrpcClient} with a circuit breaker, conservative-mode
 * state, and a fallback policy so that the rest of the platform degrades gracefully when
 * the Python AI service is unavailable.</p>
 *
 * <p><strong>CRITICAL:</strong> This service is an advisory layer only. It NEVER places,
 * submits, or influences order execution directly. The RiskEngine and ExecutionEngine
 * retain final authority over all capital decisions.</p>
 */
@Slf4j
public class LocalAiRuntimeService implements AiReasoningService, AutoCloseable {

    private static final String SERVICE_NAME = "Local Python AI";

    private final PythonAiGrpcClient grpcClient;
    private final AiFallbackPolicy fallbackPolicy;
    private final AiCircuitBreaker circuitBreaker;
    private final AiConservativeModeState conservativeModeState;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicReference<String> lastError = new AtomicReference<>("");

    public LocalAiRuntimeService(PythonAiGrpcClient grpcClient) {
        this(grpcClient,
                new AiFallbackPolicy(),
                new AiCircuitBreaker(3, Duration.ofSeconds(20)),
                new AiConservativeModeState());
    }

    public LocalAiRuntimeService(PythonAiGrpcClient grpcClient,
            AiFallbackPolicy fallbackPolicy,
            AiCircuitBreaker circuitBreaker,
            AiConservativeModeState conservativeModeState) {
        this.grpcClient           = Objects.requireNonNull(grpcClient,           "grpcClient cannot be null");
        this.fallbackPolicy       = Objects.requireNonNull(fallbackPolicy,       "fallbackPolicy cannot be null");
        this.circuitBreaker       = Objects.requireNonNull(circuitBreaker,       "circuitBreaker cannot be null");
        this.conservativeModeState = Objects.requireNonNull(conservativeModeState, "conservativeModeState cannot be null");
    }

    public static LocalAiRuntimeService fromConfiguration() {
        String host       = AppConfig.get(AppConfigKeys.AI_LOCAL_GRPC_HOST,        "127.0.0.1");
        int    port       = AppConfig.getInt(AppConfigKeys.AI_LOCAL_GRPC_PORT,     8010);
        long   timeoutMs  = AppConfig.getLong(AppConfigKeys.AI_LOCAL_GRPC_TIMEOUT_MS, 1500L);
        return new LocalAiRuntimeService(new PythonAiGrpcClient(host, port, timeoutMs));
    }

    public static boolean isGrpcAdvisoryEnabled() {
        return AppConfig.getBoolean(AppConfigKeys.AI_LOCAL_GRPC_ENABLED, true);
    }

    // =========================================================================
    // AiReasoningService
    // =========================================================================

    @Override
    public AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request) {
        if (request == null) {
            return AiTradeReviewResponse.incompleteDataResponse("request is null");
        }

        requestCounter.incrementAndGet();

        if (!circuitBreaker.allowRequest()) {
            conservativeModeState.enable("Circuit open");
            return fallbackPolicy.fallbackTradeReview(request);
        }

        long startMs = System.currentTimeMillis();
        try {
            SignalReviewRequest aiRequest = toSignalRequest(request);
            PythonAiGrpcClient.SignalReviewResult result = grpcClient.analyzeSignal(aiRequest);

            circuitBreaker.recordSuccess();
            conservativeModeState.disable("AI advisory healthy");
            lastError.set("");

            return mapToTradeReviewResponse(request, result, System.currentTimeMillis() - startMs);
        } catch (Exception exception) {
            onAiFailure(exception);
            return fallbackPolicy.fallbackTradeReview(request);
        }
    }

    /**
     * Requests an advisory AI score for a completed backtest.
     *
     * @param report backtest report to review
     * @return blended score (70% internal score + 30% AI advisory score), or the internal
     *         score alone when the AI service is unavailable.
     */
    public double reviewBacktestScore(StrategyPerformanceReport report) {
        if (report == null || !isGrpcAdvisoryEnabled() || !circuitBreaker.allowRequest()) {
            return report == null ? 0.0 : report.getScore();
        }

        try {
            BacktestReviewRequest request = BacktestReviewRequest.newBuilder()
                    .setStrategyId(report.getBaseStrategyName())
                    .setStrategyName(report.getStrategyName())
                    .setSymbol(report.getSymbol())
                    .setTimeframe(report.getTimeframe().getCode())
                    .setTotalTrades(report.getTotalTrades())
                    .setWinRate(report.getWinRate())
                    .setProfitFactor(report.getProfitFactor())
                    .setMaxDrawdown(report.getMaxDrawdown())
                    .setSharpeRatio(report.getSharpeApproximation())
                    .setExpectancy(report.getAverageRiskReward())
                    .setSampleSize(report.getTotalTrades())
                    .build();

            PythonAiGrpcClient.BacktestReviewResult response = grpcClient.reviewBacktest(request);
            circuitBreaker.recordSuccess();
            conservativeModeState.disable("Backtest advisory healthy");
            return (report.getScore() * 0.7) + (response.aiScore() * 0.3);
        } catch (Exception exception) {
            onAiFailure(exception);
            return report.getScore();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!isGrpcAdvisoryEnabled()) {
            return false;
        }
        AiGrpcHealthStatus status = healthStatus();
        return status.ok();
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    public AiGrpcHealthStatus healthStatus() {
        return grpcClient.health(
                conservativeModeState.isEnabled(),
                circuitBreaker.state().name(),
                requestCounter.get(),
                lastError.get());
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private SignalReviewRequest toSignalRequest(AiTradeReviewRequest request) {
        String symbol = request.getSymbol() == null ? "UNKNOWN" : request.getSymbol().toString('/');
        String regime = request.getRiskContext() == null
                || request.getRiskContext().getMarketBehavior() == null
                ? "UNKNOWN"
                : request.getRiskContext().getMarketBehavior().name();

        return SignalReviewRequest.newBuilder()
                .setSymbol(symbol)
                .setTimeframe("1h")
                .setSide(request.getSignalSide() == null ? "HOLD" : request.getSignalSide())
                .setConfidence(request.getSignalConfidence())
                .setPrice(request.getCurrentPrice())
                .setSpread(request.getSpreadPercent())
                .setVolatility(request.getVolatilityPercent())
                .setVolume(request.getAccountEquity())
                .setStrategyName(request.getStrategyName() == null ? "UNKNOWN" : request.getStrategyName())
                .setMarketRegime(regime)
                .build();
    }

    private AiTradeReviewResponse mapToTradeReviewResponse(
            AiTradeReviewRequest request,
            PythonAiGrpcClient.SignalReviewResult result,
            long latencyMs) {

        AiDecision decision;
        if (result.approved()) {
            decision = result.sizeAdjustment() < 0.99
                    ? AiDecision.APPROVE_WITH_REDUCED_SIZE
                    : AiDecision.APPROVE;
        } else if ("WAIT".equalsIgnoreCase(result.recommendation())) {
            decision = AiDecision.WAIT;
        } else {
            decision = AiDecision.ESCALATE_TO_MANUAL_REVIEW;
        }

        double safeAdj = Math.max(0.0, Math.min(1.0,
                result.sizeAdjustment() <= 0.0 ? 1.0 : result.sizeAdjustment()));
        double suggestedPosition = request.getRiskDecision() == null
                ? 0.0
                : request.getRiskDecision().getFinalPositionSize() * safeAdj;

        return AiTradeReviewResponse.builder()
                .decision(decision)
                .confidence(Math.max(0.0, Math.min(1.0, result.aiConfidence())))
                .suggestedRiskMultiplier(safeAdj)
                .suggestedPositionSize(suggestedPosition)
                .recommendedExecutionStrategy("LOCAL_AI_ADVISORY")
                .confirmations(result.approved()
                        ? List.of("Python advisory approved signal") : List.of())
                .concerns(result.approved()
                        ? List.of() : List.of("Python advisory requested caution"))
                .blockers(List.of())
                .recommendations(result.details().isBlank()
                        ? List.of(result.recommendation()) : List.of(result.details()))
                .explanation("Advisory decision from local Python AI: " + result.recommendation())
                .modelName(SERVICE_NAME)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(latencyMs)
                .hadErrors(false)
                .build();
    }

    private void onAiFailure(Exception exception) {
        circuitBreaker.recordFailure();
        conservativeModeState.enable("AI service failure");
        String message = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        lastError.set(message);
        log.warn("Local Python AI advisory failed: {}", message);
    }

    @Override
    public void close() {
        grpcClient.close();
    }
}
