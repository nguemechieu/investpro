package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.SimpleIntegerProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.stellar.sdk.Asset;
import org.stellar.sdk.KeyPair;
import org.stellar.sdk.Network;
import org.stellar.sdk.Price;
import org.stellar.sdk.Server;
import org.stellar.sdk.Transaction;
import org.stellar.sdk.TransactionBuilder;
import org.stellar.sdk.TransactionBuilderAccount;
import org.stellar.sdk.operations.ManageSellOfferOperation;
import org.stellar.sdk.requests.RequestBuilder;
import org.stellar.sdk.responses.AccountResponse;
import org.stellar.sdk.responses.AssetResponse;
import org.stellar.sdk.responses.OrderBookResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TradeAggregationResponse;
import org.stellar.sdk.responses.TradeResponse;
import org.stellar.sdk.responses.TransactionResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Stellar Network adapter for InvestPro.
 *
 * <p>SDK-first implementation using Stellar Horizon {@link Server} for account data,
 * balances, asset issuer discovery, order books, trades, trade aggregations, and live
 * offer submission.
 *
 * <p>Important Stellar rules:
 * <ul>
 *     <li>XLM is native and has no issuer.</li>
 *     <li>Stellar has no broker-style market orders; market orders are submitted as aggressive limit offers.</li>
 *     <li>Stellar has no broker-style positions, leverage, margin, brackets, stop-loss, or take-profit orders.</li>
 *     <li>Paper mode is local/simulated and safe by default.</li>
 *     <li>Live mode requires accountId = public G... account and apiSecret = secret S... seed.</li>
 * </ul>
 */
@Getter
@Setter
@Slf4j
@SuppressWarnings({"SpellCheckingInspection", "unused"})
public class StellarNetwork extends Exchange {

    private static final String STELLAR_API_URL = "https://horizon.stellar.org";
    private static final String STELLAR_TEST_URL = "https://horizon-testnet.stellar.org";

    private static final String MAINNET_USDC_ISSUER = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN";
    private static final String TESTNET_USDC_ISSUER = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN";


    private static final double DEFAULT_XLM_USDC_PRICE = 0.50;
    private static final int DEFAULT_ORDER_BOOK_LIMIT = 20;
    private static final int MAX_RECENT_TRADES = 200;
    private static final long DEFAULT_BASE_FEE_STROOPS = 100L;
    private static final long DEFAULT_TX_TIMEOUT_SECONDS = 45L;
    private static final double MARKET_ORDER_SLIPPAGE_BUFFER = 0.01;

    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, String> trustedAssetIssuers = new ConcurrentHashMap<>();
    private final Map<String, OpenOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, Order> orderHistory = new ConcurrentHashMap<>();
    private final List<Position> positions = new CopyOnWriteArrayList<>();
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong nextOrderId = new AtomicLong(1000);
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(
            Math.max(2, Math.min(6, Runtime.getRuntime().availableProcessors())),
            new ThreadFactory() {
                private final AtomicLong sequence = new AtomicLong(1);

                @Override
                public Thread newThread(@NotNull Runnable runnable) {
                    Thread thread = new Thread(runnable, "stellar-io-" + sequence.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });

    protected final ExchangeStreamConsumer liveTradeConsumers = new UiExchangeStreamConsumer();

    private String apiKey;
    private String apiSecret;
    private String accountId;

    private volatile Server server;
    private volatile Server marketDataServer;
    private volatile Boolean serverPaperMode;
    private volatile OkHttpClient okHttpClient;
    private volatile OkHttpClient submitHttpClient;
    private volatile boolean connected;
    private volatile boolean websocketAvailable;
    private ExchangeWebSocketClient websocketClient;

    public StellarNetwork(@NotNull ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        Objects.requireNonNull(exchangeCredentials, "exchangeCredentials must not be null");

        this.apiKey = trimToNull(exchangeCredentials.apiKey());
        this.apiSecret = trimToNull(exchangeCredentials.apiSecret());
        this.accountId = trimToNull(exchangeCredentials.accountId());
        this.okHttpClient = buildReadHttpClient();
        this.submitHttpClient = buildSubmitHttpClient();
        this.connected = false;
        this.websocketAvailable = false;

        initializePaperTradingAccount();
        this.server = createServer();
    }

    private OkHttpClient buildReadHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private OkHttpClient buildSubmitHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(70, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private Server createServer() {
        String url = horizonUrl();
        log.debug("Creating Stellar Horizon Server for {}", url);
        Server created = new Server(url, okHttpClient, submitHttpClient);
        serverPaperMode = isPaperTrading();
        return created;
    }

    private @NotNull Server activeServer() {
        Server current = server;
        boolean paperMode = isPaperTrading();

        if (current == null || serverPaperMode == null || !serverPaperMode.equals(paperMode)) {
            closeServerQuietly();
            current = createServer();
            server = current;
        }

        return current;
    }

    private @NotNull Server activeMarketDataServer() {
        Server current = marketDataServer;
        if (current == null) {
            current = new Server(STELLAR_API_URL, okHttpClient, submitHttpClient);
            marketDataServer = current;
        }
        return current;
    }

    private void refreshServerForMode() {
        closeServerQuietly();
        server = createServer();
    }

    private void closeServerQuietly() {
        Server current = server;
        if (current != null) {
            try {
                current.close();
            } catch (Exception exception) {
                log.debug("Unable to close Stellar Horizon Server cleanly: {}", exception.getMessage());
            }
        }
        Server marketData = marketDataServer;
        if (marketData != null && marketData != current) {
            try {
                marketData.close();
            } catch (Exception exception) {
                log.debug("Unable to close Stellar market-data Horizon Server cleanly: {}", exception.getMessage());
            }
        }
        marketDataServer = null;
    }

    private void initializePaperTradingAccount() {
        balances.clear();
        trustedAssetIssuers.clear();

        balances.put("USDC", 1_000.0);
        balances.put("USD", 1_000.0);
        balances.put("XLM", 1_000.0);
        balances.put("EURC", 0.0);

        trustedAssetIssuers.put("USDC", isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER);
        trustedAssetIssuers.put("USD", isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER);

        log.info("Stellar paper account initialized with 1000 USDC and 1000 XLM");
    }

    @Override
    public void connect() {
        refreshServerForMode();
        AuthCheckResult result = checkAuthentication();
        if (!result.isSuccess()) {
            connected = false;
            throw new IllegalStateException(result.getMessage());
        }
        connected = true;
        log.info("Connected to Stellar {} through {}", isPaperTrading() ? "paper/testnet" : "live/mainnet", horizonUrl());
    }

    @Override
    public void disconnect() {
        connected = false;
        websocketAvailable = false;
        orders.clear();
        closeServerQuietly();
        log.info("Disconnected Stellar Network adapter");
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
    public String getName() {
        return "STELLAR";
    }

    @Override
    public String getSignal() {
        return "XLM";
    }

    @Override
    public String getExchangeId() {
        return "stellar";
    }

    @Override
    public String getDisplayName() {
        return "Stellar Network";
    }

    @Override
    public boolean isSandbox() {
        return isPaperTrading();
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return Boolean.parseBoolean(System.getenv().getOrDefault("STELLAR_PAPER", "true")) || !hasLiveCredentials();
    }

    protected boolean modeRequestsPaperNetwork() {
        String mode = trimToNull(getUserSelectedTradingMode());
        return ("PAPER".equalsIgnoreCase(mode) || "SANDBOX".equalsIgnoreCase(mode) || "TESTNET".equalsIgnoreCase(mode));
    }

    protected boolean modeRequestsLiveNetwork() {
        String mode = trimToNull(getUserSelectedTradingMode());
        return ("LIVE".equalsIgnoreCase(mode) || "REAL".equalsIgnoreCase(mode) || "MAINNET".equalsIgnoreCase(mode));
    }

    @Override
    public String getTimestamp() {
        return Instant.now().toString();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    private String horizonUrl() {
        return isPaperTrading() ? STELLAR_TEST_URL : STELLAR_API_URL;
    }

    private Network stellarNetwork() {
        return isPaperTrading() ? Network.TESTNET : Network.PUBLIC;
    }

    public boolean hasCredentials() {
        return hasLiveCredentials();
    }

    private boolean hasLiveCredentials() {
        return accountId != null && !accountId.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    private KeyPair signingKeyPair() {
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Stellar secret seed is missing. Set apiSecret to the S... seed for live signing.");
        }
        return KeyPair.fromSecretSeed(apiSecret);
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        AuthCheckResult result = checkAuthentication();
        return result.isSuccess() ? AuthResult.success(result.getMessage()) : AuthResult.failure(result.getMessage());
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        Instant checkedAt = Instant.now();

        if (isPaperTrading()) {
            try {
                activeServer();
                return AuthCheckResult.builder()
                        .exchangeName(getName())
                        .success(true)
                        .httpStatus(200)
                        .credentialSource("PAPER_MODE")
                        .endpointTested(horizonUrl())
                        .message("Stellar paper mode ready through Horizon Server SDK")
                        .checkedAt(checkedAt)
                        .build();
            } catch (Exception exception) {
                return AuthCheckResult.builder()
                        .exchangeName(getName())
                        .success(false)
                        .httpStatus(0)
                        .credentialSource("PAPER_MODE")
                        .endpointTested(horizonUrl())
                        .message("Unable to initialize Stellar paper Horizon server: " + exception.getMessage())
                        .checkedAt(checkedAt)
                        .build();
            }
        }

        if (accountId == null || accountId.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("Stellar accountId is missing. Use the public G... account ID.")
                    .checkedAt(checkedAt)
                    .build();
        }

        if (apiSecret == null || apiSecret.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("Stellar apiSecret is missing. Use the secret S... seed only for live trading.")
                    .checkedAt(checkedAt)
                    .build();
        }

        try {
            KeyPair keyPair = signingKeyPair();
            if (!keyPair.getAccountId().equals(accountId)) {
                return AuthCheckResult.builder()
                        .exchangeName(getName())
                        .success(false)
                        .credentialIssue(true)
                        .message("Stellar accountId does not match the provided secret seed public key.")
                        .checkedAt(checkedAt)
                        .build();
            }

            AccountResponse account = activeServer().accounts().account(accountId);
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(true)
                    .httpStatus(200)
                    .credentialSource("CONFIGURATION")
                    .endpointTested("/accounts/" + accountId)
                    .message("Stellar account validated through Horizon Server SDK. Sequence=" + account.getSequenceNumber())
                    .checkedAt(checkedAt)
                    .build();
        } catch (Exception exception) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .httpStatus(0)
                    .credentialSource("CONFIGURATION")
                    .endpointTested("/accounts/" + accountId)
                    .message("Unable to validate Stellar account through Horizon Server SDK: " + exception.getMessage())
                    .checkedAt(checkedAt)
                    .build();
        }
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return supplyAsyncIo(() -> {
            try {
                if (isPaperTrading()) {
                    if (balances.isEmpty()) {
                        initializePaperTradingAccount();
                    }
                    return buildAccount(accountId == null ? "PAPER-STELLAR" : accountId);
                }

                AccountResponse accountResponse = activeServer().accounts().account(accountId);
                Map<String, Double> liveBalances = new LinkedHashMap<>();
                Map<String, String> liveIssuers = new LinkedHashMap<>();

                for (AccountResponse.Balance balance : accountResponse.getBalances()) {
                    String code = balanceCode(balance);
                    liveBalances.put(code, parseDouble(balance.getBalance(), 0.0));

                    String issuer = balanceIssuer(balance);
                    if (issuer != null && !issuer.isBlank()) {
                        liveIssuers.put(code, issuer);
                    }
                }

                balances.clear();
                balances.putAll(liveBalances);

                trustedAssetIssuers.clear();
                trustedAssetIssuers.putAll(liveIssuers);
                trustedAssetIssuers.put("USDC", isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER);
                trustedAssetIssuers.put("USD", isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER);

                return buildAccount(accountResponse.getAccountId());
            } catch (Exception exception) {
                log.warn("Failed to fetch Stellar account; using cached balances: {}", exception.getMessage());
                return buildAccount(accountId == null ? "STELLAR-CACHED" : accountId);
            }
        });
    }

    private Account buildAccount(String id) {
        Account account = new Account();
        account.setAccountId(id);
        account.setBalances(new LinkedHashMap<>(balances));
        account.setBalance("USDC", balances.getOrDefault("USDC", 0.0));
        account.setTotalBalance(computeTotalBalanceEstimateUsdc());
        account.setAvailableBalance(balances.getOrDefault("USDC", 0.0));
        return account;
    }

    private String balanceCode(AccountResponse.Balance balance) {
        String assetType = trimToNull(balance.getAssetType());
        if ("native".equalsIgnoreCase(assetType)) {
            return "XLM";
        }
        String code = trimToNull(balance.getAssetCode());
        return code == null ? "UNKNOWN" : normalizeCurrency(code);
    }

    private String balanceIssuer(AccountResponse.Balance balance) {
        String assetType = trimToNull(balance.getAssetType());
        if ("native".equalsIgnoreCase(assetType)) {
            return null;
        }
        return trimToNull(balance.getAssetIssuer());
    }

    private double computeTotalBalanceEstimateUsdc() {
        double total = balances.getOrDefault("USDC", 0.0) + balances.getOrDefault("USD", 0.0);
        total += balances.getOrDefault("XLM", 0.0) * Math.max(safeTicker(defaultPair()).getMidPrice(), 0.0);
        total += balances.getOrDefault("EURC", 0.0);
        return total;
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return CompletableFuture.completedFuture(balances.getOrDefault(normalizeCurrency(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAvailableBalance(currencyCode);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(computeTotalBalanceEstimateUsdc());
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return CompletableFuture.completedFuture(computeTotalBalanceEstimateUsdc());
    }

    @Override
    public boolean supportsLiveTrading() {
        return hasLiveCredentials();
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return true;
    }

    @Override
    public boolean supportsOrderBook() {
        return true;
    }

    @Override
    public boolean supportsPositions() {
        return false;
    }

    @Override
    public boolean supportsAccountTrades() {
        return true;
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
    public ExchangeWebSocketClient getWebsocketClient() {
        return websocketClient;
    }

    @Override
    public boolean supportsWebSocket() {
        return false;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return websocketAvailable;
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
                    double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.BUY, size);
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss,
                     double takeProfit, double slippage) {
        createMarketOrder(tradePair, Side.SELL, size);
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return createMarketOrder(symbol, side, quantity);
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return createLimitOrder(symbol, side, quantity, limitPrice);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair symbol, Side side, double quantity) {
        return supplyAsyncIo(() -> {
            TradePair pair = pairOrDefault(symbol);
            Side safeSide = side == null ? Side.BUY : side;
            double normalizedQuantity = normalizeAmount(pair, quantity);
            validateOrderNow(pair, normalizedQuantity);

            Ticker ticker = safeTicker(pair);
            double referencePrice = safeSide == Side.BUY ? ticker.getAskPrice() : ticker.getBidPrice();
            if (!Double.isFinite(referencePrice) || referencePrice <= 0) {
                referencePrice = ticker.getMidPrice() > 0 ? ticker.getMidPrice() : DEFAULT_XLM_USDC_PRICE;
            }

            double aggressiveLimitPrice = safeSide == Side.BUY
                    ? referencePrice * (1.0 + MARKET_ORDER_SLIPPAGE_BUFFER)
                    : referencePrice * (1.0 - MARKET_ORDER_SLIPPAGE_BUFFER);

            String orderId = submitOrFillOrder(pair, safeSide, normalizedQuantity, aggressiveLimitPrice, true);
            log.info("Stellar {} market order accepted: {} {} {}", isPaperTrading() ? "paper" : "live", safeSide, normalizedQuantity, pair);
            return orderId;
        });
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return supplyAsyncIo(() -> {
            TradePair pair = pairOrDefault(symbol);
            Side safeSide = side == null ? Side.BUY : side;
            double normalizedQuantity = normalizeAmount(pair, quantity);
            validateOrderNow(pair, normalizedQuantity);

            if (!Double.isFinite(limitPrice) || limitPrice <= 0) {
                throw new IllegalArgumentException("limitPrice must be positive");
            }

            String orderId = submitOrFillOrder(pair, safeSide, normalizedQuantity, normalizePrice(pair, limitPrice), false);
            log.info("Stellar {} limit order accepted: {} {} {} @ {}",
                    isPaperTrading() ? "paper" : "live", safeSide, normalizedQuantity, pair, limitPrice);
            return orderId;
        });
    }

    private String submitOrFillOrder(TradePair pair, Side side, double quantity, double limitPrice, boolean marketLike) {
        if (isPaperTrading()) {
            return submitPaperOrder(pair, side, quantity, limitPrice, marketLike);
        }
        return submitLiveOffer(pair, side, quantity, limitPrice, marketLike);
    }

    private String submitPaperOrder(TradePair pair, Side side, double quantity, double limitPrice, boolean marketLike) {
        String orderId = "STL-" + (marketLike ? "MARKET" : "LIMIT") + "-" + nextOrderId.getAndIncrement();
        Ticker ticker = safeTicker(pair);

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        boolean hasValidBid = Double.isFinite(bid) && bid > 0;
        boolean hasValidAsk = Double.isFinite(ask) && ask > 0;

        boolean marketable = marketLike
            || (side == Side.BUY && hasValidAsk && limitPrice >= ask)
            || (side == Side.SELL && hasValidBid && limitPrice <= bid);

        if (marketable) {
            double executionPrice = marketLike
                    ? (side == Side.BUY ? ticker.getAskPrice() : ticker.getBidPrice())
                    : limitPrice;

            if (!Double.isFinite(executionPrice) || executionPrice <= 0) {
                executionPrice = ticker.getMidPrice() > 0 ? ticker.getMidPrice() : DEFAULT_XLM_USDC_PRICE;
            }

            applyPaperFill(pair, side, quantity, executionPrice);

            Order order = buildOrder(orderId, pair, marketLike ? "MARKET" : "LIMIT", executionPrice, quantity, side);
            order.setStatus("FILLED");
            order.setFilledQuantity(quantity);
            order.setCummulativeQuoteQty(quantity * executionPrice);
            orderHistory.put(orderId, order);
            tradeHistory.add(new Trade(pair, executionPrice, quantity, side, parseOrderId(orderId), Instant.now()));
            return orderId;
        }

        OpenOrder openOrder = new OpenOrder(orderId, pair, side, OpenOrder.OrderType.LIMIT, limitPrice, (int) Math.ceil(quantity));
        openOrder.setSize(quantity);
        openOrder.setRemainingSize(quantity);
        openOrder.setStatus(OpenOrder.OrderStatus.OPEN);
        openOrder.setExchange(getExchangeId());
        orders.put(orderId, openOrder);

        Order order = buildOrder(orderId, pair, "LIMIT", limitPrice, quantity, side);
        order.setStatus("OPEN");
        orderHistory.put(orderId, order);
        return orderId;
    }

    /**
     * Submit a Stellar DEX offer using ManageSellOfferOperation.
     *
     * <p>For BUY base/quote: sell quote, buy base. Offer amount is quote notional.
     * For SELL base/quote: sell base, buy quote. Offer amount is base quantity.
     */
    private String submitLiveOffer(@NotNull TradePair pair, Side side, double quantity, double limitPrice, boolean marketLike) {
        ensureLiveTradingReady();

        Asset baseAsset = toStellarAsset(pair.getBaseCode());
        Asset quoteAsset = toStellarAsset(pair.getCounterCode());

        Asset selling;
        Asset buying;
        BigDecimal amountSelling;
        Price stellarPrice;

        if (side == Side.SELL) {
            selling = baseAsset;
            buying = quoteAsset;
            amountSelling = bd(quantity);
            stellarPrice = Price.fromString(formatAmount(limitPrice));
        } else {
            selling = quoteAsset;
            buying = baseAsset;
            amountSelling = bd(quantity * limitPrice);
            stellarPrice = Price.fromString(formatAmount(1.0 / limitPrice));
        }

        try {
            TransactionBuilderAccount sourceAccount = activeServer().loadAccount(accountId);
            ManageSellOfferOperation operation = ManageSellOfferOperation.builder()
                    .selling(selling)
                    .buying(buying)
                    .amount(amountSelling)
                    .price(stellarPrice)
                    .offerId(0L)
                    .build();

            Transaction transaction = new TransactionBuilder(sourceAccount, stellarNetwork())
                    .addOperation(operation)
                    .setBaseFee(DEFAULT_BASE_FEE_STROOPS)
                    .setTimeout(DEFAULT_TX_TIMEOUT_SECONDS)
                    .build();

            transaction.sign(signingKeyPair());
            TransactionResponse response = activeServer().submitTransaction(transaction);
            String txHash = response.getHash() == null || response.getHash().isBlank()
                    ? transaction.hashHex()
                    : response.getHash();

            Order order = buildOrder(txHash, pair, marketLike ? "MARKET_AS_AGGRESSIVE_LIMIT" : "LIMIT", limitPrice, quantity, side);
            order.setStatus(Boolean.TRUE.equals(response.getSuccessful()) ? "SUBMITTED" : "UNKNOWN");
            orderHistory.put(txHash, order);

            fetchAccount();
            return txHash;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to submit Stellar live offer through Horizon Server SDK: " + exception.getMessage(), exception);
        }
    }

    private void ensureLiveTradingReady() {
        if (isPaperTrading()) {
            return;
        }
        AuthCheckResult result = checkAuthentication();
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getMessage());
        }
    }

    private void validateOrderNow(TradePair tradePair, double quantity) {
        if (!supportsTradePair(tradePair)) {
            throw new IllegalArgumentException("Unsupported Stellar pair: " + tradePair);
        }
        if (normalizeAmount(tradePair, quantity) < getMinOrderAmount(tradePair)) {
            throw new IllegalArgumentException("Order quantity is below Stellar precision minimum");
        }
    }

    private void applyPaperFill(TradePair pair, Side side, double quantity, double price) {
        String base = normalizeCurrency(pair.getBaseCode());
        String quote = normalizeCurrency(pair.getCounterCode());
        double notional = quantity * price;

        if (side == Side.BUY) {
            double quoteBalance = balances.getOrDefault(quote, 0.0);
            if (quoteBalance < notional) {
                throw new IllegalArgumentException("Insufficient " + quote + " paper balance");
            }
            balances.merge(quote, -notional, Double::sum);
            balances.merge(base, quantity, Double::sum);
        } else {
            double baseBalance = balances.getOrDefault(base, 0.0);
            if (baseBalance < quantity) {
                throw new IllegalArgumentException("Insufficient " + base + " paper balance");
            }
            balances.merge(base, -quantity, Double::sum);
            balances.merge(quote, notional, Double::sum);
        }
    }

    @Override
    public CompletableFuture<String> createOrder(Order order) {
        if (order == null) {
            return failedFuture(new IllegalArgumentException("order must not be null"));
        }
        Side side = order.getSide() == null ? Side.BUY : order.getSide();
        String type = order.getType() == null ? "MARKET" : order.getType();
        if ("LIMIT".equalsIgnoreCase(type)) {
            return createLimitOrder(order.getTradePair(), side, order.getQuantity(), order.getPrice());
        }
        return createMarketOrder(order.getTradePair(), side, order.getQuantity());
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side,
                             double stopLoss, double takeProfit, double slippage) {
        TradePair pair = pairOrDefault(tradePair);
        Order order = new Order();
        order.setId((long) id);
        order.setTradePair(pair);
        order.setSymbol(pair.toString('/'));
        order.setType(type == null || type.isBlank() ? "MARKET" : type);
        order.setPrice(normalizePrice(pair, price));
        order.setQuantity(normalizeAmount(pair, amount));
        order.setSide(side == null ? Side.BUY : side);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return failedFuture(unsupported("createStopOrder"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount,
                                                        double entryPrice, double stopLoss, double takeProfit) {
        return failedFuture(unsupported("createBracketOrder"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        return supplyAsyncIo(() -> {
            if (orderId == null || orderId.isBlank()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }

            OpenOrder removed = orders.remove(orderId);
            Order order = orderHistory.get(orderId);
            if (order != null) {
                order.setStatus(removed == null ? order.getStatus() : "CANCELLED");
                order.setUpdatedAt(Instant.now());
            }
            if (removed != null) {
                removed.cancel();
            }
            return orderId;
        });
    }

    public void cancelOrder(TradePair tradePair) {
        if (tradePair == null) {
            return;
        }
        orders.entrySet().removeIf(entry -> Objects.equals(entry.getValue().getTradePair(), tradePair));
        orderHistory.values().stream()
                .filter(order -> Objects.equals(order.getTradePair(), tradePair))
                .filter(order -> "OPEN".equalsIgnoreCase(order.getStatus()))
                .forEach(order -> {
                    order.setStatus("CANCELLED");
                    order.setUpdatedAt(Instant.now());
                });
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return supplyAsyncIo(() -> {
            List<String> cancelled = new ArrayList<>();
            for (String orderId : orderIds) {
                if (orderId == null || orderId.isBlank()) {
                    continue;
                }
                if (orders.remove(orderId) != null) {
                    cancelled.add(orderId);
                    Order order = orderHistory.get(orderId);
                    if (order != null) {
                        order.setStatus("CANCELLED");
                        order.setUpdatedAt(Instant.now());
                    }
                }
            }
            return cancelled;
        });
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        int count = orders.size();
        orders.clear();
        orderHistory.values().forEach(order -> {
            if ("OPEN".equalsIgnoreCase(order.getStatus())) {
                order.setStatus("CANCELLED");
                order.setUpdatedAt(Instant.now());
            }
        });
        return CompletableFuture.completedFuture("Cancelled %d Stellar paper/local orders".formatted(count));
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return CompletableFuture.completedFuture(Optional.ofNullable(orderHistory.get(orderId)));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return CompletableFuture.completedFuture(filterOpenOrders(tradePair));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        return CompletableFuture.completedFuture(List.copyOf(orders.values()));
    }

    private List<OpenOrder> parseOpenOrders(JsonNode rootNode) {
        List<OpenOrder> openOrders = new ArrayList<>();
        if (rootNode == null || rootNode.isNull()) {
            return openOrders;
        }

        if (rootNode.isArray()) {
            for (JsonNode orderNode : rootNode) {
                OpenOrder order = parseOpenOrder(orderNode);
                if (order != null) {
                    openOrders.add(order);
                }
            }
            return openOrders;
        }

        if (rootNode.isObject()) {
            OpenOrder order = parseOpenOrder(rootNode);
            if (order != null) {
                openOrders.add(order);
            }
        }

        return openOrders;
    }

    private @Nullable OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            OpenOrder order = new OpenOrder();
            order.setOrderId(node.path("id").asText(""));

            String sellingAsset = node.path("selling").path("asset_code").asText();
            String buyingAsset = node.path("buying").path("asset_code").asText();
            if (!sellingAsset.isBlank() && !buyingAsset.isBlank()) {
                order.setTradePair(new TradePair(buyingAsset, sellingAsset));
            }

            double price = node.path("price").asDouble(0.0);
            double amount = node.path("amount").asDouble(0.0);
            order.setPrice(price);
            order.setSize(amount);
            order.setFilledSize(0.0);
            order.setRemainingSize(amount);
            order.setSide("sell".equalsIgnoreCase(node.path("side").asText("buy")) ? Side.SELL : Side.BUY);
            order.setOrderType(OpenOrder.OrderType.LIMIT);
            order.setStatus(OpenOrder.OrderStatus.PENDING);

            long timestamp = node.path("timestamp").asLong(0);
            if (timestamp > 0) {
                order.setCreatedAt(Instant.ofEpochSecond(timestamp));
                order.setUpdatedAt(Instant.ofEpochSecond(timestamp));
            }

            return order;
        } catch (Exception exception) {
            log.debug("Error parsing Stellar open order", exception);
            return null;
        }
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return CompletableFuture.completedFuture(orderHistory.values().stream()
                .filter(order -> tradePair == null || Objects.equals(order.getTradePair(), tradePair))
                .filter(order -> since == null || order.getCreatedAt() == null || !order.getCreatedAt().isBefore(since))
                .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return CompletableFuture.completedFuture(positions.stream()
                .filter(position -> tradePair == null || Objects.equals(position.getTradePair(), tradePair))
                .toList());
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return CompletableFuture.completedFuture(List.copyOf(positions));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return CompletableFuture.completedFuture(positions.stream()
                .filter(position -> tradePair == null || Objects.equals(position.getTradePair(), tradePair))
                .findFirst());
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return CompletableFuture.completedFuture("NO_POSITION_MODEL_ON_STELLAR");
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        positions.clear();
        return CompletableFuture.completedFuture("NO_POSITION_MODEL_ON_STELLAR");
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return CompletableFuture.completedFuture("NO_POSITION_MODEL_ON_STELLAR");
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return CompletableFuture.completedFuture("NO_POSITION_MODEL_ON_STELLAR");
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(unsupported("modifyStopLoss"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(unsupported("modifyTakeProfit"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(unsupported("enableTrailingStop"));
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == MARKET_TYPES.SPOT || marketType == MARKET_TYPES.MARKET || marketType == MARKET_TYPES.LIMIT;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.SPOT, MARKET_TYPES.MARKET, MARKET_TYPES.LIMIT);
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size,
                                                    double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(
                supportsMarketType(marketType)
                        && supportsTradePair(tradePair)
                        && normalizeAmount(tradePair, size) >= getMinOrderAmount(tradePair)
                        && stopLoss <= 0
                        && takeProfit <= 0);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0.0;
        }
        return Math.floor(amount * 10_000_000.0) / 10_000_000.0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        if (!Double.isFinite(price) || price <= 0) {
            return 0.0;
        }
        return Math.round(price * 10_000_000.0) / 10_000_000.0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.0000001;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 1.0;
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
        return failedFuture(unsupported("setLeverage"));
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return defaultPair();
    }

    @Override
    public List<TradePair> getTradePairSymbol() throws SQLException, ClassNotFoundException {
        return getTradablePairs();
    }

    @Override
    public List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException {
        refreshBalancesForMarketWatch();

        List<String> tradableAssets = balances.keySet().stream()
                .map(this::normalizeCurrency)
                .filter(code -> !code.isBlank())
                .distinct()
                .sorted()
                .toList();

        List<TradePair> pairs = new ArrayList<>(defaultPairs());

        for (String base : tradableAssets) {
            for (String counter : tradableAssets) {
                if (!base.equals(counter)) {
                    addPairIfMissing(pairs, new TradePair(base, counter));
                }
            }
        }

        return pairs.stream().filter(this::supportsTradePair).toList();
    }

    private void refreshBalancesForMarketWatch() {
        if (isPaperTrading() || accountId == null || accountId.isBlank()) {
            return;
        }
        try {
            fetchAccount().join();
        } catch (Exception exception) {
            log.debug("Unable to load Stellar balances/trustlines for Market Watch: {}", exception.getMessage());
        }
    }

    private void addPairIfMissing(@NotNull List<TradePair> pairs, TradePair pair) {
        boolean exists = pairs.stream().anyMatch(existing -> Objects.equals(existing.toString('/'), pair.toString('/')));
        if (!exists) {
            pairs.add(pair);
        }
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        if (tradePair == null) {
            return false;
        }

        String base = normalizeCurrency(tradePair.getBaseCode());
        String quote = normalizeCurrency(tradePair.getCounterCode());

        if (base.isBlank() || quote.isBlank() || base.equals(quote)) {
            return false;
        }

        boolean baseSupported = "XLM".equalsIgnoreCase(base) || !resolveIssuer(base).isBlank();
        boolean quoteSupported = "XLM".equalsIgnoreCase(quote) || !resolveIssuer(quote).isBlank();

        return baseSupported && quoteSupported;
    }

    @Override
    public double getLivePrice() {
        return safeTicker(defaultPair()).getMidPrice();
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        return safeTicker(tradePair);
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return supplyAsyncIo(() -> safeTicker(tradePair));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        List<TradePair> uniquePairs = tradePairs.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return supplyAsyncIo(() -> {
            List<Ticker> tickers = new ArrayList<>();

            for (TradePair pair : uniquePairs) {
                try {
                    Ticker ticker = safeTicker(pair);
                    tickers.add(ticker);
                } catch (Exception exception) {
                    log.debug("Unable to fetch Stellar ticker for {}: {}", pair, exception.getMessage());
                }
            }

            return tickers;
        });
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers(pair == null ? List.of(defaultPair()) : List.of(pair));
    }

    public List<Ticker> getTickers(MARKET_TYPES marketType) {
        if (marketType != MARKET_TYPES.SPOT && marketType != MARKET_TYPES.MARKET && marketType != MARKET_TYPES.LIMIT) {
            return List.of();
        }
        return fetchTickers(defaultPairs()).join();
    }

    public Ticker getTicker(String symbol) {
        return safeTicker(parseSymbolOrDefault(symbol));
    }

    private Ticker safeTicker(TradePair tradePair) {
        TradePair pair = pairOrDefault(tradePair);

        if (isPaperTrading()) {
            Ticker ticker = new Ticker(
                    DEFAULT_XLM_USDC_PRICE,
                    DEFAULT_XLM_USDC_PRICE * 0.999,
                    DEFAULT_XLM_USDC_PRICE * 1.001,
                    0.0,
                    System.currentTimeMillis());
            ticker.setTradePair(pair);
            return ticker;
        }

        try {
            OrderBook orderBook = fetchStellarOrderBook(pair);
            double bid = orderBook.getBestBid() == null ? 0.0 : orderBook.getBestBid().getPrice();
            double ask = orderBook.getBestAsk() == null ? 0.0 : orderBook.getBestAsk().getPrice();

            if (bid <= 0 && ask <= 0) {
                bid = DEFAULT_XLM_USDC_PRICE * 0.999;
                ask = DEFAULT_XLM_USDC_PRICE * 1.001;
            } else if (bid <= 0) {
                bid = ask * 0.999;
            } else if (ask <= 0) {
                ask = bid * 1.001;
            }

            double mid = (bid + ask) / 2.0;
            Ticker ticker = new Ticker(mid, bid, ask, 0.0, System.currentTimeMillis());
            ticker.setTradePair(pair);
            return ticker;
        } catch (Exception exception) {
            log.debug("Unable to fetch Stellar ticker for {}: {}", pair, exception.getMessage());
            Ticker ticker = new Ticker(DEFAULT_XLM_USDC_PRICE, DEFAULT_XLM_USDC_PRICE * 0.999,
                    DEFAULT_XLM_USDC_PRICE * 1.001, 0.0, System.currentTimeMillis());
            ticker.setTradePair(pair);
            return ticker;
        }
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        return supplyAsyncIo(() -> fetchStellarOrderBook(tradePair));
    }

    private OrderBook fetchStellarOrderBook(TradePair tradePair) {
        TradePair pair = pairOrDefault(tradePair);
        if (!supportsTradePair(pair)) {
            return syntheticOrderBook(pair);
        }

        try {
            var orderBookRequest = activeServer().orderBook()
                    .sellingAsset(toStellarAsset(pair.getBaseCode()))
                    .buyingAsset(toStellarAsset(pair.getCounterCode()));
            orderBookRequest.limit(DEFAULT_ORDER_BOOK_LIMIT);
            OrderBookResponse response = orderBookRequest.execute();

            List<OrderBook.PriceLevel> bids = new ArrayList<>();
            List<OrderBook.PriceLevel> asks = new ArrayList<>();

            if (response.getBids() != null) {
                for (OrderBookResponse.Row row : response.getBids()) {
                    double price = parseDouble(row.getPrice(), 0.0);
                    double amount = parseDouble(row.getAmount(), 0.0);
                    if (price > 0 && amount > 0) {
                        bids.add(new OrderBook.PriceLevel(price, amount));
                    }
                }
            }

            if (response.getAsks() != null) {
                for (OrderBookResponse.Row row : response.getAsks()) {
                    double price = parseDouble(row.getPrice(), 0.0);
                    double amount = parseDouble(row.getAmount(), 0.0);
                    if (price > 0 && amount > 0) {
                        asks.add(new OrderBook.PriceLevel(price, amount));
                    }
                }
            }

            if (bids.isEmpty() && asks.isEmpty()) {
                return syntheticOrderBook(pair);
            }

            bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
            asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));

            OrderBook orderBook = new OrderBook(pair, bids, asks);
            orderBook.setTimestamp(Instant.now());
            return orderBook;
        } catch (Exception exception) {
            log.debug("Falling back to synthetic Stellar order book for {}: {}", pair, exception.getMessage());
            return syntheticOrderBook(pair);
        }
    }

    private OrderBook syntheticOrderBook(TradePair pair) {
        double price = DEFAULT_XLM_USDC_PRICE;
        OrderBook orderBook = new OrderBook(pair,
                List.of(
                        new OrderBook.PriceLevel(price * 0.999, 5_000.0),
                        new OrderBook.PriceLevel(price * 0.995, 10_000.0)
                ),
                List.of(
                        new OrderBook.PriceLevel(price * 1.001, 5_000.0),
                        new OrderBook.PriceLevel(price * 1.005, 10_000.0)
                ));
        orderBook.setTimestamp(Instant.now());
        return orderBook;
    }

    public List<Trade> getRecentTrades(TradePair tradePair, int limit) {
        return fetchRecentTrades(tradePair, limit);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return supplyAsyncIo(() -> fetchRecentTrades(tradePair, MAX_RECENT_TRADES).stream()
                .filter(trade -> stopAt == null || !trade.getTimestamp().isAfter(stopAt))
                .toList());
    }

    private List<Trade> fetchRecentTrades(TradePair tradePair, int limit) {
        TradePair pair = pairOrDefault(tradePair);
        int safeLimit = Math.min(limit <= 0 ? 100 : limit, MAX_RECENT_TRADES);

        try {
            var request = activeServer().trades()
                    .baseAsset(toStellarAsset(pair.getBaseCode()))
                    .counterAsset(toStellarAsset(pair.getCounterCode()))
                    .limit(safeLimit);
            request.order(RequestBuilder.Order.DESC);

            Page<TradeResponse> page = request.execute();
            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                return filterTrades(pair, safeLimit);
            }

            List<Trade> trades = new ArrayList<>();
            for (TradeResponse record : page.getRecords()) {
                double price = record.getPrice() == null
                        ? DEFAULT_XLM_USDC_PRICE
                        : parseDouble(record.getPrice().toString(), DEFAULT_XLM_USDC_PRICE);
                double amount = parseDouble(record.getBaseAmount(), 0.0);
                Instant timestamp = safeParseInstant(record.getLedgerCloseTime(), Instant.now());
                long id = parseLong(record.getId(), nextOrderId.getAndIncrement());
                Side side = Boolean.TRUE.equals(record.getBaseIsSeller()) ? Side.SELL : Side.BUY;
                trades.add(new Trade(pair, price, amount, side, id, timestamp));
            }
            return trades;
        } catch (Exception exception) {
            log.debug("Unable to fetch Stellar trades for {}: {}", pair, exception.getMessage());
            return filterTrades(pair, safeLimit);
        }
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        if (isPaperTrading() || accountId == null || accountId.isBlank()) {
            return CompletableFuture.completedFuture(filterTrades(tradePair, tradeHistory.size()));
        }

        return supplyAsyncIo(() -> {
            try {
                var request = activeServer().trades()
                        .forAccount(accountId)
                        .limit(MAX_RECENT_TRADES);
                request.order(RequestBuilder.Order.DESC);

                Page<TradeResponse> page = request.execute();
                if (page == null || page.getRecords() == null) {
                    return filterTrades(tradePair, tradeHistory.size());
                }

                List<Trade> trades = new ArrayList<>();
                for (TradeResponse record : page.getRecords()) {
                    TradePair pair = pairFromTradeResponse(record, tradePair == null ? defaultPair() : tradePair);
                    if (tradePair != null && !Objects.equals(pair.toString('/'), tradePair.toString('/'))) {
                        continue;
                    }
                    double price = record.getPrice() == null
                            ? DEFAULT_XLM_USDC_PRICE
                            : parseDouble(record.getPrice().toString(), DEFAULT_XLM_USDC_PRICE);
                    double amount = parseDouble(record.getBaseAmount(), 0.0);
                    Instant timestamp = safeParseInstant(record.getLedgerCloseTime(), Instant.now());
                    long id = parseLong(record.getId(), nextOrderId.getAndIncrement());
                    Side side = Boolean.TRUE.equals(record.getBaseIsSeller()) ? Side.SELL : Side.BUY;
                    trades.add(new Trade(pair, price, amount, side, id, timestamp));
                }
                return trades;
            } catch (Exception exception) {
                log.debug("Unable to fetch Stellar account trades through SDK: {}", exception.getMessage());
                return filterTrades(tradePair, tradeHistory.size());
            }
        });
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return fetchAccountTrades(tradePair).thenApply(trades -> trades.stream()
                .filter(trade -> since == null || !trade.getTimestamp().isBefore(since))
                .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return fetchAccountTrades(tradePair).thenApply(trades -> trades.stream()
                .filter(trade -> from == null || !trade.getTimestamp().isBefore(from))
                .filter(trade -> to == null || !trade.getTimestamp().isAfter(to))
                .toList());
    }

    private TradePair pairFromTradeResponse(TradeResponse response, TradePair fallback) {
        try {
            String base = "native".equalsIgnoreCase(response.getBaseAssetType()) ? "XLM" : response.getBaseAssetCode();
            String counter = "native".equalsIgnoreCase(response.getCounterAssetType()) ? "XLM" : response.getCounterAssetCode();
            return new TradePair(base, counter);
        } catch (Exception exception) {
            return fallback;
        }
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new StellarCandleDataSupplier(200, secondsPerCandle, pairOrDefault(tradePair));
    }

    public CandleDataSupplier getCandleDataSupplier(TradePair tradePair, Timeframe timeframe) {
        return getCandleDataSupplier(secondsFor(timeframe), tradePair);
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit) {
        int seconds = secondsFor(timeframe);
        long end = Instant.now().getEpochSecond();
        long start = end - (long) Math.max(1, limit) * seconds;
        return getCandles(tradePair, timeframe, limit, start, end);
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit, Long startTime, Long endTime) {
        return buildCandlesFromTrades(tradePair, secondsFor(timeframe), Math.max(1, limit), startTime, endTime);
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle) {
        if (tradePair == null || currentCandleStartedAt == null) {
            return failedFuture(new IllegalArgumentException("tradePair and currentCandleStartedAt must not be null"));
        }
        return supplyAsyncIo(() -> fetchInProgressAggregationCandle(
                pairOrDefault(tradePair),
                currentCandleStartedAt,
                secondsPerCandle));
    }

    private <T> CompletableFuture<T> supplyAsyncIo(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, ioExecutor);
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "M1";
            case 300 -> "M5";
            case 900 -> "M15";
            case 3_600 -> "H1";
            case 86_400 -> "D1";
            case 604_800 -> "W1";
            default -> "N/A";
        };
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.D1, Timeframe.W1);
    }

    private List<CandleData> buildCandlesFromTrades(TradePair tradePair, int secondsPerCandle, int limit,
                                                    Long startTime, Long endTime) {
        int safeSeconds = Math.max(60, secondsPerCandle);
        int safeLimit = Math.max(1, Math.min(limit, 1_000));
        long end = endTime == null ? Instant.now().getEpochSecond() : endTime;
        long start = startTime == null ? end - (long) safeLimit * safeSeconds : startTime;
        long resolution = aggregationResolutionMillis(safeSeconds);

        if (resolution <= 0) {
            log.debug("Unsupported Stellar candle resolution: {} seconds", safeSeconds);
            return List.of();
        }

        TradePair pair = pairOrDefault(tradePair);
        List<CandleData> direct = fetchAggregationCandles(pair, start, end, resolution, safeSeconds, safeLimit, false);
        if (!direct.isEmpty()) {
            return direct;
        }

        List<CandleData> inverse = fetchAggregationCandles(pair, start, end, resolution, safeSeconds, safeLimit, true);
        if (!inverse.isEmpty()) {
            return inverse;
        }

        return syntheticCandlesFromTicker(pair, safeSeconds, safeLimit, end);
    }

    private List<CandleData> fetchAggregationCandles(TradePair pair, long requestedStart, long requestedEnd,
                                                     long resolution, int secondsPerCandle, int limit,
                                                     boolean inverse) {
        long requestedWindow = Math.max(requestedEnd - requestedStart, (long) limit * secondsPerCandle);
        Asset base = toStellarMarketDataAsset(inverse ? pair.getCounterCode() : pair.getBaseCode());
        Asset counter = toStellarMarketDataAsset(inverse ? pair.getBaseCode() : pair.getCounterCode());

        for (int attempt = 0; attempt < 6; attempt++) {
            long end = requestedEnd - attempt * requestedWindow;
            long start = Math.max(0, end - requestedWindow);
            if (end <= 0 || start >= end) {
                break;
            }

            try {
                Page<TradeAggregationResponse> page = activeMarketDataServer().tradeAggregations(
                        base,
                        counter,
                        start * 1000L,
                        end * 1000L,
                        resolution,
                        0L).execute();

                List<TradeAggregationResponse> records = page == null || page.getRecords() == null
                        ? List.of()
                        : page.getRecords().stream()
                        .filter(record -> record.getTimestamp() != null)
                        .sorted(Comparator.comparingLong(TradeAggregationResponse::getTimestamp))
                        .toList();

                if (!records.isEmpty()) {
                    return denseCandlesFromAggregations(records, secondsPerCandle, limit, inverse);
                }
            } catch (Exception exception) {
                log.debug("Failed to fetch Stellar {}trade aggregations for {} on {}: {}",
                        inverse ? "inverse " : "", pair, STELLAR_API_URL, exception.getMessage());
                return List.of();
            }
        }

        return List.of();
    }

    private List<CandleData> syntheticCandlesFromTicker(TradePair pair, int secondsPerCandle, int limit, long endTime) {
        Ticker ticker = safeTicker(pair);
        double mid = ticker.getMidPrice() > 0 ? ticker.getMidPrice() : DEFAULT_XLM_USDC_PRICE;
        if (mid <= 0.0) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, 1_000));
        long alignedEnd = (endTime / secondsPerCandle) * secondsPerCandle;
        long start = Math.max(0, alignedEnd - (long) safeLimit * secondsPerCandle);
        List<CandleData> candles = new ArrayList<>(safeLimit);
        double previousClose = mid;

        for (int index = 0; index < safeLimit; index++) {
            long openTime = start + (long) index * secondsPerCandle;
            double wave = Math.sin((openTime / (double) secondsPerCandle) * 0.21) * 0.0025;
            double drift = Math.cos((openTime / (double) secondsPerCandle) * 0.07) * 0.0015;
            double close = Math.max(0.0000001, mid * (1.0 + wave + drift));
            double high = Math.max(previousClose, close) * 1.001;
            double low = Math.min(previousClose, close) * 0.999;
            candles.add(new CandleData(previousClose, close, high, low, (int) openTime, 0.0));
            previousClose = close;
        }

        return candles;
    }

    private List<CandleData> denseCandlesFromAggregations(List<TradeAggregationResponse> records,
                                                          int secondsPerCandle,
                                                          int limit,
                                                          boolean inverse) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        TreeMap<Long, TradeAggregationResponse> byOpenTime = new TreeMap<>();
        for (TradeAggregationResponse record : records) {
            long openTime = (record.getTimestamp() / 1000L / secondsPerCandle) * secondsPerCandle;
            byOpenTime.put(openTime, record);
        }

        long lastOpen = byOpenTime.lastKey();
        long firstOpen = Math.max(0, lastOpen - (long) (limit - 1) * secondsPerCandle);
        double previousClose = inverse
                ? invertPrice(parseDouble(records.get(0).getOpen(), 0.0))
                : parseDouble(records.get(0).getOpen(), 0.0);

        List<CandleData> candles = new ArrayList<>();
        for (long openTime = firstOpen; openTime <= lastOpen; openTime += secondsPerCandle) {
            TradeAggregationResponse record = byOpenTime.get(openTime);
            if (record != null) {
                CandleData candle = inverse ? invertedCandleFromAggregation(record) : candleFromAggregation(record);
                previousClose = candle.closePrice();
                candles.add(candle);
            } else if (previousClose > 0) {
                candles.add(new CandleData(previousClose, previousClose, previousClose, previousClose, (int) openTime, 0.0));
            }
        }

        return candles.stream().skip(Math.max(0, candles.size() - limit)).toList();
    }

    private CandleData candleFromAggregation(TradeAggregationResponse record) {
        return new CandleData(
                parseDouble(record.getOpen(), 0.0),
                parseDouble(record.getClose(), 0.0),
                parseDouble(record.getHigh(), 0.0),
                parseDouble(record.getLow(), 0.0),
                (int) (record.getTimestamp() / 1000L),
                parseDouble(record.getBaseVolume(), 0.0));
    }

    private CandleData invertedCandleFromAggregation(TradeAggregationResponse record) {
        double open = invertPrice(parseDouble(record.getOpen(), 0.0));
        double close = invertPrice(parseDouble(record.getClose(), 0.0));
        double high = invertPrice(parseDouble(record.getLow(), 0.0));
        double low = invertPrice(parseDouble(record.getHigh(), 0.0));
        return new CandleData(open, close, Math.max(high, low), Math.min(high, low),
                (int) (record.getTimestamp() / 1000L),
                parseDouble(record.getCounterVolume(), 0.0));
    }

    private double invertPrice(double value) {
        return value <= 0 ? 0.0 : 1.0 / value;
    }

    private long aggregationResolutionMillis(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60, 300, 900, 3_600, 86_400, 604_800 -> secondsPerCandle * 1000L;
            default -> -1L;
        };
    }

    private Optional<InProgressCandleData> fetchInProgressAggregationCandle(TradePair pair,
                                                                            Instant currentCandleStartedAt,
                                                                            int secondsPerCandle) {
        long resolution = aggregationResolutionMillis(secondsPerCandle);
        if (resolution <= 0) {
            return Optional.empty();
        }

        long openSeconds = currentCandleStartedAt.getEpochSecond();
        long nowSeconds = Instant.now().getEpochSecond();
        long currentTill = Math.min(nowSeconds, openSeconds + Math.max(1, secondsPerCandle));
        if (currentTill <= openSeconds) {
            currentTill = openSeconds + 1;
        }

        Optional<InProgressCandleData> direct = fetchInProgressAggregationCandle(pair, openSeconds, currentTill, resolution, false);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<InProgressCandleData> inverse = fetchInProgressAggregationCandle(pair, openSeconds, currentTill, resolution, true);
        if (inverse.isPresent()) {
            return inverse;
        }

        Optional<InProgressCandleData> directCarry = fetchCarryForwardInProgressCandle(pair, openSeconds, currentTill,
                resolution, secondsPerCandle, false);
        if (directCarry.isPresent()) {
            return directCarry;
        }

        Optional<InProgressCandleData> inverseCarry = fetchCarryForwardInProgressCandle(pair, openSeconds, currentTill,
                resolution, secondsPerCandle, true);
        return inverseCarry.isPresent() ? inverseCarry : fetchTickerInProgressCandle(pair, openSeconds, currentTill);
    }

    private Optional<InProgressCandleData> fetchInProgressAggregationCandle(TradePair pair,
                                                                            long openSeconds,
                                                                            long currentTill,
                                                                            long resolution,
                                                                            boolean inverse) {
        try {
            Page<TradeAggregationResponse> page = activeMarketDataServer().tradeAggregations(
                    toStellarMarketDataAsset(inverse ? pair.getCounterCode() : pair.getBaseCode()),
                    toStellarMarketDataAsset(inverse ? pair.getBaseCode() : pair.getCounterCode()),
                    openSeconds * 1000L,
                    currentTill * 1000L,
                    resolution,
                    0L).execute();

            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                return Optional.empty();
            }

            TradeAggregationResponse record = page.getRecords().stream()
                    .filter(aggregation -> aggregation.getTimestamp() != null)
                    .max(Comparator.comparingLong(TradeAggregationResponse::getTimestamp))
                    .orElse(null);

            if (record == null) {
                return Optional.empty();
            }

            CandleData candle = inverse ? invertedCandleFromAggregation(record) : candleFromAggregation(record);
            return Optional.of(new InProgressCandleData(
                    (int) openSeconds,
                    candle.openPrice(),
                    candle.highPrice(),
                    candle.lowPrice(),
                    (int) currentTill,
                    candle.closePrice(),
                    candle.volume()));
        } catch (Exception exception) {
            log.debug("Failed to fetch Stellar {}in-progress aggregation for {} on {}: {}",
                    inverse ? "inverse " : "", pair, STELLAR_API_URL, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<InProgressCandleData> fetchCarryForwardInProgressCandle(TradePair pair,
                                                                             long openSeconds,
                                                                             long currentTill,
                                                                             long resolution,
                                                                             int secondsPerCandle,
                                                                             boolean inverse) {
        long lookbackSeconds = Math.max((long) secondsPerCandle * 240L, 30L * 24L * 60L * 60L);
        long startSeconds = Math.max(0, openSeconds - lookbackSeconds);
        if (startSeconds >= openSeconds) {
            return Optional.empty();
        }

        try {
            Page<TradeAggregationResponse> page = activeMarketDataServer().tradeAggregations(
                    toStellarMarketDataAsset(inverse ? pair.getCounterCode() : pair.getBaseCode()),
                    toStellarMarketDataAsset(inverse ? pair.getBaseCode() : pair.getCounterCode()),
                    startSeconds * 1000L,
                    openSeconds * 1000L,
                    resolution,
                    0L).execute();

            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                return Optional.empty();
            }

            TradeAggregationResponse record = page.getRecords().stream()
                    .filter(aggregation -> aggregation.getTimestamp() != null)
                    .max(Comparator.comparingLong(TradeAggregationResponse::getTimestamp))
                    .orElse(null);
            if (record == null) {
                return Optional.empty();
            }

            CandleData lastCompleted = inverse ? invertedCandleFromAggregation(record) : candleFromAggregation(record);
            double close = lastCompleted.closePrice();
            if (close <= 0.0) {
                return Optional.empty();
            }

            return Optional.of(new InProgressCandleData(
                    (int) openSeconds,
                    close,
                    close,
                    close,
                    (int) currentTill,
                    close,
                    0.0));
        } catch (Exception exception) {
            log.debug("Failed to fetch Stellar {}carry-forward aggregation for {} on {}: {}",
                    inverse ? "inverse " : "", pair, horizonUrl(), exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<InProgressCandleData> fetchTickerInProgressCandle(TradePair pair,
                                                                       long openSeconds,
                                                                       long currentTill) {
        try {
            double price = safeTicker(pair).getMidPrice();
            if (price <= 0.0) {
                return Optional.empty();
            }
            return Optional.of(new InProgressCandleData(
                    (int) openSeconds,
                    price,
                    price,
                    price,
                    (int) currentTill,
                    price,
                    0.0));
        } catch (Exception exception) {
            log.debug("Failed to build Stellar in-progress candle from ticker for {}: {}", pair, exception.getMessage());
            return Optional.empty();
        }
    }

    private int secondsFor(Timeframe timeframe) {
        if (timeframe == null) {
            return 60;
        }
        return switch (timeframe) {
            case M1 -> 60;
            case M5 -> 300;
            case M15 -> 900;
            case H1 -> 3_600;
            case D1 -> 86_400;
            case W1 -> 604_800;
            default -> 60;
        };
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
        connect();
    }

    @Override
    public void disconnectStream() {
        disconnect();
    }

    @Override
    public boolean isStreamConnected() {
        return connected;
    }

    @Override
    public void reconnectStream() {
        reconnect();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        log.debug("Stellar generic streaming requested; this adapter is SDK polling-first.");
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
    }

    @Override
    public void stopAllStreams() {
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        log.debug("Stellar ticker streaming unavailable; use polling fetchTicker.");
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {
        log.debug("Stellar trade streaming unavailable; use polling fetchRecentTradesUntil.");
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        log.debug("Stellar trade streaming unavailable; use polling.");
    }

    public void streamLiveCandles(TradePair tradePair, Timeframe timeframe, ExchangeStreamConsumer consumer) {
        log.debug("Stellar candle streaming unavailable; use CandleDataSupplier polling.");
    }

    public void stopStreamLiveCandles(TradePair tradePair) {
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        log.debug("Stellar order-book streaming unavailable; use fetchOrderBook polling.");
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        log.debug("Stellar candle streaming unavailable; use CandleDataSupplier polling.");
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        log.debug("Stellar account streaming unavailable.");
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        log.debug("Stellar balance streaming unavailable.");
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        log.debug("Stellar order streaming unavailable.");
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        log.debug("Stellar fill streaming unavailable.");
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        log.debug("Stellar positions are not supported.");
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
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("STELLAR")
                .exchangeId("stellar")
                .displayName("Stellar Network Distributed Exchange")
                .apiBaseUrl(horizonUrl())
                .webSocketBaseUrl("")
                .authenticationType("STELLAR_SECRET_SEED")

                .supportsCrypto(true)
                .supportsSpot(true)
                .supportsForex(false)
                .supportsStocks(false)
                .supportsEquities(false)
                .supportsDerivatives(false)
                .supportsFutures(false)
                .supportsPerpetuals(false)
                .supportsOptions(false)
                .supportsIndices(false)
                .supportsCommodities(false)

                .supportsLiveTrading(hasLiveCredentials())
                .supportsPaperTradingMode(true)
                .supportsSandbox(isPaperTrading())
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(false)
                .supportsStopLimitOrders(false)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(false)
                .supportsTrailingStopOrders(false)
                .supportsMarginTrading(false)
                .supportsLeverage(false)

                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(false)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsAccountTrades(true)
                .supportsFills(true)
                .supportsOrderValidation(true)

                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(true)
                .supportsFullOrderBook(true)
                .supportsTopOfBook(true)
                .supportsDistributionBook(false)
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(true)
                .supportsStreamingPrices(false)

                .supportsNativeWebSocket(false)
                .supportsWebSocketStreaming(false)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)
                .supportsTickerStreaming(false)
                .supportsOrderBookStreaming(false)
                .supportsTradeStreaming(false)
                .supportsCandleStreaming(false)
                .supportsAccountStreaming(false)
                .supportsOrderStreaming(false)
                .supportsFillStreaming(false)
                .supportsPositionStreaming(false)
                .supportsBalanceStreaming(false)

                .supportsRateLimitInfo(false)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(false)

                .supportedMarketType("CRYPTO")
                .supportedMarketType("SPOT")
                .supportedMarketType("XLM")

                .notes("""
                        Stellar Network DEX adapter.
                        Uses Stellar Horizon Server SDK for account loading, asset discovery, order book, trades, candles, and transaction submission.
                        Stellar does not have broker-style market orders; market orders are simulated with aggressive limit offers.
                        Stellar does not support margin, leverage, bracket orders, or stop-loss/take-profit as native order types.
                        Paper mode uses a local simulated account and Horizon testnet data when available.
                        Live mode requires accountId=G... and apiSecret=S... secret seed.
                        """)
                .build();
    }

    private Asset toStellarAsset(String code) {
        String normalized = normalizeCurrency(code);

        if ("XLM".equals(normalized)) {
            return Asset.createNativeAsset();
        }

        String issuer = resolveIssuer(normalized);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("Unknown Stellar issuer for asset: " + normalized);
        }

        return Asset.createNonNativeAsset(normalized, issuer);
    }

    private Asset toStellarMarketDataAsset(String code) {
        String normalized = normalizeCurrency(code);

        if ("XLM".equals(normalized)) {
            return Asset.createNativeAsset();
        }

        String issuer = resolveMarketDataIssuer(normalized);
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("Unknown Stellar market-data issuer for asset: " + normalized);
        }

        return Asset.createNonNativeAsset(normalized, issuer);
    }

    private String resolveIssuer(String assetCode) {
        String normalized = normalizeCurrency(assetCode);

        if (normalized.isBlank()) {
            return "";
        }

        if ("XLM".equalsIgnoreCase(normalized)) {
            return "";
        }

        String trustedIssuer = trimToNull(trustedAssetIssuers.get(normalized));
        if (trustedIssuer != null) {
            return trustedIssuer;
        }

        if ("USDC".equalsIgnoreCase(normalized) || "USD".equalsIgnoreCase(normalized)) {
            return isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER;
        }


        try {
            Page<AssetResponse> page = activeServer()
                    .assets()
                    .assetCode(normalized)
                    .limit(20)
                    .execute();

            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                log.debug("No Stellar issuer found for asset code {}", normalized);
                return "";
            }

            return page.getRecords().stream()
                    .filter(asset -> asset.getAssetIssuer() != null && !asset.getAssetIssuer().isBlank())
                    .max(Comparator.comparing(this::assetIssuerScore))
                    .map(AssetResponse::getAssetIssuer)
                    .orElse("");
        } catch (Exception exception) {
            log.debug("Unable to resolve Stellar issuer for {}: {}", normalized, exception.getMessage());
            return "";
        }
    }

    private String resolveMarketDataIssuer(String assetCode) {
        String normalized = normalizeCurrency(assetCode);

        if (normalized.isBlank() || "XLM".equals(normalized)) {
            return "";
        }

        if ("USDC".equalsIgnoreCase(normalized) || "USD".equalsIgnoreCase(normalized)) {
            return MAINNET_USDC_ISSUER;
        }

        String trustedIssuer = trimToNull(trustedAssetIssuers.get(normalized));
        if (trustedIssuer != null && !isPaperTrading()) {
            return trustedIssuer;
        }

        try {
            Page<AssetResponse> page = activeMarketDataServer()
                    .assets()
                    .assetCode(normalized)
                    .limit(20)
                    .execute();

            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                log.debug("No Stellar market-data issuer found for asset code {}", normalized);
                return "";
            }

            return page.getRecords().stream()
                    .filter(asset -> asset.getAssetIssuer() != null && !asset.getAssetIssuer().isBlank())
                    .max(Comparator.comparing(this::assetIssuerScore))
                    .map(AssetResponse::getAssetIssuer)
                    .orElse("");
        } catch (Exception exception) {
            log.debug("Unable to resolve Stellar market-data issuer for {}: {}", normalized, exception.getMessage());
            return "";
        }
    }

    private double assetIssuerScore(AssetResponse asset) {
        if (asset == null) {
            return 0.0;
        }

        double contractsAmount = parseDouble(asset.getContractsAmount(), 0.0);
        if (contractsAmount > 0.0) {
            return contractsAmount;
        }

        double numAccounts = parseDouble(String.valueOf(asset.getAccounts()), 0.0);
        double claimableBalances = parseDouble(String.valueOf(asset.getNumClaimableBalances()), 0.0);
        double liquidityPools = parseDouble(String.valueOf(asset.getNumLiquidityPools()), 0.0);

        return numAccounts + claimableBalances + liquidityPools;
    }

    private TradePair defaultPair() {
        try {
            return new TradePair("XLM", "USDC");
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to create default Stellar trade pair", exception);
        }
    }

    private List<TradePair> defaultPairs() {
        try {
            return List.of(
                    new TradePair("XLM", "USDC"),

                    new TradePair("BTC", "USDC")
                               );
        } catch (SQLException | ClassNotFoundException exception) {
            return List.of(defaultPair());
        }
    }

    private TradePair pairOrDefault(TradePair pair) {
        return pair == null ? defaultPair() : pair;
    }

    private TradePair parseSymbolOrDefault(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return defaultPair();
        }

        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('_', '/').replace('-', '/');
        String[] parts = normalized.contains("/") ? normalized.split("/") : new String[]{"XLM", normalized};
        try {
            return new TradePair(parts[0], parts.length > 1 ? parts[1] : "USDC");
        } catch (SQLException | ClassNotFoundException exception) {
            return defaultPair();
        }
    }

    private @NonNull Order buildOrder(String orderId, TradePair pair, String type, double price, double quantity, Side side) {
        Order order = new Order();
        order.setId(parseOrderId(orderId));
        order.setTradePair(pair);
        order.setSymbol(pair.toString('/'));
        order.setType(type);
        order.setPrice(normalizePrice(pair, price));
        order.setQuantity(normalizeAmount(pair, quantity));
        order.setSide(side);
        order.setCreatedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        return order;
    }

    private long parseOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return nextOrderId.get();
        }

        int index = orderId.lastIndexOf('-');
        if (index >= 0 && index + 1 < orderId.length()) {
            try {
                return Long.parseLong(orderId.substring(index + 1));
            } catch (NumberFormatException ignored) {
                // Fall through to hash-based id.
            }
        }
        return Math.abs(orderId.hashCode());
    }

    private @NonNull @Unmodifiable List<OpenOrder> filterOpenOrders(TradePair tradePair) {
        return orders.values().stream()
                .filter(order -> tradePair == null || Objects.equals(order.getTradePair(), tradePair))
                .sorted(Comparator.comparing(OpenOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private @NonNull @Unmodifiable List<Trade> filterTrades(TradePair tradePair, int limit) {
        return tradeHistory.stream()
                .filter(trade -> tradePair == null || Objects.equals(trade.getTradePair(), tradePair))
                .sorted(Comparator.comparing(Trade::getTimestamp).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    private String normalizeCurrency(String code) {
        if (code == null || code.isBlank()) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private double parseDouble(String value, double fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Instant safeParseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.DOWN);
    }

    private String formatAmount(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    protected UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException("Stellar Network adapter does not support " + methodName);
    }

    protected static <T> @NonNull CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private class StellarCandleDataSupplier extends CandleDataSupplier {

        StellarCandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair) {
            super(numCandles, secondsPerCandle, tradePair,
                    new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        }

        @Override
        public Future<List<CandleData>> get() {
            return CompletableFuture.completedFuture(getCandleData());
        }

        @Override
        public Set<Integer> getSupportedGranularities() {
            return Set.of(60, 300, 900, 3_600, 86_400, 604_800);
        }

        @Override
        public List<CandleData> getCandleData() {
            int end = endTime.get();
            List<CandleData> candles = buildCandlesFromTrades(tradePair, secondsPerCandle, numCandles, null, (long) end);
            if (!candles.isEmpty()) {
                int firstOpenTime = candles.stream()
                        .mapToInt(CandleData::openTime)
                        .min()
                        .orElse(end);
                if (firstOpenTime < end) {
                    endTime.set(firstOpenTime);
                }
            }
            return candles;
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return StellarNetwork.this.getCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
                                                                                 Instant currentCandleStartedAt,
                                                                                 long secondsIntoCurrentCandle,
                                                                                 int secondsPerCandle) {
            return StellarNetwork.this.fetchCandleDataForInProgressCandle(
                            tradePair,
                            currentCandleStartedAt,
                            secondsIntoCurrentCandle,
                            secondsPerCandle)
                    .thenApply(optional -> optional.map(value -> (Object) value));
        }

        @Override
        public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
            return StellarNetwork.this.fetchRecentTradesUntil(tradePair, stopAt);
        }
    }
}
