package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import  org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the assignment of a strategy to a specific symbol/timeframe.
 * Tracks which strategy should be used for trading a particular combination.
 */
@Getter
@Builder
public class StrategyAssignment {
    private final String assignmentId;
    private final String symbol;
    private final Timeframe timeframe;
    private final String strategyId;

    @Builder.Default
    private final StrategyAssignmentMode mode = StrategyAssignmentMode.AUTO;

    @Builder.Default
    private final AssignedBy assignedBy = AssignedBy.SYSTEM;

    @Builder.Default
    private final double scoreAtAssignment = 0.0;

    @Builder.Default
    private final Instant assignedAt = Instant.now();

    @Nullable
    private final Instant expiresAt; // optional expiry

    @Builder.Default
    private final boolean active = true;

    @Nullable
    private final String reason; // why this was assigned

    @Builder.Default
    private final String warnings = ""; // warnings at assignment time

    @Builder.Default
    private final boolean locked = false; // locked prevents auto-replacement

    public enum StrategyAssignmentMode {
        AUTO("Automatic", "Strategy selected automatically by system"),
        MANUAL("Manual", "User manually selected this strategy"),
        AI_ASSISTED("AI Assisted", "AI recommended this strategy"),
        DISABLED("Disabled", "Strategy is disabled for this symbol");

        private final String displayName;
        private final String description;

        StrategyAssignmentMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum AssignedBy {
        AUTO("Automatic Selection"),
        USER("User Selection"),
        AI("AI Recommendation"),
        SYSTEM("System Default");

        private final String displayName;

        AssignedBy(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return active && !isExpired() && mode != StrategyAssignmentMode.DISABLED;
    }

    public static String generateId() {
        return "assign_" + UUID.randomUUID().toString();
    }

    @Override
    public String toString() {
        return String.format("%s/%s -> %s (mode: %s, locked: %s)", 
                symbol, timeframe.getCode(), strategyId, mode.getDisplayName(), locked);
    }
}
