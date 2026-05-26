package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.Getter;
import org.investpro.config.AppConfig;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.models.MarketDepthType;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.AuthResult;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Solana network exchange adapter.
 *
 * <p>This adapter wires the existing Solana RPC services into the common
 * InvestPro Exchange surface. It supports connection checks and wallet balance
 * reads today; order submission is intentionally disabled until a vetted swap
 * execution service is available.</p>
 */
public class SolanaNetwork extends Exchange {

    private static final String EXCHANGE_NAME = "SOLANA NETWORK";
    private static final String EXCHANGE_ID = "solana-network";
    private static final String DEFAULT_WALLET_KEY = "solana.walletAddress";

    private final ExchangeCredentials credentials;
    @Getter
    private final SolanaNetworkAdapter adapter;
    private volatile boolean connected;

    public SolanaNetwork(@NotNull ExchangeCredentials credentials) {
        super(credentials);
        this.credentials = credentials;
        this.adapter = new SolanaNetworkAdapter(resolveConfig());
    }

    private SolanaNetworkConfig resolveConfig() {
        String network = AppConfig.get("solana.network", SolanaNetworkConfig.MAINNET);
        String commitment = AppConfig.get("solana.commitment", "confirmed");
        int timeoutSeconds = AppConfig.getInt("solana.requestTimeoutSeconds", 30);
        int maxRetries = AppConfig.getInt("solana.maxRetries", 3);
        String rpcUrl = AppConfig.get("solana.rpcUrl", "");
        if (rpcUrl.isBlank()) {
            rpcUrl = switch (network.trim().toLowerCase(Locale.ROOT)) {
                case SolanaNetworkConfig.DEVNET -> """
                        https://api.devnet.solana.com""";
                case SolanaNetworkConfig.TESTNET -> """
                        https://api.testnet.solana.com""";
                default -> """
                        https://api.mainnet-beta.solana.com""";
            };
        }
        boolean tradingEnabled = AppConfig.getBoolean("solana.tradingEnabled", false);
        return new SolanaNetworkConfig(true, network, rpcUrl, commitment, tradingEnabled, timeoutSeconds, maxRetries);
    }

    @Override
    public String getName() {
        return EXCHANGE_NAME;
    }

    @Override
    public String getSignal() {
        return "";
    }

    @Override
    public String getExchangeId() {
        return EXCHANGE_ID;
    }

    @Override
    public String getDisplayName() {
        return "Solana Network";
    }

    @Override
    public boolean isSandbox() {
        return modeRequestsExternalPaperNetwork();
    }

    @Override
    public boolean isPaperTrading() {
        return modeRequestsPaperNetwork() || !adapter.getConfig().isLiveTradingAllowed();
    }

    @Override
    public String getTimestamp() {
        return Instant.now().toString();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == null || marketType == MARKET_TYPES.SPOT || marketType == MARKET_TYPES.MARKET;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.SPOT);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName(getName())
                .exchangeId(getExchangeId())
                .displayName(getDisplayName())
                .apiBaseUrl(adapter.getConfig().rpcUrl())
                .authenticationType("WALLET_ADDRESS")
                .supportsSpot(true)
                .supportsCrypto(true)
                .supportsLiveTrading(supportsLiveTrading())
                .supportsPaperTradingMode(true)
                .supportsMarketOrders(false)
                .supportsLimitOrders(false)
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsOrderValidation(true)
                .supportsTicker(false)
                .supportsOrderBook(false)
                .marketDepthType(MarketDepthType.NONE)
                .supportsPollingFallback(true)
                .supportedMarketType("SPOT")
                .notes("Solana RPC wallet/balance integration. Swap trading is disabled until a swap executor is configured.")
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        try {
            long slot = adapter.connect().join();
            connected = true;
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(true)
                    .credentialSource("CONFIG_OR_ACCOUNT_ID")
                    .endpointTested("getSlot")
                    .httpStatus(200)
                    .message("Connected to Solana RPC at slot " + slot)
                    .credentialIssue(false)
                    .checkedAt(Instant.now())
                    .metadata(Map.of("network", adapter.getConfig().network(), "rpcUrl", adapter.getConfig().rpcUrl()))
                    .build();
        } catch (Exception exception) {
            connected = false;
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialSource("CONFIG_OR_ACCOUNT_ID")
                    .endpointTested("getSlot")
                    .httpStatus(0)
                    .message(rootMessage(exception))
                    .credentialIssue(false)
                    .checkedAt(Instant.now())
                    .metadata(Map.of("network", adapter.getConfig().network(), "rpcUrl", adapter.getConfig().rpcUrl()))
                    .build();
        }
    }

    @Override
    public void connect() {
        AuthCheckResult result = checkAuthentication();
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMessage());
        }
    }

    @Override
    public void disconnect() {
        connected = false;
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public Boolean isConnected() {
        return connected;
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public boolean supportsWebSocket() {
        return false;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return false;
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        String walletAddress = walletAddress();
        Account account = new Account(this, walletAddress, "");
        account.setAccountId(walletAddress);
        account.setAccount(walletAddress);
        account.setBaseCurrency("SOL");
        if (walletAddress.isBlank()) {
            return CompletableFuture.completedFuture(account);
        }
        return adapter.refreshBalances(walletAddress)
                .thenApply(snapshot -> {
                    account.setBalance("SOL", snapshot.solBalance().doubleValue());
                    account.setAvailableBalance(snapshot.solBalance().doubleValue());
                    account.setMetadata(Map.of(
                            "walletAddress", snapshot.address(),
                            "tokenCount", String.valueOf(snapshot.tokens().size()),
                            "network", adapter.getConfig().network()));
                    return account;
                });
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return fetchBalance(currencyCode);
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchBalance(currencyCode);
    }

    private CompletableFuture<Double> fetchBalance(String currencyCode) {
        String walletAddress = walletAddress();
        if (walletAddress.isBlank()) {
            return CompletableFuture.completedFuture(0.0);
        }
        return adapter.refreshBalances(walletAddress)
                .thenApply(snapshot -> {
                    String normalized = currencyCode == null ? "SOL" : currencyCode.trim().toUpperCase(Locale.ROOT);
                    if (normalized.isBlank() || "SOL".equals(normalized)) {
                        return snapshot.solBalance().doubleValue();
                    }
                    return snapshot.tokens().stream()
                            .filter(token -> normalized.equalsIgnoreCase(token.symbol()) || normalized.equalsIgnoreCase(token.mint()))
                            .map(SolanaTokenBalance::amount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)
                            .doubleValue();
                });
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return fetchTotalBalance("SOL");
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchAvailableBalance("SOL");
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return new TradePair("SOL", "USDC");
    }

    @Override
    public List<TradePair> getTradePairSymbol() throws SQLException, ClassNotFoundException {
        return getTradablePairs();
    }

    @Override
    public List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException {
        return List.of(new TradePair("SOL", "USDC"), new TradePair("SOL", "USD"));
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        if (tradePair == null) {
            return false;
        }
        String symbol = tradePair.toString('/').toUpperCase(Locale.ROOT);
        return symbol.equals("SOL/USDC") || symbol.equals("SOL/USD");
    }

    @Override
    public double getLivePrice() {
        return 0.0;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return Ticker.empty();
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.completedFuture(Ticker.empty());
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return CompletableFuture.completedFuture(List.of(Ticker.empty()));
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.completedFuture(new OrderBook(tradePair));
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return "Solana RPC market candles are not available in this adapter.";
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of();
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        return unsupportedTrading();
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
                             double stopLoss, double takeProfit, double slippage) {
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount, double limitPrice) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
                                                        double entryPrice, double stopLoss, double takeProfit) {
        return unsupportedTrading();
    }

    private CompletableFuture<String> unsupportedTrading() {
        return failedFuture(new UnsupportedOperationException(
                "Solana swap trading is not enabled. Wallet balances and RPC connectivity are supported."));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return CompletableFuture.completedFuture(orderId);
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        return CompletableFuture.completedFuture(orderIds == null ? List.of() : List.copyOf(orderIds));
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return CompletableFuture.completedFuture("No Solana orders to cancel");
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return CompletableFuture.completedFuture("No Solana position to close");
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return CompletableFuture.completedFuture("No Solana positions to close");
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return closePosition(symbol);
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return closePosition(symbol);
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return unsupportedTrading();
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.POLLING;
    }

    @Override
    public boolean supportsNativeWebSocket() {
        return false;
    }

    @Override
    public boolean supportsHttpStreaming() {
        return false;
    }

    @Override
    public boolean supportsPollingFallback() {
        return true;
    }

    @Override
    public void connectStream() {
    }

    @Override
    public void disconnectStream() {
    }

    @Override
    public boolean isStreamConnected() {
        return connected;
    }

    @Override
    public void reconnectStream() {
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
    }

    @Override
    public void stopAllStreams() {
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
    }

    @Override
    public void stopAccountStream() {
    }

    @Override
    public void stopBalancesStream() {
    }

    @Override
    public void stopOrdersStream() {
    }

    @Override
    public void stopFillsStream() {
    }

    @Override
    public void stopPositionsStream() {
    }

    @Override
    public boolean supportsLiveTrading() {
        return adapter.getConfig().isLiveTradingAllowed() && !isPaperTrading();
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return false;
    }

    @Override
    public boolean supportsPositions() {
        return false;
    }

    @Override
    public boolean supportsAccountTrades() {
        return false;
    }

    @Override
    public boolean supportsStopLossTakeProfit() {
        return false;
    }

    @Override
    public boolean supportsBracketOrders() {
        return false;
    }

    @Override
    public boolean supportsLeverage() {
        return false;
    }

    @Override
    public boolean supportsDerivatives() {
        return false;
    }

    @Override
    public boolean supportsForex() {
        return false;
    }

    @Override
    public boolean supportsStocks() {
        return false;
    }

    @Override
    public boolean supportsCrypto() {
        return true;
    }

    @Override
    public boolean supportsAccountStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderStreaming() {
        return false;
    }

    @Override
    public boolean supportsFillStreaming() {
        return false;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return false;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return false;
    }

    @Override
    public boolean supportsTickerStreaming() {
        return false;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return false;
    }

    @Override
    public boolean supportsCandleStreaming() {
        return false;
    }

    @Override
    public boolean supportsTradeStreaming() {
        return false;
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
                                                    double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return Math.max(0.0, amount);
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return Math.max(0.0, price);
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.0;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 0.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(new UnsupportedOperationException("Solana does not support leverage."));
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side,
                    double stopLoss, double takeProfit, double slippage) {
        throw new UnsupportedOperationException("Solana swap trading is not enabled.");
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side,
                     double stopLoss, double takeProfit, double slippage) {
        throw new UnsupportedOperationException("Solana swap trading is not enabled.");
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        AuthCheckResult result = checkAuthentication();
        return result.isSuccess()
                ? AuthResult.success(result.getMessage())
                : AuthResult.failure(result.getMessage());
    }

    public SimpleIntegerProperty orderBookLevelProperty() {
        return new SimpleIntegerProperty(0);
    }

    private @NonNull String walletAddress() {
        if (credentials.accountId() != null && !credentials.accountId().isBlank()) {
            return credentials.accountId().trim();
        }
        if (credentials.apiKey() != null && !credentials.apiKey().isBlank()) {
            return credentials.apiKey().trim();
        }
        return AppConfig.get(DEFAULT_WALLET_KEY, "").trim();
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank() ? "Unknown Solana RPC error" : message;
    }
}
