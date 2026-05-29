package org.investpro.strategy.portfolio;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Portfolio-level intelligence report produced by
 * {@link PortfolioIntelligenceEngine}.
 *
 * <p>
 * <strong>NOTE:</strong> AI recommendations in this report are advisory only.
 * The RiskEngine enforces hard exposure limits.
 * </p>
 */
@Getter
@Builder
@ToString
public class PortfolioIntelligenceReport {

    /** Unique report identifier (UUID). */
    @Builder.Default
    private final String reportId = UUID.randomUUID().toString();

    // --- Current engine/dashboard fields ---
    private final double totalEquity;
    private final int activeStrategies;
    private final int liveStrategies;
    private final int degradedStrategies;
    private final int symbolCount;
    private final int timeframeCount;
    private final int strategyTypeCount;
    private final Map<String, Double> concentrationBySymbol;
    private final double maxConcentration;
    private final boolean concentrationRisk;
    private final boolean correlationRisk;
    private final double avgAiConfidence;
    private final double avgHealthScore;
    private final List<String> recommendations;
    private final List<String> warnings;
    private final Instant analyzedAt;

    /** Total portfolio exposure as a fraction of equity. */
    private final double totalExposurePercent;

    /** Fraction of exposure allocated to cryptocurrency instruments. */
    private final double cryptoAllocationPercent;

    /** Fraction of exposure allocated to FX instruments. */
    private final double fxAllocationPercent;

    /** Fraction of exposure allocated to equity instruments. */
    private final double equityAllocationPercent;

    /** Concentration risk score (0.0-1.0; high = concentrated in few symbols). */
    private final double concentrationRiskScore;

    /** Correlation risk score (0.0-1.0; high = positions are highly correlated). */
    private final double correlationRiskScore;

    /** Volatility risk score (0.0-1.0; high = portfolio is volatile). */
    private final double volatilityRisk;

    /** Symbols that exceed the single-symbol exposure threshold. */
    private final List<String> overexposedSymbols;

    /** Pairs of symbols with high positive correlation. */
    private final List<String> highCorrelationPairs;

    /**
     * AI-generated recommendations for portfolio rebalancing.
     * These are advisory only; RiskEngine enforces hard limits.
     */
    private final List<String> aiRecommendations;

    /** Exposure broken down by asset sector/class. */
    private final Map<String, Double> sectorAllocation;

    /** Timestamp when this report was generated. */
    private final Instant generatedAt;

    /**
     * Backward-compatible alias expected by older UI components.
     */
    public List<String> getWarnings() {
        return warnings == null ? List.of() : warnings;
    }
}
