package org.investpro.ai.local.grpc;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class AiCircuitBreaker {

    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicReference<Instant> openedAt = new AtomicReference<>(Instant.EPOCH);

    public AiCircuitBreaker(int failureThreshold, Duration openDuration) {
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openDuration = openDuration == null ? Duration.ofSeconds(20) : openDuration;
    }

    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }

        if (current == State.OPEN) {
            Instant opened = openedAt.get();
            if (opened.plus(openDuration).isBefore(Instant.now())) {
                state.set(State.HALF_OPEN);
                return true;
            }
            return false;
        }

        return true;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.set(State.OPEN);
            openedAt.set(Instant.now());
        }
    }

    public State state() {
        return state.get();
    }

    public int consecutiveFailures() {
        return consecutiveFailures.get();
    }
}
