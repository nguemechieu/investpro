package org.investpro.portfolio.intelligence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Portfolio exposure intelligence for risk and operations dashboards.
 */
public record PortfolioIntelligenceSnapshot(
        BigDecimal grossExposure,
        BigDecimal netExposure,
        BigDecimal liquidityExposure,
        BigDecimal blockchainExposure,
        BigDecimal derivativesExposure,
        BigDecimal volatilityScore,
        BigDecimal concentrationScore,
        BigDecimal correlationScore,
        Map<String, BigDecimal> exposureBySymbol,
        Map<String, BigDecimal> exposureByAssetClass,
        Instant calculatedAt) {

    public PortfolioIntelligenceSnapshot {
        grossExposure = value(grossExposure);
        netExposure = value(netExposure);
        liquidityExposure = value(liquidityExposure);
        blockchainExposure = value(blockchainExposure);
        derivativesExposure = value(derivativesExposure);
        volatilityScore = value(volatilityScore);
        concentrationScore = value(concentrationScore);
        correlationScore = value(correlationScore);
        exposureBySymbol = exposureBySymbol == null ? Map.of() : Map.copyOf(exposureBySymbol);
        exposureByAssetClass = exposureByAssetClass == null ? Map.of() : Map.copyOf(exposureByAssetClass);
        calculatedAt = calculatedAt == null ? Instant.now() : calculatedAt;
    }

    public static PortfolioIntelligenceSnapshot empty() {
        return new PortfolioIntelligenceSnapshot(null, null, null, null, null, null, null, null,
                Map.of(), Map.of(), Instant.now());
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
