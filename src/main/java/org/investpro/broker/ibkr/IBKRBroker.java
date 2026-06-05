package org.investpro.broker.ibkr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.broker.Broker;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.models.Account;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.List;

/**
 * Professional IBKR broker orchestrator for account, position, order, and
 * market data workflows.
 */
@Slf4j
public class IBKRBroker implements Broker {

    private final IbkrExchange exchange;
    private final IBKRConnectionManager connectionManager;
    private final IBKRAccountService accountService;
    private final IBKRPositionService positionService;
    private final IBKROrderService orderService;
    private final IBKRMarketDataService marketDataService;
    private final IBKRContractService contractService;

    @Getter
    private final IBKREventBridge eventBridge;

    public IBKRBroker(ExchangeCredentials credentials) {
        this(credentials, IBKRConnectionConfig.defaults());
    }

    public IBKRBroker(ExchangeCredentials credentials, IBKRConnectionConfig config) {
        this.exchange = new IbkrExchange(credentials);
        this.eventBridge = new IBKREventBridge();
        this.connectionManager = new IBKRConnectionManager(exchange, new IBKRReflectiveApiGateway(), config);
        this.accountService = new IBKRAccountService(exchange);
        this.positionService = new IBKRPositionService(exchange);
        this.orderService = new IBKROrderService(exchange);
        this.marketDataService = new IBKRMarketDataService(exchange, eventBridge);
        this.contractService = new IBKRContractService();
    }

    @Override
    public void connect() {
        connectionManager.connect();
        eventBridge.onAccount(getAccount());
        eventBridge.onPositions(getPositions());
        eventBridge.onOrders(getOrders());
    }

    @Override
    public void disconnect() {
        marketDataService.stopAll();
        connectionManager.disconnect();
    }

    @Override
    public Account getAccount() {
        Account account = accountService.getAccount();
        eventBridge.onAccount(account);
        return account;
    }

    @Override
    public List<Position> getPositions() {
        List<Position> positions = positionService.getPositions();
        eventBridge.onPositions(positions);
        return positions;
    }

    @Override
    public List<Order> getOrders() {
        List<Order> orders = orderService.getOrders();
        eventBridge.onOrders(orders);
        return orders;
    }

    @Override
    public String placeOrder(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order must not be null");
        }
        String orderId = orderService.placeOrder(order);
        log.info("IBKR order submitted orderId={} symbol={} qty={}", orderId, order.getSymbol(), order.getQuantity());
        eventBridge.onOrders(getOrders());
        return orderId;
    }

    @Override
    public boolean cancelOrder(String orderId) {
        boolean cancelled = orderService.cancelOrder(orderId);
        if (cancelled) {
            eventBridge.onOrders(getOrders());
        }
        return cancelled;
    }

    @Override
    public void subscribeMarketData(TradePair tradePair) {
        contractService.resolveContract(tradePair);
        marketDataService.subscribe(tradePair);
    }

    public IBKRPortfolioSnapshot snapshotPortfolio() {
        return new IBKRPortfolioSnapshot(getAccount(), getPositions(), getOrders(), Instant.now());
    }

    public IBKRConnectionManager connectionManager() {
        return connectionManager;
    }

    public IBKRContractService contractService() {
        return contractService;
    }
}
