package org.investpro.market;

import org.investpro.marketdata.Candle;
import org.investpro.marketdata.MarketSnapshot;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MarketContext(
        String exchangeId,
        String symbol,
        String timeframe,
        Candle latestCandle,
        List<Candle> recentCandles,
        MarketSnapshot marketSnapshot,
        BigDecimal spread,
        BigDecimal volume,
        BigDecimal volatility,
        String marketRegime,
        String sessionStatus,
        AccountSnapshot accountSnapshot,
        List<String> openOrders,
        List<String> openPositions,
        ExchangeCapabilities exchangeCapabilities,
        DataFreshnessStatus dataFreshnessStatus,
        Instant builtAt,
        Map<String, Object> metadata) {

    public MarketContext {
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        timeframe = safe(timeframe);
        recentCandles = recentCandles == null ? List.of() : List.copyOf(recentCandles);
        spread = value(spread);
        volume = value(volume);
        volatility = value(volatility);
        marketRegime = safe(marketRegime).isBlank() ? "UNKNOWN" : safe(marketRegime);
        sessionStatus = safe(sessionStatus).isBlank() ? "UNKNOWN" : safe(sessionStatus);
        accountSnapshot = accountSnapshot == null ? AccountSnapshot.empty() : accountSnapshot;
        openOrders = openOrders == null ? List.of() : List.copyOf(openOrders);
        openPositions = openPositions == null ? List.of() : List.copyOf(openPositions);
        exchangeCapabilities = exchangeCapabilities == null ? ExchangeCapabilities.unknown() : exchangeCapabilities;
        dataFreshnessStatus = dataFreshnessStatus == null ? DataFreshnessStatus.UNKNOWN : dataFreshnessStatus;
        builtAt = builtAt == null ? Instant.now() : builtAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean hasPendingOrder() {
        return !openOrders.isEmpty();
    }

    public boolean hasOpenPosition() {
        return !openPositions.isEmpty();
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record AccountSnapshot(
            BigDecimal balance,
            BigDecimal buyingPower,
            BigDecimal marginAvailable,
            Instant updatedAt) {
        public AccountSnapshot {
            balance = value(balance);
            buyingPower = value(buyingPower);
            marginAvailable = value(marginAvailable);
            updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
        }

        public static AccountSnapshot empty() {
            return new AccountSnapshot(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, Instant.EPOCH);
        }

        public boolean stale() {
            return updatedAt.plusSeconds(60).isBefore(Instant.now());
        }
    }

    public record ExchangeCapabilities(
            boolean connected,
            boolean websocketHealthy,
            boolean supportsMarketOrders,
            boolean supportsLimitOrders,
            boolean supportsStopLoss,
            boolean supportsTakeProfit,
            boolean degraded) {
        public static ExchangeCapabilities unknown() {
            return new ExchangeCapabilities(false, false, false, false, false, false, true);
        }
    }

    public enum DataFreshnessStatus {
        FRESH,
        STALE,
        MISSING,
        UNKNOWN
    }
}
