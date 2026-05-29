package org.investpro.ai.local.grpc;

import java.time.Instant;

public record AiGrpcHealthStatus(
        boolean ok,
        String status,
        String serviceName,
        double avgLatencyMs,
        String circuitState,
        boolean conservativeMode,
        String lastError,
        long requestsPerMinute,
        Instant timestamp) {

    public static AiGrpcHealthStatus unavailable(String reason) {
        return new AiGrpcHealthStatus(
                false,
                "UNAVAILABLE",
                "investpro-local-ai-grpc",
                0.0,
                "OPEN",
                true,
                reason == null ? "Unavailable" : reason,
                0L,
                Instant.now());
    }
}
