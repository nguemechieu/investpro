package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Complete definition of a no-code trading strategy as authored in the
 * {@code StrategyBuilderPanel} and persisted to JSON.
 *
 * <p>A {@code NoCodeStrategyDefinition} is the source-of-truth for a no-code
 * strategy. The {@link NoCodeStrategyCompiler} validates and computes warmup bars.
 * The {@link NoCodeStrategyRuntime} evaluates the definition against live or
 * historical market data. The {@link NoCodeStrategyAdapter} wraps the runtime
 * and presents it as a standard {@link org.investpro.strategy.TradingStrategy}.</p>
 *
 * <p>Instances of this class are immutable after construction. Use the builder
 * and create a new instance to update any field.</p>
 *
 * <p><strong>CRITICAL:</strong> A definition by itself cannot submit any order.
 * It can only produce a {@link org.investpro.strategy.StrategySignal}.</p>
 */
@Getter
@Builder
@ToString
public class NoCodeStrategyDefinition {

    /** Unique strategy identifier. Auto-generated if not provided. */
    @Builder.Default
    private final String strategyId = UUID.randomUUID().toString();

    /** Human-readable strategy name. Must not be blank. */
    private final String name;

    /** Short description of the strategy logic. */
    private final String description;

    /** Primary symbol this strategy is designed for (e.g. "BTC/USD"). Empty = any. */
    private final String symbol;

    /** Primary timeframe (e.g. "1h"). Empty = any. */
    private final String timeframe;

    /** Author username or display name. */
    private final String author;

    /** Version string (e.g. "1.0.0"). */
    @Builder.Default
    private final String version = "1.0.0";

    /**
     * Entry rules. At least one rule is required.
     * Entry rules fire to open new positions.
     */
    @Singular
    private final List<NoCodeRule> entryRules;

    /**
     * Exit rules. At least one rule is recommended.
     * Exit rules fire to close open positions.
     */
    @Singular
    private final List<NoCodeRule> exitRules;

    /** Risk and money-management settings. */
    @Builder.Default
    private final NoCodeRiskSettings riskSettings = NoCodeRiskSettings.builder().build();

    /** Timestamp when this definition was first created. */
    @Builder.Default
    private final Instant createdAt = Instant.now();

    /** Timestamp when this definition was last modified. */
    @Builder.Default
    private final Instant updatedAt = Instant.now();

    // =========================================================================
    // Computed
    // =========================================================================

    /**
     * Computes the minimum warmup bars required by any rule in this definition.
     *
     * @return warmup bars (at least 1)
     */
    public int computeWarmupBars() {
        int entryWarmup = entryRules == null ? 0 :
                entryRules.stream().mapToInt(NoCodeRule::requiredWarmupBars).max().orElse(0);
        int exitWarmup = exitRules == null ? 0 :
                exitRules.stream().mapToInt(NoCodeRule::requiredWarmupBars).max().orElse(0);
        return Math.max(1, Math.max(entryWarmup, exitWarmup));
    }

    /**
     * Generates a human-readable strategy logic preview combining all entry and exit rules.
     *
     * @return multi-line preview text
     */
    public String toPreviewText() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(name).append(" ===\n");
        sb.append("\nENTRY RULES:\n");
        if (entryRules == null || entryRules.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            entryRules.forEach(r -> sb.append("  ").append(r.toPreviewText()).append("\n"));
        }
        sb.append("\nEXIT RULES:\n");
        if (exitRules == null || exitRules.isEmpty()) {
            sb.append("  (none)\n");
        } else {
            exitRules.forEach(r -> sb.append("  ").append(r.toPreviewText()).append("\n"));
        }
        sb.append("\nRISK SETTINGS:\n");
        if (riskSettings != null) {
            sb.append("  Stop Loss: ").append(riskSettings.getStopLossPercent()).append("%\n");
            sb.append("  Take Profit: ").append(riskSettings.getTakeProfitPercent()).append("%\n");
            sb.append("  Max Trades/Day: ").append(riskSettings.getMaxTradesPerDay()).append("\n");
        }
        return sb.toString();
    }
}
