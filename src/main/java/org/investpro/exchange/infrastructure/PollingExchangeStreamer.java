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
    private ScheduledExecutorService scheduler;
    private final Set<ExchangeFeature> skippedFeatures = ConcurrentHashMap.newKeySet();
    private final Map<TradePair, ScheduledFuture<?>> tickerTasks = new ConcurrentHashMap<>();
    private final Map<TradePair, AtomicInteger> tickerCycles = new ConcurrentHashMap<>();
    private final Map<TradePair, AtomicInteger> orderBookCycles = new ConcurrentHashMap<>();

    @Override
    public String toString() {
        return "PollingExchangeStreamer{exchange=%s, tickerTasks=%d, orderBookTasks=%d, accountTaskActive=%s, ordersTaskActive=%s, positionsTaskActive=%s}"
                .formatted(exchange.getName(), tickerTasks.size(), orderBookTasks.size(), accountTask != null,
                        ordersTask != null, positionsTask != null);
    }

    private final Map<TradePair, ScheduledFuture<?>> orderBookTasks = new ConcurrentHashMap<>();
    private ScheduledFuture<?> accountTask;
    private ScheduledFuture<?> ordersTask;
    private ScheduledFuture<?> positionsTask;

    public PollingExchangeStreamer(Exchange exchange) {
        this(exchange, () -> Executors.newScheduledThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "%s-polling-stream".formatted(exchange.getName()));
            thread.setDaemon(true);
            return thread;
        }));
    }

    PollingExchangeStreamer(Exchange exchange, Supplier<ScheduledExecutorService> schedulerFactory) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.schedulerFactory = Objects.requireNonNull(schedulerFactory, "schedulerFactory must not be null");
    }

    public synchronized void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        tickerTasks.computeIfAbsent(tradePair, pair -> scheduleAtFixedRate(() -> {
            try {
                if (exchange instanceof Coinbase coinbase) {
                    AtomicInteger cycle = tickerCycles.computeIfAbsent(pair, ignored -> new AtomicInteger(0));
                    int n = cycle.incrementAndGet();

                    var snapshot = coinbase.getLatestSnapshot(pair);
                    if (snapshot.ticker() != null) {
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

                exchange.fetchOrderBook(pair)
                        .thenAccept(orderBook -> consumer.onOrderBook(exchange.getName(), pair, orderBook))
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
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
            try {
                exchange.fetchAccount()
                        .exceptionally(ex -> {
                            try {
                                return exchange.getUserAccountDetails();
                            } catch (Exception exception) {
                                throw new RuntimeException(exception);
                            }
                        })
                        .thenAccept(account -> {
                            consumer.onAccount(exchange.getName(), account);
                            if (exchange.canUseCapability(ExchangeFeature.BALANCES)) {
                                consumer.onBalanceChanged(exchange.getName(), account);
                            }
                        })
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
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
            try {
                exchange.fetchAllOpenOrders()
                        .thenAccept(orders -> consumer.onOpenOrders(exchange.getName(), orders))
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
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
            try {
                exchange.fetchAllPositions()
                        .thenAccept(positions -> consumer.onPositions(exchange.getName(), positions))
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, privatePeriodSeconds());
    }

    public synchronized void stopTicker(TradePair tradePair) {
        cancel(tickerTasks.remove(tradePair));
        tickerCycles.remove(tradePair);
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
    }

    private @NotNull ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long periodSeconds) {
        if (scheduler == null) {
            scheduler = schedulerFactory.get();
        }
        return scheduler.scheduleAtFixedRate(() -> {
            try {
                runnable.run();
            } catch (Throwable ignored) {
                // Individual tasks report errors to their consumer; keep scheduler threads
                // alive.
            }
        }, 0, periodSeconds, TimeUnit.SECONDS);
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
