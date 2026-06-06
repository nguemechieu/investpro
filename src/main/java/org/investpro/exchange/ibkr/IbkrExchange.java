package org.investpro.exchange.ibkr;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.InteractiveBrokers;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.licensing.LicenseManager;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;

@Slf4j
@Getter
public class IbkrExchange extends InteractiveBrokers {

    private final IbkrConnectionManager connectionManager;
    private final IbkrClientPortalClient clientPortalClient;
    private final IbkrContractMapper contractMapper;
    private final IbkrMarketDataProvider marketDataProvider;
    private final IbkrPersistenceStore persistenceStore;
    private final IbkrAccountService accountService;
    private final IbkrLocalServiceDetector localServiceDetector;
    private final IbkrConnectionDiagnosticsService connectionDiagnosticsService;
    private final IbkrConnectionService connectionService;
    private final IbkrFeatureAvailabilityService featureAvailabilityService;
    private final IbkrPositionService positionService;
    private final IbkrPortfolioService portfolioService;
    private final IbkrOrderService orderService;
    private final IbkrPortfolioSynchronizer portfolioSynchronizer;
    private final IbkrExecutionAdapter executionAdapter;
    private final IbkrContractRepository contractRepository;
    private final IbkrContractCache contractCache;
    private final IbkrContractResolver contractResolver;
    private final Map<String, Double> leverageBySymbol = new ConcurrentHashMap<>();

    private volatile BooleanSupplier liveTradingLicenseGate = () -> true;
    private volatile BooleanSupplier liveRiskApprovalGate = () -> false;

    public IbkrExchange(ExchangeCredentials credentials) {
        super(credentials);
        this.connectionManager = new IbkrConnectionManager();
        this.clientPortalClient = new IbkrClientPortalClient(credentials);
        this.contractMapper = new IbkrContractMapper();
        this.persistenceStore = new IbkrPersistenceStore();
        this.localServiceDetector = new IbkrLocalServiceDetector();
        this.connectionDiagnosticsService = new IbkrConnectionDiagnosticsService(localServiceDetector);
        this.connectionService = new IbkrConnectionService(
                new TwsIbkrBrokerConnection(connectionManager),
                new ClientPortalIbkrBrokerConnection(clientPortalClient));
        this.contractRepository = new IbkrContractRepository();
        this.contractCache = new IbkrContractCache(contractRepository);
        IbkrContractSearchService twsSearch = new IbkrTwsContractSearchService(connectionManager);
        IbkrContractDetailsService twsDetails = new IbkrTwsContractDetailsService(connectionManager);
        IbkrContractSearchService clientPortalSearch = new IbkrClientPortalContractSearchService(clientPortalClient);
        IbkrContractDetailsService clientPortalDetails = new IbkrClientPortalContractDetailsService(clientPortalClient);
        this.contractResolver = new IbkrContractResolver(
                new IbkrAdaptiveContractSearchService(connectionManager, twsSearch, clientPortalSearch,
                        clientPortalClient),
                new IbkrAdaptiveContractDetailsService(connectionManager, twsDetails, clientPortalDetails,
                        clientPortalClient),
                contractCache);
        this.marketDataProvider = new IbkrMarketDataProvider(connectionManager, clientPortalClient, contractResolver);
        this.featureAvailabilityService = new IbkrFeatureAvailabilityService();
        this.accountService = new IbkrAccountService(
                connectionManager,
                persistenceStore,
                clientPortalClient,
                modeRequestsPaperNetwork());
        this.positionService = new IbkrPositionService(persistenceStore);
        this.portfolioService = new IbkrPortfolioService(positionService, accountService);
        this.orderService = new IbkrOrderService(positionService, accountService, marketDataProvider, persistenceStore,
                contractResolver);
        this.portfolioSynchronizer = new IbkrPortfolioSynchronizer(persistenceStore);
        this.executionAdapter = new IbkrExecutionAdapter(this);
    }

    public IbkrExchange(String apiKey, String apiSecret) {
        this(new ExchangeCredentials("interactive_brokers", apiKey, apiSecret, null, null, null, null, true));
    }

    public void setLicenseManager(LicenseManager licenseManager) {
        this.liveTradingLicenseGate = () -> licenseManager != null
                && licenseManager.isLicenseValid()
                && (licenseManager.isFeatureEnabled("ADVANCED_TRADING")
                        || licenseManager.isFeatureEnabled("BASIC_TRADING"));
    }

    public void setLiveTradingLicenseGate(BooleanSupplier licenseGate) {
        this.liveTradingLicenseGate = licenseGate == null ? () -> true : licenseGate;
    }

    public void setLiveRiskApprovalGate(BooleanSupplier riskApprovalGate) {
        this.liveRiskApprovalGate = riskApprovalGate == null ? () -> false : riskApprovalGate;
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == null
                || marketType == MARKET_TYPES.STOCKS
                || marketType == MARKET_TYPES.FOREX
                || marketType == MARKET_TYPES.FUTURES;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS, MARKET_TYPES.FOREX, MARKET_TYPES.FUTURES);
    }

    @Override
    public void connect() {
        IbkrConnectionProfile profile = connectionProfileFromCredentials();
        if (profile == null) {
            connectionManager.connect(
                    modeRequestsPaperNetwork() ? IbkrConnectionManager.Mode.PAPER : IbkrConnectionManager.Mode.LIVE);
        } else {
            connectionManager.connect(profile);
        }
        accountService.refreshFromBrokerIfAvailable();
    }

    public IbkrSessionState connect(IbkrConnectionProfile profile) {
        IbkrSessionState state = connectionService.connect(profile);
        accountService.refreshFromBrokerIfAvailable();
        return state;
    }

    @Override
    public void disconnect() {
        connectionService.disconnect();
        connectionManager.disconnect();
    }

    @Override
    public void reconnect() {
        connectionManager.reconnect();
    }

    @Override
    public Boolean isConnected() {
        return connectionManager.isConnected();
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        ensureResolvedContract(tradePair);
        if (canTradeNow()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("IBKR live trading gate denied market order"));
        }
        return CompletableFuture.completedFuture(orderService.submitMarket(tradePair, side, amount));
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        ensureResolvedContract(tradePair);
        if (canTradeNow()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("IBKR live trading gate denied limit order"));
        }
        return CompletableFuture.completedFuture(orderService.submitLimit(tradePair, side, amount, limitPrice));
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        ensureResolvedContract(tradePair);
        if (canTradeNow()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("IBKR live trading gate denied stop order"));
        }
        return CompletableFuture.completedFuture(orderService.submitStop(tradePair, side, amount, stopPrice));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
        ensureResolvedContract(tradePair);
        if (canTradeNow()) {
            return CompletableFuture
                    .failedFuture(new IllegalStateException("IBKR live trading gate denied bracket order"));
        }
        return CompletableFuture.completedFuture(
                orderService.submitBracket(tradePair, side, amount, entryPrice, stopLoss, takeProfit));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(orderService.fetchOpenOrders(tradePair));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(orderService.fetchAllOpenOrders());
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return CompletableFuture.completedFuture(orderService.cancelOrder(orderId));
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        return CompletableFuture.completedFuture(orderService.cancelOrders(orderIds));
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return CompletableFuture.completedFuture(orderService.cancelAll());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(positionService.fetchFor(tradePair));
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(positionService.fetchAll());
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(positionService.fetchOne(tradePair));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        positionService.close(tradePair);
        return CompletableFuture.completedFuture("CLOSED");
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        positionService.closeAll();
        return CompletableFuture.completedFuture("CLOSED_ALL");
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return closePosition(symbol);
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        if (symbol == null || quantity <= 0.0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Trade pair and quantity must be provided"));
        }
        double exitPrice = marketDataProvider.fetchTicker(symbol).join().getMidPrice();
        Optional<Position> existing = positionService.fetchOne(symbol);
        positionService.closePartial(symbol, quantity, exitPrice);

        existing.ifPresent(position -> {
            double signedCashDelta = position.getSide() == Side.BUY
                    ? (quantity * exitPrice)
                    : -(quantity * exitPrice);
            accountService.applyFillCashChange(signedCashDelta);
        });
        return CompletableFuture.completedFuture("PARTIAL_CLOSED");
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        positionService.setStopLoss(symbol, stopLoss);
        return CompletableFuture.completedFuture("STOP_UPDATED");
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        positionService.setTakeProfit(symbol, takeProfit);
        return CompletableFuture.completedFuture("TAKE_PROFIT_UPDATED");
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return CompletableFuture.completedFuture(
                orderService.submitTrailingStop(symbol, Side.SELL, Math.max(1.0, quantityFor(symbol)),
                        trailingDistance));
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.fetchTicker(tradePair).join();
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.fetchTicker(tradePair);
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs != null) {
            tradePairs.forEach(this::ensureResolvedContract);
        }
        return marketDataProvider.fetchTickers(tradePairs);
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTicker(pair).thenApply(List::of);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.candleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<org.investpro.data.InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.fetchCandleDataForInProgressCandle(
                tradePair,
                currentCandleStartedAt);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.fetchRecentTradesUntil(tradePair, stopAt);
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return marketDataProvider.supportedTimeframes();
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return secondsPerCandle > 0 ? "SUPPORTED" : "UNSUPPORTED";
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        ensureAuthenticatedSessionForAccountAccess();
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (shouldRequireClientPortalAuth() && !clientPortalClient.isAuthenticated()) {
            return CompletableFuture.failedFuture(
                    new SecurityException("IBKR access denied: " + clientPortalClient.authenticationFailureReason()));
        }
        return CompletableFuture.completedFuture(accountService.toAccount(this, currentSnapshot()));
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(
                currentSnapshot().balances().getOrDefault(normalize(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAvailableBalance(currencyCode);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(currentSnapshot().equity());
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(currentSnapshot().marginUsed());
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return CompletableFuture.completedFuture(currentSnapshot().availableFunds());
    }

    @Override
    public CompletableFuture<org.investpro.models.trading.OrderBook> fetchOrderBook(TradePair tradePair) {
        ensureResolvedContract(tradePair);
        return marketDataProvider.fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return CompletableFuture.completedFuture(orderService.fetchOrder(orderId).map(this::toOrder));
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        List<Order> history = new ArrayList<>();

        for (OpenOrder openOrder : orderService.fetchAllOpenOrders()) {
            if (!matchesPair(openOrder.getTradePair(), tradePair)) {
                continue;
            }
            if (!matchesSince(openOrder.getCreatedAt(), since)) {
                continue;
            }
            history.add(toOrder(openOrder));
        }

        for (Trade trade : fetchAccountTradesSince(tradePair, since).join()) {
            history.add(toOrder(trade));
        }

        history.sort(Comparator.comparing(Order::getDate, Comparator.nullsLast(Comparator.reverseOrder())));
        return CompletableFuture.completedFuture(history);
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.completedFuture(1.0);
        }
        return CompletableFuture.completedFuture(
                leverageBySymbol.getOrDefault(tradePair.toString('/'), positionService.fetchOne(tradePair)
                        .map(Position::getLeverage)
                        .orElse(1.0)));
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        if (tradePair == null || leverage <= 0.0) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Trade pair and leverage are required"));
        }
        String symbol = tradePair.toString('/');
        leverageBySymbol.put(symbol, leverage);
        positionService.setLeverage(tradePair, leverage);
        return CompletableFuture.completedFuture("LEVERAGE_UPDATED");
    }

    public void synchronizePortfolio() {
        portfolioSynchronizer.synchronize(
                accountService.snapshot(),
                positionService.fetchAll(),
                orderService.fetchAllOpenOrders(),
                orderService.executions());
    }

    public TradePair parsePair(String symbol) throws SQLException, ClassNotFoundException {
        TradePair tradePair = TradePair.fromSymbol(symbol);
        tradePair.setTradingSession(IbkrTradingSessionFactory.forInstrument(symbol));
        return tradePair;
    }

    public IbkrConnectionManager.ConnectionHealth connectionHealth() {
        return connectionManager.snapshotHealth();
    }

    public IbkrSessionState ibkrSessionState() {
        return connectionService.getSessionState();
    }

    public CompletableFuture<List<IbkrContractCandidate>> searchContracts(String userSearchTerm) {
        return contractResolver.search(userSearchTerm);
    }

    public CompletableFuture<IbkrResolvedContract> resolveContract(IbkrContractCandidate candidate) {
        return contractResolver.resolve(candidate);
    }

    public Optional<IbkrResolvedContract> cachedContract(TradePair tradePair) {
        return contractResolver.cached(tradePair);
    }

    public List<IbkrResolvedContract> cachedContracts() {
        return contractResolver.cachedContracts();
    }

    private void ensureResolvedContract(TradePair tradePair) {
        contractResolver.requireResolved(tradePair);
    }

    private boolean canTradeNow() {
        if (isPaperTrading()) {
            return false;
        }
        boolean clientPortalBlocked = shouldRequireClientPortalAuth() && !clientPortalClient.isAuthenticated();
        return !connectionManager.isConnected()
                || clientPortalBlocked
                || !marketDataProvider.isMarketDataHealthy()
                || !liveRiskApprovalGate.getAsBoolean()
                || !liveTradingLicenseGate.getAsBoolean();
    }

    private double quantityFor(TradePair pair) {
        return positionService.fetchOne(pair).map(Position::getQuantity).orElse(1.0);
    }

    private IbkrAccountSnapshot currentSnapshot() {
        ensureAuthenticatedSessionForAccountAccess();
        if (modeRequestsPaperNetwork()) {
            return accountService.snapshot();
        }
        return accountService.refreshFromBrokerIfAvailable();
    }

    private void ensureAuthenticatedSessionForAccountAccess() {
        if (shouldRequireClientPortalAuth() && !clientPortalClient.isAuthenticated()) {
            throw new SecurityException("IBKR access denied: " + clientPortalClient.authenticationFailureReason());
        }
    }

    private boolean shouldRequireClientPortalAuth() {
        if (modeRequestsPaperNetwork()) {
            return false;
        }

        String mode = firstNonBlank(
                getCredentials() == null ? null : getCredentials().param("authMode"),
                System.getProperty("investpro.ibkr.authMode"),
                System.getenv("IBKR_AUTH_MODE"),
                "gateway");
        String normalized = mode == null ? "gateway" : mode.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "client-portal", "client_portal", "portal", "webapi", "web-api" -> true;
            case "gateway", "tws", "socket", "ib-gateway", "ib_gateway" -> false;
            default -> false;
        };
    }

    private IbkrConnectionProfile connectionProfileFromCredentials() {
        ExchangeCredentials credentials = getCredentials();
        if (credentials == null || !hasIbkrConnectionParams(credentials)) {
            return null;
        }

        boolean paper = modeRequestsPaperNetwork();
        IbkrConnectionMode connectionMode = ibkrConnectionMode(credentials.param("authMode"));
        return new IbkrConnectionProfile(
                connectionMode,
                credentials.param("host"),
                credentials.intParamOrDefault("port", 0),
                credentials.intParamOrDefault("clientId", 1),
                paper,
                credentials.booleanParamOrDefault("autoDetect", true),
                credentials.param("connectionName"),
                null);
    }

    private boolean hasIbkrConnectionParams(ExchangeCredentials credentials) {
        return firstNonBlank(
                credentials.param("host"),
                credentials.param("port"),
                credentials.param("clientId"),
                credentials.param("authMode")) != null;
    }

    private IbkrConnectionMode ibkrConnectionMode(String authMode) {
        String normalized = authMode == null ? "" : authMode.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "client-portal", "client_portal", "portal", "webapi", "web-api" ->
                    IbkrConnectionMode.CLIENT_PORTAL_GATEWAY;
            default -> IbkrConnectionMode.TWS_API;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String currency) {
        if (currency == null) {
            return "USD";
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private Order toOrder(OpenOrder openOrder) {
        Order order = new Order();
        order.setId(parseOrderId(openOrder.getOrderId()));
        order.setDate(java.util.Date.from(openOrder.getCreatedAt() == null ? Instant.now() : openOrder.getCreatedAt()));
        order.setTradePair(openOrder.getTradePair());
        order.setSide(openOrder.getSide());
        order.setOrderType(openOrder.getOrderType() == null ? "UNKNOWN" : openOrder.getOrderType().name());
        order.setPrice(openOrder.getPrice());
        order.setQuantity(openOrder.getSize());
        order.setFilledQuantity(openOrder.getFilledSize());
        order.setStatus(openOrder.getStatus() == null ? "UNKNOWN" : openOrder.getStatus().name());
        order.setCreatedAt(openOrder.getCreatedAt());
        order.setUpdatedAt(openOrder.getUpdatedAt());
        return order;
    }

    private Order toOrder(Trade trade) {
        Order order = new Order();
        order.setId(trade.getLocalTradeId());
        order.setDate(java.util.Date.from(trade.getTimestamp() == null ? Instant.now() : trade.getTimestamp()));
        order.setTradePair(trade.getTradePair());
        order.setSide(trade.getTransactionType());
        order.setOrderType("MARKET");
        order.setPrice(trade.getPrice());
        order.setQuantity(trade.getAmount());
        order.setFilledQuantity(trade.getAmount());
        order.setStatus("FILLED");
        order.setCreatedAt(trade.getTimestamp());
        order.setUpdatedAt(trade.getTimestamp());
        return order;
    }

    private boolean matchesPair(TradePair source, TradePair requested) {
        if (requested == null) {
            return true;
        }
        return source != null && requested.toString('/').equals(source.toString('/'));
    }

    private boolean matchesSince(Instant timestamp, Instant since) {
        if (since == null) {
            return true;
        }
        return timestamp != null && !timestamp.isBefore(since);
    }

    private long parseOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(orderId.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ignored) {
            return Math.abs(orderId.hashCode());
        }
    }

    @Override
    public boolean supportsLiveTrading() {
        return true;
    }

    @Override
    public boolean supportsTradePair(@NotNull TradePair tradePair) {
        return contractMapper.supports(tradePair, MARKET_TYPES.STOCKS);
    }
}
