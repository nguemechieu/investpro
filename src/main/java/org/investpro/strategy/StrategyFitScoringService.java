package org.investpro.strategy;

import org.investpro.decision.AssetMarketType;
import org.investpro.decision.MarketRegime;
import org.investpro.decision.StrategyFitScore;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Scores strategy fit for side, asset class, and inferred regime.
 */
public class StrategyFitScoringService {

    @NotNull
    public StrategyFitScore score(
            @NotNull String strategyName,
            @NotNull Side side,
            @NotNull AssetMarketType assetType,
            @NotNull MarketRegime regime,
            double signalStrength) {

        double regimeFit = 0.50;
        double assetFit = 0.50;
        double timeframeFit = 0.70;
        double recentPerformance = 0.65;
        double riskCompatibility = Math.max(0.0, Math.min(1.0, signalStrength));
        String warning = null;

        if (regime == MarketRegime.STRONG_UPTREND || regime == MarketRegime.STRONG_DOWNTREND) {
            regimeFit = contains(strategyName, "trend", "momentum") ? 0.85 : 0.60;
        } else if (regime == MarketRegime.WEAK_UPTREND || regime == MarketRegime.WEAK_DOWNTREND) {
            regimeFit = contains(strategyName, "pullback", "reversion") ? 0.75 : 0.55;
        } else if (regime == MarketRegime.RANGE_BOUND) {
            regimeFit = contains(strategyName, "reversion", "range") ? 0.80 : 0.45;
        }

        if (assetType == AssetMarketType.FOREX) {
            assetFit = contains(strategyName, "carry", "trend") ? 0.80 : 0.65;
        } else if (assetType == AssetMarketType.CRYPTO_SPOT || assetType == AssetMarketType.CRYPTO_DERIVATIVES) {
            assetFit = contains(strategyName, "momentum", "volatility") ? 0.80 : 0.60;
        } else if (assetType == AssetMarketType.EQUITIES || assetType == AssetMarketType.EQUITY_DERIVATIVES) {
            assetFit = contains(strategyName, "mean", "momentum") ? 0.75 : 0.65;
        }

        if (side == Side.HOLD) {
            warning = "HOLD side reduces strategy actionability";
            riskCompatibility = Math.min(riskCompatibility, 0.40);
        }

        double finalScore = (regimeFit + assetFit + timeframeFit + recentPerformance + riskCompatibility) / 5.0;

        return new StrategyFitScore(
                strategyName,
                "org.investpro.strategy." + strategyName.replace(" ", ""),
                regimeFit,
                assetFit,
                timeframeFit,
                recentPerformance,
                riskCompatibility,
                finalScore,
                "Scored by side, asset type, market regime, and signal strength",
                warning,
                Instant.now());
    }

    private boolean contains(String strategyName, String... tokens) {
        String lower = strategyName.toLowerCase();
        for (String token : tokens) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
