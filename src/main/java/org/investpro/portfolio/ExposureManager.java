package org.investpro.portfolio;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.Position;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Calculates and manages exposure across multiple dimensions:
 * symbol, asset class, strategy, broker, contract type, currency.
 */
@Slf4j
public class ExposureManager {
    /**
     * Calculate exposure breakdown from open positions.
     */
    @NotNull
    public PortfolioExposure calculateExposure(@NotNull List<Position> positions, double accountEquity) {
        if (accountEquity <= 0) {
            return PortfolioExposure.builder().build(); // Return empty exposure
        }

        Map<String, Double> symbolExp = new HashMap<>();
        Map<String, Double> assetClassExp = new HashMap<>();
        Map<String, Double> strategyExp = new HashMap<>();
        Map<String, Double> brokerExp = new HashMap<>();
        Map<String, Double> contractExp = new HashMap<>();
        Map<String, Double> currencyExp = new HashMap<>();

        double totalExposure = 0.0;
        double netExposure = 0.0;
        double grossExposure = 0.0;
        double longExp = 0.0;
        double shortExp = 0.0;

        for (Position position : positions) {
            double positionValue = Math.abs(position.getQuantity() * position.getCurrentPrice());
            double positionExposurePercent = (positionValue / accountEquity) * 100.0;

            // Track by symbol
            symbolExp.merge(position.getSymbol(), positionExposurePercent, Double::sum);

            // Note: AssetClass, Strategy, Broker, ContractType, Currency info would come
            // from TradePair
            // For now, we track by symbol and leave others for integration with context

            // Aggregate exposure
            totalExposure += positionExposurePercent;

            if (position.isBuy()) {
                longExp += positionExposurePercent;
                netExposure += positionExposurePercent;
            } else {
                shortExp += positionExposurePercent;
                netExposure -= positionExposurePercent;
            }

            grossExposure += positionExposurePercent;
        }

        return PortfolioExposure.builder()
                .totalExposure(totalExposure)
                .netExposure(netExposure)
                .grossExposure(grossExposure)
                .longExposure(longExp)
                .shortExposure(shortExp)
                .symbolExposure(symbolExp)
                .assetClassExposure(assetClassExp)
                .strategyExposure(strategyExp)
                .brokerExposure(brokerExp)
                .contractTypeExposure(contractExp)
                .quoteCurrencyExposure(currencyExp)
                .build();
    }

    /**
     * Check if adding a trade would exceed exposure limits.
     */
    public boolean wouldExceedSymbolLimit(@NotNull String symbol, double currentSymbolExposure,
            double addedExposure, double symbolLimit) {
        return (currentSymbolExposure + addedExposure) > symbolLimit;
    }

    public boolean wouldExceedAssetClassLimit(@NotNull String assetClass, double currentExposure,
            double addedExposure, double limit) {
        return (currentExposure + addedExposure) > limit;
    }

    public boolean wouldExceedStrategyLimit(@NotNull String strategyId, double currentExposure,
            double addedExposure, double limit) {
        return (currentExposure + addedExposure) > limit;
    }

    public boolean wouldExceedBrokerLimit(@NotNull String broker, double currentExposure,
            double addedExposure, double limit) {
        return (currentExposure + addedExposure) > limit;
    }

    /**
     * Calculate exposure impact of a candidate trade.
     */
    public double calculateCandidateExposure(double candidateQuantity, double candidatePrice,
            double accountEquity, double leverage) {
        if (accountEquity <= 0)
            return 0;
        double positionValue = Math.abs(candidateQuantity * candidatePrice);
        double exposurePercent = (positionValue / accountEquity) * 100.0;
        if (leverage > 1) {
            exposurePercent *= leverage;
        }
        return exposurePercent;
    }

    /**
     * Get largest position in portfolio by exposure.
     */
    public Optional<Map.Entry<String, Double>> getLargestExposure(@NotNull Map<String, Double> exposures) {
        return exposures.entrySet().stream()
                .max(Comparator.comparingDouble(Map.Entry::getValue));
    }

    /**
     * Get top N exposures.
     */
    @NotNull
    public Map<String, Double> getTopExposures(@NotNull Map<String, Double> exposures, int topN) {
        return exposures.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .collect(LinkedHashMap::new, (m, e) -> m.put(e.getKey(), e.getValue()), Map::putAll);
    }

    /**
     * Calculate net exposure (long - short).
     */
    public double calculateNetExposure(@NotNull List<Position> positions, double accountEquity) {
        if (accountEquity <= 0)
            return 0;
        double longExp = 0;
        double shortExp = 0;

        for (Position pos : positions) {
            double posExp = (Math.abs(pos.getQuantity()) * pos.getCurrentPrice() / accountEquity) * 100;
            if (pos.isBuy()) {
                longExp += posExp;
            } else {
                shortExp += posExp;
            }
        }

        return longExp - shortExp;
    }

    /**
     * Calculate gross exposure (|long| + |short|).
     */
    public double calculateGrossExposure(@NotNull List<Position> positions, double accountEquity) {
        if (accountEquity <= 0)
            return 0;
        return positions.stream()
                .mapToDouble(p -> (Math.abs(p.getQuantity()) * p.getCurrentPrice() / accountEquity) * 100)
                .sum();
    }
}
