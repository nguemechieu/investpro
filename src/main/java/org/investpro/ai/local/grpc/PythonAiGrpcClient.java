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
import org.investpro.ai.local.grpc.generated.SignalReviewRequest;
import org.investpro.ai.local.grpc.generated.SignalReviewResponse;

import java.util.concurrent.TimeUnit;

@Slf4j
public class PythonAiGrpcClient implements AutoCloseable {

    public record SignalReviewResult(boolean approved, double aiConfidence, String recommendation,
            double sizeAdjustment, String details) {
    }

    public record BacktestReviewResult(boolean accepted, double aiScore, double overfitRisk, String warnings) {
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
