package org.investpro.exchange;

import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;

import java.util.List;

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

    default void onConnected(String exchangeName) {
    }

    default void onDisconnected(String exchangeName, String reason) {
    }

    default void onError(String exchangeName, Throwable throwable) {
    }

    default void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
    }

    default void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
    }

    default void onTrades(String exchangeName, TradePair tradePair, List<Trade> trades) {
        if (trades == null) {
            return;
        }

        for (Trade trade : trades) {
            onTrade(exchangeName, tradePair, trade);
        }
    }

    default void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
    }

    default void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
    }

    default void onAccount(String exchangeName, Account account) {
    }

    default void onOpenOrder(String exchangeName, OpenOrder order) {
    }

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

    default void onPositions(String exchangeName, List<Position> positions) {
        if (positions == null) {
            return;
        }

        for (Position position : positions) {
            onPosition(exchangeName, position);
        }
    }

    default void onOrderAccepted(String exchangeName, String orderId) {
    }

    default void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
    }

    default void onOrderFilled(String exchangeName, String orderId, Trade fill) {
    }

    default void onBalanceChanged(String exchangeName, Account account) {
    }

    default void onOrderCancelled(String exchangeName, String orderId) {
    }

    default void onRawMessage(String exchangeName, String channel, String rawJson) {
    }
}
