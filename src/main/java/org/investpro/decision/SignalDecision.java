package org.investpro.decision;

import org.investpro.utils.Side;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Normalized signal-level decision before trade review.
 */
public record SignalDecision(
        @NotNull Side side,
        double signalStrength,
        @NotNull List<String> reasons,
        @NotNull List<String> warnings,
        @NotNull List<String> blockers) {

    @Contract("_, _ -> new")
    public static @NonNull SignalDecision of(@NotNull Side side, double signalStrength) {
        return new SignalDecision(side, signalStrength, List.of(), List.of(), List.of());
    }
}
