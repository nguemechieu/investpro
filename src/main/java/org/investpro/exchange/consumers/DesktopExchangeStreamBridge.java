package org.investpro.exchange.consumers;

import javafx.application.Platform;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.ui.TradingDesk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * JavaFX-safe bridge between exchange streaming callbacks and TradingDesk.
 * <p>
 * This class keeps WebSocket/exchange threads clean by pushing all UI work
 * onto the JavaFX Application Thread and protecting the stream from UI errors.
 */
@Slf4j
@Getter
@Setter
public class DesktopExchangeStreamBridge implements ExchangeStreamConsumer {

    private final TradingDesk tradingDesk;

    private final AtomicLong tickerEvents = new AtomicLong();
    private final AtomicLong tradeEvents = new AtomicLong();
    private final AtomicLong candleEvents = new AtomicLong();
    private final AtomicLong orderBookEvents = new AtomicLong();
    private final AtomicLong accountEvents = new AtomicLong();
    private final AtomicLong orderEvents = new AtomicLong();
    private final AtomicLong fillEvents = new AtomicLong();
    private final AtomicLong positionEvents = new AtomicLong();
    private final AtomicLong rawMessageEvents = new AtomicLong();
    private final AtomicLong connectionEvents = new AtomicLong();
    private final AtomicLong errorEvents = new AtomicLong();

    private volatile Instant lastEventAt;
    private volatile Instant lastTickerAt;
    private volatile Instant lastTradeAt;
    private volatile Instant lastCandleAt;
    private volatile Instant lastOrderBookAt;
    private volatile Instant lastAccountAt;
    private volatile Instant lastOrderAt;
    private volatile Instant lastFillAt;
    private volatile Instant lastPositionAt;
    private volatile Instant lastRawMessageAt;
    private volatile Instant lastConnectionAt;
    private volatile Instant lastErrorAt;

    private volatile String lastExchangeName = "";
    private volatile String lastStatusMessage = "";
    private volatile String lastRawChannel = "";
    private volatile TradePair lastTradePair;

    public DesktopExchangeStreamBridge(@NotNull TradingDesk tradingDesk) {
        this.tradingDesk = Objects.requireNonNull(tradingDesk, "tradingDesk must not be null");
    }

    @Override
    public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
        if (tradePair == null || ticker == null) {
            return;
        }

        markEvent(exchangeName, tradePair);
        tickerEvents.incrementAndGet();
        lastTickerAt = Instant.now();

        runOnFx("ticker update", () -> tradingDesk.updateTickerFromStream(tradePair, ticker));
    }

    @Override
    public void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
        if (trade == null) {
            return;
        }

        markEvent(exchangeName, tradePair);
        tradeEvents.incrementAndGet();
        lastTradeAt = Instant.now();

        runOnFx("trade update", () -> tradingDesk.updateTradeFromStream(trade));
    }

    @Override
    public void onCandle(String exchangeName, TradePair tradePair, CandleData candle) {
        if (candle == null) {
            return;
        }

        markEvent(exchangeName, tradePair);
        candleEvents.incrementAndGet();
        lastCandleAt = Instant.now();

        runOnFx("candle update", tradingDesk::updateCandleFromStream);
    }

    @Override
    public void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
        if (orderBook == null) {
            return;
        }

        markEvent(exchangeName, tradePair);
        orderBookEvents.incrementAndGet();
        lastOrderBookAt = Instant.now();

        runOnFx("order book update", () -> tradingDesk.updateOrderBookFromStream(orderBook));
    }

    @Override
    public void onAccount(String exchangeName, Account account) {
        if (account == null) {
            return;
        }

        markEvent(exchangeName, null);
        accountEvents.incrementAndGet();
        lastAccountAt = Instant.now();

        runOnFx("account update", () -> tradingDesk.updateAccountFromStream(account));
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
        if (order == null) {
            return;
        }

        markEvent(exchangeName, null);
        orderEvents.incrementAndGet();
        lastOrderAt = Instant.now();

        runOnFx("order update", () -> tradingDesk.updateOpenOrderFromStream(order));
    }


    @Override
    public void onOpenOrders(String exchangeName, List<OpenOrder> orders) {
        onOrders(exchangeName, orders);
    }
@Override
    public void onOrders(@Nullable String exchangeName, @Nullable List<OpenOrder> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }

        markEvent(exchangeName, null);
        orderEvents.addAndGet(orders.size());
        lastOrderAt = Instant.now();

        runOnFx("orders snapshot", () -> {
            for (OpenOrder order : orders) {
                if (order != null) {
                    tradingDesk.updateOpenOrderFromStream(order);
                }
            }
        });
    }

    public void onFill(@Nullable String exchangeName, @Nullable TradePair tradePair, @Nullable Trade fill) {
        if (fill == null) {
            return;
        }

        markEvent(exchangeName, tradePair);
        fillEvents.incrementAndGet();
        lastFillAt = Instant.now();

        runOnFx("fill update", () -> tradingDesk.updateTradeFromStream(fill));
    }

    public void onPosition(@Nullable String exchangeName, @Nullable Position position) {
        if (position == null) {
            return;
        }

        markEvent(exchangeName, null);
        positionEvents.incrementAndGet();
        lastPositionAt = Instant.now();

        runOnFx("position update", () -> tradingDesk.updatePositionFromStream(position));
    }

    public void onPositions(@Nullable String exchangeName, @Nullable List<Position> positions) {
        if (positions == null || positions.isEmpty()) {
            return;
        }

        markEvent(exchangeName, null);
        positionEvents.addAndGet(positions.size());
        lastPositionAt = Instant.now();

        runOnFx("positions snapshot", () -> {
            for (Position position : positions) {
                if (position != null) {
                    tradingDesk.updatePositionFromStream(position);
                }
            }
        });
    }

    @Override
    public ExchangeStreamConsumer get(TradePair tradePair) {
        return this;
    }

    @Override
    public void put(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {
        // No-op.
        // This bridge is a UI dispatch consumer, not a map-backed registry.
    }

    @Override
    public void remove(TradePair tradePair) {
        // No-op.
        // Stream lifecycle is owned by Exchange/TradingDesk.
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

        connectionEvents.incrementAndGet();
        lastConnectionAt = Instant.now();
        markEvent(safeExchangeName, null);

        runOnFx("stream connected", () -> {
            tradingDesk.updateStreamingStatus("%s connected".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s stream connected.".formatted(safeExchangeName));
        });
    }

    @Override
    public void onDisconnected(String exchangeName, String reason) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeReason = reason == null || reason.isBlank() ? "Disconnected" : reason.trim();

        connectionEvents.incrementAndGet();
        lastConnectionAt = Instant.now();
        markEvent(safeExchangeName, null);

        runOnFx("stream disconnected", () -> {
            tradingDesk.updateStreamingStatus("%s disconnected".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s stream disconnected: %s".formatted(safeExchangeName, safeReason));
        });
    }

    @Override
    public void onOrderAccepted(String exchangeName, String orderId) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = orderId == null || orderId.isBlank() ? "unknown" : orderId.trim();

        markEvent(safeExchangeName, null);
        orderEvents.incrementAndGet();
        lastOrderAt = Instant.now();

        runOnFx("order accepted", () -> {
            tradingDesk.updateStreamingStatus("%s order accepted".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s order accepted: %s".formatted(safeExchangeName, safeOrderId));
        });
    }

    @Override
    public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = clientOrderId == null || clientOrderId.isBlank() ? "unknown" : clientOrderId.trim();
        String safeReason = reason == null || reason.isBlank() ? "Rejected" : reason.trim();

        markEvent(safeExchangeName, null);
        orderEvents.incrementAndGet();
        lastOrderAt = Instant.now();

        runOnFx("order rejected", () -> {
            tradingDesk.updateStreamingStatus("%s order rejected".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s order rejected: %s | %s".formatted(
                    safeExchangeName,
                    safeOrderId,
                    safeReason
            ));
        });
    }

    @Override
    public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = orderId == null || orderId.isBlank() ? "unknown" : orderId.trim();

        markEvent(safeExchangeName, null);
        fillEvents.incrementAndGet();
        orderEvents.incrementAndGet();
        lastFillAt = Instant.now();
        lastOrderAt = Instant.now();

        runOnFx("order filled", () -> {
            tradingDesk.updateStreamingStatus("%s order filled".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s order filled: %s".formatted(safeExchangeName, safeOrderId));

            if (fill != null) {
                tradingDesk.updateTradeFromStream(fill);
            }
        });
    }

    @Override
    public void onOrderCancelled(String exchangeName, String orderId) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeOrderId = orderId == null || orderId.isBlank() ? "unknown" : orderId.trim();

        markEvent(safeExchangeName, null);
        orderEvents.incrementAndGet();
        lastOrderAt = Instant.now();

        runOnFx("order cancelled", () -> {
            tradingDesk.updateStreamingStatus("%s order cancelled".formatted(safeExchangeName));
            tradingDesk.appendJournal("%s order cancelled: %s".formatted(safeExchangeName, safeOrderId));
        });
    }

    @Override
    public void onRawMessage(String exchangeName, String channel, String rawJson) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeChannel = channel == null || channel.isBlank() ? "unknown" : channel.trim();

        markEvent(safeExchangeName, null);
        rawMessageEvents.incrementAndGet();
        lastRawMessageAt = Instant.now();
        lastRawChannel = safeChannel;

        log.debug(
                "Raw stream message. exchange={} channel={} payload={}",
                safeExchangeName,
                safeChannel,
                rawJson
        );
    }

    @Override
    public void onStatus(@Nullable String exchangeName, @Nullable String message) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String safeMessage = message == null || message.isBlank() ? "Streaming" : message.trim();

        markEvent(safeExchangeName, null);
        lastStatusMessage = "%s: %s".formatted(safeExchangeName, safeMessage);

        runOnFx("stream status", () -> tradingDesk.updateStreamingStatus(lastStatusMessage));
    }

    @Override
    public void onError(@Nullable String exchangeName, @Nullable Throwable throwable) {
        String safeExchangeName = normalizeExchangeName(exchangeName);
        String message = rootMessage(throwable);

        errorEvents.incrementAndGet();
        lastErrorAt = Instant.now();
        markEvent(safeExchangeName, null);

        log.warn(
                "Desktop exchange stream error. exchange={} error={}",
                safeExchangeName,
                message,
                throwable
        );

        runOnFx("stream error", () -> {
            tradingDesk.updateStreamingStatus("%s stream error".formatted(safeExchangeName));
            tradingDesk.appendJournal("Stream error from %s: %s".formatted(safeExchangeName, message));
        });
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
        rawMessageEvents.set(0);
        connectionEvents.set(0);
        errorEvents.set(0);
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
                + rawMessageEvents.get()
                + connectionEvents.get();
    }

    @Override
    public boolean hasReceivedEvents() {
        return getTotalEvents() > 0;
    }

    @Override
    public boolean hasErrors() {
        return errorEvents.get() > 0;
    }

    @Override
    public UiExchangeStreamConsumer onOrdersUpdate(@Nullable Consumer<List<OpenOrder>> setAll) {
        return null;
    }


    private static final int  MAX_RECENT_ORDERS=1000;
  private final LinkedList<OpenOrder> openOrders=new LinkedList<>();


    private void markUpdated(String exchangeName, Object o) {
    }

    private void markEvent(@Nullable String exchangeName, @Nullable TradePair tradePair) {
        lastEventAt = Instant.now();

        if (exchangeName != null && !exchangeName.isBlank()) {
            lastExchangeName = exchangeName.trim();
        }

        if (tradePair != null) {
            lastTradePair = tradePair;
        }
    }

    private String normalizeExchangeName(@Nullable String exchangeName) {
        return exchangeName == null || exchangeName.isBlank()
                ? "Exchange"
                : exchangeName.trim();
    }

    private void runOnFx(@NotNull String operationName, @NotNull Runnable task) {
        Objects.requireNonNull(operationName, "operationName must not be null");
        Objects.requireNonNull(task, "task must not be null");

        try {
            if (Platform.isFxApplicationThread()) {
                safeRun(operationName, task);
            } else {
                Platform.runLater(() -> safeRun(operationName, task));
            }
        } catch (IllegalStateException exception) {
            safeRun(operationName, task);
        }
    }

    private void safeRun(@NotNull String operationName, @NotNull Runnable task) {
        try {
            task.run();
        } catch (Exception exception) {
            errorEvents.incrementAndGet();
            lastErrorAt = Instant.now();

            log.warn(
                    "Desktop stream UI dispatch failed. operation={} error={}",
                    operationName,
                    exception.getMessage(),
                    exception
            );
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