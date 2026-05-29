package org.investpro.ai.local.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.local.grpc.generated.BacktestReviewRequest;
import org.investpro.ai.local.grpc.generated.BacktestReviewResponse;
import org.investpro.ai.local.grpc.generated.HealthRequest;
import org.investpro.ai.local.grpc.generated.HealthResponse;
import org.investpro.ai.local.grpc.generated.InvestProAiServiceGrpc;
import org.investpro.ai.local.grpc.generated.RankedStrategy;
import org.investpro.ai.local.grpc.generated.RegimeDetectionRequest;
import org.investpro.ai.local.grpc.generated.RegimeDetectionResponse;
import org.investpro.ai.local.grpc.generated.RiskScoreRequest;
import org.investpro.ai.local.grpc.generated.RiskScoreResponse;
import org.investpro.ai.local.grpc.generated.SignalReviewRequest;
import org.investpro.ai.local.grpc.generated.SignalReviewResponse;
import org.investpro.ai.local.grpc.generated.StrategyRankingRequest;
import org.investpro.ai.local.grpc.generated.StrategyRankingResponse;
import org.investpro.ai.local.grpc.generated.StrategyReviewRequest;
import org.investpro.ai.local.grpc.generated.StrategyReviewResponse;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PythonAiGrpcClient implements AutoCloseable {

    public record SignalReviewResult(boolean approved, double aiConfidence, String recommendation,
            double sizeAdjustment, String details) {
    }

    public record BacktestReviewResult(boolean accepted, double aiScore, double overfitRisk, String warnings) {
    }

    public record StrategyReviewResult(boolean accepted, double aiScore, String recommendation,
            String reasons, String warnings) {
    }

    public record StrategyRankingResult(List<RankedStrategy> ranked, String recommendation, String warnings) {
    }

    public record RegimeDetectionResult(String regime, double confidence, String volatilityState,
            double trendStrength, String reasons) {
    }

    public record RiskScoreResult(double riskScore, String recommendation, double maxPositionSize, String reasons) {
    }

    private final ManagedChannel channel;
    private final InvestProAiServiceGrpc.InvestProAiServiceBlockingStub blockingStub;
    private final long timeoutMs;

    public PythonAiGrpcClient(String host, int port, long timeoutMs) {
        this.timeoutMs = Math.max(200L, timeoutMs);
        this.channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        this.blockingStub = InvestProAiServiceGrpc.newBlockingStub(channel);
    }

    public AiGrpcHealthStatus health(boolean conservativeMode, String circuitState, long requestsPerMinute,
            String lastError) {
        try {
            HealthResponse response = blockingStub
                    .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                    .health(HealthRequest.newBuilder().setSource("investpro-java").build());
            return new AiGrpcHealthStatus(
                    response.getOk(),
                    response.getStatus(),
                    response.getServiceName(),
                    response.getAvgLatencyMs(),
                    circuitState,
                    conservativeMode,
                    lastError == null ? "" : lastError,
                    requestsPerMinute,
                    java.time.Instant.now());
        } catch (StatusRuntimeException exception) {
            return AiGrpcHealthStatus.unavailable(exception.getStatus().getDescription());
        }
    }

    public SignalReviewResult analyzeSignal(SignalReviewRequest request) {
        SignalReviewResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .analyzeSignal(request);
        return new SignalReviewResult(
                response.getApproved(),
                response.getAiConfidence(),
                response.getRecommendation(),
                response.getSizeAdjustment(),
                String.join(";", response.getReasonsList()));
    }

    public BacktestReviewResult reviewBacktest(BacktestReviewRequest request) {
        BacktestReviewResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .reviewBacktest(request);
        return new BacktestReviewResult(
                response.getAccepted(),
                response.getAiScore(),
                response.getOverfitRisk(),
                String.join(";", response.getWarningsList()));
    }

    public StrategyReviewResult reviewStrategy(StrategyReviewRequest request) {
        StrategyReviewResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .reviewStrategy(request);
        return new StrategyReviewResult(
                response.getAccepted(),
                response.getAiScore(),
                response.getRecommendation(),
                String.join(";", response.getReasonsList()),
                String.join(";", response.getWarningsList()));
    }

    public StrategyRankingResult rankStrategies(StrategyRankingRequest request) {
        StrategyRankingResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .rankStrategies(request);
        return new StrategyRankingResult(
                List.copyOf(response.getRankedList()),
                response.getRecommendation(),
                String.join(";", response.getWarningsList()));
    }

    public RegimeDetectionResult detectRegime(RegimeDetectionRequest request) {
        RegimeDetectionResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .detectRegime(request);
        return new RegimeDetectionResult(
                response.getRegime(),
                response.getConfidence(),
                response.getVolatilityState(),
                response.getTrendStrength(),
                String.join(";", response.getReasonsList()));
    }

    public RiskScoreResult scoreRisk(RiskScoreRequest request) {
        RiskScoreResponse response = blockingStub
                .withDeadlineAfter(timeoutMs, TimeUnit.MILLISECONDS)
                .scoreRisk(request);
        return new RiskScoreResult(
                response.getRiskScore(),
                response.getRecommendation(),
                response.getMaxPositionSize(),
                String.join(";", response.getReasonsList()));
    }

    @Override
    public void close() {
        try {
            channel.shutdown();
            channel.awaitTermination(1500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.debug("Error while closing gRPC channel", exception);
        }
    }
}
