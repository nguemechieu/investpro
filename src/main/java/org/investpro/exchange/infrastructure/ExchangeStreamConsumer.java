package org.investpro.exchange.infrastructure;

import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.models.trading.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * Receives real-time exchange/broker stream updates.
 * One implementation can update:
 * - UI tables
 * - chart candles
 * - portfolio state
 * - risk engine
 * - database/repositories
 * - alert engine
 */
public interface ExchangeStreamConsumer {

    void onStatus(@Nullable String exchangeName, @Nullable String message);

    default void onError(String exchangeName, Throwable throwable) {
    }

    default void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
    }

    default void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
    }

    default void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
    }

    default void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
    }

    default void onAccount(String exchangeName, Account account) {
    }

    default void onOpenOrder(String exchangeName, OpenOrder order) {
    }

    void onBalance(@Nullable String exchangeName, @Nullable Account account);

    void onOrder(@Nullable String exchangeName, @Nullable OpenOrder order);

    default void onOpenOrders(String exchangeName, List<OpenOrder> orders) {
        if (orders == null) {
            return;
        }

        for (OpenOrder order : orders) {
            onOpenOrder(exchangeName, order);
        }
    }

    default void onPosition(String exchangeName, Position position) {
    }

    void onOrders(String exchangeName, List<OpenOrder> orders);

    void onFill(String exchangeName, TradePair tradePair, Trade fill);

    default void onPositions(String exchangeName, List<Position> positions) {
        if (positions == null) {
            return;
        }

        for (Position position : positions) {
            onPosition(exchangeName, position);
        }
    }

    default void onBalanceChanged(String exchangeName, Account account) {

    }

    default boolean containsKey(TradePair tradePair) {
        return false;
    }

    default ExchangeStreamConsumer get(TradePair tradePair) {
        return null;
    }

    default void put(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {
    }

    default void remove(TradePair tradePair) {
    }

    default void acceptTrades(Trade newTrade) {
    }

    default void onConnected(String exchangeName) {
    }

    default void onDisconnected(String exchangeName, String reason) {
    }

    void onOrderAccepted(String exchangeName, String orderId);

    void onOrderRejected(String exchangeName, String clientOrderId, String reason);

    void onOrderFilled(String exchangeName, String orderId, Trade fill);

    void onOrderCancelled(String exchangeName, String orderId);

    void onRawMessage(String exchangeName, String channel, String rawJson);

    boolean hasReceivedEvents();

    boolean hasErrors();

    UiExchangeStreamConsumer onOrdersUpdate(@Nullable Consumer<List<OpenOrder>> setAll);
}
