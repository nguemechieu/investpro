package org.investpro.backtesting.simulation;

/**
 * Simulated broker fill returned by the execution layer.
 */
public record ExecutionFill(
        int candleIndex,
        double price,
        double quantity,
        double commission,
        boolean partial,
        String reason
) {
}
