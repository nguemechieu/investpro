package org.investpro.decision;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Estimated holding period for a trade.
 * Based on setup type, timeframe, market regime, and strategy characteristics.
 */
public record HoldingPeriodEstimate(
        @NotNull Duration minimumHoldTime, // minimum duration to hold position
        @NotNull Duration expectedHoldTime, // expected/target duration
        @NotNull Duration maximumHoldTime, // maximum duration before exit
        @NotNull String estimationReason, // why this holding period was estimated
        boolean isMicroHold // true if < 5 minutes (scalp)
) {

    public boolean isScalpSetup() {
        return isMicroHold || expectedHoldTime.toMinutes() < 5;
    }

    public boolean isSwingSetup() {
        return expectedHoldTime.toHours() >= 1 && expectedHoldTime.toDays() < 10;
    }

    public boolean isPositionSetup() {
        return expectedHoldTime.toDays() >= 10;
    }

    /**
     * Return expected hold time as human-readable string
     */
    public String getHoldTimeFormatted() {
        long minutes = expectedHoldTime.toMinutes();
        long hours = expectedHoldTime.toHours();
        long days = expectedHoldTime.toDays();

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else {
            return minutes + "m";
        }
    }
}
