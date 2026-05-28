package org.investpro.execution.lifecycle;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe lifecycle monitor for broker and blockchain execution states.
 */
public final class ExecutionLifecycleMonitor {
    private static final ExecutionLifecycleMonitor INSTANCE = new ExecutionLifecycleMonitor();

    private final Map<String, ExecutionLifecycleStatus> statuses = new ConcurrentHashMap<>();

    private ExecutionLifecycleMonitor() {
    }

    public static ExecutionLifecycleMonitor getInstance() {
        return INSTANCE;
    }

    public ExecutionLifecycleStatus update(@NotNull ExecutionLifecycleStatus status) {
        statuses.put(status.clientOrderId(), status);
        return status;
    }

    public ExecutionLifecycleStatus transition(
            @NotNull String clientOrderId,
            ExecutionLifecycleState state,
            BigDecimal filledQuantity,
            BigDecimal averageFillPrice,
            String reason) {
        ExecutionLifecycleStatus previous = statuses.get(clientOrderId);
        ExecutionLifecycleStatus next = new ExecutionLifecycleStatus(
                clientOrderId,
                previous == null ? "" : previous.brokerOrderId(),
                previous == null ? "" : previous.venueId(),
                previous == null ? "" : previous.symbol(),
                state,
                previous == null ? BigDecimal.ZERO : previous.requestedQuantity(),
                filledQuantity == null && previous != null ? previous.filledQuantity() : filledQuantity,
                averageFillPrice == null && previous != null ? previous.averageFillPrice() : averageFillPrice,
                reason,
                previous == null ? 0 : previous.blockchainConfirmations(),
                Instant.now(),
                previous == null ? Map.of() : previous.metadata());
        return update(next);
    }

    public Optional<ExecutionLifecycleStatus> find(String clientOrderId) {
        return Optional.ofNullable(statuses.get(clientOrderId));
    }

    public List<ExecutionLifecycleStatus> active() {
        List<ExecutionLifecycleStatus> result = new ArrayList<>();
        for (ExecutionLifecycleStatus status : statuses.values()) {
            if (!status.terminal()) {
                result.add(status);
            }
        }
        return result;
    }
}
