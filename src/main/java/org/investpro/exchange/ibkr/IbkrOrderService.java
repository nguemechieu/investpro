package org.investpro.exchange.ibkr;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
public final class IbkrOrderService {

    private final IbkrPositionService positionService;
    private final IbkrAccountService accountService;
    private final IbkrMarketDataProvider marketDataProvider;
    private final IbkrPersistenceStore persistenceStore;

    private final ConcurrentHashMap<String, OpenOrder> openOrders = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<String> executions = new CopyOnWriteArrayList<>();

    public IbkrOrderService(IbkrPositionService positionService,
            IbkrAccountService accountService,
            IbkrMarketDataProvider marketDataProvider,
            IbkrPersistenceStore persistenceStore) {
        this.positionService = positionService;
        this.accountService = accountService;
        this.marketDataProvider = marketDataProvider;
        this.persistenceStore = persistenceStore;
    }

    public String submitMarket(TradePair pair, Side side, double quantity) {
        double fillPrice = marketDataProvider.fetchTicker(pair).join().getMidPrice();
        String executionId = executionId("MKT");

        positionService.upsert(pair, side, quantity, fillPrice);
        accountService.applyFillCashChange(side.isBuy() ? -(quantity * fillPrice) : (quantity * fillPrice));

        executions.add(executionId + ":" + pair.toString('/') + ":" + side + ":" + quantity + "@" + fillPrice);
        persistenceStore.persistExecutions(List.copyOf(executions));
        persistenceStore.persistPositions(positionService.fetchAll());
        return executionId;
    }

    public String submitLimit(TradePair pair, Side side, double quantity, double limitPrice) {
        return createOpenOrder(pair, side, OpenOrder.OrderType.LIMIT, quantity, limitPrice, null);
    }

    public String submitStop(TradePair pair, Side side, double quantity, double stopPrice) {
        return createOpenOrder(pair, side, OpenOrder.OrderType.STOP_LOSS, quantity, stopPrice, null);
    }

    public String submitStopLimit(TradePair pair, Side side, double quantity, double stopPrice, double limitPrice) {
        return createOpenOrder(pair, side, OpenOrder.OrderType.STOP_LIMIT, quantity, limitPrice, "stop=" + stopPrice);
    }

    public String submitTrailingStop(TradePair pair, Side side, double quantity, double trailingDistance) {
        return createOpenOrder(pair, side, OpenOrder.OrderType.TRAILING_STOP, quantity, trailingDistance,
                "trailingDistance=" + trailingDistance);
    }

    public String submitBracket(TradePair pair,
            Side side,
            double quantity,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
        String parentId = createOpenOrder(pair, side, OpenOrder.OrderType.LIMIT, quantity, entryPrice,
                "bracket=parent");
        createOpenOrder(pair, opposite(side), OpenOrder.OrderType.STOP_LOSS, quantity, stopLoss,
                "bracket=stop,parent=" + parentId);
        createOpenOrder(pair, opposite(side), OpenOrder.OrderType.TAKE_PROFIT, quantity, takeProfit,
                "bracket=tp,parent=" + parentId);
        return parentId;
    }

    public Optional<OpenOrder> fetchOrder(String orderId) {
        return Optional.ofNullable(openOrders.get(orderId));
    }

    public List<OpenOrder> fetchAllOpenOrders() {
        return List.copyOf(openOrders.values());
    }

    public List<OpenOrder> fetchOpenOrders(TradePair pair) {
        String symbol = pair.toString('/');
        return openOrders.values().stream()
                .filter(order -> order.getTradePair() != null)
                .filter(order -> symbol.equals(order.getTradePair().toString('/')))
                .toList();
    }

    public String cancelOrder(String orderId) {
        OpenOrder order = openOrders.remove(orderId);
        if (order == null) {
            return orderId;
        }
        order.cancel();
        persistOpenOrders();
        return orderId;
    }

    public List<String> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return List.of();
        }
        List<String> cancelled = new ArrayList<>();
        for (String orderId : orderIds) {
            cancelOrder(orderId);
            cancelled.add(orderId);
        }
        return cancelled;
    }

    public String cancelAll() {
        List<String> ids = new ArrayList<>(openOrders.keySet());
        openOrders.clear();
        persistOpenOrders();
        return String.valueOf(ids.size());
    }

    public List<String> executions() {
        return List.copyOf(executions);
    }

    private String createOpenOrder(TradePair pair,
            Side side,
            OpenOrder.OrderType type,
            double quantity,
            double price,
            String clientMetadata) {
        String orderId = UUID.randomUUID().toString();
        OpenOrder order = new OpenOrder();
        order.setOrderId(orderId);
        order.setTradePair(pair);
        order.setSide(side);
        order.setOrderType(type);
        order.setSize(quantity);
        order.setRemainingSize(quantity);
        order.setPrice(price);
        order.setStatus(OpenOrder.OrderStatus.OPEN);
        order.setClientOrderId(clientMetadata == null ? "" : clientMetadata);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        order.setExchange("INTERACTIVE_BROKERS");

        openOrders.put(orderId, order);
        persistOpenOrders();
        return orderId;
    }

    private void persistOpenOrders() {
        persistenceStore.persistOrders(fetchAllOpenOrders());
    }

    private String executionId(String prefix) {
        return prefix + "-" + Instant.now().toEpochMilli();
    }

    private Side opposite(Side side) {
        return side == Side.BUY ? Side.SELL : Side.BUY;
    }
}
