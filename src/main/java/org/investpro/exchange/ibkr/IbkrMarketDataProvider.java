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
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class IbkrMarketDataProvider {

    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(4, 4, 30,
            TimeUnit.SECONDS, new ArrayBlockingQueue<>(64), task -> {
                Thread thread = new Thread(task, "ibkr-market-data");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    private final boolean simulationEnabled;
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
        this(connectionManager, clientPortalClient, contractResolver, false);
    }

    public IbkrMarketDataProvider(IbkrConnectionManager connectionManager,
            IbkrClientPortalClient clientPortalClient, IbkrContractResolver contractResolver,
            boolean simulationEnabled) {
        this.simulationEnabled = simulationEnabled;
        if (simulationEnabled) log.warn("ibkr.marketData.simulation enabled for PAPER only; LIVE always uses broker data");
        this.connectionManager = connectionManager;
        this.clientPortalClient = clientPortalClient;
        this.contractResolver = contractResolver;
    }

    private boolean simulation() {
        return simulationEnabled && connectionManager.getMode() == IbkrConnectionManager.Mode.PAPER;
    }

    private <T> CompletableFuture<T> request(java.util.function.Supplier<CompletableFuture<T>> operation,
                                            boolean real) {
        CompletableFuture<T> future;
        try { future = operation.get(); }
        catch (RuntimeException error) { future = CompletableFuture.failedFuture(error); }
        return future.whenComplete((value, error) -> {
            if (!real && connectionManager.getMode() == IbkrConnectionManager.Mode.LIVE) {
                connectionManager.markMarketDataAvailable(false);
                throw new IbkrMarketDataException("Simulation request discarded because IBKR switched to LIVE");
            }
            if (real) connectionManager.markMarketDataAvailable(error == null);
            if (error != null) log.debug("ibkr.marketData.unavailable: {}", error.toString());
        });
    }

    public CompletableFuture<Ticker> fetchTicker(TradePair pair) {
        boolean simulated = simulation();
        return request(() -> {
            if (simulated) return CompletableFuture.supplyAsync(() -> syntheticTicker(pair), WORKERS);
            IbkrResolvedContract contract = resolvedContract(pair).orElseThrow(() ->
                    new IbkrMarketDataException("Resolve the IBKR contract before requesting data for " + pair));
            CompletableFuture<Ticker> quote;
            if (connectionManager.getConnectionMode() == IbkrConnectionMode.TWS_API) {
                quote = connectionManager.getTwsSession().ticker(contract);
            } else {
                quote = CompletableFuture.supplyAsync(() -> {
                    if (clientPortalClient == null) throw new IbkrMarketDataException("Client Portal is not configured");
                    return clientPortalClient.fetchTicker(contract).orElseThrow(() ->
                            new IbkrMarketDataException("No IBKR Client Portal quote for " + pair
                                    + "; check session, contract and market-data permissions"));
                }, WORKERS);
            }
            return quote.thenApply(this::validateTicker);
        }, !simulated);
    }

    private Ticker validateTicker(Ticker ticker) {
        if (ticker == null || ticker.getQuoteType() == null || ticker.getQuoteType() == Ticker.QuoteType.SIMULATED || !Double.isFinite(ticker.getLastPrice()) || ticker.getLastPrice() < 0
                || !Double.isFinite(ticker.getBidPrice()) || ticker.getBidPrice() < 0
                || !Double.isFinite(ticker.getAskPrice()) || ticker.getAskPrice() < 0
                || !Double.isFinite(ticker.getMidPrice()) || ticker.getMidPrice() <= 0
                || ticker.getTimestamp() <= 0 || ticker.getTimestamp() > System.currentTimeMillis() + 60_000
                || (ticker.getBidPrice() > 0 && ticker.getAskPrice() > 0
                    && ticker.getBidPrice() > ticker.getAskPrice()))
            throw new IbkrMarketDataException("IBKR returned an invalid quote");
        Ticker copy = new Ticker(ticker.getLastPrice(), ticker.getBidPrice(), ticker.getAskPrice(),
                ticker.getOpenPrice(), ticker.getHighPrice(), ticker.getLowPrice(), ticker.getVolume(), ticker.getTimestamp());
        copy.setQuoteType(ticker.getQuoteType());
        return copy;
    }

    private Ticker syntheticTicker(TradePair pair) {
        double price = nextPrice(pair);
        log.trace("ibkr.marketData.simulation symbol={}", pair);
        Ticker ticker = new Ticker(price, price * 0.9999, price * 1.0001, 1000, System.currentTimeMillis());
        ticker.setQuoteType(Ticker.QuoteType.SIMULATED);
        return ticker;
    }
    public CompletableFuture<org.investpro.models.trading.OrderBook> fetchOrderBook(TradePair pair) {
        boolean simulated = simulation();
        return request(() -> CompletableFuture.supplyAsync(() -> {
            if (!simulated) {
                resolvedContract(pair).orElseThrow(() -> new IbkrMarketDataException("Resolve the IBKR contract first"));
                throw new IbkrMarketDataException("IBKR market depth is not implemented; top-of-book is not depth. Level 2 entitlement is required.");
            }
            Ticker ticker = syntheticTicker(pair);
            org.investpro.models.trading.OrderBook fallback = new org.investpro.models.trading.OrderBook(pair);
            double bid = ticker.getBidPrice() > 0.0 ? ticker.getBidPrice()
                    : Math.max(0.01, ticker.getMidPrice() - 0.01);
            double ask = ticker.getAskPrice() > 0.0 ? ticker.getAskPrice() : Math.max(bid, ticker.getMidPrice() + 0.01);
            fallback.setBids(List.of(new org.investpro.models.trading.OrderBook.PriceLevel(bid, 1_000.0, 1)));
            fallback.setAsks(List.of(new org.investpro.models.trading.OrderBook.PriceLevel(ask, 1_000.0, 1)));
            fallback.setTimestamp(Instant.now());
            fallback.setSequence("ibkr-SIMULATED-" + System.currentTimeMillis());
            return fallback;
        }, WORKERS), !simulated);
    }

    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<CompletableFuture<Ticker>> futures = List.copyOf(pairs).stream().map(this::fetchTicker).toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(_ -> futures.stream().map(f -> f.getNow(null)).toList());
    }

    public CandleDataSupplier candleDataSupplier(int secondsPerCandle, TradePair pair) {
        return new CandleDataSupplier(300, secondsPerCandle, pair, new SimpleIntegerProperty(0)) {
            @Override
            public Future<List<CandleData>> get() {
                return history(pair, secondsPerCandle);
            }

            @Override
            public List<CandleData> getCandleData() {
                return history(pair, secondsPerCandle).join();
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
                        currentCandleStartedAt, secondsPerCandle).thenApply(value -> value);
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return IbkrMarketDataProvider.this.fetchRecentTradesUntil(tradePair, stopAt);
            }
        };
    }

    private CompletableFuture<List<CandleData>> history(TradePair pair, int seconds) {
        boolean simulated = simulation();
        return request(() -> {
            if (seconds <= 0) throw new IllegalArgumentException("Candle duration must be positive");
            if (simulated) return CompletableFuture.supplyAsync(() -> syntheticCandles(pair, seconds, 300), WORKERS);
            IbkrResolvedContract contract = resolvedContract(pair).orElseThrow(() -> new IbkrMarketDataException("Resolve the IBKR contract first"));
            if (connectionManager.getConnectionMode() != IbkrConnectionMode.TWS_API)
                return CompletableFuture.failedFuture(new IbkrMarketDataException("Client Portal historical candles are not implemented; use TWS API for charts"));
            return connectionManager.getTwsSession().history(contract, seconds).thenApply(candles -> {
                if (candles == null || candles.isEmpty()) throw new IbkrMarketDataException("IBKR returned no historical candles");
                for (CandleData candle : candles) {
                    if (candle == null || candle.placeHolder() || candle.openTime() <= 0
                            || !positive(candle.openPrice()) || !positive(candle.closePrice())
                            || !positive(candle.highPrice()) || !positive(candle.lowPrice())
                            || candle.lowPrice() > Math.min(candle.openPrice(), candle.closePrice())
                            || candle.highPrice() < Math.max(candle.openPrice(), candle.closePrice())
                            || !Double.isFinite(candle.volume()) || candle.volume() < 0)
                        throw new IbkrMarketDataException("IBKR returned invalid historical candles");
                }
                return List.copyOf(candles);
            });
        }, !simulated);
    }
    private static boolean positive(double price) { return Double.isFinite(price) && price > 0; }

    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair pair, Instant started) {
        return fetchCandleDataForInProgressCandle(pair, started, 60);
    }
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair pair, Instant started, int seconds) {
        return history(pair, seconds).thenApply(candles -> candles.stream()
                .filter(c -> c.openTime() == started.getEpochSecond()).findFirst()
                .map(c -> new InProgressCandleData(c.openTime(), c.openPrice(), c.highPrice(), c.lowPrice(),
                        (int) Instant.now().getEpochSecond(), c.closePrice(), c.volume())));
    }
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair pair, Instant stopAt) {

        boolean simulated = simulation();
        return request(() -> CompletableFuture.supplyAsync(() -> {
            if (!simulated) {
                resolvedContract(pair).orElseThrow(() -> new IbkrMarketDataException("Resolve the IBKR contract first"));
                throw new UnsupportedOperationException("IBKR historical market prints are not implemented by this integration");
            }
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

            Ticker ticker = syntheticTicker(pair);
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
        }, WORKERS), !simulated);
    }

    public List<Timeframe> supportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.H4, Timeframe.D1);
    }

    public boolean isMarketDataHealthy() {
        return connectionManager.isMarketDataAvailable();
    }

    private Optional<IbkrResolvedContract> resolvedContract(TradePair pair) {
        if (contractResolver == null) {
            return Optional.empty();
        }
        try {
            IbkrResolvedContract contract = contractResolver.requireResolved(pair);
            if (contract.conId() <= 0 || (contract.metadataJson() != null
                    && contract.metadataJson().matches("(?s).*\"syntheticCashContract\"\\s*:\\s*true.*")))
                throw new IllegalStateException("A real broker-resolved conId is required, not a synthetic contract");
            return Optional.of(contract);
        } catch (RuntimeException error) {
            throw new IbkrMarketDataException("ibkr.contract.unresolved: resolve the IBKR contract for " + pair, error);
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
        return lastPriceBySymbol.compute(key, (_, previous) -> {
            double current = previous == null ? seedPrice(pair) : previous;
            return Math.max(0.01, current * (1 + ThreadLocalRandom.current().nextDouble(-0.003, 0.003)));
        });
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
