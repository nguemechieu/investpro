package org.investpro.portfolio;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Analyzes correlation risk between candidate trade and existing positions.
 */
@Slf4j
@Getter
@Setter
public class CorrelationRiskAnalyzer {
    // Default correlation assumptions when no matrix provided
    @SuppressWarnings("unused")
    private static final double SAME_ASSET_CLASS_CORRELATION = 0.7;
    private static final double SAME_SECTOR_CORRELATION = 0.6;
    private static final double SAME_QUOTE_CURRENCY_CORRELATION = 0.5;
    private static final double CRYPTO_BETA_CORRELATION = 0.85; // All major cryptos correlate with BTC

    /**
     * Calculate correlation risk score for a candidate trade.
     * Considers correlation matrix if provided, or uses heuristics if missing.
     */
    public double analyzeCorrelationRisk(
            @NotNull PortfolioContext context,
            @Nullable Map<String, Map<String, Double>> correlationMatrix) {

        if (context.getOpenPositions().isEmpty()) {
            return 0.0; // No existing positions = no correlation risk
        }

        List<Position> positions = context.getOpenPositions();
        double totalCorrelationRisk = 0.0;
        double maxSingleCorrelationRisk = 0.0;

        for (Position position : positions) {
            double correlationRisk = calculateCorrelationBetweenTrades(
                    context.getCandidateSymbol(),
                    context.getCandidateAssetClass(),
                    context.getCandidateContractType(),
                    position,
                    correlationMatrix);

            // Weight by position size
            double positionExposurePercent = Math
                    .abs(position.getQuantity() * position.getCurrentPrice() / context.getAccountEquity());
            double weightedCorrelationRisk = correlationRisk * positionExposurePercent / 100.0;

            totalCorrelationRisk += weightedCorrelationRisk;
            maxSingleCorrelationRisk = Math.max(maxSingleCorrelationRisk, correlationRisk);

            log.debug("Candidate {} correlation with {}: {}, weighted: {}",
                    context.getCandidateSymbol(), position.getSymbol(), correlationRisk, weightedCorrelationRisk);
        }

        // Normalize correlation risk score (0-100)
        double correlationScore = Math.min(100, totalCorrelationRisk * 100);

        // Apply penalty if single correlation too high
        if (maxSingleCorrelationRisk > 0.8) {
            correlationScore += 20;
        } else if (maxSingleCorrelationRisk > 0.6) {
            correlationScore += 10;
        }

        correlationScore = Math.min(100, correlationScore);
        return correlationScore;
    }

    /**
     * Calculate correlation between two trades (candidate and existing).
     */
    private double calculateCorrelationBetweenTrades(
            @NotNull String candidateSymbol,
            @Nullable String candidateAssetClass,
            @Nullable String candidateContractType,
            @NotNull Position existingPosition,
            @Nullable Map<String, Map<String, Double>> correlationMatrix) {

        // If correlation matrix provided, use it
        if (correlationMatrix != null) {
            Map<String, Double> candCorrelations = correlationMatrix.get(candidateSymbol);
            if (candCorrelations != null) {
                Double correlation = candCorrelations.get(existingPosition.getSymbol());
                if (correlation != null) {
                    log.debug("Found correlation matrix entry: {} vs {} = {}",
                            candidateSymbol, existingPosition.getSymbol(), correlation);
                    return Math.abs(correlation);
                }
            }
        }

        // Fallback to heuristics
        return estimateCorrelationHeuristic(candidateSymbol, candidateAssetClass, candidateContractType,
                existingPosition);
    }

    /**
     * Estimate correlation using heuristics when no matrix available.
     */
    private double estimateCorrelationHeuristic(
            @NotNull String candidateSymbol,
            @Nullable String candidateAssetClass,
            @Nullable String candidateContractType,
            @NotNull Position existingPosition) {

        // Same symbol = 1.0 correlation
        if (candidateSymbol.equalsIgnoreCase(existingPosition.getSymbol())) {
            return 1.0;
        }

        // Different asset classes = low correlation
        // Note: AssetClass info comes from PortfolioContext, not Position directly
        // This is a simplified heuristic based on symbol patterns

        // Crypto pairs have high correlation
        if (isCrypto(candidateSymbol) && isCrypto(existingPosition.getSymbol())) {
            return CRYPTO_BETA_CORRELATION;
        }

        // Forex pairs with same quote currency
        if (sharesQuoteCurrency(candidateSymbol, existingPosition.getSymbol())) {
            return SAME_QUOTE_CURRENCY_CORRELATION;
        }

        // Tech stocks (simple heuristic)
        if (hasSameSector(candidateSymbol, existingPosition.getSymbol())) {
            return SAME_SECTOR_CORRELATION;
        }

        // Default low correlation
        return 0.2;
    }

    private boolean isCrypto(@NotNull String symbol) {
        // Simple heuristic: BTC, ETH, SOL, XRP, etc.
        return symbol.toUpperCase().matches("(BTC|ETH|SOL|XRP|ADA|DOGE|DOT|LINK|AVAX|MATIC|.*USD)");
    }

    private boolean hasSameSector(@NotNull String symbol1, @NotNull String symbol2) {
        // Placeholder - would need sector database
        // For now, simple heuristic: tech stocks start with certain patterns
        Set<String> techStocks = Set.of("AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA");
        return techStocks.contains(symbol1) && techStocks.contains(symbol2);
    }

    private boolean sharesQuoteCurrency(@NotNull String symbol1, @NotNull String symbol2) {
        // Forex pairs like EUR/USD and GBP/USD share quote currency (USD)
        if (symbol1.length() >= 6 && symbol2.length() >= 6) {
            String quote1 = symbol1.substring(4); // USD part of EUR/USD
            String quote2 = symbol2.substring(4);
            return quote1.equalsIgnoreCase(quote2);
        }
        return false;
    }

    /**
     * Assess whether correlation risk is too high.
     */
    public boolean isCorrelationRiskTooHigh(double correlationScore, double threshold) {
        return correlationScore > threshold;
    }

    /**
     * Get warning message for high correlation.
     */
    @NotNull
    public String getCorrelationWarning(double correlationScore) {
        if (correlationScore > 80) {
            return "CRITICAL: Candidate trade is highly correlated with existing positions";
        } else if (correlationScore > 60) {
            return "HIGH: Candidate trade is strongly correlated with existing positions";
        } else if (correlationScore > 40) {
            return "MODERATE: Candidate trade is somewhat correlated with existing positions";
        } else {
            return "LOW: Candidate trade shows low correlation with existing positions";
        }
    }
}
