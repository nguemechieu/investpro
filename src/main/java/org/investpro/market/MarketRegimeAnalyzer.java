package org.investpro.market;

import org.investpro.decision.AssetMarketType;
import org.investpro.decision.MarketRegime;
import org.investpro.models.trading.Ticker;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Conservative regime inference from ticker-only data.
 */
public class MarketRegimeAnalyzer {

    @NotNull
    public MarketRegimeAnalysis analyze(@NotNull Ticker ticker, @NotNull AssetMarketType assetType) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double last = ticker.getLastPrice();

        if (bid <= 0 || ask <= 0 || last <= 0 || ask <= bid) {
            warnings.add("Invalid ticker inputs for regime analysis");
            return new MarketRegimeAnalysis(MarketRegime.UNKNOWN, 0.0, List.copyOf(reasons), List.copyOf(warnings));
        }

        double mid = (bid + ask) / 2.0;
        double spread = ask - bid;
        double relative = spread > 0 ? (last - mid) / spread : 0.0;

        MarketRegime regime;
        double confidence;
        if (relative > 0.4) {
            regime = MarketRegime.WEAK_UPTREND;
            confidence = 0.45;
            reasons.add("Last price near ask side indicates mild buying pressure");
        } else if (relative < -0.4) {
            regime = MarketRegime.WEAK_DOWNTREND;
            confidence = 0.45;
            reasons.add("Last price near bid side indicates mild selling pressure");
        } else {
            regime = MarketRegime.RANGE_BOUND;
            confidence = 0.40;
            reasons.add("Last price near midpoint suggests range behavior");
        }

        warnings.add("Ticker-only regime inference; candle/indicator context required for strong-trend classification");
        return new MarketRegimeAnalysis(regime, confidence, List.copyOf(reasons), List.copyOf(warnings));
    }
}
