package org.investpro.exchange.infrastructure;

import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Trade;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.Coinbase;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PollingExchangeStreamer {
    private static final long DEFAULT_TICKER_PERIOD_SECONDS = 10;
    private static final long DEFAULT_ORDER_BOOK_PERIOD_SECONDS = 15;
    private static final long DEFAULT_ACCOUNT_PERIOD_SECONDS = 15;
    private static final long DEFAULT_PRIVATE_PERIOD_SECONDS = 20;

    private final Exchange exchange;
    private final ScheduledExecutorService scheduler;
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
    private ScheduledFuture<?> fillsTask;
    private ScheduledFuture<?> positionsTask;
    private final Set<String> seenFillKeys = ConcurrentHashMap.newKeySet();

    public PollingExchangeStreamer(Exchange exchange) {
        this.exchange = Objects.requireNonNull(exchange, "exchange must not be null");
        this.scheduler = Executors.newScheduledThreadPool(3, runnable -> {
            Thread thread = new Thread(runnable, "%s-polling-stream".formatted(exchange.getName()));
            thread.setDaemon(true);
            return thread;
        });
    }

    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
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

    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
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
        return DEFAULT_TICKER_PERIOD_SECONDS;
    }

    private long orderBookPeriodSeconds() {
        if (exchange instanceof Coinbase) {
            return 60L;
        }
        return DEFAULT_ORDER_BOOK_PERIOD_SECONDS;
    }

    public void streamAccount(ExchangeStreamConsumer consumer) {
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
                            consumer.onBalanceChanged(exchange.getName(), account);
                        })
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, DEFAULT_ACCOUNT_PERIOD_SECONDS);
    }

    public void streamOrders(ExchangeStreamConsumer consumer) {
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
        }, DEFAULT_PRIVATE_PERIOD_SECONDS);
    }

    public void streamFills(Set<TradePair> tradePairs, ExchangeStreamConsumer consumer) {
        if (fillsTask != null && !fillsTask.isCancelled()) {
            return;
        }

        Set<TradePair> pairs = tradePairs == null ? Set.of() : new HashSet<>(tradePairs);
        if (pairs.isEmpty()) {
            return;
        }

        fillsTask = scheduleAtFixedRate(() -> {
            for (TradePair pair : pairs) {
                if (pair == null) {
                    continue;
                }

                try {
                    exchange.fetchAccountTrades(pair)
                            .thenAccept(trades -> publishNewFills(pair, trades, consumer))
                            .exceptionally(throwable -> {
                                consumer.onError(exchange.getName(), unwrap(throwable));
                                return null;
                            });
                } catch (Exception exception) {
                    consumer.onError(exchange.getName(), exception);
                }
            }
        }, DEFAULT_PRIVATE_PERIOD_SECONDS);
    }

    public void streamFills(ExchangeStreamConsumer consumer) {
        streamFills(Set.of(), consumer);
    }

    private void publishNewFills(TradePair pair, List<Trade> trades, ExchangeStreamConsumer consumer) {
        if (trades == null || trades.isEmpty()) {
            return;
        }

        trades.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(trade -> {
                    Instant timestamp = trade.getTimestamp();
                    return timestamp == null ? Instant.EPOCH : timestamp;
                }))
                .forEach(trade -> {
                    String key = fillKey(pair, trade);
                    if (seenFillKeys.add(key)) {
                        consumer.onFill(exchange.getName(), pair, trade);
                    }
                });
    }

    public void streamPositions(ExchangeStreamConsumer consumer) {
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
        }, DEFAULT_PRIVATE_PERIOD_SECONDS);
    }

    public void stopTicker(TradePair tradePair) {
        cancel(tickerTasks.remove(tradePair));
        tickerCycles.remove(tradePair);
    }

    public void stopOrderBook(TradePair tradePair) {
        cancel(orderBookTasks.remove(tradePair));
        orderBookCycles.remove(tradePair);
    }

    public void stopAccount() {
        cancel(accountTask);
        accountTask = null;
    }

    public void stopOrders() {
        cancel(ordersTask);
        ordersTask = null;
    }

    public void stopFills() {
        cancel(fillsTask);
        fillsTask = null;
        seenFillKeys.clear();
    }

    public void stopPositions() {
        cancel(positionsTask);
        positionsTask = null;
    }

    public void stopAll() {
        tickerTasks.values().forEach(this::cancel);
        orderBookTasks.values().forEach(this::cancel);
        tickerTasks.clear();
        orderBookTasks.clear();
        tickerCycles.clear();
        orderBookCycles.clear();
        stopAccount();
        stopOrders();
        stopFills();
        stopPositions();
    }

    private @NotNull ScheduledFuture<?> scheduleAtFixedRate(Runnable runnable, long periodSeconds) {
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

    private String fillKey(TradePair pair, Trade trade) {
        return "%s::%d::%s::%.12f::%.12f::%s".formatted(
                pair == null ? "unknown" : pair.toString('/'),
                trade.getLocalTradeId(),
                trade.getTimestamp(),
                trade.getPrice(),
                trade.getAmount(),
                trade.getTransactionType());
    }
}
