package org.investpro.exchange.infrastructure;

import org.investpro.models.trading.Order;
import org.investpro.models.trading.Trade;

public interface OrderCommandConsumer {

    default void onSubmitted(Order order) {
    }

    default void onAccepted(String orderId, Order order) {
    }

    default void onRejected(Order order, String reason) {
    }

    default void onFilled(String orderId, Trade fill) {
    }

    default void onCancelled(String orderId) {
    }

    default void onError(Order order, Throwable throwable) {
    }
}
