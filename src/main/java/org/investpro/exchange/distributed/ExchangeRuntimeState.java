package org.investpro.exchange.distributed;

import org.investpro.exchange.resilience.model.ExchangeConnectivityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable snapshot of the runtime state of a single exchange worker.
 *
 * <p>Design-only record — used as the return type of
 * {@link RemoteExchangeWorker#getWorkerState()} and
 * {@link CloudExecutionNode#getNodeRuntimeStates()}.
 * Captures connectivity, pending order count, and circuit breaker status at a point in time.
 */
public record ExchangeRuntimeState(
        @NotNull String exchangeName,
        @NotNull String workerId,
        @NotNull ExchangeConnectivityState connectivityState,
        int pendingOrderCount,
        int openPositionCount,
        boolean circuitBreakerOpen,
        double uptimeSeconds,
        @Nullable String lastErrorMessage,
        @NotNull Instant capturedAt
) {

    /**
     * Creates a healthy runtime state for a fully operational exchange worker.
     *
     * @param exchangeName the exchange name
     * @param workerId     the worker's unique ID
     * @return an operational {@link ExchangeRuntimeState}
     */
    public static @NotNull ExchangeRuntimeState operational(
            @NotNull String exchangeName,
            @NotNull String workerId
    ) {
        return new ExchangeRuntimeState(
                exchangeName, workerId,
                ExchangeConnectivityState.CONNECTED,
                0, 0, false, 0.0, null,
                Instant.now()
        );
    }

    /** Returns {@code true} if the exchange is currently operational for live trading. */
    public boolean isOperational() {
        return connectivityState.isOperational() && !circuitBreakerOpen;
    }
}
