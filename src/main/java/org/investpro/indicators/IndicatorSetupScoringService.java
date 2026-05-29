package org.investpro.indicators;

import org.investpro.decision.AssetMarketType;
import org.investpro.decision.IndicatorSetupScore;
import org.investpro.decision.IndicatorSetupType;
import org.investpro.decision.MarketRegime;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Produces a conservative synthetic indicator score when indicator data is not
 * wired.
 */
public class IndicatorSetupScoringService {

    @NotNull
    public IndicatorSetupScore score(@NotNull Side side, @NotNull AssetMarketType assetType,
            @NotNull MarketRegime regime) {
        IndicatorSetupType setupType = selectSetup(regime);
        if (setupType == IndicatorSetupType.NONE) {
            return new IndicatorSetupScore(
                    IndicatorSetupType.NONE,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "No indicator setup available for UNKNOWN regime",
                    "Indicator setup unavailable",
                    Instant.now());
        }

        double regimeFit = regime == MarketRegime.RANGE_BOUND ? 0.72 : 0.78;
        double signalClarity = side == Side.HOLD ? 0.45 : 0.68;
        double historicalWinRate = assetType.isHighVolatility ? 0.56 : 0.60;
        double volatilityAlignment = assetType.isHighVolatility ? 0.80 : 0.70;
        double finalScore = (regimeFit + signalClarity + historicalWinRate + volatilityAlignment) / 4.0;

        String warning = finalScore < 0.65 ? "Indicator score below threshold" : null;
        String reasoning = "Indicator composite scored from regime fit, signal clarity, historical win rate, and volatility alignment";

        return new IndicatorSetupScore(
                setupType,
                regimeFit,
                signalClarity,
                historicalWinRate,
                volatilityAlignment,
                finalScore,
                reasoning,
                warning,
                Instant.now());
    }

    private IndicatorSetupType selectSetup(MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND -> IndicatorSetupType.MOMENTUM;
            case WEAK_UPTREND, WEAK_DOWNTREND, RANGE_BOUND -> IndicatorSetupType.MEAN_REVERSION;
            case HIGH_VOLATILITY, LOW_VOLATILITY -> IndicatorSetupType.VOLATILITY_BREAKOUT;
            case TRANSITIONAL -> IndicatorSetupType.LEVEL_BREAKOUT;
            case UNKNOWN -> IndicatorSetupType.NONE;
        };
    }
}
