package org.investpro.market;

import org.investpro.marketdata.Candle;
import org.investpro.marketdata.MarketSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class MarketContextBuilder {
    public MarketContext build(MarketSnapshot snapshot,
                               MarketContext.AccountSnapshot accountSnapshot,
                               List<String> openOrders,
                               List<String> openPositions,
                               MarketContext.ExchangeCapabilities capabilities) {
        MarketSnapshot safeSnapshot = snapshot == null ? MarketSnapshot.empty("", "", "") : snapshot;
        List<Candle> candles = safeSnapshot.recentCandles();
        Candle latest = safeSnapshot.latestCandle();
        BigDecimal spread = safeSnapshot.tick() == null ? BigDecimal.ZERO : safeSnapshot.tick().spread();
        BigDecimal volume = latest == null ? BigDecimal.ZERO : latest.volume();
        BigDecimal volatility = averageRange(candles);
        String regime = volatility.signum() == 0 ? "UNKNOWN" : "NORMAL";
        MarketContext.DataFreshnessStatus freshness = safeSnapshot.stale()
                ? MarketContext.DataFreshnessStatus.STALE
                : MarketContext.DataFreshnessStatus.FRESH;

        return new MarketContext(
                safeSnapshot.exchangeId(),
                safeSnapshot.symbol(),
                safeSnapshot.timeframe(),
                latest,
                candles,
                safeSnapshot,
                spread,
                volume,
                volatility,
                regime,
                "UNKNOWN",
                accountSnapshot,
                openOrders,
                openPositions,
                capabilities,
                freshness,
                Instant.now(),
                Map.of());
    }

    private BigDecimal averageRange(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = candles.stream()
                .map(Candle::range)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(candles.size()), java.math.MathContext.DECIMAL64);
    }
}
