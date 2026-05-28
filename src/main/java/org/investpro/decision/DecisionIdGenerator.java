package org.investpro.decision;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * High-performance decision ID generator.
 *
 * <p>Uses a monotonically increasing atomic long sequence for SIMULATION and LIGHTWEIGHT
 * modes (virtually zero allocation cost), and UUID for LIVE and PAPER modes where a
 * globally unique, persisted identifier is required.</p>
 *
 * <p>Under mass backtesting, {@code UUID.randomUUID()} triggers significant GC pressure
 * due to internal SecureRandom usage and object allocation. This generator avoids that
 * entirely in simulation mode.</p>
 */
public final class DecisionIdGenerator {

    private static final AtomicLong SEQUENCE = new AtomicLong(0L);
    private static final String SIMULATION_PREFIX = "sim-";

    private DecisionIdGenerator() {}

    /**
     * Generate a decision ID appropriate for the given mode.
     *
     * @param mode the decision execution mode
     * @return a string ID; sequential for simulation, UUID for live/paper
     */
    public static String generate(DecisionMode mode) {
        if (mode == null || mode.useUuidIds) {
            return UUID.randomUUID().toString();
        }
        return SIMULATION_PREFIX + SEQUENCE.incrementAndGet();
    }

    /**
     * Generate a UUID-based ID regardless of mode.
     * Use only where global uniqueness and persistence are required.
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a sequential simulation ID without the prefix.
     * Extremely cheap; suitable for tight loops over millions of decisions.
     */
    public static long generateSequential() {
        return SEQUENCE.incrementAndGet();
    }

    /**
     * Reset the sequential counter (test-only).
     * Not safe to call in production concurrently with {@link #generate}.
     */
    static void resetForTest() {
        SEQUENCE.set(0L);
    }
}
