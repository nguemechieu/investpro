package org.investpro.strategy.auto;

import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;

import java.util.List;

public record StrategyGenerationContext(
        String symbol,
        Timeframe timeframe,
        List<CandleData> candles,
        MarketRegime marketRegime,
        RiskProfile riskProfile,
        String userPrompt) {

    public StrategyGenerationContext {
        candles = candles == null ? List.of() : List.copyOf(candles);
        marketRegime = marketRegime == null ? MarketRegime.UNKNOWN : marketRegime;
        riskProfile = riskProfile == null ? RiskProfile.conservative() : riskProfile;
        userPrompt = userPrompt == null ? "" : userPrompt;
    }
}
