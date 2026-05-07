package org.investpro.strategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents the assignment of a strategy to a specific symbol/timeframe.
 *
 * A StrategyAssignment tells the engine which strategy should be used for a
 * specific market and timeframe combination.
 *
 * Examples:
 * - BTC/USD + H1 -> trend-following
 * - EUR/USD + M15 -> mean-reversion
 * - AAPL + D1 -> breakout
 */
@Getter
@Builder(toBuilder = true)
public class StrategyAssignment {

    /**
     * Unique assignment ID.
     */
    @Builder.Default
    private final String assignmentId = generateId();

    /**
     * Trading symbol, for example BTC/USD, EUR/USD, AAPL.
     */
    private final String symbol;

    /**
     * Trading timeframe, for example M15, H1, H4, D1.
     */
    private final Timeframe timeframe;

    /**
     * Strategy ID registered in StrategyRegistry.
     *
     * Example:
     * - trend-following
     * - mean-reversion
     * - breakout
     */
    private final String strategyId;

    /**
     * Assignment mode.
     */
    @Builder.Default
    private final StrategyAssignmentMode mode = StrategyAssignmentMode.AUTO;

    /**
     * Who created this assignment.
     */
    @Builder.Default
    private final AssignedBy assignedBy = AssignedBy.SYSTEM;

    /**
     * Score at the time this strategy was assigned.
     */
    @Builder.Default
    private final double scoreAtAssignment = 0.0;

    /**
     * Time this assignment was created.
     */
    @Builder.Default
    private final Instant assignedAt = Instant.now();

    /**
     * Optional expiration time.
     *
     * null means the assignment does not expire.
     */
    @Nullable
    private final Instant expiresAt;

    /**
     * Whether this assignment is currently active.
     */
    @Builder.Default
    private final boolean active = true;

    /**
     * Human-readable reason for this assignment.
     */
    @Nullable
    private final String reason;

    /**
     * Warnings captured at assignment time.
     */
    @Builder.Default
    private final String warnings = "";

    /**
     * Locked assignments cannot be automatically replaced.
     */
    @Builder.Default
    private final boolean locked = false;

    /**
     * Optional reason why this assignment was disabled.
     */
    @Nullable
    private final String disableReason;

    @JsonCreator
    public StrategyAssignment(
            @JsonProperty("assignmentId") String assignmentId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("timeframe") Timeframe timeframe,
            @JsonProperty("strategyId") String strategyId,
            @JsonProperty("mode") StrategyAssignmentMode mode,
            @JsonProperty("assignedBy") AssignedBy assignedBy,
            @JsonProperty("scoreAtAssignment") double scoreAtAssignment,
            @JsonProperty("assignedAt") Instant assignedAt,
            @JsonProperty("expiresAt") @Nullable Instant expiresAt,
            @JsonProperty("active") boolean active,
            @JsonProperty("reason") @Nullable String reason,
            @JsonProperty("warnings") String warnings,
            @JsonProperty("locked") boolean locked,
            @JsonProperty("disableReason") @Nullable String disableReason
    ) {
        this.assignmentId = isBlank(assignmentId) ? generateId() : assignmentId;
        this.symbol = normalizeRequired(symbol, "symbol");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe must not be null");
        this.strategyId = normalizeRequired(strategyId, "strategyId");

        this.mode = mode == null ? StrategyAssignmentMode.AUTO : mode;
        this.assignedBy = assignedBy == null ? AssignedBy.SYSTEM : assignedBy;
        this.scoreAtAssignment = scoreAtAssignment;
        this.assignedAt = assignedAt == null ? Instant.now() : assignedAt;
        this.expiresAt = expiresAt;
        this.active = active;
        this.reason = normalizeNullable(reason);
        this.warnings = warnings == null ? "" : warnings;
        this.locked = locked;
        this.disableReason = normalizeNullable(disableReason);
    }

    public boolean isDisabled() {
        return mode == StrategyAssignmentMode.DISABLED || !active;
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return active
                && !isExpired()
                && mode != StrategyAssignmentMode.DISABLED
                && !isBlank(symbol)
                && timeframe != null
                && !isBlank(strategyId);
    }

    public boolean canBeAutoReplaced() {
        return !locked
                && mode != StrategyAssignmentMode.MANUAL
                && mode != StrategyAssignmentMode.DISABLED;
    }

    public boolean isManual() {
        return mode == StrategyAssignmentMode.MANUAL;
    }

    public boolean isAuto() {
        return mode == StrategyAssignmentMode.AUTO;
    }

    public boolean isAiAssisted() {
        return mode == StrategyAssignmentMode.AI_ASSISTED;
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isBlank();
    }

    public String getDisableReason() {
        if (!isDisabled()) {
            return "";
        }

        if (disableReason != null && !disableReason.isBlank()) {
            return disableReason;
        }

        if (reason != null && !reason.isBlank()) {
            return reason;
        }

        return "Strategy assignment is disabled.";
    }

    public StrategyAssignment disabled(@NotNull String disableReason) {
        return this.toBuilder()
                .active(false)
                .mode(StrategyAssignmentMode.DISABLED)
                .disableReason(disableReason)
                .build();
    }

    public StrategyAssignment activated(@Nullable String reason) {
        return this.toBuilder()
                .active(true)
                .mode(StrategyAssignmentMode.AUTO)
                .reason(reason)
                .disableReason(null)
                .build();
    }

    public StrategyAssignment locked() {
        return this.toBuilder()
                .locked(true)
                .build();
    }

    public StrategyAssignment unlocked() {
        return this.toBuilder()
                .locked(false)
                .build();
    }

    public static @NotNull String generateId() {
        return "assign_" + UUID.randomUUID();
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public String toString() {
        return String.format(
                "%s/%s -> %s (mode: %s, assignedBy: %s, active: %s, locked: %s, expired: %s)",
                symbol,
                timeframe != null ? timeframe.getCode() : "UNKNOWN",
                strategyId,
                mode != null ? mode.getDisplayName() : "UNKNOWN",
                assignedBy != null ? assignedBy.getDisplayName() : "UNKNOWN",
                active,
                locked,
                isExpired()
        );
    }

    @Getter
    public enum StrategyAssignmentMode {

        AUTO(
                "Automatic",
                "Strategy selected automatically by system."
        ),

        MANUAL(
                "Manual",
                "User manually selected this strategy."
        ),

        AI_ASSISTED(
                "AI Assisted",
                "AI recommended this strategy."
        ),

        DISABLED(
                "Disabled",
                "Strategy is disabled for this symbol/timeframe."
        );

        private final String displayName;
        private final String description;

        StrategyAssignmentMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    @Getter
    public enum AssignedBy {

        AUTO("Automatic Selection"),
        USER("User Selection"),
        AI("AI Recommendation"),
        SYSTEM("System Default");

        private final String displayName;

        AssignedBy(String displayName) {
            this.displayName = displayName;
        }
    }
}