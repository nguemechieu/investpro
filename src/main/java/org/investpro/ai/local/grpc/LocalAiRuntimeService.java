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

@Slf4j
public class LocalAiRuntimeService implements AiReasoningService, AutoCloseable {

    private static final String SERVICE_NAME = "Local Python gRPC AI";

    private final PythonAiGrpcClient grpcClient;
    private final AiFallbackPolicy fallbackPolicy;
    private final AiCircuitBreaker circuitBreaker;
    private final AiConservativeModeState conservativeModeState;
    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicReference<String> lastError = new AtomicReference<>("");

    public LocalAiRuntimeService(PythonAiGrpcClient grpcClient) {
        this(grpcClient, new AiFallbackPolicy(), new AiCircuitBreaker(3, Duration.ofSeconds(20)), new AiConservativeModeState());
    }

    public LocalAiRuntimeService(PythonAiGrpcClient grpcClient,
            AiFallbackPolicy fallbackPolicy,
            AiCircuitBreaker circuitBreaker,
            AiConservativeModeState conservativeModeState) {
        this.grpcClient = Objects.requireNonNull(grpcClient, "grpcClient cannot be null");
        this.fallbackPolicy = Objects.requireNonNull(fallbackPolicy, "fallbackPolicy cannot be null");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker cannot be null");
        this.conservativeModeState = Objects.requireNonNull(conservativeModeState, "conservativeModeState cannot be null");
    }

    public static LocalAiRuntimeService fromConfiguration() {
        String host = AppConfig.get(AppConfigKeys.AI_LOCAL_GRPC_HOST, "127.0.0.1");
        int port = AppConfig.getInt(AppConfigKeys.AI_LOCAL_GRPC_PORT, 8010);
        long timeoutMs = AppConfig.getLong(AppConfigKeys.AI_LOCAL_GRPC_TIMEOUT_MS, 1500L);
        return new LocalAiRuntimeService(new PythonAiGrpcClient(host, port, timeoutMs));
    }

    public static boolean isGrpcAdvisoryEnabled() {
        return AppConfig.getBoolean(AppConfigKeys.AI_LOCAL_GRPC_ENABLED, true);
    }

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
            SignalReviewRequest grpcRequest = toGrpcSignalRequest(request);
            PythonAiGrpcClient.SignalReviewResult result = grpcClient.analyzeSignal(grpcRequest);

            circuitBreaker.recordSuccess();
            conservativeModeState.disable("gRPC advisory healthy");
            lastError.set("");

            return mapToTradeReviewResponse(request, result, System.currentTimeMillis() - startMs);
        } catch (Exception exception) {
            onGrpcFailure(exception);
            return fallbackPolicy.fallbackTradeReview(request);
        }
    }

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
            onGrpcFailure(exception);
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

    private SignalReviewRequest toGrpcSignalRequest(AiTradeReviewRequest request) {
        String symbol = request.getSymbol() == null ? "UNKNOWN" : request.getSymbol().toString('/');
        String regime = request.getRiskContext() == null || request.getRiskContext().getMarketBehavior() == null
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
            decision = result.sizeAdjustment() < 0.99 ? AiDecision.APPROVE_WITH_REDUCED_SIZE : AiDecision.APPROVE;
        } else if ("WAIT".equalsIgnoreCase(result.recommendation())) {
            decision = AiDecision.WAIT;
        } else {
            decision = AiDecision.ESCALATE_TO_MANUAL_REVIEW;
        }

        double safeAdjustment = Math.max(0.0, Math.min(1.0, result.sizeAdjustment() <= 0.0 ? 1.0 : result.sizeAdjustment()));
        double suggestedPosition = request.getRiskDecision() == null
                ? 0.0
                : request.getRiskDecision().getFinalPositionSize() * safeAdjustment;

        return AiTradeReviewResponse.builder()
                .decision(decision)
                .confidence(Math.max(0.0, Math.min(1.0, result.aiConfidence())))
                .suggestedRiskMultiplier(safeAdjustment)
                .suggestedPositionSize(suggestedPosition)
                .recommendedExecutionStrategy("LOCAL_GRPC_ADVISORY")
                .confirmations(result.approved() ? List.of("Python advisory approved signal") : List.of())
                .concerns(result.approved() ? List.of() : List.of("Python advisory requested caution"))
                .blockers(List.of())
                .recommendations(result.details().isBlank() ? List.of(result.recommendation()) : List.of(result.details()))
                .explanation("Advisory decision from local Python gRPC runtime: " + result.recommendation())
                .modelName(SERVICE_NAME)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(latencyMs)
                .hadErrors(false)
                .build();
    }

    private void onGrpcFailure(Exception exception) {
        circuitBreaker.recordFailure();
        conservativeModeState.enable("gRPC failure");
        String message = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        lastError.set(message);
        log.warn("Local gRPC AI advisory failed: {}", message);
    }

    @Override
    public void close() {
        grpcClient.close();
    }
}
