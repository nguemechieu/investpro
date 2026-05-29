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
 * <p><strong>NOTE:</strong> AI recommendations in this report are advisory only.
 * The RiskEngine enforces hard exposure limits.</p>
 */
@Getter
@Builder
@ToString
public class PortfolioIntelligenceReport {

    /** Unique report identifier (UUID). */
    @Builder.Default
    private final String reportId = UUID.randomUUID().toString();

    /** Total portfolio exposure as a fraction of equity. */
    private final double totalExposurePercent;

    /** Fraction of exposure allocated to cryptocurrency instruments. */
    private final double cryptoAllocationPercent;

    /** Fraction of exposure allocated to FX instruments. */
    private final double fxAllocationPercent;

    /** Fraction of exposure allocated to equity instruments. */
    private final double equityAllocationPercent;

    /** Concentration risk score (0.0-1.0; high = concentrated in few symbols). */
    private final double concentrationRisk;

    /** Correlation risk score (0.0-1.0; high = positions are highly correlated). */
    private final double correlationRisk;

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
}
