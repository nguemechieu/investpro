package org.investpro.ai.local.grpc;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AiConservativeModeState {

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicReference<String> reason = new AtomicReference<>("None");
    private final AtomicReference<Instant> updatedAt = new AtomicReference<>(Instant.EPOCH);

    public void enable(String why) {
        enabled.set(true);
        reason.set(why == null || why.isBlank() ? "AI runtime unavailable" : why.trim());
        updatedAt.set(Instant.now());
    }

    public void disable(String why) {
        enabled.set(false);
        reason.set(why == null || why.isBlank() ? "Recovered" : why.trim());
        updatedAt.set(Instant.now());
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public String reason() {
        return reason.get();
    }

    public Instant updatedAt() {
        return updatedAt.get();
    }
}
