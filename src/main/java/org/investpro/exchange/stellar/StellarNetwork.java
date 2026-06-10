package org.investpro.exchange.stellar;

import javafx.beans.property.SimpleIntegerProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.investpro.data.CandleData;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.PairQuality;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
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
import java.util.LinkedHashSet;
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
import java.util.stream.Stream;

/**
 * Stellar Network adapter for InvestPro.
 *
 * <p>
 * SDK-first implementation using Stellar Horizon {@link Server} for account
 * data,
 * balances, asset issuer discovery, order books, trades, trade aggregations,
 * and live
 * offer submission.
 *
 * <p>
 * Important Stellar rules:
 * <ul>
 * <li>XLM is native and has no issuer.</li>
 * <li>Stellar has no broker-style market orders; market orders are submitted as
 * aggressive limit offers.</li>
 * <li>Stellar has no broker-style positions, leverage, margin, brackets,
 * stop-loss, or take-profit orders.</li>
 * <li>Paper mode is local/simulated and safe by default.</li>
 * <li>Live mode requires accountId = public G... account and apiSecret = secret
 * S... seed.</li>
 * </ul>
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class StellarNetwork extends Exchange {

    private static final String STELLAR_API_URL = "https://horizon.stellar.org";
    private static final String STELLAR_TEST_URL = "https://horizon-testnet.stellar.org";

    private static final String MAINNET_USDC_ISSUER = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN";
    private static final String TESTNET_USDC_ISSUER = "GBBD47IF6LWK7P7MDEVSCWR7DPUWV3NY3DTQEVFL4NAT4AQH3ZLLFLA5";

    private static final double DEFAULT_XLM_USDC_PRICE = 0.50;
    private static final int DEFAULT_ORDER_BOOK_LIMIT = 200;
    private static final int MAX_RECENT_TRADES = 200;
    private static final long PAIR_DISCOVERY_TTL_MS = 10 * 60_000L;
    private static final double STELLAR_DISCOVERY_MAX_SPREAD_PERCENT = 5.0;
    private static final double STELLAR_DISCOVERY_MIN_DEPTH = 1.0;
    private static final double STELLAR_BOT_MAX_SPREAD_PERCENT = 1.0;
    private static final long DEFAULT_BASE_FEE_STROOPS = 100L;
    private static final long DEFAULT_TX_TIMEOUT_SECONDS = 45L;
    private static final double MARKET_ORDER_SLIPPAGE_BUFFER = 0.01;
    private static final int DEFAULT_ASSETS_NUMBER = 1000;
    private static final int MAX_CANDLE_LIMIT = 50000;
    private static final int SECONDS_M1 = 60;

    private final Map<String, Double> balances = new ConcurrentHashMap<>();
    private final Map<String, String> trustedAssetIssuers = new ConcurrentHashMap<>();
    private final Map<String, OpenOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, Order> orderHistory = new ConcurrentHashMap<>();
    private final List<Position> positions = new CopyOnWriteArrayList<>();
    private final List<Trade> tradeHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong nextOrderId = new AtomicLong(1000);
    private final Object pairDiscoveryLock = new Object();
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
    private volatile Boolean marketDataServerPaperMode;
    private volatile OkHttpClient okHttpClient;
    private volatile OkHttpClient submitHttpClient;
    private volatile boolean connected;
    private volatile boolean websocketAvailable;
    private volatile StellarTrustedAssetRegistry trustedAssetRegistry;
    private volatile Boolean trustedAssetRegistryTestnet;
    private volatile List<TradePair> cachedDiscoveredPairs = List.of();
    private volatile long cachedDiscoveredPairsExpiresAtMs;
    private ExchangeWebSocketClient websocketClient;

    public StellarNetwork(@NotNull ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
        Objects.requireNonNull(exchangeCredentials, "exchangeCredentials must not be null");

        this.apiKey = trimToNull(exchangeCredentials.apiKey());
        this.apiSecret = trimToNull(exchangeCredentials.apiSecret());
        this.accountId = resolveAccountId(exchangeCredentials.accountId(), this.apiKey);
        this.okHttpClient = buildReadHttpClient();
        this.submitHttpClient = buildSubmitHttpClient();
        this.connected = false;
        this.websocketAvailable = false;
        this.trustedAssetRegistry = trustedAssetRegistryForMode();

        initializePaperTradingAccount();
        this.server = createServer();
    }

    @Contract(" -> new")
    private @NonNull OkHttpClient buildReadHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    @Contract(" -> new")
    private @NonNull OkHttpClient buildSubmitHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(70, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    private @NonNull Server createServer() {
        boolean paperMode = isPaperTrading();
        String url = horizonUrl(paperMode);

        log.debug("Creating Stellar Horizon Server for {}", url);

        serverPaperMode = paperMode;
        return new Server(url, okHttpClient, submitHttpClient);
    }

    private String horizonUrl(boolean paperMode) {
        return paperMode ? STELLAR_TEST_URL : STELLAR_API_URL;
    }

    private Network stellarNetwork() {
        return isPaperTrading() ? Network.TESTNET : Network.PUBLIC;
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
        boolean paperMode = isPaperTrading();

        if (current == null || marketDataServerPaperMode == null || !marketDataServerPaperMode.equals(paperMode)) {
            if (current != null) {
                try {
                    current.close();
                } catch (Exception exception) {
                    log.debug("Unable to close stale Stellar market-data Horizon Server cleanly: {}",
                            exception.getMessage());
                }
            }
            current = new Server(horizonUrl(), okHttpClient, submitHttpClient);
            marketDataServer = current;
            marketDataServerPaperMode = paperMode;
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
        marketDataServerPaperMode = null;
    }

    private void initializePaperTradingAccount() {
        balances.clear();
        trustedAssetIssuers.clear();

        balances.put("USDC", 1_000.0);
        balances.put("XLM", 1_000.0);
        balances.put("EURC", 1_000.0);

        trustedAssetIssuers.put("USDC", isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER);
        syncTrustedIssuerCache();

        log.info("Stellar paper account initialized with 1000 USDC and 1000 XLM");
    }

    private StellarTrustedAssetRegistry trustedAssetRegistryForMode() {
        boolean testnet = isPaperTrading();
        StellarTrustedAssetRegistry current = trustedAssetRegistry;
        if (current == null || trustedAssetRegistryTestnet == null || !trustedAssetRegistryTestnet.equals(testnet)) {
            current = new StellarTrustedAssetRegistry(testnet);
            trustedAssetRegistry = current;
            trustedAssetRegistryTestnet = testnet;
            syncTrustedIssuerCache();
        }
        return current;
    }

    private void syncTrustedIssuerCache() {
        StellarTrustedAssetRegistry registry = trustedAssetRegistry;
        if (registry == null) {
            return;
        }
        for (StellarAssetIdentity asset : registry.allAssets()) {
            if (!asset.isNative() && !asset.issuer().isBlank()) {
                trustedAssetIssuers.put(asset.code(), asset.issuer());
                if ("USDC".equalsIgnoreCase(asset.code())) {
                    trustedAssetIssuers.put("USD", asset.issuer());
                }
            }
        }
    }

    public void addUserTrustlineAsset(String code, String issuer, String homeDomain) {
        trustedAssetRegistryForMode().addUserTrustlineAsset(code, issuer, homeDomain);
        syncTrustedIssuerCache();
        invalidateStellarPairDiscovery();
    }

    public void recheckStellarPairLiquidity() {
        invalidateStellarPairDiscovery();
    }

    private void invalidateStellarPairDiscovery() {
        synchronized (pairDiscoveryLock) {
            cachedDiscoveredPairs = List.of();
            cachedDiscoveredPairsExpiresAtMs = 0L;
        }
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
        log.info("Connected to Stellar {} through {}", isPaperTrading() ? "paper/testnet" : "live/mainnet",
                horizonUrl());
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

    private boolean hasLiveCredentials() {
        return accountId != null && !accountId.isBlank() && apiSecret != null && !apiSecret.isBlank();
    }

    private KeyPair signingKeyPair() {
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "Stellar secret seed is missing. Set apiSecret to the S... seed for live signing.");
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
                    .message("Stellar account validated through Horizon Server SDK. Sequence="
                            + account.getSequenceNumber())
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
                for (Map.Entry<String, String> entry : liveIssuers.entrySet()) {
                    trustedAssetRegistryForMode().addUserTrustlineAsset(entry.getKey(), entry.getValue(), "");
                }
                syncTrustedIssuerCache();

                return buildAccount(accountResponse.getAccountId());
            } catch (Exception exception) {
                log.warn("Failed to fetch Stellar account; using cached balances: {}", exception.getMessage());
                return buildAccount(accountId == null ? "STELLAR-CACHED" : accountId);
            }
        });
    }

    private @NonNull Account buildAccount(String id) {
        Account account = new Account();
        account.setAccountId(id);
        account.setBalances(new LinkedHashMap<>(balances));
        account.setBalance("USDC", balances.getOrDefault("USDC", 0.0));
        account.setTotalBalance(computeTotalBalanceEstimateUsdc());
        account.setAvailableBalance(balances.getOrDefault("USDC", 0.0));
        return account;
    }

    private @NonNull String balanceCode(AccountResponse.Balance balance) {
        String assetType = trimToNull(balance.getAssetType());
        if ("native".equalsIgnoreCase(assetType)) {
            return "XLM";
        }
        String code = trimToNull(balance.getAssetCode());
        return code == null ? "UNKNOWN" : normalizeCurrency(code);
    }

    private String balanceIssuer(AccountResponse.@NonNull Balance balance) {
        String assetType = trimToNull(balance.getAssetType());
        if ("native".equalsIgnoreCase(assetType)) {
            return null;
        }
        return trimToNull(balance.getAssetIssuer());
    }

    private double computeTotalBalanceEstimateUsdc() {
        double total = balances.getOrDefault("USDC", 0.0) + balances.getOrDefault("USD", 0.0);
        total += balances.getOrDefault("XLM", 0.0) * Math.max(safeTicker(defaultPair()).getMidPrice(), 0.0);

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
            log.info("Stellar {} market order accepted: {} {} {}", isPaperTrading() ? "paper" : "live", safeSide,
                    normalizedQuantity, pair);
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

            String orderId = submitOrFillOrder(pair, safeSide, normalizedQuantity, normalizePrice(pair, limitPrice),
                    false);
            log.info("Stellar {} limit order accepted: {} {} {} @ {}",
                    isPaperTrading() ? "paper" : "live", safeSide, normalizedQuantity, pair, limitPrice);
            return orderId;
        });
    }

    private String submitOrFillOrder(TradePair pair, Side side, double quantity, double limitPrice,
            boolean marketLike) {
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

        OpenOrder openOrder = new OpenOrder(orderId, pair, side, OpenOrder.OrderType.LIMIT, limitPrice,
                (int) Math.ceil(quantity));
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
     * <p>
     * For BUY base/quote: sell quote, buy base. Offer amount is quote notional.
     * For SELL base/quote: sell base, buy quote. Offer amount is base quantity.
     */
    private String submitLiveOffer(@NotNull TradePair pair, Side side, double quantity, double limitPrice,
            boolean marketLike) {
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

        validateLiveOfferPreflight(pair, side, quantity, limitPrice, amountSelling);

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

            Order order = buildOrder(txHash, pair, marketLike ? "MARKET_AS_AGGRESSIVE_LIMIT" : "LIMIT", limitPrice,
                    quantity, side);
            order.setStatus(Boolean.TRUE.equals(response.getSuccessful()) ? "SUBMITTED" : "UNKNOWN");
            orderHistory.put(txHash, order);

            fetchAccount();
            return txHash;
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Failed to submit Stellar live offer through Horizon Server SDK: "
                            + detailedStellarError(exception),
                    exception);
        }
    }

    private void validateLiveOfferPreflight(TradePair pair, Side side, double quantity, double limitPrice,
            BigDecimal amountSelling) {
        if (!Double.isFinite(limitPrice) || limitPrice <= 0) {
            throw new IllegalArgumentException("limitPrice must be positive");
        }
        if (amountSelling == null || amountSelling.signum() <= 0) {
            throw new IllegalArgumentException("Order amount is too small after Stellar precision normalization");
        }

        double minNotional = getMinOrderNotional(pair);
        double notional = quantity * limitPrice;
        if (Double.isFinite(minNotional) && minNotional > 0 && notional < minNotional) {
            throw new IllegalArgumentException(
                    "Order notional is below minimum %.8f for %s".formatted(minNotional, pair.toString('/')));
        }

        fetchAccount().join();
        String sellingCode = side == Side.SELL
                ? normalizeCurrency(pair.getBaseCode())
                : normalizeCurrency(pair.getCounterCode());

        double available = balances.getOrDefault(sellingCode, 0.0);
        double required = amountSelling.doubleValue();
        if (available + 0.0000001 < required) {
            throw new IllegalArgumentException(
                    "Insufficient %s balance: required %.8f, available %.8f"
                            .formatted(sellingCode, required, available));
        }
    }

    private String detailedStellarError(Throwable throwable) {
        if (throwable == null) {
            return "Unknown Stellar submission error";
        }

        LinkedHashSet<String> messages = new LinkedHashSet<>();
        Throwable current = throwable;
        while (current != null) {
            String message = trimToNull(current.getMessage());
            if (message != null) {
                messages.add(message);
            }
            current = current.getCause();
        }

        if (messages.isEmpty()) {
            return throwable.getClass().getSimpleName();
        }
        return String.join(" | ", messages);
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
        Optional<StellarPairIdentity> resolvedPair = resolvePairIdentity(tradePair);
        if (resolvedPair.isEmpty()) {
            throw new IllegalArgumentException("Unsupported Stellar pair: " + tradePair);
        }
        if (normalizeAmount(tradePair, quantity) < getMinOrderAmount(tradePair)) {
            throw new IllegalArgumentException("Order quantity is below Stellar precision minimum");
        }
        if (!isPaperTrading()) {
            StellarPairIdentity pair = resolvedPair.get();
            Optional<PairQuality> quality = evaluatePairWithInversion(pair);
            if (quality.isEmpty() || !quality.get().tradeable()) {
                throw new IllegalArgumentException("Stellar pair is not currently tradeable: "
                        + quality.map(PairQuality::reason).orElse("LIQUIDITY_UNAVAILABLE"));
            }
            if (!hasTrustline(pair.base()) || !hasTrustline(pair.quote())) {
                throw new IllegalArgumentException("Missing required Stellar trustline for " + pair.displaySymbol());
            }
        }
    }

    public CompletableFuture<org.investpro.trading.tradability.SymbolTradability> evaluateStellarTradability(
            TradePair tradePair,
            org.investpro.trading.tradability.TradabilityScope scope,
            boolean forceRefresh) {
        return supplyAsyncIo(() -> {
            Optional<StellarPairIdentity> resolvedPair = resolvePairIdentity(tradePair);
            if (resolvedPair.isEmpty()) {
                return defaultTradability(tradePair,
                        org.investpro.trading.tradability.TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE,
                        unresolvedReason(tradePair));
            }

            StellarPairIdentity pair = resolvedPair.get();
            if (forceRefresh && !isPaperTrading()) {
                fetchAccount().join();
            }

            Optional<PairQuality> quality = evaluatePairWithInversion(pair);
            PairQuality resolvedQuality = quality.orElse(new PairQuality(
                    pair,
                    false,
                    false,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    "LIQUIDITY_UNAVAILABLE"));

            return StellarTradabilityEvaluator.buildStatus(
                    this,
                    pair,
                    scope == null ? org.investpro.trading.tradability.TradabilityScope.LIVE_TRADING : scope,
                    resolvedQuality,
                    hasTrustline(pair.base()),
                    hasTrustline(pair.quote()),
                    resolvedQuality.reason());
        });
    }

    private String unresolvedReason(TradePair tradePair) {
        if (tradePair == null) {
            return "TRADE_PAIR_REQUIRED";
        }
        boolean baseResolved = trustedAssetRegistryForMode().resolve(tradePair.getBaseCode()).isPresent();
        boolean quoteResolved = trustedAssetRegistryForMode().resolve(tradePair.getCounterCode()).isPresent();
        if (!baseResolved) {
            return "UNRESOLVED_BASE_ISSUER";
        }
        if (!quoteResolved) {
            return "UNRESOLVED_QUOTE_ISSUER";
        }
        return "UNRESOLVED_STELLAR_PAIR";
    }

    public boolean requiresTrustline(StellarAssetIdentity asset) {
        return asset != null && !asset.isNative();
    }

    public boolean hasTrustline(StellarAssetIdentity asset) {
        if (asset == null) {
            return false;
        }
        if (!requiresTrustline(asset)) {
            return true;
        }
        if (isPaperTrading()) {
            return trustedAssetRegistryForMode().resolve(asset.code(), asset.issuer()).isPresent()
                    || balances.containsKey(asset.code());
        }
        return Objects.equals(trustedAssetIssuers.get(asset.code()), asset.issuer())
                || balances.containsKey(asset.code());
    }

    public boolean canReceiveAsset(StellarAssetIdentity asset) {
        return !requiresTrustline(asset) || hasTrustline(asset);
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
        return marketType == MARKET_TYPES.SPOT;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.SPOT);
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
        try {
            return getGoodTradingPairs().get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Unable to discover Stellar trading pairs, using safe defaults: {}", exception.getMessage());
            return defaultStellarPairs();
        }
    }

    private void refreshBalancesForMarketWatch() {
        try {
            if (isPaperTrading()) {
                if (balances.isEmpty()) {
                    initializePaperTradingAccount();
                }
                syncTrustedIssuerCache();
                return;
            }

            if (accountId == null || accountId.isBlank()) {
                return;
            }

            fetchAccount().join();
            syncTrustedIssuerCache();

        } catch (Exception exception) {
            log.debug("Unable to load Stellar balances/trustlines for Market Watch: {}", exception.getMessage());
        }
    }

    private void addPairIfMissing(@NotNull List<TradePair> pairs, TradePair pair) {
        boolean exists = pairs.stream()
                .anyMatch(existing -> Objects.equals(existing.toString('/'), pair.toString('/')));
        if (!exists) {
            pairs.add(pair);
        }
    }

    public CompletableFuture<List<TradePair>> getGoodTradingPairs() {
        long nowMs = System.currentTimeMillis();
        List<TradePair> cached = cachedDiscoveredPairs;
        addPairIfMissing(cached, defaultPair());
        if (!cached.isEmpty() && nowMs < cachedDiscoveredPairsExpiresAtMs) {
            return CompletableFuture.completedFuture(cached);
        }

        return discoverTradablePairs()
                .thenApply(discovered -> {
                    List<TradePair> resolved = discovered == null || discovered.isEmpty()
                            ? defaultStellarPairs()
                            : discovered;
                    if (!resolved.isEmpty()) {
                        synchronized (pairDiscoveryLock) {
                            cachedDiscoveredPairs = List.copyOf(resolved);
                            cachedDiscoveredPairsExpiresAtMs = System.currentTimeMillis() + PAIR_DISCOVERY_TTL_MS;
                        }
                    }
                    return resolved;
                })
                .exceptionally(exception -> {
                    log.warn("Unable to discover Stellar trading pairs, using safe defaults: {}",
                            exception.getMessage());
                    return defaultStellarPairs();
                });
    }

    public CompletableFuture<List<TradePair>> discoverTradablePairs() {
        return supplyAsyncIo(() -> {
            refreshBalancesForMarketWatch();

            List<StellarPairIdentity> candidates = buildTrustedPairCandidates();

            log.info("stellar.pair.discovery.started candidates={} balances={}",
                    candidates.size(),
                    balances.keySet());

            LinkedHashMap<String, TradePair> marketWatchPairs = new LinkedHashMap<>();

            /*
             * 1. Add balance-backed pairs first.
             *
             * Market Watch should show assets the user owns, even if liquidity is weak.
             * Trading permission is still controlled later by tradability/risk checks.
             */
            for (StellarPairIdentity candidate : candidates) {
                if (isBalanceCounterMarketWatchPair(candidate)) {
                    TradePair tradePair = candidate.toTradePair();
                    marketWatchPairs.putIfAbsent(tradePair.toString('/'), tradePair);
                }
            }

            /*
             * 2. Evaluate liquidity/tradability.
             *
             * Accepted pairs are better candidates for bots/trading,
             * but rejected balance-backed pairs can still remain visible in Market Watch.
             */
            List<PairQuality> accepted = new ArrayList<>();

            for (StellarPairIdentity candidate : candidates) {
                Optional<PairQuality> quality = evaluatePairWithInversion(candidate);

                if (quality.isPresent() && quality.get().tradeable()) {
                    PairQuality acceptedQuality = quality.get();
                    accepted.add(acceptedQuality);

                    log.info("stellar.pair.discovery.accepted pair={} inverted={} spread={} bidDepth={} askDepth={}",
                            candidate.displaySymbol(),
                            acceptedQuality.inverted(),
                            acceptedQuality.spreadPercent(),
                            acceptedQuality.bidDepth(),
                            acceptedQuality.askDepth());
                } else {
                    log.info("stellar.pair.discovery.rejected pair={} reason={}",
                            candidate.displaySymbol(),
                            quality.map(PairQuality::reason)
                                    .orElse("NO_DIRECT_ORDERBOOK;NO_INVERTED_ORDERBOOK"));
                }
            }

            /*
             * 3. Add accepted/liquid pairs next, sorted by quality.
             */
            accepted.stream()
                    .sorted(Comparator
                            .comparing((PairQuality quality) -> trustedQuoteRank(quality.pair().quote()))
                            .thenComparingDouble(PairQuality::spreadPercent)
                            .thenComparing(Comparator.comparingDouble(
                                    (PairQuality quality) -> quality.bidDepth() + quality.askDepth()).reversed())
                            .thenComparing(quality -> quality.pair().displaySymbol()))
                    .map(quality -> quality.pair().toTradePair())
                    .forEach(pair -> marketWatchPairs.putIfAbsent(pair.toString('/'), pair));

            /*
             * 4. Add trusted issuer pairs after liquid pairs.
             */
            for (StellarPairIdentity candidate : candidates) {
                if (isTrustedIssuerMarketWatchPair(candidate)) {
                    TradePair tradePair = candidate.toTradePair();
                    marketWatchPairs.putIfAbsent(tradePair.toString('/'), tradePair);
                }
            }

            /*
             * 5. Always keep safe defaults.
             */
            for (TradePair defaultPair : defaultStellarPairs()) {
                marketWatchPairs.putIfAbsent(defaultPair.toString('/'), defaultPair);
            }

            List<TradePair> result = List.copyOf(marketWatchPairs.values());

            log.info("stellar.pair.discovery.completed marketWatchPairs={} balances={}",
                    result.size(),
                    balances.keySet());

            return result;
        });
    }

    private boolean isTrustedIssuerMarketWatchPair(StellarPairIdentity pair) {
        if (pair == null) {
            return false;
        }
        return isTrustedOrNative(pair.base()) && isTrustedOrNative(pair.quote());
    }

    private boolean isTrustedOrNative(StellarAssetIdentity asset) {
        return asset != null && (asset.isNative() || asset.trusted());
    }

    private int trustedQuoteRank(StellarAssetIdentity quote) {
        if (quote == null) {
            return 100;
        }
        return switch (quote.code()) {
            case "USDC" -> 0;
            case "XLM" -> 1;
            case "EURC" -> 2;
            default -> quote.trusted() ? 10 : 50;
        };
    }

    private @NonNull @Unmodifiable List<StellarPairIdentity> buildTrustedPairCandidates() {
        StellarTrustedAssetRegistry registry = trustedAssetRegistryForMode();

        List<StellarAssetIdentity> assets = registry.allAssets();
        List<StellarAssetIdentity> balanceAssets = balanceBackedAssets(registry);

        List<StellarAssetIdentity> preferredQuotes = Stream.of( "BTC","USDC")
                .map(registry::resolve)
                .flatMap(Optional::stream)
                .toList();

        LinkedHashMap<String, StellarPairIdentity> candidates = new LinkedHashMap<>();

        for (StellarAssetIdentity balanceAsset : balanceAssets) {
            addBalanceCounterCandidates(candidates, balanceAsset, preferredQuotes);
        }

        for (StellarAssetIdentity balanceAsset : balanceAssets) {
            for (StellarAssetIdentity counterAsset : balanceAssets) {
                if (balanceAsset == null || counterAsset == null) {
                    continue;
                }

                if (balanceAsset.canonicalKey().equals(counterAsset.canonicalKey())) {
                    continue;
                }

                StellarPairIdentity pair = new StellarPairIdentity(balanceAsset, counterAsset);
                candidates.putIfAbsent(pair.canonicalKey(), pair);
            }
        }

        for (StellarAssetIdentity base : assets) {
            if (base == null) {
                continue;
            }

            for (StellarAssetIdentity quote : preferredQuotes) {
                if (quote == null || base.canonicalKey().equals(quote.canonicalKey())) {
                    continue;
                }

                StellarPairIdentity pair = new StellarPairIdentity(base, quote);
                candidates.putIfAbsent(pair.canonicalKey(), pair);
                addCandidateIfResolved(candidates, pair.quote().code(), pair.base().code());

            }
        }

        return List.copyOf(candidates.values());
    }

    private List<StellarAssetIdentity> balanceBackedAssets(StellarTrustedAssetRegistry registry) {
        LinkedHashMap<String, StellarAssetIdentity> result = new LinkedHashMap<>();
        for (String code : balances.keySet()) {
            if (code == null || code.isBlank()) {
                continue;
            }
            Optional<StellarAssetIdentity> asset = resolveBalanceAsset(registry, code);
            asset.ifPresent(identity -> result.putIfAbsent(identity.canonicalKey(), identity));
        }
        return List.copyOf(result.values());
    }

    private Optional<StellarAssetIdentity> resolveBalanceAsset(StellarTrustedAssetRegistry registry, String code) {
        String normalized = normalizeCurrency(code);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if ("XLM".equalsIgnoreCase(normalized)) {
            return registry.resolve("XLM");
        }

        String issuer = trimToNull(trustedAssetIssuers.get(normalized));
        if (issuer != null) {
            Optional<StellarAssetIdentity> registered = registry.resolve(normalized, issuer);
            if (registered.isPresent()) {
                return registered;
            }
            try {
                registry.addUserTrustlineAsset(normalized, issuer, "");
                return registry.resolve(normalized, issuer);
            } catch (Exception exception) {
                log.debug("Unable to register Stellar balance asset {}:{} for Market Watch: {}",
                        normalized, issuer, exception.getMessage());
                return Optional.of(new StellarAssetIdentity(
                        normalized,
                        issuer,
                        false,
                        "",
                        false,
                        true,
                        "account-balance"));
            }
        }
        return registry.resolve(normalized);
    }

    private void addBalanceCounterCandidates(
            Map<String, StellarPairIdentity> candidates,
            StellarAssetIdentity balanceAsset,
            List<StellarAssetIdentity> counters) {
        if (balanceAsset == null || counters == null) {
            return;
        }
        for (StellarAssetIdentity counter : counters) {
            if (counter == null || balanceAsset.canonicalKey().equals(counter.canonicalKey())) {
                continue;
            }
            StellarPairIdentity pair = new StellarPairIdentity(balanceAsset, counter);
            candidates.putIfAbsent(pair.canonicalKey(), pair);
        }
    }

    private boolean isBalanceCounterMarketWatchPair(StellarPairIdentity pair) {
        if (pair == null || pair.base() == null || pair.quote() == null) {
            return false;
        }
        String quoteCode = pair.quote().code();
        boolean preferredCounter = "XLM".equalsIgnoreCase(quoteCode) || "USDC".equalsIgnoreCase(quoteCode);
        if (!preferredCounter) {
            return false;
        }
        boolean balanceBackedBase = pair.base().isNative() || balances.containsKey(pair.base().code());
        boolean quoteUsable = pair.quote().isNative()
                || trustedAssetRegistryForMode().resolve(pair.quote().code(), pair.quote().issuer()).isPresent()
                || balances.containsKey(pair.quote().code());
        return balanceBackedBase && quoteUsable;
    }

    private void addCandidateIfResolved(Map<String, StellarPairIdentity> candidates, String baseCode,
            String quoteCode) {
        Optional<StellarPairIdentity> pair = resolvePairIdentity(baseCode, quoteCode);
        pair.ifPresent(identity -> candidates.putIfAbsent(identity.canonicalKey(), identity));
    }

    public Optional<StellarPairIdentity> resolvePairIdentity(TradePair tradePair) {
        if (tradePair == null) {
            return Optional.empty();
        }
        return resolvePairIdentity(tradePair.getBaseCode(), tradePair.getCounterCode());
    }

    public Optional<StellarPairIdentity> resolvePairIdentity(String baseCode, String quoteCode) {
        StellarTrustedAssetRegistry registry = trustedAssetRegistryForMode();
        Optional<StellarAssetIdentity> base = registry.resolve(baseCode);
        Optional<StellarAssetIdentity> quote = registry.resolve(quoteCode);
        if (base.isEmpty() || quote.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StellarPairIdentity(base.get(), quote.get()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<PairQuality> evaluatePairWithInversion(StellarPairIdentity pair) {
        Optional<PairQuality> direct = evaluateDirectOrderBook(pair, false);
        if (direct.isPresent() && direct.get().tradeable()) {
            return direct;
        }

        Optional<PairQuality> inverted = evaluateDirectOrderBook(pair.inverted(), true);
        if (inverted.isPresent() && inverted.get().tradeable()) {
            PairQuality invertedQuality = inverted.get();
            double displayBid = 1.0 / invertedQuality.bestAsk();
            double displayAsk = 1.0 / invertedQuality.bestBid();
            double spreadPercent = spreadPercent(displayBid, displayAsk);
            return Optional.of(new PairQuality(
                    pair,
                    isAcceptableLiquidity(displayBid, displayAsk, spreadPercent,
                            invertedQuality.bidDepth(), invertedQuality.askDepth()),
                    true,
                    displayBid,
                    displayAsk,
                    spreadPercent,
                    invertedQuality.askDepth(),
                    invertedQuality.bidDepth(),
                    "ORDERBOOK_INVERTED"));
        }

        return Optional.of(new PairQuality(pair, false, false, 0.0, 0.0, 0.0, 0.0, 0.0,
                direct.map(PairQuality::reason).orElse("NO_DIRECT_ORDERBOOK")
                        + ";"
                        + inverted.map(PairQuality::reason).orElse("NO_INVERTED_ORDERBOOK")));
    }

    private Optional<PairQuality> evaluateDirectOrderBook(StellarPairIdentity pair, boolean inverted) {
        Optional<OrderBook> orderBook = fetchRealOrderBookForDiscovery(pair);
        if (orderBook.isEmpty()) {
            return Optional.of(new PairQuality(pair, false, inverted, 0.0, 0.0, 0.0, 0.0, 0.0,
                    inverted ? "NO_INVERTED_ORDERBOOK" : "NO_DIRECT_ORDERBOOK"));
        }

        OrderBook book = orderBook.get();
        OrderBook.PriceLevel bestBid = book.getBestBid();
        OrderBook.PriceLevel bestAsk = book.getBestAsk();
        if (bestBid == null || bestAsk == null) {
            return Optional.of(new PairQuality(pair, false, inverted, 0.0, 0.0, 0.0, 0.0, 0.0,
                    "MISSING_BID_ASK"));
        }

        double bid = bestBid.getPrice();
        double ask = bestAsk.getPrice();
        double bidDepth = book.getTotalBidVolume();
        double askDepth = book.getTotalAskVolume();
        double spread = spreadPercent(bid, ask);
        boolean tradeable = isAcceptableLiquidity(bid, ask, spread, bidDepth, askDepth);
        String reason = tradeable
                ? "TRADABLE"
                : liquidityRejectionReason(bid, ask, spread, bidDepth, askDepth);
        return Optional.of(new PairQuality(pair, tradeable, inverted, bid, ask, spread, bidDepth, askDepth, reason));
    }

    private boolean isAcceptableLiquidity(double bid, double ask, double spreadPercent, double bidDepth,
            double askDepth) {
        return Double.isFinite(bid)
                && Double.isFinite(ask)
                && bid > 0.0
                && ask > 0.0
                && ask >= bid
                && Double.isFinite(spreadPercent)
                && spreadPercent <= STELLAR_DISCOVERY_MAX_SPREAD_PERCENT
                && bidDepth >= STELLAR_DISCOVERY_MIN_DEPTH
                && askDepth >= STELLAR_DISCOVERY_MIN_DEPTH;
    }

    private String liquidityRejectionReason(double bid, double ask, double spreadPercent, double bidDepth,
            double askDepth) {
        if (!Double.isFinite(bid) || !Double.isFinite(ask) || bid <= 0.0 || ask <= 0.0) {
            return "INVALID_PRICES";
        }
        if (ask < bid) {
            return "INVALID_SPREAD";
        }
        if (!Double.isFinite(spreadPercent) || spreadPercent > STELLAR_DISCOVERY_MAX_SPREAD_PERCENT) {
            return "SPREAD_TOO_WIDE";
        }
        if (bidDepth < STELLAR_DISCOVERY_MIN_DEPTH || askDepth < STELLAR_DISCOVERY_MIN_DEPTH) {
            return "INSUFFICIENT_DEPTH";
        }
        return "LIQUIDITY_UNAVAILABLE";
    }

    private double spreadPercent(double bid, double ask) {
        if (!Double.isFinite(bid) || !Double.isFinite(ask) || bid <= 0.0 || ask <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        double mid = (bid + ask) / 2.0;
        return mid <= 0.0 ? Double.POSITIVE_INFINITY : ((ask - bid) / mid) * 100.0;
    }

    public double getStellarBotMaxSpreadPercent() {
        return STELLAR_BOT_MAX_SPREAD_PERCENT;
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return resolvePairIdentity(tradePair).isPresent();
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

    public Optional<OrderBook> fetchRealOrderBookForDiscovery(StellarPairIdentity pair) {
        if (pair == null) {
            return Optional.empty();
        }

        try {
            var orderBookRequest = activeMarketDataServer().orderBook()
                    .sellingAsset(toStellarAsset(pair.base()))
                    .buyingAsset(toStellarAsset(pair.quote()));
            orderBookRequest.limit(DEFAULT_ORDER_BOOK_LIMIT);
            OrderBookResponse response = orderBookRequest.execute();

            List<OrderBook.PriceLevel> bids = parsePriceLevels(response == null ? null : response.getBids());
            List<OrderBook.PriceLevel> asks = parsePriceLevels(response == null ? null : response.getAsks());
            if (bids.isEmpty() || asks.isEmpty()) {
                return Optional.empty();
            }

            bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
            asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));

            OrderBook orderBook = new OrderBook(pair.toTradePair(), bids, asks);
            orderBook.setTimestamp(Instant.now());
            return Optional.of(orderBook);
        } catch (Exception exception) {
            log.debug("Strict Stellar order book fetch failed for {}: {}", pair.displaySymbol(),
                    exception.getMessage());
            return Optional.empty();
        }
    }

    private List<OrderBook.PriceLevel> parsePriceLevels(List<OrderBookResponse.Row> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        List<OrderBook.PriceLevel> levels = new ArrayList<>();
        for (OrderBookResponse.Row row : rows) {
            double price = parseDouble(row.getPrice(), 0.0);
            double amount = parseDouble(row.getAmount(), 0.0);
            if (price > 0.0 && amount > 0.0) {
                levels.add(new OrderBook.PriceLevel(price, amount));
            }
        }
        return levels;
    }

    private @NonNull OrderBook fetchStellarOrderBook(TradePair tradePair) {
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
                        new OrderBook.PriceLevel(price * 0.995, 10_000.0)),
                List.of(
                        new OrderBook.PriceLevel(price * 1.001, 5_000.0),
                        new OrderBook.PriceLevel(price * 1.005, 10_000.0)));
        orderBook.setTimestamp(Instant.now());
        return orderBook;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return supplyAsyncIo(() -> fetchRecentTrades(tradePair).stream()
                .filter(trade -> stopAt == null || !trade.getTimestamp().isAfter(stopAt))
                .toList());
    }

    private @NonNull List<Trade> fetchRecentTrades(TradePair tradePair) {
        TradePair pair = pairOrDefault(tradePair);
        int safeLimit = Math.min(StellarNetwork.MAX_RECENT_TRADES <= 0 ? 100 : StellarNetwork.MAX_RECENT_TRADES,
                MAX_RECENT_TRADES);

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
            String counter = "native".equalsIgnoreCase(response.getCounterAssetType()) ? "XLM"
                    : response.getCounterAssetCode();
            return new TradePair(base, counter);
        } catch (Exception exception) {
            return fallback;
        }
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new StellarCandleDataSupplier(1000, secondsPerCandle, pairOrDefault(tradePair));
    }

    public CandleDataSupplier getCandleDataSupplier(TradePair tradePair, Timeframe timeframe) {
        return getCandleDataSupplier(secondsFor(timeframe), tradePair);
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit) {
        CandleDataSupplier supplier = new StellarCandleDataSupplier(
                Math.max(1, limit),
                secondsFor(timeframe),
                pairOrDefault(tradePair));
        return supplier.getCandleData();
    }

    public List<CandleData> getCandles(TradePair tradePair, Timeframe timeframe, int limit, Long startTime,
            Long endTime) {
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
            case 2_419_200 -> "MN";
            default -> "N/A";
        };
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(Timeframe.M1, Timeframe.M5, Timeframe.M15, Timeframe.H1, Timeframe.D1, Timeframe.W1,
                Timeframe.MN);
    }

    private List<CandleData> buildCandlesFromTrades(
            TradePair tradePair,
            int secondsPerCandle,
            int limit,
            Long startTime,
            Long endTime) {
        int safeSeconds = Math.max(SECONDS_M1, secondsPerCandle);
        int safeLimit = clampCandleLimit(limit);

        long end = endTime == null ? Instant.now().getEpochSecond() : endTime;
        long start = startTime == null ? end - (long) safeLimit * safeSeconds : startTime;

        long resolution = aggregationResolutionMillis(safeSeconds);
        if (resolution <= 0) {
            throw new IllegalArgumentException("Unsupported Stellar candle resolution: " + safeSeconds);
        }

        return fetchAggregationCandles(
                pairOrDefault(tradePair),
                start,
                end,
                resolution,
                safeSeconds,
                safeLimit);
    }

    private int clampCandleLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_CANDLE_LIMIT));
    }

    private List<CandleData> fetchAggregationCandles(
            @NonNull TradePair pair,
            long requestedStart,
            long requestedEnd,
            long resolution,
            int secondsPerCandle,
            int limit) {
        int safeLimit = clampCandleLimit(limit);
        int safeSeconds = Math.max(SECONDS_M1, secondsPerCandle);

        long alignedEnd = alignToCandleBoundary(requestedEnd, safeSeconds);
        long window = candleWindowSeconds(requestedStart, requestedEnd, safeSeconds, safeLimit);

        Asset base = toStellarMarketDataAsset(pair.getBaseCode());
        Asset counter = toStellarMarketDataAsset(pair.getCounterCode());

        RuntimeException lastFailure = null;
        TreeMap<Integer, CandleData> accumulatedCandles = new TreeMap<>();

        for (int attempt = 0; attempt < 6; attempt++) {
            long end = alignedEnd - attempt * window;
            long start = Math.max(0, end - window);

            if (end <= 0 || start >= end) {
                break;
            }

            try {
                List<TradeAggregationResponse> records = fetchAggregationRecords(
                        base,
                        counter,
                        start,
                        end,
                        resolution);
                boolean inverted = false;

                if (records.isEmpty()) {
                    records = fetchAggregationRecords(
                            counter,
                            base,
                            start,
                            end,
                            resolution);
                    inverted = !records.isEmpty();
                }

                log.debug(
                        "stellar.candles.aggregations.records pair={} start={} end={} resolution={} records={} inverted={}",
                        pair,
                        start,
                        end,
                        resolution,
                        records.size(),
                        inverted);

                if (records.isEmpty()) {
                    continue;
                }

                List<CandleData> candles = denseCandlesFromAggregations(
                        records,
                        safeSeconds,
                        safeLimit,
                        inverted);

                for (CandleData candle : candles) {
                    accumulatedCandles.put(candle.openTime(), candle);
                }

                if (accumulatedCandles.size() >= safeLimit) {
                    return normalizeCandlePage(new ArrayList<>(accumulatedCandles.values()), safeLimit);
                }

            } catch (Exception exception) {
                lastFailure = new RuntimeException(
                        "Failed to fetch Stellar aggregation candles for "
                                + pair
                                + " from "
                                + start
                                + " to "
                                + end,
                        exception);

                log.debug(
                        "Stellar aggregation attempt {} failed for {}: {}",
                        attempt + 1,
                        pair,
                        exception.getMessage());
            }
        }

        if (lastFailure != null) {
            log.warn("No Stellar aggregation candles loaded after retries: {}", lastFailure.getMessage());
        }

        return normalizeCandlePage(new ArrayList<>(accumulatedCandles.values()), safeLimit);
    }

    private long alignToCandleBoundary(long epochSeconds, int secondsPerCandle) {
        return (epochSeconds / secondsPerCandle) * secondsPerCandle;
    }

    private long candleWindowSeconds(
            long requestedStart,
            long requestedEnd,
            int secondsPerCandle,
            int limit) {
        long requestedWindow = Math.max(
                requestedEnd - requestedStart,
                (long) limit * secondsPerCandle);

        long alignedWindow = alignToCandleBoundary(requestedWindow, secondsPerCandle);

        if (alignedWindow <= 0) {
            return (long) limit * secondsPerCandle;
        }

        return alignedWindow;
    }

    private List<TradeAggregationResponse> fetchAggregationRecords(
            Asset base,
            Asset counter,
            long startSeconds,
            long endSeconds,
            long resolution) {
        try {
            Page<TradeAggregationResponse> page = activeMarketDataServer()
                    .tradeAggregations(
                            base,
                            counter,
                            startSeconds * 1000L,
                            endSeconds * 1000L,
                            resolution,
                            0L)
                    .execute();

            if (page == null || page.getRecords() == null) {
                return List.of();
            }

            return page.getRecords()
                    .stream()
                    .filter(record -> record != null && record.getTimestamp() != null)
                    .sorted(Comparator.comparingLong(TradeAggregationResponse::getTimestamp))
                    .toList();

        } catch (Exception exception) {
            throw new IllegalStateException("Unable to fetch Stellar trade aggregations", exception);
        }
    }

    private List<CandleData> denseCandlesFromAggregations(
            List<TradeAggregationResponse> records,
            int secondsPerCandle,
            int limit,
            boolean inverted) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        int safeLimit = clampCandleLimit(limit);
        int safeSeconds = Math.max(SECONDS_M1, secondsPerCandle);

        TreeMap<Integer, CandleData> realCandlesByOpenTime = new TreeMap<>();

        for (TradeAggregationResponse record : records) {
            CandleData candle = inverted
                    ? invertedCandleFromAggregation(record)
                    : candleFromAggregation(record);

            if (!isValidCandle(candle)) {
                continue;
            }

            int openTime = (int) alignToCandleBoundary(candle.openTime(), safeSeconds);

            realCandlesByOpenTime.put(openTime, new CandleData(
                    candle.openPrice(),
                    candle.closePrice(),
                    candle.highPrice(),
                    candle.lowPrice(),
                    openTime,
                    candle.volume(),
                    candle.averagePrice(),
                    candle.volumeWeightedAveragePrice(),
                    false));
        }

        if (realCandlesByOpenTime.isEmpty()) {
            return List.of();
        }

        int firstOpen = realCandlesByOpenTime.firstKey();
        int lastOpen = realCandlesByOpenTime.lastKey();

        double previousClose = realCandlesByOpenTime.firstEntry().getValue().closePrice();
        List<CandleData> dense = new ArrayList<>(safeLimit);
        int placeholderCount = 0;

        for (int openTime = firstOpen; openTime <= lastOpen; openTime += safeSeconds) {
            CandleData real = realCandlesByOpenTime.get(openTime);

            if (real != null) {
                previousClose = real.closePrice();
                dense.add(real);
            } else if (previousClose > 0.0) {
                dense.add(placeholderCandle(previousClose, openTime));
                placeholderCount++;
            }
        }

        List<CandleData> normalized = normalizeCandlePage(dense, safeLimit);
        log.debug(
                "stellar.candles.dense.normalized inputRecords={} outputCandles={} placeholders={}",
                records.size(),
                normalized.size(),
                placeholderCount);
        return normalized;
    }

    @Contract("_, _ -> new")
    private @NonNull CandleData placeholderCandle(double price, long openTime) {
        return new CandleData(
                price,
                price,
                price,
                price,
                (int) openTime,
                0.0,
                price,
                price,
                true);
    }

    private boolean isValidCandle(CandleData candle) {
        return candle != null
                && candle.openTime() > 0
                && candle.openPrice() > 0.0
                && candle.highPrice() > 0.0
                && candle.lowPrice() > 0.0
                && candle.closePrice() > 0.0;
    }

    private List<CandleData> normalizeCandlePage(List<CandleData> candles, int limit) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }

        int safeLimit = clampCandleLimit(limit);

        TreeMap<Integer, CandleData> byOpenTime = new TreeMap<>();

        for (CandleData candle : candles) {
            if (!isValidCandle(candle)) {
                continue;
            }

            byOpenTime.put(candle.openTime(), candle);
        }

        if (byOpenTime.isEmpty()) {
            return List.of();
        }

        return byOpenTime.values()
                .stream()
                .skip(Math.max(0, byOpenTime.size() - safeLimit))
                .toList();
    }

    @Contract("_ -> new")
    private @NonNull CandleData candleFromAggregation(@NonNull TradeAggregationResponse record) {
        return new CandleData(
                parseDouble(record.getOpen(), 0.0),
                parseDouble(record.getClose(), 0.0),
                parseDouble(record.getHigh(), 0.0),
                parseDouble(record.getLow(), 0.0),
                (int) (record.getTimestamp() / 1000L),
                parseDouble(record.getBaseVolume(), 0.0));
    }

    private @NonNull CandleData invertedCandleFromAggregation(@NonNull TradeAggregationResponse record) {
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
            case 60, 300, 900, 3_600, 86_400, 604_800, 2_419_200 -> secondsPerCandle * 1000L;
            default -> -60;
        };
    }

    private int secondsFor(Timeframe timeframe) {
        if (timeframe == null) {
            return 60;
        }

        return switch (timeframe) {
            case M1 -> 60;
            case M5 -> 300;
            case M15 -> 900;
            case M30 -> (30*60);
            case H1 -> 3_600;
            case H6 -> (3600*6);
            case D1 -> 86_400;
            case W1 -> 604_800;
            case MN -> 2_419_200;
            default -> 1500;
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

        Optional<InProgressCandleData> direct = fetchInProgressAggregationCandle(pair, openSeconds, currentTill,
                resolution, false);
        if (direct.isPresent()) {
            return direct;
        }

        Optional<InProgressCandleData> inverse = fetchInProgressAggregationCandle(pair, openSeconds, currentTill,
                resolution, true);
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
0L,
//                    openSeconds * 1000L,
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
                    inverse ? "inverse " : "", pair, horizonUrl(), exception.getMessage());
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
            log.debug("Failed to build Stellar in-progress candle from ticker for {}: {}", pair,
                    exception.getMessage());
            return Optional.empty();
        }
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

        String issuer = trustedAssetRegistryForMode().resolve(normalized)
                .map(StellarAssetIdentity::issuer)
                .orElseGet(() -> resolveIssuer(normalized));
        if (issuer.isBlank()) {
            throw new IllegalArgumentException("Unknown Stellar issuer for asset: " + normalized);
        }

        return Asset.createNonNativeAsset(normalized, issuer);
    }

    private Asset toStellarAsset(StellarAssetIdentity asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Stellar asset identity is required");
        }
        if (asset.isNative()) {
            return Asset.createNativeAsset();
        }
        if (asset.issuer().isBlank()) {
            throw new IllegalArgumentException("Unknown Stellar issuer for asset: " + asset.code());
        }
        return Asset.createNonNativeAsset(asset.code(), asset.issuer());
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

        Optional<StellarAssetIdentity> registered = trustedAssetRegistryForMode().resolve(normalized);
        if (registered.isPresent() && !registered.get().issuer().isBlank()) {
            return registered.get().issuer();
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
                    .limit(DEFAULT_ASSETS_NUMBER)

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
            return isPaperTrading() ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER;
        }

        String trustedIssuer = trimToNull(trustedAssetIssuers.get(normalized));
        if (trustedIssuer != null && !isPaperTrading()) {
            return trustedIssuer;
        }

        try {
            Page<AssetResponse> page = activeMarketDataServer()
                    .assets()
                    .assetCode(normalized)
                    .execute();

            if (page == null || page.getRecords() == null || page.getRecords().isEmpty()) {
                log.debug("No Stellar market-data issuer found for asset code {}", normalized);
                return "";
            }
            // saving assets

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
            TradePair pair = TradePair.fromSymbol("XLM_USDC");
            pair.setNativeSymbol("XLM_USDC");
            return pair;
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Unable to create default Stellar trade pair", exception);
        }
    }



    private List<TradePair> defaultStellarPairs() {
        try {
            List<TradePair> defaults = new ArrayList<>();
            TradePair xlmUsdc = TradePair.fromSymbol("XLM_USDC");
            xlmUsdc.setNativeSymbol("XLM_USDC");
            defaults.add(xlmUsdc);

            TradePair usdcXlm = TradePair.fromSymbol("USDC_XLM");
            usdcXlm.setNativeSymbol("USDC_XLM");
            defaults.add(usdcXlm);

            if (trustedAssetRegistryForMode().resolve("EURC").isPresent()) {
                TradePair eurcUsdc = TradePair.fromSymbol("EURC_USDC");
                eurcUsdc.setNativeSymbol("EURC_USDC");
                defaults.add(eurcUsdc);
            }
            return defaults;
        } catch (SQLException | ClassNotFoundException exception) {
            return List.of(defaultPair());
        }
    }

    private TradePair pairOrDefault(TradePair pair) {
        return pair == null ? defaultPair() : pair;
    }

    private @NonNull TradePair parseSymbolOrDefault(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return defaultPair();
        }

        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('_', '/').replace('-', '/');
        String[] parts = normalized.contains("/") ? normalized.split("/") : new String[] { "XLM", normalized };
        try {
            TradePair pair = TradePair.fromSymbol(parts[0] + "/" + (parts.length > 1 ? parts[1] : "USDC"));
            pair.setNativeSymbol(symbol);
            return pair;
        } catch (SQLException | ClassNotFoundException exception) {
            return defaultPair();
        }
    }

    private @NonNull Order buildOrder(String orderId, TradePair pair, String type, double price, double quantity,
            Side side) {
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

    private @NonNull String normalizeCurrency(String code) {
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

    private @NonNull BigDecimal bd(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.DOWN);
    }

    private @NonNull String formatAmount(double value) {
        return BigDecimal.valueOf(value).setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private @Nullable String resolveAccountId(String configuredAccountId, String configuredApiKey) {
        String explicitAccountId = trimToNull(configuredAccountId);
        if (explicitAccountId != null) {
            return explicitAccountId;
        }

        String apiKeyValue = trimToNull(configuredApiKey);
        return isLikelyStellarAccountId(apiKeyValue) ? apiKeyValue : null;
    }

    private boolean isLikelyStellarAccountId(String value) {
        String candidate = trimToNull(value);
        return candidate != null && candidate.startsWith("G") && candidate.length() >= 32;
    }

    protected UnsupportedOperationException unsupported(String methodName) {
        return new UnsupportedOperationException("Stellar Network adapter does not support " + methodName);
    }

    protected static <T> @NonNull CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    private static final int MAX_CHART_CANDLES = 50000;

    private class StellarCandleDataSupplier extends CandleDataSupplier {

        public StellarCandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair) {
            super(numCandles, secondsPerCandle, tradePair,
                    new SimpleIntegerProperty((int) Instant.now().getEpochSecond()));
        }

        @Contract(" -> new")
        @Override
        public @NonNull Future<List<CandleData>> get() {
            return supplyAsyncIo(this::loadAggregatedCandlePage);
        }

        @Contract(value = " -> new", pure = true)
        @Override
        public @NonNull @Unmodifiable Set<Integer> getSupportedGranularities() {
            return Set.of(60, 300, 900, 3_600, 86_400, 604_800, 2_419_200);
        }

        @Override
        public @NonNull List<CandleData> getCandleData() {
            return loadAggregatedCandlePage();
        }

        private @NonNull List<CandleData> loadAggregatedCandlePage() {
            int safeNumCandles = Math.max(1, Math.min(numCandles, MAX_CHART_CANDLES));
            long end = Instant.now().getEpochSecond();

            log.debug(
                    "stellar.candles.latest.loading pair={} timeframe={} requested={} end={}",
                    tradePair,
                    secondsPerCandle,
                    safeNumCandles,
                    end);

            List<CandleData> candles = normalizeCandlePage(
                    buildCandlesFromTrades(
                            tradePair,
                            secondsPerCandle,
                            safeNumCandles,
                            null,
                            end),
                    safeNumCandles);

            log.debug(
                    "stellar.candles.latest.loaded pair={} timeframe={} count={} end={}",
                    tradePair,
                    secondsPerCandle,
                    candles.size(),
                    end);
            return candles;
        }

        @Override
        public @NonNull Future<List<CandleData>> getPrevious() {
            return supplyAsyncIo(this::loadPreviousAggregatedCandlePage);
        }

        @Override
        public @NonNull List<CandleData> getPreviousCandleData() {
            return loadPreviousAggregatedCandlePage();
        }

        private @NonNull List<CandleData> loadPreviousAggregatedCandlePage() {
            int safeNumCandles = Math.max(1, Math.min(numCandles, MAX_CHART_CANDLES));
            long oldEnd = endTime.get();

            log.debug(
                    "stellar.candles.previous.loading pair={} timeframe={} requested={} end={}",
                    tradePair,
                    secondsPerCandle,
                    safeNumCandles,
                    oldEnd);

            List<CandleData> candles = normalizeCandlePage(
                    buildCandlesFromTrades(
                            tradePair,
                            secondsPerCandle,
                            safeNumCandles,
                            null,
                            oldEnd),
                    safeNumCandles);

            long newEnd = oldEnd;
            if (!candles.isEmpty()) {
                int firstOpenTime = candles.stream()
                        .mapToInt(CandleData::openTime)
                        .min()
                        .orElse((int) oldEnd);

                newEnd = Math.max(0, firstOpenTime - 1);
                endTime.set((int) newEnd);
            }

            log.debug(
                    "stellar.candles.previous.loaded pair={} timeframe={} count={} oldEnd={} newEnd={}",
                    tradePair,
                    secondsPerCandle,
                    candles.size(),
                    oldEnd,
                    newEnd);

            return candles;
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return StellarNetwork.this.getCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public @NonNull CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
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
