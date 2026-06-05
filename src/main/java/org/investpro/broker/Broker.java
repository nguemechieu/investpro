package org.investpro.broker;

import org.investpro.models.Account;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;

import java.util.List;

/**
 * Broker-facing contract for execution, account, and market-data operations.
 */
public interface Broker {

    void connect();

    void disconnect();

    Account getAccount();

    List<Position> getPositions();

    List<Order> getOrders();

    String placeOrder(Order order);

    boolean cancelOrder(String orderId);

    void subscribeMarketData(TradePair tradePair);
}
