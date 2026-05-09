package org.investpro.exchange.contracts;


import com.fasterxml.jackson.core.JsonProcessingException;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface OrderExecutionProvider {

    CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity);

    CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice);

    CompletableFuture<String> createOrder(Order order) throws JsonProcessingException;

    Order createOrder(
            int id,
            TradePair tradePair,
            String type,
            double price,
            double amount,
            Side side,
            double stopLoss,
            double takeProfit,
            double slippage
    );

    CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount);

    CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount, double limitPrice);

    CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice);

    CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit
    );

    CompletableFuture<String> cancelOrder(String orderId);

    CompletableFuture<List<String>> cancelOrders(List<String> orderIds);

    CompletableFuture<String> cancelAllOrders();

    CompletableFuture<Optional<Order>> fetchOrder(String orderId);

    CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair);

    CompletableFuture<List<OpenOrder>> fetchAllOpenOrders();

    CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since);
}