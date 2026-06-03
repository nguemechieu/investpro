package org.investpro.exchange.ibkr;

import javafx.beans.property.SimpleIntegerProperty;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public final class IbkrMarketDataProvider {

    private final IbkrConnectionManager connectionManager;
    private final ConcurrentHashMap<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();

    public IbkrMarketDataProvider(IbkrConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    public CompletableFuture<Ticker> fetchTicker(TradePair pair) {
        return CompletableFuture.supplyAsync(() -> {
            double nextPrice = nextPrice(pair);
            connectionManager.markMarketDataAvailable(true);
            return new Ticker(nextPrice, nextPrice - 0.01, nextPrice + 0.01, 1_000.0, System.currentTimeMillis());
        });
    }

    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        return CompletableFuture.supplyAsync(() -> pairs.stream().map(this::tickerSync).toList());
    }

    public CandleDataSupplier candleDataSupplier(int secondsPerCandle, TradePair pair) {
        return new CandleDataSupplier(300, secondsPerCandle, pair, new SimpleIntegerProperty(0)) {
            @Override
            public Future<List<CandleData>> get() {
                return CompletableFuture.completedFuture(getCandleData());
            }

            @Override
            public List<CandleData> getCandleData() {
                return syntheticCandles(pair, secondsPerCandle, 300);
            }

            @Override
            public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                return candleDataSupplier(secondsPerCandle, tradePair);
            }

            @Override
            public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
                    Instant currentCandleStartedAt,
                    long secondsIntoCurrentCandle,
                    int secondsPerCandle) {
                return IbkrMarketDataProvider.this.fetchCandleDataForInProgressCandle(
                        tradePair,
                        currentCandleStartedAt,
                        secondsIntoCurrentCandle,
                        secondsPerCandle).thenApply(value -> Optional.of(value));
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return IbkrMarketDataProvider.this.fetchRecentTradesUntil(tradePair, stopAt);
            }
        };
    }

    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair pair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle) {
        return fetchTicker(pair).thenApply(ticker -> {
            InProgressCandleData data = new InProgressCandleData(
                    (int) currentCandleStartedAt.getEpochSecond(),
                    ticker.getLastPrice(),
                    ticker.getLastPrice(),
                    ticker.getLastPrice(),
                    (int) Instant.now().getEpochSecond(),
                    ticker.getLastPrice(),
                    ticker.getVolume());
            return Optional.of(data);
        });
    }

    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair pair, Instant stopAt) {
        return CompletableFuture.completedFuture(List.of());
    }

    public List<Timeframe> supportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1);
    }

    public boolean isMarketDataHealthy() {
        return connectionManager.isMarketDataAvailable();
    }

    private Ticker tickerSync(TradePair pair) {
        try {
            return fetchTicker(pair).get();
        } catch (Exception exception) {
            log.warn("Failed to build ticker for {}", pair, exception);
            return Ticker.empty();
        }
    }

    private List<CandleData> syntheticCandles(TradePair pair, int secondsPerCandle, int count) {
        List<CandleData> candles = new ArrayList<>(count);
        int now = (int) Instant.now().getEpochSecond();
        int start = now - (count * secondsPerCandle);
        double px = nextPrice(pair);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < count; i++) {
            int openTime = start + (i * secondsPerCandle);
            double open = px;
            double move = random.nextDouble(-0.004, 0.004) * Math.max(1.0, open);
            double close = Math.max(0.01, open + move);
            double high = Math.max(open, close) * (1 + random.nextDouble(0.0, 0.0015));
            double low = Math.min(open, close) * (1 - random.nextDouble(0.0, 0.0015));
            double volume = random.nextDouble(100.0, 4_000.0);
            candles.add(new CandleData(open, close, high, low, openTime, volume));
            px = close;
        }

        candles.sort(Comparator.comparingInt(CandleData::openTime));
        return candles;
    }

    private double nextPrice(TradePair pair) {
        String key = pair.toString('/');
        double current = lastPriceBySymbol.computeIfAbsent(key, ignored -> seedPrice(pair));
        double drift = ThreadLocalRandom.current().nextDouble(-0.003, 0.003) * Math.max(1.0, current);
        double next = Math.max(0.01, current + drift);
        lastPriceBySymbol.put(key, next);
        return next;
    }

    private double seedPrice(TradePair pair) {
        String base = pair.getBaseCurrency().getCode().toUpperCase();
        return switch (base) {
            case "AAPL" -> 210.0;
            case "MSFT" -> 430.0;
            case "SPY" -> 540.0;
            case "QQQ" -> 470.0;
            case "EUR" -> 1.09;
            case "GBP" -> 1.27;
            default -> 100.0;
        };
    }
}
