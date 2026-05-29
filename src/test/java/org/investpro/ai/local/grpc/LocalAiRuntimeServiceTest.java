package org.investpro.ai.local.grpc;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.investpro.ai.AiDecision;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAiRuntimeServiceTest {

    @Test
    void usesFallbackAndEnablesConservativeModeWhenGrpcFails() {
        FakePythonClient failingClient = new FakePythonClient(true);
        AiFallbackPolicy fallbackPolicy = new AiFallbackPolicy(new TestFallbackAiService());
        LocalAiRuntimeService service = new LocalAiRuntimeService(
                failingClient,
                fallbackPolicy,
                new AiCircuitBreaker(1, Duration.ofSeconds(5)),
                new AiConservativeModeState());

        AiTradeReviewResponse response = service.reviewTrade(sampleRequest());

        assertThat(response.getDecision()).isEqualTo(AiDecision.WAIT);
        assertThat(service.healthStatus().conservativeMode()).isTrue();
    }

    @Test
    void mapsGrpcApproveResponseToAiApprove() {
        FakePythonClient healthyClient = new FakePythonClient(false);
        LocalAiRuntimeService service = new LocalAiRuntimeService(healthyClient);

        AiTradeReviewResponse response = service.reviewTrade(sampleRequest());

        assertThat(response.getDecision()).isIn(AiDecision.APPROVE, AiDecision.APPROVE_WITH_REDUCED_SIZE);
        assertThat(response.getConfidence()).isGreaterThanOrEqualTo(0.0);
    }

    private AiTradeReviewRequest sampleRequest() {
        return AiTradeReviewRequest.builder()
                .signalSide("BUY")
                .signalConfidence(0.75)
                .strategyName("Trend")
                .currentPrice(1.1)
                .spreadPercent(0.001)
                .volatilityPercent(0.01)
                .accountEquity(10000)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private static class TestFallbackAiService implements AiReasoningService {
        @Override
        public AiTradeReviewResponse reviewTrade(AiTradeReviewRequest request) {
            return AiTradeReviewResponse.builder()
                    .decision(AiDecision.WAIT)
                    .confidence(0.4)
                    .suggestedRiskMultiplier(0.5)
                    .suggestedPositionSize(0.0)
                    .recommendedExecutionStrategy("FALLBACK")
                    .confirmations(List.of())
                    .concerns(List.of("fallback"))
                    .blockers(List.of())
                    .recommendations(List.of("manual check"))
                    .explanation("fallback")
                    .modelName("fallback")
                    .createdAt(LocalDateTime.now())
                    .processingTimeMs(1L)
                    .hadErrors(false)
                    .build();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getServiceName() {
            return "fallback";
        }
    }

    private static class FakePythonClient extends PythonAiGrpcClient {
        private final boolean fail;

        private FakePythonClient(boolean fail) {
            super("127.0.0.1", 6553, 100);
            this.fail = fail;
        }

        @Override
        public SignalReviewResult analyzeSignal(org.investpro.ai.local.grpc.generated.SignalReviewRequest request) {
            if (fail) {
                throw new StatusRuntimeException(Status.UNAVAILABLE.withDescription("simulated"));
            }
            return new SignalReviewResult(true, 0.8, "APPROVE", 0.95, "ok");
        }

        @Override
        public AiGrpcHealthStatus health(boolean conservativeMode, String circuitState, long requestsPerMinute,
                String lastError) {
            return new AiGrpcHealthStatus(true, "SERVING", "test", 2.0, circuitState, conservativeMode, lastError,
                    requestsPerMinute, java.time.Instant.now());
        }

        @Override
        public void close() {
            // no-op for test
        }
    }
}
