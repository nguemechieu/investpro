package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Unified descriptor shared by ALL strategy types (built-in, developer plugin, no-code, AI).
 *
 * <p>The {@link StrategyRegistry} stores one {@code StrategyDescriptor} per registered
 * {@link TradingStrategy}. This ensures that every strategy — regardless of its origin —
 * is treated identically by the backtesting engine, AI review layer, paper trading
 * validation, and live assignment manager.</p>
 *
 * <p><strong>CRITICAL:</strong> {@code liveAllowed} is only set to {@code true} after a
 * strategy has passed all safety gates. Setting it manually without risk/AI approval
 * is a policy violation.</p>
 */
@Getter
@Builder
@ToString
public class StrategyDescriptor {

    /** Unique strategy identifier (UUID). */
    @Builder.Default
    private final String strategyId = UUID.randomUUID().toString();

    /** Human-readable strategy name. */
    private final String name;

    /** Short description of the strategy logic. */
    private final String description;

    /** How this strategy was created/authored. */
    @Builder.Default
    private final StrategyType strategyType = StrategyType.BUILT_IN;

    /**
     * Source identifier — for plugins: the JAR filename; for no-code: the JSON file path;
     * for built-in: the fully qualified class name.
     */
    private final String source;

    /** Semantic version string (e.g. "1.0.0"). */
    @Builder.Default
    private final String version = "1.0.0";

    /** Author name, username, or organization. */
    private final String author;

    /** Minimum number of candle bars required before the strategy can generate valid signals. */
    @Builder.Default
    private final int warmupBars = 50;

    /** Timeframe codes this strategy supports (e.g. "1h", "4h", "1d"). Empty = all. */
    private final List<String> supportedTimeframes;

    /** Asset codes this strategy supports (e.g. "BTC", "EUR", "AAPL"). Empty = all. */
    private final List<String> supportedAssets;

    /** Current pipeline validation status. */
    @Builder.Default
    private final StrategyValidationStatus validationStatus = StrategyValidationStatus.UNVALIDATED;

    /** Whether this strategy has been cleared for live trading. */
    @Builder.Default
    private final boolean liveAllowed = false;

    /** Timestamp when this descriptor was first created. */
    @Builder.Default
    private final Instant registeredAt = Instant.now();

    /** Timestamp of the last status change. */
    @Builder.Default
    private final Instant lastUpdatedAt = Instant.now();

    // =========================================================================
    // Convenience
    // =========================================================================

    /** @return true if the strategy is ready to produce signals (validated + enabled). */
    public boolean isReady() {
        return validationStatus != null && validationStatus.isProgressing()
                && validationStatus != StrategyValidationStatus.UNVALIDATED;
    }

    /** @return true if the strategy originated from a developer-submitted JAR. */
    public boolean isPlugin() {
        return strategyType == StrategyType.USER_PLUGIN;
    }

    /** @return true if the strategy was built using the no-code builder. */
    public boolean isNoCode() {
        return strategyType == StrategyType.NO_CODE;
    }

    /**
     * Returns a copy of this descriptor with the given validation status.
     *
     * @param newStatus the new validation status
     * @return updated descriptor
     */
    public StrategyDescriptor withValidationStatus(StrategyValidationStatus newStatus) {
        return StrategyDescriptor.builder()
                .strategyId(this.strategyId)
                .name(this.name)
                .description(this.description)
                .strategyType(this.strategyType)
                .source(this.source)
                .version(this.version)
                .author(this.author)
                .warmupBars(this.warmupBars)
                .supportedTimeframes(this.supportedTimeframes)
                .supportedAssets(this.supportedAssets)
                .validationStatus(newStatus)
                .liveAllowed(this.liveAllowed)
                .registeredAt(this.registeredAt)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this descriptor with live trading enabled.
     * Should only be called after AI + risk approval.
     *
     * @return updated descriptor with liveAllowed = true
     */
    public StrategyDescriptor withLiveApproval() {
        return StrategyDescriptor.builder()
                .strategyId(this.strategyId)
                .name(this.name)
                .description(this.description)
                .strategyType(this.strategyType)
                .source(this.source)
                .version(this.version)
                .author(this.author)
                .warmupBars(this.warmupBars)
                .supportedTimeframes(this.supportedTimeframes)
                .supportedAssets(this.supportedAssets)
                .validationStatus(StrategyValidationStatus.LIVE_APPROVED)
                .liveAllowed(true)
                .registeredAt(this.registeredAt)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
