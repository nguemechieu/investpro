package org.investpro.strategy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents the assignment of a strategy to a specific symbol/timeframe.
 * <p>
 * A StrategyAssignment tells the engine which strategy should be used for a
 * specific market and timeframe combination.
 * <p>
 * Examples:
 * - BTC/USD + H1 -> trend-following
 * - EUR/USD + M15 -> mean-reversion
 * - AAPL + D1 -> breakout
 * <p>
 * This class is intentionally a clean data model.
 * It must not contain execution, exchange, indicator, news, AI, or SystemCore logic.
 */
@Getter
@ToString
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
     * Strategy ID assigned to this symbol/timeframe.
     *
     * Examples:
     * - trend-following
     * - mean-reversion
     * - breakout
     * - user.simple_ema
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
     *
     * Usually produced by backtesting, paper trading, voting, or consensus ranking.
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
     *
     * Manual assignments should normally be locked.
     */
    @Builder.Default
    private final boolean locked = false;

    /**
     * Optional reason why this assignment was disabled.
     */
    @Nullable
    private final String disableReason;

    /**
     * Jackson constructor.
     * <p>
     * Keeps JSON loading safe while preserving validation/defaults.
     */
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
            @JsonProperty("active") Boolean active,
            @JsonProperty("reason") @Nullable String reason,
            @JsonProperty("warnings") String warnings,
            @JsonProperty("locked") Boolean locked,
            @JsonProperty("disableReason") @Nullable String disableReason
    ) {
        this.assignmentId = isBlank(assignmentId) ? generateId() : assignmentId.trim();
        this.symbol = normalizeRequired(symbol, "symbol");
        this.timeframe = requireTimeframe(timeframe);
        this.strategyId = normalizeRequired(strategyId, "strategyId");

        this.mode = mode == null ? StrategyAssignmentMode.AUTO : mode;
        this.assignedBy = assignedBy == null ? AssignedBy.SYSTEM : assignedBy;
        this.scoreAtAssignment = sanitizeScore(scoreAtAssignment);
        this.assignedAt = assignedAt == null ? Instant.now() : assignedAt;
        this.expiresAt = expiresAt;
        this.active = active == null || active;
        this.reason = normalizeNullable(reason);
        this.warnings = warnings == null ? "" : warnings.trim();
        this.locked = locked != null && locked;
        this.disableReason = normalizeNullable(disableReason);
    }

    /**
     * True when assignment is disabled either by mode or active flag.
     */
    public boolean isDisabled() {
        return mode == StrategyAssignmentMode.DISABLED || !active;
    }

    /**
     * True when assignment has an expiry date and that expiry is in the past.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * True when assignment can be used for live/paper/backtest routing.
     */
    public boolean isValid() {
        return active
                && !isExpired()
                && mode != StrategyAssignmentMode.DISABLED
                && !isBlank(symbol)
                && timeframe != null
                && !isBlank(strategyId);
    }

    /**
     * True if the system is allowed to replace this assignment automatically.
     */
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

    public boolean hasReason() {
        return reason != null && !reason.isBlank();
    }

    public boolean hasDisableReason() {
        return disableReason != null && !disableReason.isBlank();
    }

    /**
     * Returns the best available disabled reason.
     */
    public String getDisableReason() {
        if (!isDisabled()) {
            return "";
        }

        if (!isBlank(disableReason)) {
            return disableReason;
        }

        if (!isBlank(reason)) {
            return reason;
        }

        if (mode == StrategyAssignmentMode.DISABLED) {
            return "Strategy assignment mode is disabled.";
        }

        if (!active) {
            return "Strategy assignment is inactive.";
        }

        return "Strategy assignment is disabled.";
    }

    /**
     * Stable key for maps: symbol::timeframe.
     */
    @JsonIgnore
    public String getKey() {
        return key(symbol, timeframe);
    }

    /**
     * Human-readable label for UI/logging.
     */
    @JsonIgnore
    public String getDisplayName() {
        return "%s / %s -> %s".formatted(
                symbol,
                timeframe == null ? "UNKNOWN" : timeframe.getCode(),
                strategyId
        );
    }

    /**
     * Disable this assignment.
     */
    public StrategyAssignment disabled(@NotNull String disableReason) {
        return this.toBuilder()
                .active(false)
                .mode(StrategyAssignmentMode.DISABLED)
                .disableReason(normalizeNullable(disableReason))
                .build();
    }

    /**
     * Activate this assignment as AUTO.
     */
    public StrategyAssignment activated(@Nullable String reason) {
        return this.toBuilder()
                .active(true)
                .mode(StrategyAssignmentMode.AUTO)
                .reason(normalizeNullable(reason))
                .disableReason(null)
                .build();
    }

    /**
     * Lock this assignment from auto-replacement.
     */
    public StrategyAssignment locked() {
        return this.toBuilder()
                .locked(true)
                .build();
    }

    /**
     * Unlock this assignment so the system may auto-replace it.
     */
    public StrategyAssignment unlocked() {
        return this.toBuilder()
                .locked(false)
                .build();
    }

    /**
     * Return copy with new score.
     */
    public StrategyAssignment withScore(double score) {
        return this.toBuilder()
                .scoreAtAssignment(sanitizeScore(score))
                .build();
    }

    /**
     * Return copy with new reason.
     */
    public StrategyAssignment withReason(@Nullable String reason) {
        return this.toBuilder()
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Return copy with new warnings.
     */
    public StrategyAssignment withWarnings(@Nullable String warnings) {
        return this.toBuilder()
                .warnings(warnings == null ? "" : warnings.trim())
                .build();
    }

    /**
     * Return copy with expiration.
     */
    public StrategyAssignment withExpiration(@Nullable Instant expiresAt) {
        return this.toBuilder()
                .expiresAt(expiresAt)
                .build();
    }

    /**
     * Return copy as manual user assignment.
     */
    public StrategyAssignment asManual(@Nullable String reason) {
        return this.toBuilder()
                .mode(StrategyAssignmentMode.MANUAL)
                .assignedBy(AssignedBy.USER)
                .locked(true)
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Return copy as AI-assisted assignment.
     */
    public StrategyAssignment asAiAssisted(@Nullable String reason) {
        return this.toBuilder()
                .mode(StrategyAssignmentMode.AI_ASSISTED)
                .assignedBy(AssignedBy.AI)
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Return copy as automatic system assignment.
     */
    public StrategyAssignment asAuto(@Nullable String reason) {
        return this.toBuilder()
                .mode(StrategyAssignmentMode.AUTO)
                .assignedBy(AssignedBy.SYSTEM)
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Factory for automatic system assignment.
     */
    public static StrategyAssignment auto(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            double score,
            @Nullable String reason
    ) {
        return StrategyAssignment.builder()
                .symbol(normalizeRequired(symbol, "symbol"))
                .timeframe(requireTimeframe(timeframe))
                .strategyId(normalizeRequired(strategyId, "strategyId"))
                .mode(StrategyAssignmentMode.AUTO)
                .assignedBy(AssignedBy.SYSTEM)
                .scoreAtAssignment(sanitizeScore(score))
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Factory for manual user assignment.
     */
    public static StrategyAssignment manual(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            @Nullable String reason
    ) {
        return StrategyAssignment.builder()
                .symbol(normalizeRequired(symbol, "symbol"))
                .timeframe(requireTimeframe(timeframe))
                .strategyId(normalizeRequired(strategyId, "strategyId"))
                .mode(StrategyAssignmentMode.MANUAL)
                .assignedBy(AssignedBy.USER)
                .reason(normalizeNullable(reason))
                .locked(true)
                .build();
    }

    /**
     * Factory for AI-assisted assignment.
     */
    public static StrategyAssignment aiAssisted(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            double score,
            @Nullable String reason
    ) {
        return StrategyAssignment.builder()
                .symbol(normalizeRequired(symbol, "symbol"))
                .timeframe(requireTimeframe(timeframe))
                .strategyId(normalizeRequired(strategyId, "strategyId"))
                .mode(StrategyAssignmentMode.AI_ASSISTED)
                .assignedBy(AssignedBy.AI)
                .scoreAtAssignment(sanitizeScore(score))
                .reason(normalizeNullable(reason))
                .build();
    }

    /**
     * Factory for disabled assignment.
     */
    public static StrategyAssignment disabled(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            @NotNull String disableReason
    ) {
        return StrategyAssignment.builder()
                .symbol(normalizeRequired(symbol, "symbol"))
                .timeframe(requireTimeframe(timeframe))
                .strategyId(normalizeRequired(strategyId, "strategyId"))
                .mode(StrategyAssignmentMode.DISABLED)
                .assignedBy(AssignedBy.SYSTEM)
                .active(false)
                .disableReason(normalizeNullable(disableReason))
                .build();
    }

    /**
     * Stable map key helper.
     */
    public static String key(@NotNull String symbol, @NotNull Timeframe timeframe) {
        return normalizeRequired(symbol, "symbol") + "::" + requireTimeframe(timeframe).getCode();
    }

    public static @NotNull String generateId() {
        return "assign_" + UUID.randomUUID();
    }

    private static Timeframe requireTimeframe(Timeframe timeframe) {
        if (timeframe == null) {
            throw new IllegalArgumentException("timeframe must not be null");
        }

        return timeframe;
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

    private static double sanitizeScore(double score) {
        if (!Double.isFinite(score)) {
            return 0.0;
        }

        return Math.max(0.0, score);
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