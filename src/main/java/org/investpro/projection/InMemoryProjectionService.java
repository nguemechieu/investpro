package org.investpro.projection;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class InMemoryProjectionService implements ProjectionService {
    @Override
    public ProjectionSnapshot rebuild(List<BrokerActivityEvent> events) {
        List<BrokerActivityEvent> safeEvents = events == null ? List.of() : events;
        Map<String, OrdersProjection.OrderView> orders = new LinkedHashMap<>();
        Map<String, PositionsProjection.PositionView> positions = new LinkedHashMap<>();
        Map<String, TradesProjection.TradeView> trades = new LinkedHashMap<>();
        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal realizedPnl = BigDecimal.ZERO;
        int filledTrades = 0;
        String exchangeId = "";

        for (BrokerActivityEvent event : safeEvents) {
            if (event == null) {
                continue;
            }
            exchangeId = event.getExchangeId();
            String orderId = event.getOrderId() == null ? event.getEventId() : event.getOrderId();
            String symbol = event.getTradePair() == null ? "" : event.getTradePair().toString('/');
            BrokerActivityType type = event.getActivityType();
            if (type == BrokerActivityType.ORDER_SUBMITTED || type == BrokerActivityType.ORDER_ACCEPTED
                    || type == BrokerActivityType.ORDER_REJECTED || type == BrokerActivityType.ORDER_FILLED
                    || type == BrokerActivityType.ORDER_CANCELED || type == BrokerActivityType.ORDER_CANCELLED) {
                orders.put(orderId, new OrdersProjection.OrderView(exchangeId, orderId, symbol, type.name()));
            }
            if (type == BrokerActivityType.ORDER_FILLED) {
                String tradeId = event.getTradeId() == null ? event.getEventId() : event.getTradeId();
                trades.put(tradeId, new TradesProjection.TradeView(exchangeId, tradeId, orderId, symbol,
                        event.getFilledQuantity(), event.getAverageFillPrice() == null ? event.getPrice() : event.getAverageFillPrice()));
                realizedPnl = realizedPnl.add(event.getRealizedPnl());
                filledTrades++;
            }
            if (type == BrokerActivityType.POSITION_OPENED || type == BrokerActivityType.POSITION_UPDATED
                    || type == BrokerActivityType.POSITION_CLOSED) {
                String positionId = event.getPositionId() == null ? event.getEventId() : event.getPositionId();
                positions.put(positionId, new PositionsProjection.PositionView(exchangeId, positionId, symbol, type.name()));
            }
            if (type == BrokerActivityType.BALANCE_UPDATED || type == BrokerActivityType.BALANCE_CHANGED) {
                balance = event.getBalanceAfter() == null ? balance : event.getBalanceAfter();
            }
        }

        Instant now = Instant.now();
        AccountProjection account = new AccountProjection(exchangeId, balance, BigDecimal.ZERO, now);
        PortfolioProjection portfolio = new PortfolioProjection(balance, BigDecimal.ZERO, now);
        PerformanceProjection performance = new PerformanceProjection(realizedPnl, filledTrades, now);
        return new ProjectionSnapshot(
                new OrdersProjection(List.copyOf(orders.values()), now),
                new PositionsProjection(List.copyOf(positions.values()), now),
                new TradesProjection(List.copyOf(trades.values()), now),
                account,
                portfolio,
                performance,
                now);
    }
}
