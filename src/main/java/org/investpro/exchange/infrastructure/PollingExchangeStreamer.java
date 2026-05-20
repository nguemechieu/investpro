package org.investpro.exchange.infrastructure;

import org.investpro.models.trading.TradePair;
import org.investpro.exchange.Exchange;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
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
                consumer.onTicker(exchange.getName(), pair, exchange.getLivePrice(pair));
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, DEFAULT_TICKER_PERIOD_SECONDS));
    }

    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        orderBookTasks.computeIfAbsent(tradePair, pair -> scheduleAtFixedRate(() -> {
            try {
                exchange.fetchOrderBook(pair)
                        .thenAccept(orderBook -> consumer.onOrderBook(exchange.getName(), pair, orderBook))
                        .exceptionally(throwable -> {
                            consumer.onError(exchange.getName(), unwrap(throwable));
                            return null;
                        });
            } catch (Exception exception) {
                consumer.onError(exchange.getName(), exception);
            }
        }, DEFAULT_ORDER_BOOK_PERIOD_SECONDS));
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
    }

    public void stopOrderBook(TradePair tradePair) {
        cancel(orderBookTasks.remove(tradePair));
    }

    public void stopAccount() {
        cancel(accountTask);
        accountTask = null;
    }

    public void stopOrders() {
        cancel(ordersTask);
        ordersTask = null;
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
        stopAccount();
        stopOrders();
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
}
