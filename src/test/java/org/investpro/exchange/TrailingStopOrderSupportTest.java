package org.investpro.exchange;

import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.exchange.core.NormalizedOrderRequest;
import org.investpro.exchange.oanda.OandaOrderPayloadFactory;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class TrailingStopOrderSupportTest {

    @Test
    void oandaPayloadContainsTrailingStopLossDistance() {
        NormalizedOrderRequest request = NormalizedOrderRequest.builder(
                        "EUR_USD",
                        NormalizedOrderRequest.Side.SELL,
                        NormalizedOrderRequest.OrderType.TRAILING_STOP,
                        BigDecimal.valueOf(1_000))
                .trailingAmount(BigDecimal.valueOf(0.0025))
                .build();

        String payload = new OandaOrderPayloadFactory().createOrderPayload(request);

        assertTrue(payload.contains("\"type\":\"TRAILING_STOP_LOSS\""));
        assertTrue(payload.contains("\"distance\":\"0.0025\""));
    }

    @Test
    void defaultProviderRejectsTrailingStopInsteadOfSubmittingFallbackOrder() {
        OrderExecutionProvider provider = new UnsupportedTrailingStopProvider();

        CompletionException exception = assertThrows(CompletionException.class,
                () -> provider.createTrailingStopOrder(pair(), Side.SELL, 1.0, 0.01, false).join());

        assertInstanceOf(UnsupportedOperationException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("does not support trailing stop orders"));
    }

    @Test
    void openOrderModelExposesTrailingStopOrderType() {
        assertEquals(OpenOrder.OrderType.TRAILING_STOP, OpenOrder.OrderType.valueOf("TRAILING_STOP"));
    }

    private static TradePair pair() {
        try {
            return new TradePair("BTC", "USDC");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class UnsupportedTrailingStopProvider implements OrderExecutionProvider {
        @Override
        public java.util.concurrent.CompletableFuture<String> placeMarketOrder(
                TradePair symbol, Side side, double quantity) {
            return java.util.concurrent.CompletableFuture.completedFuture("market");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> placeLimitOrder(
                TradePair symbol, Side side, double quantity, double limitPrice) {
            return java.util.concurrent.CompletableFuture.completedFuture("limit");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> createOrder(Order order) {
            return java.util.concurrent.CompletableFuture.completedFuture("order");
        }

        @Override
        public Order createOrder(int id, TradePair tradePair, String type, double price, double amount,
                                 Side side, double stopLoss, double takeProfit, double slippage) {
            return new Order();
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> createMarketOrder(
                TradePair tradePair, Side side, double amount) {
            return java.util.concurrent.CompletableFuture.completedFuture("market");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> createLimitOrder(
                TradePair tradePair, Side side, double amount, double limitPrice) {
            return java.util.concurrent.CompletableFuture.completedFuture("limit");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> createStopOrder(
                TradePair tradePair, Side side, double amount, double stopPrice) {
            return java.util.concurrent.CompletableFuture.completedFuture("stop");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> createBracketOrder(
                TradePair tradePair, Side side, double amount, double entryPrice, double stopLoss, double takeProfit) {
            return java.util.concurrent.CompletableFuture.completedFuture("bracket");
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> cancelOrder(String orderId) {
            return java.util.concurrent.CompletableFuture.completedFuture(orderId);
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
            return java.util.concurrent.CompletableFuture.completedFuture(orderIds);
        }

        @Override
        public java.util.concurrent.CompletableFuture<String> cancelAllOrders() {
            return java.util.concurrent.CompletableFuture.completedFuture("all");
        }

        @Override
        public java.util.concurrent.CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
            return java.util.concurrent.CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }

        @Override
        public java.util.concurrent.CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
            return java.util.concurrent.CompletableFuture.completedFuture(List.of());
        }
    }
}
