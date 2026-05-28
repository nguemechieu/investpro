package org.investpro.exchange.consumers;

import javafx.application.Platform;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * JavaFX-safe UI stream consumer for InvestPro.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Receives live exchange stream events.</li>
 *     <li>Caches latest ticker/order book/candle/account/position/order/trade data.</li>
 *     <li>Dispatches updates to JavaFX UI safely.</li>
 *     <li>Tracks stream health/event counters.</li>
 * </ul>
 *
 * <p>This class should not:</p>
 * <ul>
 *     <li>Place orders.</li>
 *     <li>Run strategies.</li>
 *     <li>Execute risk checks.</li>
 *     <li>Call REST polling repeatedly.</li>
 * </ul>
 */
@Slf4j
@Getter
public class UiExchangeStreamConsumer implements ExchangeStreamConsumer {

    private static final int MAX_RECENT_TRADES = 500;
    private static final int MAX_RECENT_FILLS = 500;
    private static final int MAX_RECENT_ORDERS = 500;
    private static final int MAX_RECENT_POSITIONS = 500;

    private final Map<String, Ticker> latestTickers = new ConcurrentHashMap<>();
    private final Map<String, OrderBook> latestOrderBooks = new ConcurrentHashMap<>();
    private final Map<String, CandleData> latestCandles = new ConcurrentHashMap<>();

    private final ConcurrentLinkedDeque<Trade> recentTrades = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Trade> recentFills = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<OpenOrder> openOrders = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<Position> positions = new ConcurrentLinkedDeque<>();

    private final AtomicLong tickerEvents = new AtomicLong();
    private final AtomicLong tradeEvents = new AtomicLong();
    private final AtomicLong candleEvents = new AtomicLong();
    private final AtomicLong orderBookEvents = new AtomicLong();
    private final AtomicLong accountEvents = new AtomicLong();
    private final AtomicLong orderEvents = new AtomicLong();
    private final AtomicLong fillEvents = new AtomicLong();
    private final AtomicLong positionEvents = new AtomicLong();
    private final AtomicLong connectionEvents = new AtomicLong();
    private final AtomicLong rawMessageEvents = new AtomicLong();
    private final AtomicLong errorEvents = new AtomicLong();

    private volatile Account latestAccount;
    private volatile Instant lastUpdateAt;
    private volatile Instant lastErrorAt;
    private volatile String lastExchangeName = "";
    private volatile String lastStatusMessage = "";
    private volatile String lastRawChannel = "";
    private volatile TradePair lastTradePair;

    private volatile Consumer<Ticker> tickerHandler;
    private volatile Consumer<Trade> tradeHandler;
    private volatile Consumer<CandleData> candleHandler;
    private volatile Consumer<OrderBook> orderBookHandler;
    private volatile Consumer<Account> accountHandler;
    private volatile Consumer<OpenOrder> openOrderHandler;
    private volatile Consumer<List<OpenOrder>> openOrdersHandler;
    private volatile Consumer<Trade> fillHandler;
    private volatile Consumer<Position> positionHandler;
    private volatile Consumer<List<Position>> positionsHandler;
    private volatile BiConsumer<String, Throwable> errorHandler;
    private volatile Consumer<String> statusHandler;
    private volatile Consumer<String> rawMessageHandler;

    public UiExchangeStreamConsumer onTickerUpdate(@Nullable Consumer<Ticker> handler) {
        this.tickerHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onTradeUpdate(@Nullable Consumer<Trade> handler) {
        this.tradeHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onCandleUpdate(@Nullable Consumer<CandleData> handler) {
        this.candleHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onOrderBookUpdate(@Nullable Consumer<OrderBook> handler) {
        this.orderBookHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onAccountUpdate(@Nullable Consumer<Account> handler) {
        this.accountHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onOpenOrderUpdate(@Nullable Consumer<OpenOrder> handler) {
        this.openOrderHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onOpenOrdersUpdate(@Nullable Consumer<List<OpenOrder>> handler) {
        this.openOrdersHandler = handler;
        publishOpenOrdersSnapshot();
        return this;
    }

    @Override
    public UiExchangeStreamConsumer onOrdersUpdate(@Nullable Consumer<List<OpenOrder>> handler) {
        this.openOrdersHandler = handler;
        publishOpenOrdersSnapshot();
        return this;
    }

    public UiExchangeStreamConsumer onFillUpdate(@Nullable Consumer<Trade> handler) {
        this.fillHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onPositionUpdate(@Nullable Consumer<Position> handler) {
        this.positionHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onPositionsUpdate(@Nullable Consumer<List<Position>> handler) {
        this.positionsHandler = handler;
        publishPositionsSnapshot();
        return this;
    }

    public UiExchangeStreamConsumer onError(@Nullable BiConsumer<String, Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }

    public UiExchangeStreamConsumer onStatus(@Nullable Consumer<String> handler) {
        this.statusHandler = handler;

        if (handler != null && lastStatusMessage != null && !lastStatusMessage.isBlank()) {
            runOnFx(() -> handler.accept(lastStatusMessage));
        }

        return this;
    }

    public UiExchangeStreamConsumer onRawMessageUpdate(@Nullable Consumer<String> handler) {
        this.rawMessageHandler = handler;
        return this;
    }

    @Override
    public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
        if (tradePair == null || ticker == null) {
            return;
        }

        markUpdated(exchangeName, tradePair);
        tickerEvents.incrementAndGet();
        latestTickers.put(key(exchangeName, tradePair), ticker);

        runOnFx(() -> {
            Consumer<Ticker> handler = tickerHandler;
            if (handler != null) {
                handler.accept(ticker);
            }
        });
    }

    @Override
    public void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
        if (trade == null) {
            return;
        }

        markUpdated(exchangeName, tradePair);
        tradeEvents.incrementAndGet();

        recentTrades.addFirst(trade);
        trim(recentTrades, MAX_RECENT_TRADES);

        runOnFx(() -> {
            Consumer<Trade> handler = tradeHandler;
            if (handler != null) {
                handler.accept(trade);
            }
        });
    }

    @Override
    public void onCandle(String exchangeName, TradePair tradePair, CandleData candle) {
        if (tradePair == null || candle == null) {
            return;
        }

        markUpdated(exchangeName, tradePair);
        candleEvents.incrementAndGet();
        latestCandles.put(key(exchangeName, tradePair), candle);

        runOnFx(() -> {
            Consumer<CandleData> handler = candleHandler;
            if (handler != null) {
                handler.accept(candle);
            }
        });
    }

    @Override
    public void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
        if (tradePair == null || orderBook == null) {
            return;
        }

        markUpdated(exchangeName, tradePair);
        orderBookEvents.incrementAndGet();
        latestOrderBooks.put(key(exchangeName, tradePair), orderBook);

        runOnFx(() -> {
            Consumer<OrderBook> handler = orderBookHandler;
            if (handler != null) {
                handler.accept(orderBook);
            }
        });
    }

    @Override
    public void onAccount(String exchangeName, Account account) {
        if (account == null) {
            return;
        }

        markUpdated(exchangeName, null);
        accountEvents.incrementAndGet();
        latestAccount = account;

        runOnFx(() -> {
            Consumer<Account> handler = accountHandler;
            if (handler != null) {
                handler.accept(account);
            }
        });
    }

    @Override
    public void onBalanceChanged(String exchangeName, Account account) {
        onAccount(exchangeName, account);
    }

    @Override
    public void onBalance(@Nullable String exchangeName, @Nullable Account account) {
        onAccount(exchangeName, account);
    }

    @Override
    public void onOrder(@Nullable String exchangeName, @Nullable OpenOrder order) {
        onOpenOrder(exchangeName, order);
    }

    public void onOpenOrder(@Nullable String exchangeName, @Nullable OpenOrder order) {
        if (order == null) {
            return;
        }

        markUpdated(exchangeName, null);
        orderEvents.incrementAndGet();

        openOrders.addFirst(order);
        trim(openOrders, MAX_RECENT_ORDERS);

        runOnFx(() -> {
            Consumer<OpenOrder> singleHandler = openOrderHandler;
            if (singleHandler != null) {
                singleHandler.accept(order);
            }

            Consumer<List<OpenOrder>> listHandler = openOrdersHandler;
            if (listHandler != null) {
                listHandler.accept(getOpenOrdersSnapshot());
            }
        });
    }

    @Override
    public void onOpenOrders(String exchangeName, List<OpenOrder> orders) {
        onOrders(exchangeName, orders);
    }

    @Override
    public void onOrders(@Nullable String exchangeName, @Nullable List<OpenOrder> orders) {
        markUpdated(exchangeName, null);

        openOrders.clear();

        if (orders != null) {
            for (OpenOrder order : orders) {
                if (order != null) {
                    openOrders.addLast(order);
                }

                if (openOrders.size() >= MAX_RECENT_ORDERS) {
                    break;
                }
            }
        }

        orderEvents.addAndGet(openOrders.size());
        publishOpenOrdersSnapshot();
    }

    public void onFill(@Nullable String exchangeName, @Nullable TradePair tradePair, @Nullable Trade fill) {
        if (fill == null) {
            return;
        }

        markUpdated(exchangeName, tradePair);
        fillEvents.incrementAndGet();

        recentFills.addFirst(fill);
        recentTrades.addFirst(fill);

        trim(recentFills, MAX_RECENT_FILLS);
        trim(recentTrades, MAX_RECENT_TRADES);

        runOnFx(() -> {
            Consumer<Trade> fillUpdate = fillHandler;
            if (fillUpdate != null) {
                fillUpdate.accept(fill);
            }

            Consumer<Trade> tradeUpdate = tradeHandler;
            if (tradeUpdate != null) {
                tradeUpdate.accept(fill);
            }
        });
    }

    public void onPosition(@Nullable String exchangeName, @Nullable Position position) {
        if (position == null) {
            return;
        }

        markUpdated(exchangeName, null);
        positionEvents.incrementAndGet();

        positions.addFirst(position);
        trim(positions, MAX_RECENT_POSITIONS);

        runOnFx(() -> {
            Consumer<Position> singleHandler = positionHandler;
            if (singleHandler != null) {
                singleHandler.accept(position);
            }

            Consumer<List<Position>> listHandler = positionsHandler;
            if (listHandler != null) {
                listHandler.accept(getPositionsSnapshot());
            }
        });
    }

    public void onPositions(@Nullable String exchangeName, @Nullable List<Position> newPositions) {
        markUpdated(exchangeName, null);

        positions.clear();

        if (newPositions != null) {
            for (Position position : newPositions) {
                if (position != null) {
                    positions.addLast(position);
                }

                if (positions.size() >= MAX_RECENT_POSITIONS) {
                    break;
                }
            }
        }

        positionEvents.addAndGet(positions.size());
        publishPositionsSnapshot();
    }

    @Override
    public boolean containsKey(TradePair tradePair) {
        if (tradePair == null) {
            return false;
        }

        String suffix = ":" + safePair(tradePair);
        return latestTickers.keySet().stream().anyMatch(key -> key.endsWith(suffix))
                || latestOrderBooks.keySet().stream().anyMatch(key -> key.endsWith(suffix))
                || latestCandles.keySet().stream().anyMatch(key -> key.endsWith(suffix));
    }

    @Override
    public ExchangeStreamConsumer get(TradePair tradePair) {
        return this;
    }

    @Override
    public void put(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {
        // No-op. This consumer is not a registry.
    }

    @Override
    public void remove(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }

        String suffix = ":" + safePair(tradePair);

        latestTickers.keySet().removeIf(key -> key.endsWith(suffix));
        latestOrderBooks.keySet().removeIf(key -> key.endsWith(suffix));
        latestCandles.keySet().removeIf(key -> key.endsWith(suffix));
    }

    @Override
    public void acceptTrades(Trade newTrade) {
        if (newTrade == null) {
            return;
        }

        onTrade(lastExchangeName, lastTradePair, newTrade);
    }

    @Override
    public void onConnected(String exchangeName) {
        String safeExchangeName = normalizeExchangeName(exchangeName);

        markUpdated(safeExchangeName, null);
        connectionEvents.incrementAndGet();

        onStatus(safeExchangeName, "connected");
    }

    @Override
    public void onDisconnected(String exchangeName, String reason) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeReason = reason == null || reason.isBlank() ? "disconnected" : reason.trim();

        markUpdated(safeExchangeName, null);
        connectionEvents.incrementAndGet();

        onStatus(safeExchangeName, safeReason);
    }

    @Override
    public void onOrderAccepted(String exchangeName, String orderId) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = safeId(orderId);

        markUpdated(safeExchangeName, null);
        orderEvents.incrementAndGet();

        onStatus(safeExchangeName, "order accepted: " + safeOrderId);
    }

    @Override
    public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = safeId(clientOrderId);
        String safeReason = reason == null || reason.isBlank() ? "rejected" : reason.trim();

        markUpdated(safeExchangeName, null);
        orderEvents.incrementAndGet();
        errorEvents.incrementAndGet();
        lastErrorAt = Instant.now();

        onStatus(safeExchangeName, "order rejected: " + safeOrderId + " | " + safeReason);
    }

    @Override
    public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = safeId(orderId);

        markUpdated(safeExchangeName, null);
        orderEvents.incrementAndGet();

        if (fill != null) {
            onFill(safeExchangeName, lastTradePair, fill);
        }

        onStatus(safeExchangeName, "order filled: " + safeOrderId);
    }

    @Override
    public void onOrderCancelled(String exchangeName, String orderId) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = safeId(orderId);

        markUpdated(safeExchangeName, null);
        orderEvents.incrementAndGet();

        onStatus(safeExchangeName, "order cancelled: " + safeOrderId);
    }

    @Override
    public void onRawMessage(String exchangeName, String channel, String rawJson) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeChannel = channel == null || channel.isBlank() ? "unknown" : channel.trim();

        markUpdated(safeExchangeName, null);
        rawMessageEvents.incrementAndGet();
        lastRawChannel = safeChannel;

        Consumer<String> handler = rawMessageHandler;
        if (handler != null) {
            runOnFx(() -> handler.accept(rawJson));
        }

        log.debug(
                "Raw exchange stream message. exchange={} channel={} payload={}",
                safeExchangeName,
                safeChannel,
                rawJson
        );
    }

    @Override
    public void onStatus(@Nullable String exchangeName, @Nullable String message) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeMessage = message == null || message.isBlank() ? "status update" : message.trim();

        markUpdated(safeExchangeName, null);
        lastStatusMessage = "%s: %s".formatted(safeExchangeName, safeMessage);

        runOnFx(() -> {
            Consumer<String> handler = statusHandler;
            if (handler != null) {
                handler.accept(lastStatusMessage);
            }
        });
    }

    @Override
    public void onError(@Nullable String exchangeName, @Nullable Throwable throwable) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String message = rootMessage(throwable);

        markUpdated(safeExchangeName, null);
        errorEvents.incrementAndGet();
        lastErrorAt = Instant.now();

        log.warn(
                "Exchange stream error. exchange={} error={}",
                safeExchangeName,
                message,
                throwable
        );

        runOnFx(() -> {
            BiConsumer<String, Throwable> handler = errorHandler;
            if (handler != null) {
                handler.accept(safeExchangeName, throwable);
            }

            Consumer<String> status = statusHandler;
            if (status != null) {
                status.accept("%s error: %s".formatted(safeExchangeName, message));
            }
        });
    }

    @Override
    public boolean hasReceivedEvents() {
        return getTotalEvents() > 0;
    }

    @Override
    public boolean hasErrors() {
        return errorEvents.get() > 0;
    }

    public long getTotalEvents() {
        return tickerEvents.get()
                + tradeEvents.get()
                + candleEvents.get()
                + orderBookEvents.get()
                + accountEvents.get()
                + orderEvents.get()
                + fillEvents.get()
                + positionEvents.get()
                + connectionEvents.get()
                + rawMessageEvents.get();
    }

    public void resetCounters() {
        tickerEvents.set(0);
        tradeEvents.set(0);
        candleEvents.set(0);
        orderBookEvents.set(0);
        accountEvents.set(0);
        orderEvents.set(0);
        fillEvents.set(0);
        positionEvents.set(0);
        connectionEvents.set(0);
        rawMessageEvents.set(0);
        errorEvents.set(0);
    }

    public @Nullable Ticker getLatestTicker(String exchangeName, TradePair tradePair) {
        return latestTickers.get(key(exchangeName, tradePair));
    }

    public @Nullable OrderBook getLatestOrderBook(String exchangeName, TradePair tradePair) {
        return latestOrderBooks.get(key(exchangeName, tradePair));
    }

    public @Nullable CandleData getLatestCandle(String exchangeName, TradePair tradePair) {
        return latestCandles.get(key(exchangeName, tradePair));
    }

    public List<Trade> getRecentTradesSnapshot() {
        return snapshot(recentTrades);
    }

    public List<Trade> getRecentFillsSnapshot() {
        return snapshot(recentFills);
    }

    public List<OpenOrder> getOpenOrdersSnapshot() {
        return snapshot(openOrders);
    }

    public List<Position> getPositionsSnapshot() {
        return snapshot(positions);
    }

    public void clear() {
        latestTickers.clear();
        latestOrderBooks.clear();
        latestCandles.clear();
        recentTrades.clear();
        recentFills.clear();
        openOrders.clear();
        positions.clear();

        latestAccount = null;
        lastUpdateAt = null;
        lastErrorAt = null;
        lastStatusMessage = "";
        lastRawChannel = "";
        lastTradePair = null;
    }

    private void publishOpenOrdersSnapshot() {
        Consumer<List<OpenOrder>> handler = openOrdersHandler;

        if (handler == null) {
            return;
        }

        runOnFx(() -> handler.accept(getOpenOrdersSnapshot()));
    }

    private void publishPositionsSnapshot() {
        Consumer<List<Position>> handler = positionsHandler;

        if (handler == null) {
            return;
        }

        runOnFx(() -> handler.accept(getPositionsSnapshot()));
    }

    private void markUpdated(@Nullable String exchangeName, @Nullable TradePair tradePair) {
        lastUpdateAt = Instant.now();

        if (exchangeName != null && !exchangeName.isBlank()) {
            lastExchangeName = exchangeName.trim();
        }

        if (tradePair != null) {
            lastTradePair = tradePair;
        }
    }

    private String key(String exchangeName, TradePair tradePair) {
        return "%s:%s".formatted(
                normalizeExchangeName(exchangeName),
                tradePair == null ? "UNKNOWN" : safePair(tradePair)
        );
    }

    private String safePair(TradePair tradePair) {
        try {
            return tradePair.toString('/');
        } catch (Exception exception) {
            return Objects.toString(tradePair, "UNKNOWN");
        }
    }

    private String normalizeExchangeName(@Nullable String exchangeName) {
        return exchangeName == null || exchangeName.isBlank()
                ? "Exchange"
                : exchangeName.trim();
    }

    private String safeId(@Nullable String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }

    private <T> void trim(ConcurrentLinkedDeque<T> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.pollLast();
        }
    }

    private <T> List<T> snapshot(ConcurrentLinkedDeque<T> deque) {
        return List.copyOf(new ArrayList<>(deque));
    }

    private void runOnFx(@Nullable Runnable runnable) {
        if (runnable == null) {
            return;
        }

        try {
            if (Platform.isFxApplicationThread()) {
                safeRun(runnable);
            } else {
                Platform.runLater(() -> safeRun(runnable));
            }
        } catch (IllegalStateException exception) {
            safeRun(runnable);
        }
    }

    private void safeRun(@NotNull Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            errorEvents.incrementAndGet();
            lastErrorAt = Instant.now();
            log.warn("UI stream consumer dispatch failed: {}", exception.getMessage(), exception);
        }
    }

    private String rootMessage(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "Unknown stream error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}