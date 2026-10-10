package org.investpro.exchange.infrastructure;

import org.investpro.models.trading.TradePair;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.oanda.Oanda;
import org.jetbrains.annotations.NotNull;
import org.investpro.exchange.models.ExchangeFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.function.Supplier;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PollingExchangeStreamer {
    private static final Logger log = LoggerFactory.getLogger(PollingExchangeStreamer.class);
    private static final long DEFAULT_TICKER_PERIOD_SECONDS = 10;
    private static final long DEFAULT_ORDER_BOOK_PERIOD_SECONDS = 15;
    private static final long DEFAULT_ACCOUNT_PERIOD_SECONDS = 15;
    private static final long DEFAULT_PRIVATE_PERIOD_SECONDS = 20;

    private final Exchange exchange;
    private final Supplier<ScheduledExecutorService> schedulerFactory;
    private final java.util.concurrent.Executor marketWorker;
    private final Map<TradePair, java.util.concurrent.CopyOnWriteArrayList<ExchangeStreamConsumer>> oandaTickerConsumers = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean oandaPricingInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicLong streamGeneration = new java.util.concurrent.atomic.AtomicLong();
    private ScheduledFuture<?> oandaTickerTask;
    private ScheduledExecutorService scheduler;
    private final Set<ExchangeFeature> skippedFeatures = ConcurrentHashMap.newKeySet();
    private final Map<TradePair, ScheduledFuture<?>> tickerTasks = new ConcurrentHashMap<>();
    private final Map<TradePair, AtomicInteger> tickerCycles = new ConcurrentHashMap<>();
    private final Map<TradePair, AtomicInteger> orderBookCycles = new ConcurrentHashMap<>();
    private record CandleKey(TradePair pair, int seconds) {}
    private final Map<CandleKey, ScheduledFuture<?>> candleTasks = new ConcurrentHashMap<>();
    private long coinbasePollSequence;

    @Override
    public String toString() {
        return "PollingExchangeStreamer{exchange=%s, tickerTasks=%d, orderBookTasks=%d, accountTaskActive=%s, ordersTaskActive=%s, positionsTaskActive=%s}"
                .formatted(exchange.getName(), tickerTasks.size(), orderBookTasks.size(), accountTask != null,
                        ordersTask != null, positionsTask != null);
    }

    private final Map<TradePair, ScheduledFuture<?>> orderBookTasks = new ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicBoolean accountInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean ordersInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean positionsInFlight = new java.util.concurrent.atomic.AtomicBoolean();
    private final Map<TradePair, java.util.concurrent.atomic.AtomicBoolean> depthInFlight = new ConcurrentHashMap<>();
    private ScheduledFuture<?> accountTask;
    private ScheduledFuture<?> ordersTask;
    private ScheduledFuture<?> positionsTask;

    public PollingExchangeStreamer(Exchange exchange) {
        this(exchange, () -> Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "%s-polling-stream".formatted(exchange.getName()));
            thread.setDaemon(true);
            return thread;
        }), org.investpro.core.concurrent.AppExecutors.MARKET_DATA);
    }

    PollingExchangeStreamer(Exchange exchange, Supplier<ScheduledExecutorService> schedulerFactory) {
        this(exchange, schedulerFactory, Runnable::run);
    }

    PollingExchangeStreamer(Exchange exchange, Supplier<ScheduledExecutorService> schedulerFactory,
            java.util.concurrent.Executor marketWorker) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory, "schedulerFactory must not be null");
        this.marketWorker = Objects.requireNonNull(marketWorker);
    }

    public synchronized void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        if (exchange instanceof Oanda oanda) {
            oandaTickerConsumers.computeIfAbsent(tradePair, _ -> new java.util.concurrent.CopyOnWriteArrayList<>()).addIfAbsent(consumer);
            if (oandaTickerTask == null) oandaTickerTask = scheduleAtFixedRate(() -> pollOandaPrices(oanda), tickerPeriodSeconds());
            return;
        }
        tickerTasks.computeIfAbsent(tradePair, pair -> scheduleAtFixedRate(() -> {
            try {
                if (exchange instanceof Coinbase coinbase) {
                    AtomicInteger cycle = tickerCycles.computeIfAbsent(pair, ignored -> new AtomicInteger(0));
                    int n = cycle.incrementAndGet();

                    var snapshot = coinbase.getLatestSnapshot(pair);
                    if (snapshot.ticker() != null
                            && System.currentTimeMillis() - snapshot.ticker().getTimestamp() <= 25_000
                            && snapshot.ticker().getTimestamp() <= System.currentTimeMillis() + 1_000) {
                        consumer.onTicker(exchange.getName(), pair, snapshot.ticker());
                        return;
                    }

                    // Sparse fallback refresh: one REST-backed attempt every 3 cycles.
                    if (n % 3 != 0) {
                        return;
                    }
                }
                consumer.onTicker(exchange.getName(), pair, exchange.getLivePrice(pair));
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, tickerPeriodSeconds()));
    }

    public synchronized void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        if (exchange instanceof Oanda) {
            log.debug("OANDA has no exchange depth stream; order book polling disabled");
            return;
        }
        orderBookTasks.computeIfAbsent(tradePair, pair -> scheduleAtFixedRate(() -> {
            try {
                if (exchange instanceof Coinbase coinbase) {
                    AtomicInteger cycle = orderBookCycles.computeIfAbsent(pair, ignored -> new AtomicInteger(0));
                    int n = cycle.incrementAndGet();

                    var snapshot = coinbase.getLatestSnapshot(pair);
                    if (snapshot.orderBook() != null) {
                        consumer.onOrderBook(exchange.getName(), pair, snapshot.orderBook());
                        return;
                    }

                    // Sparse fallback refresh: one REST-backed attempt every 5 cycles.
                    if (n % 5 != 0) {
                        return;
                    }
                }

                pollAsync(depthInFlight.computeIfAbsent(pair, _ -> new java.util.concurrent.atomic.AtomicBoolean()),
                        () -> exchange.fetchOrderBook(pair),
                        book -> consumer.onOrderBook(exchange.getName(), pair, book), consumer);
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, orderBookPeriodSeconds()));
    }

    private long tickerPeriodSeconds() {
        if (exchange instanceof Coinbase) {
            return 20L;
        }
        if (exchange instanceof Oanda) {
            return 30L;
        }
        return DEFAULT_TICKER_PERIOD_SECONDS;
    }

    private long orderBookPeriodSeconds() {
        if (exchange instanceof Coinbase) {
            return 60L;
        }
        if (exchange instanceof Oanda) {
            return 60L;
        }
        return DEFAULT_ORDER_BOOK_PERIOD_SECONDS;
    }

    private long accountPeriodSeconds() {
        if (exchange instanceof Oanda) {
            return 60L;
        }
        return DEFAULT_ACCOUNT_PERIOD_SECONDS;
    }

    private long privatePeriodSeconds() {
        if (exchange instanceof Oanda) {
            return 60L;
        }
        return DEFAULT_PRIVATE_PERIOD_SECONDS;
    }

    private boolean canStart(ExchangeFeature feature) {
        if (exchange.canUseCapability(feature)) {
            skippedFeatures.remove(feature);
            return true;
        }
        if (skippedFeatures.add(feature)) {
            exchange.getCapability();
            boolean supported = exchange.getCapability().supports(feature);
            log.info("Private polling disabled. exchange={} feature={} reason={}", exchange.getName(), feature,
                    supported ? "authentication-not-configured" : "unsupported-capability");
        }
        return false;
    }

    public synchronized void streamBalances(ExchangeStreamConsumer consumer) {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.streamBalances(consumer);
            return;
        }
        if (canStart(ExchangeFeature.BALANCES)) {
            streamAccount(consumer);
        }
    }

    /** Existing fill fallback shares the orders poller; no separate fill endpoint is polled. */
    public synchronized void streamFills(ExchangeStreamConsumer consumer) {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.streamFills(consumer);
            return;
        }
        if (canStart(ExchangeFeature.FILLS)) {
            streamOrders(consumer);
        }
    }

    public synchronized void streamAccount(ExchangeStreamConsumer consumer) {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.streamAccount(consumer);
            return;
        }
        if (!canStart(ExchangeFeature.ACCOUNT_INFO)) {
            return;
        }
        if (accountTask != null && !accountTask.isCancelled()) {
            return;
        }

        accountTask = scheduleAtFixedRate(() -> {
            if (!canStart(ExchangeFeature.ACCOUNT_INFO)) return;
            pollAsync(accountInFlight, () -> exchange.fetchAccount().exceptionallyAsync(error -> {
                try { return exchange.getUserAccountDetails(); }
                catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
            }, marketWorker), account -> {
                consumer.onAccount(exchange.getName(), account);
                if (exchange.canUseCapability(ExchangeFeature.BALANCES)) consumer.onBalanceChanged(exchange.getName(), account);
            }, consumer);
        }, accountPeriodSeconds());
    }
    public synchronized void streamOrders(ExchangeStreamConsumer consumer) {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.streamOrders(consumer);
            return;
        }
        if (!canStart(ExchangeFeature.OPEN_ORDERS)) {
            return;
        }
        if (ordersTask != null && !ordersTask.isCancelled()) {
            return;
        }

        ordersTask = scheduleAtFixedRate(() -> {
            if (!canStart(ExchangeFeature.OPEN_ORDERS)) return;
            pollAsync(ordersInFlight, exchange::fetchAllOpenOrders,
                    orders -> consumer.onOpenOrders(exchange.getName(), orders), consumer);
        }, privatePeriodSeconds());
    }
    public synchronized void streamPositions(ExchangeStreamConsumer consumer) {
        if (!canStart(ExchangeFeature.POSITIONS)) {
            return;
        }
        if (positionsTask != null && !positionsTask.isCancelled()) {
            return;
        }

        positionsTask = scheduleAtFixedRate(() -> {
            if (!canStart(ExchangeFeature.POSITIONS)) return;
            pollAsync(positionsInFlight, exchange::fetchAllPositions,
                    positions -> consumer.onPositions(exchange.getName(), positions), consumer);
        }, privatePeriodSeconds());
    }

    private <T> void pollAsync(java.util.concurrent.atomic.AtomicBoolean gate,
            Supplier<java.util.concurrent.CompletableFuture<T>> request, java.util.function.Consumer<T> deliver,
            ExchangeStreamConsumer consumer) {
        if (!gate.compareAndSet(false, true)) return;
        long session = streamGeneration.get();
        try {
            request.get().whenComplete((value, error) -> {
                try {
                    if (session != streamGeneration.get()) return;
                    if (error != null) consumer.onError(exchange.getName(), unwrap(error));
                    else deliver.accept(value);
                } finally { gate.set(false); }
            });
        } catch (Exception error) {
            gate.set(false);
            if (session == streamGeneration.get()) consumer.onError(exchange.getName(), error);
        }
    }
    public synchronized void stopTicker(TradePair tradePair) {
        oandaTickerConsumers.remove(tradePair);
        if (oandaTickerConsumers.isEmpty()) { cancel(oandaTickerTask); oandaTickerTask = null; }
        cancel(tickerTasks.remove(tradePair));
        tickerCycles.remove(tradePair);
    }

    public synchronized void streamCandles(TradePair pair, int seconds, ExchangeStreamConsumer consumer) {
        if (seconds <= 0) throw new IllegalArgumentException("Candle duration must be positive");
        candleTasks.computeIfAbsent(new CandleKey(pair, seconds), _ -> scheduleAtFixedRate(() -> {
            try {
                var supplier = exchange.getCandleDataSupplier(seconds, pair);
                if (supplier == null) throw new IllegalStateException("Candle supplier unavailable");
                var pending = supplier.get();
                java.util.List<org.investpro.data.CandleData> candles;
                try { candles = pending.get(15, TimeUnit.SECONDS); }
                catch (Exception error) { pending.cancel(true); throw error; }
                long now = java.time.Instant.now().getEpochSecond();
                var latest = candles.stream().filter(java.util.Objects::nonNull)
                        .filter(candle -> (long) candle.openTime() + seconds <= now)
                        .max(java.util.Comparator.comparingInt(org.investpro.data.CandleData::openTime));
                if (latest.isPresent()) {
                    // Quotes must reach the signal agent before the candle is evaluated.
                    consumer.onTicker(exchange.getName(), pair, exchange.getLivePrice(pair));
                    consumer.onCandle(exchange.getName(), pair, latest.get());
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (Exception error) {
                consumer.onError(exchange.getName(), unwrap(error));
            }
        }, 60));
    }

    public synchronized void stopCandles(TradePair pair, int seconds) {
        cancel(candleTasks.remove(new CandleKey(pair, seconds)));
    }

    public synchronized void stopOrderBook(TradePair tradePair) {
        cancel(orderBookTasks.remove(tradePair));
        orderBookCycles.remove(tradePair);
    }

    public synchronized void stopAccount() {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.stopAccountStream(); binanceUs.stopBalancesStream();
        }
        cancel(accountTask);
        accountTask = null;
    }

    public synchronized void stopOrders() {
        if (exchange instanceof org.investpro.exchange.binanceus.BinanceUs binanceUs) {
            binanceUs.stopOrdersStream(); binanceUs.stopFillsStream();
        }
        cancel(ordersTask);
        ordersTask = null;
    }

    public synchronized void stopPositions() {
        cancel(positionsTask);
        positionsTask = null;
    }

    public synchronized void stopAll() {
        streamGeneration.incrementAndGet();
        cancel(oandaTickerTask); oandaTickerTask = null; oandaTickerConsumers.clear();
        candleTasks.values().forEach(this::cancel);
        candleTasks.clear();
        tickerTasks.values().forEach(this::cancel);
        orderBookTasks.values().forEach(this::cancel);
        tickerTasks.clear();
        orderBookTasks.clear();
        tickerCycles.clear();
        orderBookCycles.clear();
        stopAccount();
        stopOrders();
        stopPositions();
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        coinbasePollSequence = 0;
    }

    private @NotNull ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long periodSeconds) {
        if (scheduler == null) {
            scheduler = schedulerFactory.get();
        }
        var inFlight = new java.util.concurrent.atomic.AtomicBoolean();
        long session = streamGeneration.get();
        return scheduler.scheduleAtFixedRate(() -> {
            if (!inFlight.compareAndSet(false, true)) return;
            try { marketWorker.execute(() -> {
                try { if (session == streamGeneration.get()) runnable.run(); }
                catch (Throwable error) { log.warn("Polling task failed for {}", exchange.getName(), error); }
                finally { inFlight.set(false); }
            }); }
            catch (java.util.concurrent.RejectedExecutionException error) {
                inFlight.set(false); log.warn("Market data workers busy; deferring {} poll", exchange.getName());
            }
        }, exchange instanceof Coinbase ? coinbasePollSequence++ % periodSeconds : 0,
                periodSeconds, TimeUnit.SECONDS);
    }

    private void pollOandaPrices(Oanda oanda) {
        if (!oandaPricingInFlight.compareAndSet(false, true)) return;
        long session = streamGeneration.get();
        var pairs = oandaTickerConsumers.keySet().stream().sorted(java.util.Comparator.comparing(p -> p.toString('/'))).toList();
        try {
            java.util.concurrent.CompletableFuture<Map<String, org.investpro.models.trading.Ticker>> prices =
                    java.util.concurrent.CompletableFuture.completedFuture(new java.util.HashMap<>());
            for (int offset = 0; offset < pairs.size(); offset += 20) {
                var batch = pairs.subList(offset, Math.min(offset + 20, pairs.size()));
                prices = prices.thenCompose(accumulator -> oanda.getLatestPrices(batch).thenApply(values -> {
                    if (values != null) accumulator.putAll(values);
                    return accumulator;
                }));
            }
            prices.whenComplete((quotes, error) -> {
                try {
                    if (session != streamGeneration.get()) return;
                    for (TradePair pair : pairs) {
                        var consumers = oandaTickerConsumers.get(pair);
                        if (consumers == null) continue;
                        for (ExchangeStreamConsumer consumer : consumers) {
                            if (error != null) { consumer.onError(exchange.getName(), unwrap(error)); continue; }
                            var ticker = quotes == null ? null : quotes.get(pair.toString('_'));
                            if (ticker != null) consumer.onTicker(exchange.getName(), pair, ticker);
                        }
                    }
                } finally { oandaPricingInFlight.set(false); }
            });
        } catch (Exception error) {
            oandaPricingInFlight.set(false);
            oandaTickerConsumers.values().forEach(consumers -> consumers.forEach(c -> c.onError(exchange.getName(), error)));
        }
    }

    private void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private Throwable unwrap(@NotNull Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }
}
