package org.investpro.exchange.ibkr;

import javafx.beans.property.SimpleIntegerProperty;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
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
    private final IbkrClientPortalClient clientPortalClient;
    private final IbkrContractResolver contractResolver;
    private final ConcurrentHashMap<String, Double> lastPriceBySymbol = new ConcurrentHashMap<>();

    public IbkrMarketDataProvider(IbkrConnectionManager connectionManager, IbkrClientPortalClient clientPortalClient) {
        this(connectionManager, clientPortalClient, null);
    }

    public IbkrMarketDataProvider(IbkrConnectionManager connectionManager,
            IbkrClientPortalClient clientPortalClient,
            IbkrContractResolver contractResolver) {
        this.connectionManager = connectionManager;
        this.clientPortalClient = clientPortalClient;
        this.contractResolver = contractResolver;
    }

    public CompletableFuture<Ticker> fetchTicker(TradePair pair) {
        return CompletableFuture.supplyAsync(() -> {
            if (connectionManager.getMode() == IbkrConnectionManager.Mode.LIVE && clientPortalClient != null) {
                Optional<Ticker> liveTicker = resolvedContract(pair)
                        .flatMap(clientPortalClient::fetchTicker);
                if (liveTicker.isPresent()) {
                    connectionManager.markMarketDataAvailable(true);
                    return liveTicker.get();
                }
            }

            double nextPrice = nextPrice(pair);
            connectionManager.markMarketDataAvailable(true);
            return new Ticker(nextPrice, nextPrice - 0.01, nextPrice + 0.01, 1_000.0, System.currentTimeMillis());
        });
    }

    public CompletableFuture<org.investpro.models.trading.OrderBook> fetchOrderBook(TradePair pair) {
        return CompletableFuture.supplyAsync(() -> {
            if (connectionManager.getMode() == IbkrConnectionManager.Mode.LIVE && clientPortalClient != null) {
                Optional<org.investpro.models.trading.OrderBook> liveOrderBook = clientPortalClient
                        .fetchOrderBook(resolvedContract(pair).orElse(null), pair);
                if (liveOrderBook.isPresent()) {
                    connectionManager.markMarketDataAvailable(true);
                    return liveOrderBook.get();
                }
            }

            Ticker ticker = tickerSync(pair);
            org.investpro.models.trading.OrderBook fallback = new org.investpro.models.trading.OrderBook(pair);
            double bid = ticker.getBidPrice() > 0.0 ? ticker.getBidPrice()
                    : Math.max(0.01, ticker.getMidPrice() - 0.01);
            double ask = ticker.getAskPrice() > 0.0 ? ticker.getAskPrice() : Math.max(bid, ticker.getMidPrice() + 0.01);
            fallback.setBids(List.of(new org.investpro.models.trading.OrderBook.PriceLevel(bid, 1_000.0, 1)));
            fallback.setAsks(List.of(new org.investpro.models.trading.OrderBook.PriceLevel(ask, 1_000.0, 1)));
            fallback.setTimestamp(Instant.now());
            fallback.setSequence("ibkr-fallback-" + System.currentTimeMillis());
            return fallback;
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
                        currentCandleStartedAt).thenApply(Optional::of);
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return IbkrMarketDataProvider.this.fetchRecentTradesUntil(tradePair, stopAt);
            }
        };
    }

    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair pair,
            Instant currentCandleStartedAt) {
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
        return CompletableFuture.supplyAsync(() -> {
            if (pair == null) {
                return List.of();
            }

            Instant now = Instant.now();
            if (stopAt != null && stopAt.isAfter(now)) {
                return List.of();
            }

            // Keep the request bounded so UI refreshes stay responsive.
            Instant lowerBound = stopAt != null ? stopAt : now.minusSeconds(120);
            long windowSeconds = Math.max(1L, Duration.between(lowerBound, now).getSeconds());
            int count = (int) Math.min(500L, Math.max(20L, windowSeconds / 5L));

            Ticker ticker = tickerSync(pair);
            double anchorPrice = ticker.getLastPrice() > 0.0
                    ? ticker.getLastPrice()
                    : ticker.getMidPrice() > 0.0 ? ticker.getMidPrice() : nextPrice(pair);

            List<Trade> trades = new ArrayList<>(count);
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double runningPrice = Math.max(0.01, anchorPrice);
            long baseTradeId = System.currentTimeMillis() * 1_000L;

            for (int index = count - 1; index >= 0; index--) {
                Instant tradeTime = now.minusSeconds((long) index * 5L);
                if (tradeTime.isBefore(lowerBound)) {
                    continue;
                }

                double drift = random.nextDouble(-0.0015, 0.0015) * Math.max(1.0, runningPrice);
                runningPrice = Math.max(0.01, runningPrice + drift);
                double size = random.nextDouble(1.0, 250.0);
                Side side = random.nextBoolean() ? Side.BUY : Side.SELL;

                trades.add(new Trade(
                        pair,
                        runningPrice,
                        size,
                        side,
                        baseTradeId + (count - index),
                        tradeTime));
            }

            trades.sort(Comparator.comparing(Trade::getTimestamp));
            return trades;
        });
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

    private Optional<IbkrResolvedContract> resolvedContract(TradePair pair) {
        if (contractResolver == null) {
            return Optional.empty();
        }
        return Optional.of(contractResolver.requireResolved(pair));
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
