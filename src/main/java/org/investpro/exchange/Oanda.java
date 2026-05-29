package org.investpro.exchange;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import org.investpro.service.AuthResult;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.oanda.OandaCandleDataSupplier;
import org.investpro.exchange.oanda.OandaTransactionClient;
import org.investpro.exchange.oanda.OandaTradingSessionFactory;
import org.investpro.exchange.oanda.OandaRateLimiter;
import org.investpro.exchange.websocket.OandaWebSocketClient;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.infrastructure.PollingExchangeStreamer;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.UnresolvedAddressException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
@EqualsAndHashCode(callSuper = true)

@Getter
@Setter

public class Oanda extends Exchange {

   private  static  final Logger logger=log;
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    protected final ExchangeStreamConsumer liveTradeConsumers = new UiExchangeStreamConsumer();

    private static final String OANDA_LIVE_API_URL = "https://api-fxtrade.oanda.com";
    private static final String OANDA_PRACTICE_API_URL = "https://api-fxpractice.oanda.com";
    private static final String OANDA_LIVE_STREAM_URL = "https://stream-fxtrade.oanda.com";
    private static final String OANDA_PRACTICE_STREAM_URL = "https://stream-fxpractice.oanda.com";
    private static final String ACCOUNTS_ROUTE = "/v3/accounts";
    private static final String ACCOUNT_ROUTE = "/v3/accounts/%s";
    private static final String ACCOUNT_SUMMARY_ROUTE = "/v3/accounts/%s/summary";
    private static final String ACCOUNT_INSTRUMENTS_ROUTE = "/v3/accounts/%s/instruments";
    private static final String ACCOUNT_CONFIGURATION_ROUTE = "/v3/accounts/%s/configuration";
    private static final String ACCOUNT_CHANGES_ROUTE = "/v3/accounts/%s/changes";
    private static final String ACCOUNT_ORDERS_ROUTE = "/v3/accounts/%s/orders";
    private static final String ACCOUNT_PENDING_ORDERS_ROUTE = "/v3/accounts/%s/pendingOrders";
    private static final String ACCOUNT_ORDER_ROUTE = "/v3/accounts/%s/orders/%s";
    private static final String ACCOUNT_ORDER_CANCEL_ROUTE = "/v3/accounts/%s/orders/%s/cancel";
    private static final String ACCOUNT_ORDER_CLIENT_EXTENSIONS_ROUTE =
            "/v3/accounts/%s/orders/%s/clientExtensions";
    private static final String ACCOUNT_POSITIONS_ROUTE = "/v3/accounts/%s/positions";
    private static final String ACCOUNT_OPEN_POSITIONS_ROUTE = "/v3/accounts/%s/openPositions";
    private static final String ACCOUNT_POSITION_ROUTE = "/v3/accounts/%s/positions/%s";
    private static final String ACCOUNT_POSITION_CLOSE_ROUTE = "/v3/accounts/%s/positions/%s/close";
    private static final String ACCOUNT_TRANSACTIONS_ROUTE = "/v3/accounts/%s/transactions";
    private static final long CONNECTIVITY_LOG_INTERVAL_MS = 60_000L;
    private static final long ACCOUNT_CONNECTIVITY_FAILURE_TTL_MS = 30_000L;

    private final HttpClient httpClient;
    private final PollingExchangeStreamer pollingStreamer;

    private OandaWebSocketClient websocketClient;

    private TradePair tradePair;
    private String apiKey;
    private String apiSecret;
    private String accountId;
    private boolean websocketAvailable;

    // Rate limiting and concurrency
    private final OandaRateLimiter rateLimiter = new OandaRateLimiter(1, Duration.ofMillis(250));
    private final ExecutorService oandaExecutor=Executors.newFixedThreadPool(2,new ThreadFactory(){private final AtomicInteger count=new AtomicInteger(0);

    @Override public Thread newThread(@NotNull Runnable r){Thread t=new Thread(r,"oanda-http-worker-"+count.incrementAndGet());t.setDaemon(false);return t;}});

    // Caching with TTL
    private static class CacheEntry<T> {
        T value;
        long expiresAt;

        CacheEntry(T value, long ttlMs) {
            this.value = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry<Map<String, Ticker>>> pricingCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Account>> accountCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<Position>>> positionsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<OpenOrder>>> openOrdersCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<List<Order>>> orderHistoryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> orderHistoryWarnAfterMs = new ConcurrentHashMap<>();

    // Request coalescing - reuse in-flight requests
    private CompletableFuture<Account> inflightAccountRequest = null;
    private CompletableFuture<List<Position>> inflightPositionsRequest = null;
    private CompletableFuture<List<OpenOrder>> inflightOpenOrdersRequest = null;

    // Diagnostics
    private final AtomicLong totalOandaRequests = new AtomicLong(0);
    private final AtomicLong oanda429Count = new AtomicLong(0);
    private final AtomicLong oandaRetryCount = new AtomicLong(0);
    private final AtomicLong cachePricingHits = new AtomicLong(0);
    private final AtomicLong cachePricingMisses = new AtomicLong(0);
    private final AtomicLong cacheAccountHits = new AtomicLong(0);
    private final AtomicLong cacheAccountMisses = new AtomicLong(0);
    private long last429TimeMs = 0L;
    private String last429Endpoint = "";
    private volatile long lastConnectivityLogMs = 0L;
    private volatile long accountConnectivityUnavailableUntilMs = 0L;

    private static final Random jitterRandom = new Random();

    @Override
    public String toString() {
        return "Oanda{" +
                "httpClient=" + (httpClient != null ? "HttpClient" : "null") +
                ", pollingStreamer=" + (pollingStreamer != null ? pollingStreamer.toString() : "null") +
                ", websocketClient=" + (websocketClient != null ? "WebSocketClient" : "null") +
                ", apiKey='" + (apiKey != null ? "***" : "null") + '\'' +
                ", apiSecret='" + (apiSecret != null ? "***" : "null") + '\'' +
                ", accountId='" + accountId + '\'' +
                ", websocketAvailable=" + websocketAvailable +
                '}';
    }

    public Oanda(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);

        this.apiKey = exchangeCredentials.apiKey();
        this.apiSecret = exchangeCredentials.apiSecret();

        /*
         * For this adapter:
         * - apiKey = OANDA token
         * - apiSecret = OANDA account id, if known
         *
         * If apiSecret is blank, the adapter will auto-detect account id from
         * /v3/accounts.
         */
        this.accountId = exchangeCredentials.accountId();

        initializePaperTradingAccount();

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        this.pollingStreamer = new PollingExchangeStreamer(this);

        try {
            this.websocketClient = new OandaWebSocketClient(
                    URI.create(oandaStreamUrl() + "/v3/accounts/%s/pricing/stream".formatted(this.accountId)),
                    new Draft_6455());
            this.websocketClient.addHeader("Authorization", "Bearer %s".formatted(this.apiKey));
            this.websocketAvailable = false;
        } catch (Exception exception) {
            log.warn("OANDA WebSocket-style client unavailable. Polling/HTTP streaming fallback will be used.",
                    exception);
            this.websocketAvailable = false;
        }
    }

    private void initializePaperTradingAccount() {
    }

    private String oandaApiUrl() {
        return isPaperTrading() ? OANDA_PRACTICE_API_URL : OANDA_LIVE_API_URL;
    }

    private String oandaStreamUrl() {
        return isPaperTrading() ? OANDA_PRACTICE_STREAM_URL : OANDA_LIVE_STREAM_URL;
    }

    private String oandaRoute(String routeTemplate, Object... args) {
        String route = args == null || args.length == 0
                ? routeTemplate
                : routeTemplate.formatted(args);
        return oandaApiUrl() + route;
    }

    private OandaTransactionClient transactionClient() {
        return new OandaTransactionClient(httpClient, OBJECT_MAPPER, oandaApiUrl(), oandaStreamUrl(), apiKey);
    }

    // ---------------------------------------------------------------------
    // Identity / metadata
    // ---------------------------------------------------------------------

    @Override
    public String getName() {
        return "OANDA";
    }

    @Override
    public String getSignal() {
        return "OANDA";
    }

    @Override
    public String getExchangeId() {
        return "oanda";
    }

    @Override
    public String getDisplayName() {
        return "OANDA";
    }

    @Override
    public boolean isSandbox() {
        return true;
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return isSandbox();
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        return marketType == MARKET_TYPES.FOREX
                || marketType == MARKET_TYPES.CFD
                || marketType == MARKET_TYPES.METAL
                || marketType == MARKET_TYPES.INDEX;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(
                MARKET_TYPES.FOREX);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder()
                .exchangeName("OANDA")
                .exchangeId("oanda")
                .displayName("OANDA Forex & CFD Trading")
                .apiBaseUrl(oandaApiUrl())
                .webSocketBaseUrl(oandaStreamUrl())
                .authenticationType("API_KEY")

                // Market coverage - OANDA specializes in forex and CFD
                .supportsCrypto(false)
                .supportsSpot(true)
                .supportsFutures(false)
                .supportsDerivatives(true)
                .supportsForex(true)
                .supportsStocks(false)
                .supportsOptions(false)
                .supportsIndices(false)

                // Trading support
                .supportsLiveTrading(true)
                .supportsPaperTradingMode(true)
                .supportsSandbox(true)
                .supportsMarketOrders(true)
                .supportsLimitOrders(true)
                .supportsStopOrders(true)
                .supportsStopLimitOrders(true)
                .supportsBracketOrders(false)
                .supportsStopLossTakeProfit(true)
                .supportsTrailingStopOrders(true)
                .supportsMarginTrading(true)
                .supportsLeverage(true)

                // Account / portfolio
                .supportsAccountInfo(true)
                .supportsBalances(true)
                .supportsPositions(true)
                .supportsAccountTrades(true)
                .supportsOpenOrders(true)
                .supportsOrderHistory(true)
                .supportsFills(true)

                // Market data
                .supportsTicker(true)
                .supportsTickers(true)
                .supportsOrderBook(false)
                .supportsFullOrderBook(false)
                .supportsDistributionBook(false)
                .supportsHistoricalCandles(true)
                .supportsRecentTrades(false)
                .supportsStreamingPrices(true)

                // Streaming
                .supportsWebSocket(true)
                .supportsNativeWebSocket(true)
                .supportsWebSocketStreaming(true)
                .supportsTickerStreaming(true)
                .supportsTradeStreaming(false)
                .supportsCandleStreaming(true)
                .supportsOrderBookStreaming(false)
                .supportsAccountStreaming(true)
                .supportsOrderStreaming(true)
                .supportsFillStreaming(true)
                .supportsPositionStreaming(true)
                .supportsBalanceStreaming(true)
                .supportsHttpStreaming(false)
                .supportsPollingFallback(true)

                // Infrastructure / limits
                .supportsRateLimitInfo(true)
                .requiresAuthenticationForTrading(true)
                .requiresAuthenticationForAccountInfo(true)
                .requiresAuthenticationForMarketData(false)

                // Notes
                .notes("""
                        OANDA Forex & CFD Trading capability profile.
                        Specializes in currency pairs (forex), indices, commodities, and CFD instruments.
                        Supports both live and sandbox (demo) accounts for learning and testing.
                        High leverage available (up to 50:1 depending on regulatory region).
                        Streaming prices and candles via REST API with polling support.
                        Account, order, position, and balance data require authenticated API access.
                        """)
                .build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        if (apiKey == null || apiKey.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("OANDA token is missing or empty")
                    .checkedAt(Instant.now())
                    .build();
        }

        if (accountId == null || accountId.isBlank()) {
            return AuthCheckResult.builder()
                    .exchangeName(getName())
                    .success(false)
                    .credentialIssue(true)
                    .message("OANDA account ID is missing or empty")
                    .checkedAt(Instant.now())
                    .build();
        }

        return AuthCheckResult.builder()
                .exchangeName(getName())
                .success(true)
                .httpStatus(200)
                .credentialSource("CONFIGURATION")
                .endpointTested("/v3/accounts")
                .message("OANDA API credentials validated")
                .checkedAt(Instant.now())
                .build();
    }

    @Override
    public CompletableFuture<String> placeMarketOrder(TradePair symbol, Side side, double quantity) {
        return createMarketOrder(symbol, side, quantity);
    }

    @Override
    public CompletableFuture<String> placeLimitOrder(TradePair symbol, Side side, double quantity, double limitPrice) {
        return createLimitOrder(symbol, side, quantity, limitPrice);
    }

    // ---------------------------------------------------------------------
    // Connection / lifecycle
    // ---------------------------------------------------------------------

    @Override
    public void connect() {
        if (apiKey.isBlank()) {
            log.warn("OANDA token is empty. Adapter cannot connect.");
            return;
        }

        resolveAccountId();

        if (accountId.isBlank()) {
            log.warn("OANDA account id could not be resolved.");
            return;
        }

        log.info("OANDA adapter connected using account {}", mask(accountId));
    }

    @Override
    public void disconnect() {
        stopAllStreams();

        try {
            if (websocketClient != null && websocketClient.isOpen()) {
                websocketClient.close();
            }
        } catch (Exception exception) {
            log.warn("Failed to close OANDA websocket client", exception);
        }
    }

    @Override
    public void reconnect() {
        disconnect();
        connect();
    }

    @Override
    public Boolean isConnected() {
        return !apiKey.isBlank() && !resolveAccountId().isBlank();
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
        return websocketAvailable
                && websocketClient != null
                && websocketClient.isOpen();
    }

    @Override
    public String getTimestamp() {
        return Instant.now().toString();
    }

    @Override
    public Instant now() {
        return Instant.now();
    }

    // ---------------------------------------------------------------------
    // Selected symbol
    // ---------------------------------------------------------------------

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        return tradePair == null ? getSelecTradePair() : tradePair;
    }

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        if (tradePair != null) {
            return tradePair;
        }

        tradePair = TradePair.fromSymbol("EUR_USD");
        tradePair.setNativeSymbol("EUR_USD");
        tradePair.setTradingSession(OandaTradingSessionFactory.forInstrument("EUR_USD"));
        return tradePair;
    }

    // ---------------------------------------------------------------------
    // Market discovery
    // ---------------------------------------------------------------------

    @Override
    public List<TradePair> getTradePairSymbol() {
        String resolvedAccountId = resolveAccountId();

        if (resolvedAccountId.isBlank()) {
            return Collections.emptyList();
        }

        String url = oandaRoute(ACCOUNT_INSTRUMENTS_ROUTE, resolvedAccountId);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        try {
            HttpResponse<String> response = send(request);

            if (!isSuccess(response)) {
                log.warn("OANDA instruments failed HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode instruments = root.path("instruments");

            if (!instruments.isArray()) {
                log.warn("OANDA instruments response had unexpected format: {}", response.body());
                return Collections.emptyList();
            }

            List<TradePair> pairs = new ArrayList<>();

            for (JsonNode instrument : instruments) {
                String name = instrument.path("name").asText("");

                if (name.isBlank() || !name.contains("_")) {
                    continue;
                }

                String[] parts = name.split("_", 2);
                if (parts.length != 2) {
                    continue;
                }

                try {
                    TradePair pair = TradePair.fromSymbol(name);
                    pair.setNativeSymbol(name);
                    pair.setTradingSession(OandaTradingSessionFactory.forInstrument(name));
                    pairs.add(pair);
                } catch (SQLException | ClassNotFoundException exception) {
                    log.debug("Skipping OANDA instrument {}", name, exception);
                }
            }

            return pairs;

        } catch (Exception exception) {
            if (isConnectivityException(exception)) {
                logConnectivityFailure("OANDA trade pair load", oandaRoute(ACCOUNT_INSTRUMENTS_ROUTE, resolvedAccountId), exception);
            } else {
                log.error("Failed to load OANDA trade pairs", exception);
            }
            return Collections.emptyList();
        }
    }

    @Override
    public List<TradePair> getTradablePairs() {
        return getTradePairSymbol();
    }

    @Override
    public CompletableFuture<List<SymbolTradability>> fetchTradabilityStatus(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }

        return CompletableFuture.supplyAsync(() -> {
            Set<String> accountInstruments = getTradePairSymbol().stream()
                    .map(this::toInstrument)
                    .collect(java.util.stream.Collectors.toSet());

            return pairs.stream()
                    .filter(Objects::nonNull)
                    .map(pair -> mapOandaTradability(pair, accountInstruments))
                    .toList();
        });
    }

    @Override
    public CompletableFuture<SymbolTradability> fetchTradabilityStatus(TradePair pair) {
        if (pair == null) {
            return CompletableFuture.completedFuture(defaultTradability(null, TradabilityStatus.UNKNOWN, "Trade pair is null"));
        }

        return CompletableFuture.supplyAsync(() -> {
            Set<String> accountInstruments = getTradePairSymbol().stream()
                    .map(this::toInstrument)
                    .collect(java.util.stream.Collectors.toSet());
            return mapOandaTradability(pair, accountInstruments);
        });
    }

    private SymbolTradability mapOandaTradability(TradePair pair, Set<String> accountInstruments) {
        String instrument = toInstrument(pair);
        boolean instrumentAllowed = accountInstruments.contains(instrument);

        if (!instrumentAllowed) {
            return new SymbolTradability(
                    getExchangeId(),
                    pair,
                    instrument,
                    TradabilityStatus.PERMISSION_DENIED,
                    true,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    true,
                    true,
                    false,
                    supportsLeverage(),
                    supportsLeverage(),
                    "Instrument is not enabled for this OANDA account",
                    Instant.now(),
                    Map.of("instrument", instrument));
        }

        TradabilityStatus status = TradabilityStatus.FULLY_TRADABLE;
        String reason = "OANDA instrument is tradable";

        if (pair.getTradingSessionStatus() != null && !pair.getTradingSessionStatus().isTradable()) {
            status = TradabilityStatus.MARKET_CLOSED;
            reason = "OANDA market session is currently closed";
        }

        Ticker ticker = null;
        try {
            ticker = fetchTicker(pair).get(3, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.debug("Could not fetch OANDA tradability ticker for {}", pair, exception);
        }

        if (status == TradabilityStatus.FULLY_TRADABLE) {
            boolean hasLiquidity = ticker != null
                    && Double.isFinite(ticker.getBidPrice())
                    && Double.isFinite(ticker.getAskPrice())
                    && ticker.getBidPrice() > 0.0
                    && ticker.getAskPrice() > 0.0;

            if (!hasLiquidity) {
                status = TradabilityStatus.LIQUIDITY_UNAVAILABLE;
                reason = "No OANDA pricing liquidity is currently available";
            }
        }

        boolean orderSubmissionAllowed = status == TradabilityStatus.FULLY_TRADABLE && canSubmitOrders();
        if (!canSubmitOrders() && status == TradabilityStatus.FULLY_TRADABLE) {
            status = TradabilityStatus.API_KEY_RESTRICTED;
            reason = "OANDA API/account cannot currently submit orders";
        }

        return new SymbolTradability(
                getExchangeId(),
                pair,
                instrument,
                status,
                true,
                true,
                true,
                true,
                orderSubmissionAllowed,
                orderSubmissionAllowed,
                orderSubmissionAllowed,
                true,
                true,
                true,
                false,
                supportsLeverage(),
                supportsLeverage(),
                reason,
                Instant.now(),
                Map.of(
                        "instrument", instrument,
                        "session", String.valueOf(pair.getTradingSessionStatus())));
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        if (tradePair == null) {
            return false;
        }

        String target = toInstrument(tradePair);

        return getTradePairSymbol().stream()
                .map(this::toInstrument)
                .anyMatch(target::equalsIgnoreCase);
    }

    // ---------------------------------------------------------------------
    // Candles / market data
    // ---------------------------------------------------------------------

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiKey, oandaApiUrl());
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

        int granularitySeconds = getCandleDataSupplier(secondsPerCandle, tradePair)
                .getSupportedGranularities()
                .stream()
                .min(Comparator
                        .comparingInt(value -> Math.abs(value - Math.max(5, (int) secondsIntoCurrentCandle / 100))))
                .orElseThrow(() -> new NoSuchElementException("Supported granularity was empty"));

        String granularity = toOandaGranularity(granularitySeconds);
        String from = currentCandleStartedAt.toString();
        String to = Instant.now().toString();

        String url = "%s/v3/instruments/%s/candles?from=%s&to=%s&granularity=%s&price=M"
                .formatted(oandaApiUrl(), toInstrument(tradePair), from, to, granularity);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        log.warn("OANDA in-progress candle failed HTTP {}: {}", response.statusCode(),
                                response.body());
                        return Optional.empty();
                    }

                    try {
                        JsonNode candles = OBJECT_MAPPER.readTree(response.body()).path("candles");

                        if (!candles.isArray() || candles.isEmpty()) {
                            return Optional.empty();
                        }

                        double open = -1;
                        double high = -1;
                        double low = Double.MAX_VALUE;
                        double close = -1;
                        double volume = 0;
                        int currentTill = (int) currentCandleStartedAt.getEpochSecond();

                        for (JsonNode candle : candles) {
                            JsonNode mid = candle.path("mid");

                            if (mid.isMissingNode()) {
                                continue;
                            }

                            if (open < 0) {
                                open = mid.path("o").asDouble(-1);
                            }

                            high = Math.max(high, mid.path("h").asDouble(high));
                            low = Math.min(low, mid.path("l").asDouble(low));
                            close = mid.path("c").asDouble(close);
                            volume += candle.path("volume").asDouble(0);

                            Instant candleTime = Instant
                                    .parse(candle.path("time").asText(currentCandleStartedAt.toString()));
                            currentTill = (int) candleTime.getEpochSecond();
                        }

                        if (open < 0 || high < 0 || low == Double.MAX_VALUE || close < 0) {
                            return Optional.empty();
                        }

                        int openTime = (int) currentCandleStartedAt.getEpochSecond();

                        return Optional.of(new InProgressCandleData(
                                openTime,
                                open,
                                high,
                                low,
                                currentTill,
                                close,
                                volume));

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        /*
         * OANDA v20 does not expose public tick-by-tick trade tape like crypto venues.
         * For live chart sync, use pricing stream/polling + candle polling instead.
         */
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        // Reject malformed symbols (XXX, ¤¤¤) before making HTTP requests
        String baseCode = tradePair.getBaseCode();
        String counterCode = tradePair.getCounterCode();
        
        if (baseCode.equals("XXX") || baseCode.equals("¤¤¤") || 
            counterCode.equals("XXX") || counterCode.equals("¤¤¤")) {
            log.debug("Skipping OANDA order book fetch for malformed symbol: {}", tradePair);
            return CompletableFuture.completedFuture(new OrderBook(tradePair));
        }

        // Attempt 1: Try orderBook endpoint
        String orderBookUrl = "%s/v3/instruments/%s/orderBook".formatted(oandaApiUrl(), tradePair.toString('_'));
        HttpRequest orderBookRequest = requestBuilder(orderBookUrl).GET().build();

        return httpClient.sendAsync(orderBookRequest, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (isSuccess(response)) {
                        log.debug("OANDA orderBook endpoint succeeded for {}", tradePair);
                        return CompletableFuture.completedFuture(parseOandaOrderBook(response.body(), tradePair));
                    }

                    log.debug("OANDA orderBook HTTP {}, attempting positionBook fallback", response.statusCode());

                    // Attempt 2: Try positionBook endpoint
                    String positionBookUrl = "%s/v3/instruments/%s/positionBook".formatted(oandaApiUrl(),
                            tradePair.toString('_'));
                    HttpRequest positionBookRequest = requestBuilder(positionBookUrl).GET().build();

                    return httpClient.sendAsync(positionBookRequest, HttpResponse.BodyHandlers.ofString())
                            .thenCompose(posResponse -> {
                                if (isSuccess(posResponse)) {
                                    log.debug("OANDA positionBook endpoint succeeded for {}", tradePair);
                                    return CompletableFuture
                                            .completedFuture(parseOandaPositionBook(posResponse.body(), tradePair));
                                }

                                log.debug("OANDA positionBook HTTP {}, attempting pricing fallback",
                                        posResponse.statusCode());

                                // Attempt 3: Fallback to pricing endpoint
                                return fetchSyntheticOrderBookFromPricing(tradePair);
                            });
                });
    }





    /**
     * Parse OANDA distribution order book response.
     *
     * <p>
     * OANDA's orderBook endpoint returns a distribution of orders by price level.
     */
    @Contract("_, _ -> new")
    private @NotNull OrderBook parseOandaOrderBook(String body, TradePair tradePair) {
        OrderBook orderBook = new OrderBook(tradePair);

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode orderBookNode = root.path("orderBook");
            JsonNode bucketsNode = orderBookNode.path("buckets");

            List<OrderBook.PriceLevel> bids = new ArrayList<>();
            List<OrderBook.PriceLevel> asks = new ArrayList<>();

            if (!bucketsNode.isArray()) {
                log.warn("OANDA orderBook response for {} does not contain buckets array", tradePair);
                return orderBook;
            }

            for (JsonNode bucket : bucketsNode) {
                if (bucket == null || !bucket.isObject()) {
                    continue;
                }

                double price = bucket.path("price").asDouble(0.0);

                /*
                 * OANDA orderBook bucket fields:
                 * - longCountPercent  = long-side order distribution at this price
                 * - shortCountPercent = short-side order distribution at this price
                 *
                 * They are not real bid/ask sizes like Binance/Coinbase order books.
                 * We map them into PriceLevel.size so DepthChart can still visualize them.
                 */
                double longCountPercent = bucket.path("longCountPercent").asDouble(0.0);
                double shortCountPercent = bucket.path("shortCountPercent").asDouble(0.0);

                if (price <= 0.0) {
                    continue;
                }

                if (longCountPercent > 0.0) {
                    bids.add(new OrderBook.PriceLevel(price, longCountPercent));
                }

                if (shortCountPercent > 0.0) {
                    asks.add(new OrderBook.PriceLevel(price, shortCountPercent));
                }
            }

            /*
             * For display:
             * - bids should show highest price first
             * - asks should show lowest price first
             */
            bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
            asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));

            orderBook.setBids(bids);
            orderBook.setAsks(asks);

            String time = orderBookNode.path("time").asText("");
            if (!time.isBlank()) {
                try {
                    orderBook.setTimestamp(Instant.parse(time));
                } catch (Exception parseTimeException) {
                    orderBook.setTimestamp(Instant.now());
                }
            } else {
                orderBook.setTimestamp(Instant.now());
            }

            log.debug(
                    "Parsed OANDA orderBook for {} with {} bid buckets and {} ask buckets",
                    tradePair,
                    bids.size(),
                    asks.size()
            );

            return orderBook;

        } catch (Exception exception) {
            log.warn(
                    "Failed to parse OANDA orderBook response for {}: {}",
                    tradePair,
                    exception.getMessage()
            );
            orderBook.setTimestamp(Instant.now());
            return orderBook;
        }
    }
    /**
     * Parse OANDA position book (distribution) response.
     *
     * <p>
     * OANDA's positionBook endpoint returns distribution of open positions by
     * price.
     */
    @Contract("_, _ -> new")
    private @NotNull OrderBook parseOandaPositionBook(String body, TradePair tradePair) {
        OrderBook positionBookDepth = new OrderBook(tradePair);

        List<OrderBook.PriceLevel> bids = new ArrayList<>();
        List<OrderBook.PriceLevel> asks = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode positionBookNode = root.path("positionBook");
            JsonNode bucketsNode = positionBookNode.path("buckets");

            if (!bucketsNode.isArray()) {
                log.warn("OANDA positionBook response for {} does not contain buckets array", tradePair);
                positionBookDepth.setTimestamp(parseInstantOrNow(positionBookNode.path("time").asText("")));
                positionBookDepth.setBids(bids);
                positionBookDepth.setAsks(asks);
                return positionBookDepth;
            }

            for (JsonNode bucket : bucketsNode) {
                if (bucket == null || !bucket.isObject()) {
                    continue;
                }

                double price = bucket.path("price").asDouble(0.0);
                if (!Double.isFinite(price) || price <= 0.0) {
                    continue;
                }

                /*
                 * OANDA positionBook bucket fields are distribution percentages,
                 * not executable order sizes.
                 *
                 * Use them as visual market concentration:
                 * - longCountPercent  => bullish/long concentration
                 * - shortCountPercent => bearish/short concentration
                 */
                double longCountPercent = bucket.path("longCountPercent").asDouble(0.0);
                double shortCountPercent = bucket.path("shortCountPercent").asDouble(0.0);

                if (Double.isFinite(longCountPercent) && longCountPercent > 0.0) {
                    bids.add(new OrderBook.PriceLevel(price, longCountPercent));
                }

                if (Double.isFinite(shortCountPercent) && shortCountPercent > 0.0) {
                    asks.add(new OrderBook.PriceLevel(price, shortCountPercent));
                }
            }

            bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
            asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));

            positionBookDepth.setBids(bids);
            positionBookDepth.setAsks(asks);
            positionBookDepth.setTimestamp(parseInstantOrNow(positionBookNode.path("time").asText("")));

            log.debug(
                    "Parsed OANDA positionBook for {} with {} long buckets and {} short buckets",
                    tradePair,
                    bids.size(),
                    asks.size()
            );

            return positionBookDepth;

        } catch (Exception exception) {
            log.warn(
                    "Failed to parse OANDA positionBook response for {}: {}",
                    tradePair,
                    exception.getMessage()
            );

            positionBookDepth.setBids(bids);
            positionBookDepth.setAsks(asks);
            positionBookDepth.setTimestamp(Instant.now());
            return positionBookDepth;
        }
    }

    /**
     * Create a synthetic top-of-book order book from OANDA pricing endpoint.
     *
     * <p>
     * OANDA does not provide full depth order books like Binance/Coinbase.
     * This fallback uses the pricing endpoint to create a synthetic top-of-book
     * snapshot.
     *
     * @return Synthetic OrderBook with only best bid/ask
     */
    private CompletableFuture<OrderBook> fetchSyntheticOrderBookFromPricing(TradePair tradePair) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            log.debug("No account ID for OANDA pricing fallback");
            return CompletableFuture.completedFuture(new OrderBook(tradePair));
        }

        String instrument = toInstrument(tradePair);
        String url = "%s/v3/accounts/%s/pricing?instruments=%s"
                .formatted(oandaApiUrl(), account, instrument);

        HttpRequest request = requestBuilder(url).GET().build();

        return sendWithExponentialBackoff(request)
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        log.debug(
                                "OANDA pricing fallback failed for {}. HTTP {}: {}",
                                tradePair,
                                response.statusCode(),
                                response.body()
                        );
                        return emptyOrderBook(tradePair);
                    }

                    try {
                        JsonNode root = OBJECT_MAPPER.readTree(response.body());
                        JsonNode prices = root.path("prices");

                        if (!prices.isArray() || prices.isEmpty()) {
                            log.debug("OANDA pricing response empty for {}", tradePair);
                            return emptyOrderBook(tradePair);
                        }

                        JsonNode priceNode = prices.get(0);

                        List<OrderBook.PriceLevel> bids = parseOandaPricingLevels(priceNode.path("bids"));
                        List<OrderBook.PriceLevel> asks = parseOandaPricingLevels(priceNode.path("asks"));

                        if (bids.isEmpty() || asks.isEmpty()) {
                            log.debug("OANDA pricing fallback has no valid bid/ask levels for {}", tradePair);
                            return emptyOrderBook(tradePair);
                        }

                        bids.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice).reversed());
                        asks.sort(Comparator.comparingDouble(OrderBook.PriceLevel::getPrice));

                        OrderBook orderBook = new OrderBook(tradePair);
                        orderBook.setBids(bids);
                        orderBook.setAsks(asks);

                        String time = priceNode.path("time").asText("");
                        orderBook.setTimestamp(parseInstantOrNow(time));

                        log.debug(
                                "OANDA synthetic order book from pricing succeeded for {}: bestBid={}, bestAsk={}, bidLevels={}, askLevels={}",
                                tradePair,
                                bids.get(0).getPrice(),
                                asks.get(0).getPrice(),
                                bids.size(),
                                asks.size()
                        );

                        return orderBook;

                    } catch (Exception exception) {
                        log.warn(
                                "Failed to parse OANDA pricing fallback for {}: {}",
                                tradePair,
                                exception.getMessage()
                        );
                        return emptyOrderBook(tradePair);
                    }
                })
                .exceptionally(exception -> {
                    if (isConnectivityException(exception)) {
                        logConnectivityFailure("OANDA pricing fallback", url, exception);
                    } else {
                        log.warn("OANDA pricing fallback failed for {}: {}", tradePair, rootCause(exception).getMessage());
                        log.debug("OANDA pricing fallback failure details for {}", tradePair, exception);
                    }
                    return emptyOrderBook(tradePair);
                });
    }


    private List<OrderBook.PriceLevel> parseOandaPricingLevels(JsonNode levelsNode) {
        List<OrderBook.PriceLevel> levels = new ArrayList<>();

        if (levelsNode == null || !levelsNode.isArray()) {
            return levels;
        }

        for (JsonNode levelNode : levelsNode) {
            if (levelNode == null || !levelNode.isObject()) {
                continue;
            }

            double price = levelNode.path("price").asDouble(0.0);

            /*
             * OANDA pricing levels usually expose liquidity.
             * This is not a real centralized exchange order-book size,
             * but it is better than zero for UI visualization.
             */
            double liquidity = levelNode.path("liquidity").asDouble(0.0);

            if (price <= 0.0) {
                continue;
            }

            if (liquidity <= 0.0) {
                liquidity = 1.0;
            }

            levels.add(new OrderBook.PriceLevel(price, liquidity));
        }

        return levels;
    }

    private OrderBook emptyOrderBook(TradePair tradePair) {
        OrderBook orderBook = new OrderBook(tradePair);
        orderBook.setTimestamp(Instant.now());
        return orderBook;
    }

    private Instant parseInstantOrNow(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return Instant.now();
        }
    }
    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        try {
            return fetchTicker(tradePair).join();
        } catch (Exception exception) {
            if (isConnectivityException(exception)) {
                logConnectivityFailure("OANDA live price fetch", toInstrument(tradePair), exception);
            } else {
                log.warn("OANDA live price fetch failed for {}: {}", tradePair, rootCause(exception).getMessage());
                log.debug("OANDA live price fetch failure details for {}", tradePair, exception);
            }
            return emptyTicker();
        }
    }

    @Override
    public double getLivePrice() {
        if (tradePair == null) {
            return 0.0;
        }

        Ticker ticker ;
        try {
            ticker = fetchTicker(tradePair).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();

        if (bid > 0 && ask > 0) {
            return (bid + ask) / 2.0;
        }

        return Math.max(bid, ask);
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        // Use batch pricing which includes caching
        return getLatestPrices(List.of(tradePair))
                .thenApply(priceMap -> priceMap.getOrDefault(toInstrument(tradePair), emptyTicker()));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        // Use batch pricing instead of individual requests
        return getLatestPrices(tradePairs)
                .thenApply(priceMap -> tradePairs.stream()
                        .filter(Objects::nonNull)
                        .map(pair -> {
                            String instrument = toInstrument(pair);
                            return priceMap.getOrDefault(instrument, emptyTicker());
                        })
                        .toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTickers((List<TradePair>) pair);
    }

    // ---------------------------------------------------------------------
    // Account / balances
    // ---------------------------------------------------------------------

    @Override
    public Account getUserAccountDetails() {
        String account = resolveAccountId();

        if (account.isBlank()) {
            log.warn("OANDA account id is blank — cannot fetch account details. "
                    + "Ensure accountId is provided in credentials.");
            return null;
        }

        // Check cache first
        String cacheKey = "account_" + account;
        Account cached = getAccountCached(cacheKey);
        if (cached != null) {
            return cached;
        }

        String url = oandaRoute(ACCOUNT_SUMMARY_ROUTE, account);

        if (isAccountConnectivityBackoffActive()) {
            log.warn("OANDA account details fetch skipped — connectivity backoff active until {}ms. "
                    + "Will retry automatically.", accountConnectivityUnavailableUntilMs);
            return null;
        }

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        try {
            HttpResponse<String> response = sendCritical(request);

            if (!isSuccess(response)) {
                log.warn("OANDA account summary failed HTTP {} at [{}]: {}",
                        response.statusCode(), url, truncate(response.body(), 300));

                // On 401: the token may belong to the alternate OANDA environment
                // (practice vs live). Automatically retry with the opposite base URL
                // so users who have practice credentials but select Live trading mode
                // are still connected, and a clear suggestion is logged.
                if (response.statusCode() == 401) {
                    return retryWithAlternateEnvironment(account, cacheKey);
                }
                return null;
            }

            JsonNode accountNode = OBJECT_MAPPER.readTree(response.body()).path("account");

            Account userAccount = new Account();
            userAccount.setExchange(this);
            userAccount.setEmail(accountNode.path("id").asText(account));
            userAccount.setUsername(accountNode.path("id").asText(account));
            userAccount.setAccountId(accountNode.path("id").asText(account));
            userAccount.setBrokerName("OANDA");
            userAccount.setExchangeId("oanda");

            // Parse financial metrics
            userAccount.setBaseCurrency(accountNode.path("currency").asText("USD"));
            userAccount.setTotalBalance(accountNode.path("balance").asDouble(0.0));
            userAccount.setAvailableBalance(accountNode.path("availableMarginClosingOnly").asDouble(0.0));
            userAccount.setEquity(accountNode.path("NAV").asDouble(0.0));
            userAccount.setNav(accountNode.path("NAV").asDouble(0.0));
            userAccount.setMarginUsed(accountNode.path("marginUsed").asDouble(0.0));
            userAccount.setFreeMargin(accountNode.path("marginAvailable").asDouble(0.0));
            userAccount.setMarginAvailable(accountNode.path("marginAvailable").asDouble(0.0));
            userAccount.setUnrealizedPnl(accountNode.path("unrealizedPL").asDouble(0.0));
            userAccount.setRealizedPnl(accountNode.path("realizedPL").asDouble(0.0));
            userAccount.setOpenPositionCount(accountNode.path("openPositionCount").asInt(0));
            userAccount.setOpenOrderCount(accountNode.path("openTradeCount").asInt(0));

            // Parse metadata
            if (accountNode.has("hedgingEnabled")) {
                userAccount.getMetadata().put("hedgingEnabled", accountNode.path("hedgingEnabled").asBoolean());
            }
            if (accountNode.has("marginRate")) {
                userAccount.getMetadata().put("marginRate", accountNode.path("marginRate").asText());
            }
            if (accountNode.has("positionAggregationMode")) {
                userAccount.getMetadata().put("positionAggregationMode",
                        accountNode.path("positionAggregationMode").asText());
            }

            // Parse timestamps
            String createdByUserAtStr = accountNode.path("createdByUserID").asText("");
            String lastTransactionIDStr = accountNode.path("lastTransactionID").asText("");
            if (!lastTransactionIDStr.isEmpty()) {
                userAccount.getMetadata().put("lastTransactionID", lastTransactionIDStr);
            }

            userAccount.setUpdatedAt(Instant.now());
            userAccount.setConnected(true);
            userAccount.setSandbox(accountNode.path("id").asText("").startsWith("101-")); // OANDA sandbox accounts
            userAccount.setCreatedBy(createdByUserAtStr);                                                                                          // start with 101-
            userAccount.setPaperTrading(userAccount.isSandbox());

            // Cache the account
            setAccountCached(cacheKey, userAccount);

            log.debug("OANDA account loaded: {} ({})", userAccount.getUsername(), userAccount.getAccountId());
            return userAccount;

        } catch (Exception exception) {
            if (isConnectivityException(exception)) {
                accountConnectivityUnavailableUntilMs = System.currentTimeMillis() + ACCOUNT_CONNECTIVITY_FAILURE_TTL_MS;
                logConnectivityFailure("OANDA account details", url, exception);
            } else {
                log.error("Failed to fetch OANDA account details", exception);
            }
            return null;
        }
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        synchronized (this) {
            if (inflightAccountRequest != null && !inflightAccountRequest.isDone()) {
                return inflightAccountRequest;
            }

            inflightAccountRequest = CompletableFuture
                    .supplyAsync(this::getUserAccountDetails, oandaExecutor)
                    .whenComplete((account, exception) -> {
                        synchronized (Oanda.this) {
                            inflightAccountRequest = null;
                        }
                    });
            return inflightAccountRequest;
        }
    }

    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        // Fetch from cached account summary instead of making separate call
        return fetchAccount().thenApply(account -> account != null ? account.getMarginAvailable() : 0.0);
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        // Fetch from cached account summary instead of making separate call
        return fetchAccount().thenApply(account -> account != null ? account.getTotalBalance() : 0.0);
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        // Fetch from cached account summary instead of making separate call
        return fetchAccount().thenApply(account -> account != null ? account.getEquity() : 0.0);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        // Fetch from cached account summary instead of making separate call
        return fetchAccount().thenApply(account -> account != null ? account.getMarginUsed() : 0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        // Fetch from cached account summary instead of making separate call
        return fetchAccount().thenApply(account -> account != null ? account.getFreeMargin() : 0.0);
    }


    // ---------------------------------------------------------------------
    // Orders
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<String> createOrder(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        return CompletableFuture.supplyAsync(() -> {
            try {
                String account = resolveAccountId();

                if (account.isBlank()) {
                    throw new IllegalStateException("OANDA account id is missing");
                }

                String url = oandaRoute(ACCOUNT_ORDERS_ROUTE, account);

                String instrument = safe(order.getSymbol()).replace("/", "_").replace("-", "_")
                        .toUpperCase(Locale.ROOT);
                if (instrument.isBlank()) {
                    instrument = tradePair == null ? "EUR_USD" : toInstrument(tradePair);
                }

                double normalizedUnits = normalizeAmount(tradePair, order.getQuantity());
                if (normalizedUnits <= 0) {
                    throw new IllegalArgumentException("OANDA order units must be greater than zero");
                }

                Side orderSide = order.getSide();
                String type = safe(order.getType()).toUpperCase(Locale.ROOT);
                if (orderSide == null) {
                    orderSide = "SELL".equals(type) ? Side.SELL : Side.BUY;
                }

                double signedUnits = orderSide == Side.SELL ? -normalizedUnits : normalizedUnits;

                Map<String, Object> payload = new LinkedHashMap<>();
                Map<String, Object> orderNode = new LinkedHashMap<>();

                orderNode.put("type", "MARKET");
                orderNode.put("instrument", instrument);
                orderNode.put("units", String.valueOf((long) signedUnits));
                orderNode.put("timeInForce", "FOK");
                orderNode.put("positionFill", "DEFAULT");

                if (order.getTakeProfit() > 0) {
                    orderNode.put("takeProfitOnFill", Map.of("price", String.valueOf(order.getTakeProfit())));
                }

                if (order.getStopLoss() > 0) {
                    orderNode.put("stopLossOnFill", Map.of("price", String.valueOf(order.getStopLoss())));
                }

                payload.put("order", orderNode);

                HttpRequest request = requestBuilder(url)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                        .build();

                HttpResponse<String> response = send(request);

                if (!isSuccess(response)) {
                    log.warn("OANDA create order failed HTTP {}: {}", response.statusCode(), response.body());
                    return "";
                }

                JsonNode root = OBJECT_MAPPER.readTree(response.body());

                String fillId = root.path("orderFillTransaction").path("id").asText("");
                String createId = root.path("orderCreateTransaction").path("id").asText("");

                return !fillId.isBlank() ? fillId : createId;

            } catch (Exception exception) {
                log.error("Failed to create OANDA order", exception);
                return "";
            }
        }, oandaExecutor);
    }

    @Override
    public Order createOrder(
            int id,
            TradePair tradePair,
            String type,
            double price,
            double amount,
            Side side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        Order order = new Order();
        order.setSymbol(tradePair == null ? "" : toInstrument(tradePair));
        order.setType(side == Side.SELL ? "SELL" : "BUY");
        order.setSide(side == null ? Side.BUY : side);
        order.setPrice(price);
        order.setQuantity(amount);
        order.setStopLoss(stopLoss);
        order.setTakeProfit(takeProfit);
        order.setStatus("PENDING");
        return order;
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        Order order = createOrder(0, tradePair, "MARKET", 0, amount, side, 0, 0, 0);
        return createOrder(order);
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        /*
         * You can later convert this to OANDA LIMIT order payload.
         * For now return unsupported explicitly instead of silently sending wrong order
         * type.
         */
        return failedFuture(new UnsupportedOperationException("OANDA limit order adapter is not implemented yet"));
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        return failedFuture(new UnsupportedOperationException("OANDA stop order adapter is not implemented yet"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
        Order order = createOrder(0, tradePair, "MARKET", entryPrice, amount, side, stopLoss, takeProfit, 0);
        return createOrder(order);
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        if (safe(orderId).isBlank()) {
            return failedFuture(new IllegalArgumentException("orderId must not be blank"));
        }

        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        String url = oandaRoute(ACCOUNT_ORDER_CANCEL_ROUTE, account, orderId.trim());

        HttpRequest request = requestBuilder(url)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> isSuccess(response) ? orderId : "");
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<CompletableFuture<String>> futures = orderIds.stream()
                .filter(id -> !id.isBlank())
                .map(this::cancelOrder)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
                        .filter(id -> !id.isBlank())
                        .toList());
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        return fetchAllOpenOrders()
                .thenCompose(orders -> {
                    /*
                     * OpenOrder model fields are unknown in this project snapshot.
                     * Keep this method safe for now.
                     */
                    return CompletableFuture.completedFuture("Cancel-all requires OpenOrder id mapping.");
                });
    }

    @Override
    public void cancelALL() {
        cancelAllOrders().thenAccept(result -> log.info("OANDA cancelALL result: {}", result));
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        if (safe(orderId).isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String url = oandaRoute(ACCOUNT_ORDER_ROUTE, account, orderId.trim());

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        return Optional.empty();
                    }

                    try {
                        JsonNode orderNode = OBJECT_MAPPER.readTree(response.body()).path("order");

                        if (orderNode.isMissingNode()) {
                            return Optional.empty();
                        }

                        Order order = new Order();
                        order.setSymbol(orderNode.path("instrument").asText(""));
                        order.setQuantity(orderNode.path("units").asDouble(0.0));
                        order.setPrice(orderNode.path("price").asDouble(0.0));
                        order.setType(orderNode.path("type").asText(""));
                        order.setStatus(orderNode.path("state").asText(""));

                        return Optional.of(order);

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        return fetchAllOpenOrders()
                .thenApply(orders -> orders.stream().toList());
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = oandaRoute(ACCOUNT_PENDING_ORDERS_ROUTE, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        log.warn("OANDA open orders failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    return parseOandaAllOpenOrders(response.body());
                });
    }

    private List<OpenOrder> parseOandaAllOpenOrders(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode orders = root.path("orders");
            return parseOpenOrders(orders);
        } catch (Exception e) {
            log.warn("Failed to parse OANDA all open orders: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parses OANDA open orders response into a list of OpenOrder objects.
     * Handles both array format and single object format.
     */
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

        // Optional fallback: some endpoints may return a single object
        if (rootNode.isObject()) {
            OpenOrder order = parseOpenOrder(rootNode);
            if (order != null) {
                openOrders.add(order);
            }
        }

        return openOrders;
    }

    /**
     * Parses a single OANDA open order from JsonNode.
     */
    private OpenOrder parseOpenOrder(JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            String instrument = node.path("instrument").asText("");
            double signedUnits = node.path("units").asDouble(0.0);
            double filledUnits = Math.abs(node.path("filledUnits").asDouble(0.0));
            double totalUnits = Math.abs(signedUnits);

            OpenOrder order = new OpenOrder();
            order.setOrderId(node.path("id").asText(""));
            order.setTradePair(instrumentToTradePair(instrument));
            order.setSide(signedUnits < 0 ? Side.SELL : Side.BUY);
            order.setOrderType(toOpenOrderType(node.path("type").asText("LIMIT")));
            order.setPrice(node.path("price").asDouble(0.0));
            order.setSize(totalUnits);
            order.setFilledSize(filledUnits);
            order.setRemainingSize(Math.max(0.0, totalUnits - filledUnits));
            order.setCreatedAt(parseInstant(node.path("createTime").asText("")));
            order.setUpdatedAt(parseInstant(node.path("time").asText(node.path("createTime").asText(""))));
            order.setStatus(toOpenOrderStatus(node.path("state").asText("PENDING")));
            order.setClientOrderId(node.path("clientExtensions").path("id").asText(""));
            order.setExchange("OANDA");

            return order;
        } catch (Exception e) {
            log.debug("Failed to parse OANDA open order node: {}", node, e);
            return null;
        }
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String cacheKey = account + "|" + (tradePair == null ? "ALL" : toInstrument(tradePair)) + "|"
                + (since == null ? "ALL" : since.toEpochMilli());
        List<Order> cached = getCached(cacheKey, orderHistoryCache);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        String instrumentQuery = tradePair == null ? "" : "&instrument=%s".formatted(toInstrument(tradePair));
        String url = "%s?state=ALL&count=100%s".formatted(oandaRoute(ACCOUNT_ORDERS_ROUTE, account),
                instrumentQuery);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return CompletableFuture.supplyAsync(() -> {
            totalOandaRequests.incrementAndGet();
            try {
                HttpResponse<String> response = sendWithExponentialBackoffSync(request, 2, 1_000, 8_000);

                if (response.statusCode() == 429) {
                    oanda429Count.incrementAndGet();
                    last429TimeMs = System.currentTimeMillis();
                    last429Endpoint = "order-history";
                }

                    if (!isSuccess(response)) {
                    List<Order> fallback = getCached(cacheKey + "|stale", orderHistoryCache);
                    logOrderHistoryFailure(cacheKey, response.statusCode(), response.body(), fallback != null);
                    return fallback == null ? Collections.emptyList() : fallback;
                    }

                JsonNode orders = OBJECT_MAPPER.readTree(response.body()).path("orders");
                if (!orders.isArray() || orders.isEmpty()) {
                    setCached(cacheKey, Collections.emptyList(), 30_000, orderHistoryCache);
                    setCached(cacheKey + "|stale", Collections.emptyList(), 300_000, orderHistoryCache);
                    return Collections.emptyList();
                }

                List<Order> result = new ArrayList<>();

                for (JsonNode node : orders) {
                    Instant createdAt = parseInstant(node.path("createTime").asText(""));

                    if (since != null && createdAt.isBefore(since)) {
                        continue;
                    }

                    double signedUnits = node.path("units").asDouble(0.0);
                    Order order = new Order();
                    order.setId(parseLong(node.path("id").asText("0")));
                    order.setSymbol(node.path("instrument").asText(""));
                    order.setQuantity(Math.abs(signedUnits));
                    order.setPrice(node.path("price").asDouble(0.0));
                    order.setType(node.path("type").asText(""));
                    order.setSide(signedUnits < 0 ? Side.SELL : Side.BUY);
                    order.setStatus(node.path("state").asText(""));
                    order.setDate(java.util.Date.from(createdAt));
                    result.add(order);
                }

                List<Order> immutable = List.copyOf(result);
                setCached(cacheKey, immutable, 30_000, orderHistoryCache);
                setCached(cacheKey + "|stale", immutable, 300_000, orderHistoryCache);
                return immutable;

            } catch (Exception exception) {
                List<Order> fallback = getCached(cacheKey + "|stale", orderHistoryCache);
                logOrderHistoryFailure(cacheKey, -1, exception.getMessage(), fallback != null);
                return fallback == null ? Collections.emptyList() : fallback;
            }
        }, oandaExecutor);
    }

    private void logOrderHistoryFailure(String cacheKey, int statusCode, String message, boolean usingCachedFallback) {
        long now = System.currentTimeMillis();
        long nextWarn = orderHistoryWarnAfterMs.getOrDefault(cacheKey, 0L);
        String fallback = usingCachedFallback ? " using cached fallback" : "";
        if (now >= nextWarn) {
            log.warn("OANDA order history unavailable{} HTTP {}: {}", fallback, statusCode, safe(message));
            orderHistoryWarnAfterMs.put(cacheKey, now + 120_000L);
        } else {
            log.debug("OANDA order history unavailable{} HTTP {}: {}", fallback, statusCode, safe(message));
        }
    }

    // ---------------------------------------------------------------------
    // Positions / trades
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return fetchAllPositions();
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return fetchOpenPositions();
    }

    /**
     * GET /v3/accounts/{accountID}/openPositions - List currently open positions.
     */
    public CompletableFuture<List<Position>> fetchOpenPositions() {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = oandaRoute(ACCOUNT_OPEN_POSITIONS_ROUTE, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        log.warn("OANDA positions failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    return parseOandaAllPositions(response.body());
                });
    }

    private List<Position> parseOandaAllPositions(String body) {
        try {
            JsonNode positions = OBJECT_MAPPER.readTree(body).path("positions");

            if (!positions.isArray() || positions.isEmpty()) {
                return Collections.emptyList();
            }

            List<Position> result = new ArrayList<>();

            for (JsonNode node : positions) {
                TradePair pair = instrumentToTradePair(node.path("instrument").asText(""));
                addPositionLeg(result, pair, node.path("instrument").asText(""), node.path("long"), Side.BUY);
                addPositionLeg(result, pair, node.path("instrument").asText(""), node.path("short"), Side.SELL);
            }

            return result;

        } catch (Exception exception) {
            logger.warn("Unable to parse OANDA positions", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return fetchPositions(tradePair)
                .thenApply(positions -> positions == null || positions.isEmpty()
                        ? Optional.empty()
                        : Optional.ofNullable(positions.get(0)));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        String url = oandaRoute(ACCOUNT_POSITION_CLOSE_ROUTE, account, toInstrument(tradePair));

        Map<String, Object> body = Map.of(
                "longUnits", "ALL",
                "shortUnits", "ALL");

        try {
            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? response.body() : "");

        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return fetchAllPositions()
                .thenApply(positions -> "Close-all positions requires Position -> TradePair mapping.");
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String instrumentQuery = tradePair == null ? "" : "&instrument=%s".formatted(toInstrument(tradePair));
        String url = "%s/v3/accounts/%s/trades?state=OPEN%s".formatted(oandaApiUrl(), account, instrumentQuery);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA account trades failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    try {
                        JsonNode trades = OBJECT_MAPPER.readTree(response.body()).path("trades");

                        if (!trades.isArray() || trades.isEmpty()) {
                            return Collections.emptyList();
                        }

                        List<Trade> result = new ArrayList<>();

                        for (JsonNode node : trades) {
                            double units = node.path("currentUnits").asDouble(node.path("initialUnits").asDouble(0.0));
                            TradePair pair = instrumentToTradePair(node.path("instrument").asText(""));
                            Trade trade = new Trade(
                                    pair,
                                    node.path("price").asDouble(0.0),
                                    Math.abs(units),
                                    units < 0 ? Side.SELL : Side.BUY,
                                    parseLong(node.path("id").asText("0")),
                                    parseInstant(node.path("openTime").asText("")));
                            trade.setFee(node.path("financing").asDouble(0.0));
                            result.add(trade);
                        }

                        return result;

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        if (since == null) {
            return failedFuture(new IllegalArgumentException("since must not be null"));
        }

        return fetchAccountTrades(tradePair)
                .thenApply(trades -> trades.stream()
                        .filter(trade -> trade != null && trade.getTimestamp() != null
                                && trade.getTimestamp().isAfter(since))
                        .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        if (from == null || to == null) {
            return failedFuture(new IllegalArgumentException("from and to must not be null"));
        }

        return fetchAccountTrades(tradePair)
                .thenApply(trades -> trades.stream()
                        .filter(trade -> trade != null && trade.getTimestamp() != null)
                        .filter(trade -> !trade.getTimestamp().isBefore(from) && !trade.getTimestamp().isAfter(to))
                        .toList());
    }

    // ---------------------------------------------------------------------
    // Manual trading / risk
    // ---------------------------------------------------------------------

    @Override
    public void buy(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        createBracketOrder(tradePair, Side.BUY, size, 0.0, stopLoss, takeProfit)
                .thenAccept(id -> logger.info("OANDA BUY submitted id={}", id));
    }

    @Override
    public void sell(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        createBracketOrder(tradePair, Side.SELL, size, 0.0, stopLoss, takeProfit)
                .thenAccept(id -> logger.info("OANDA SELL submitted id={}", id));
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        if (apiKey == null || apiKey.isBlank()) {
            return AuthResult.failure("OANDA API token is not configured");
        }
        if (accountId == null || accountId.isBlank()) {
            return AuthResult.failure("OANDA account ID is not configured");
        }
        return AuthResult.success("OANDA authentication validated");
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(
            TradePair tradePair,
            MARKET_TYPES marketType,
            double size,
            double side,
            double stopLoss,
            double takeProfit,
            double slippage) {
        boolean valid = tradePair != null
                && supportsMarketType(marketType)
                && size > 0
                && stopLoss >= 0
                && takeProfit >= 0
                && slippage >= 0;

        return CompletableFuture.completedFuture(valid);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        if (!Double.isFinite(amount)) {
            return 0.0;
        }

        return Math.max(getMinOrderAmount(tradePair), Math.round(Math.abs(amount)));
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        if (!Double.isFinite(price)) {
            return 0.0;
        }

        return Math.max(0.0, price);
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 0.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 50.0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(getMaxLeverage(tradePair));
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(new UnsupportedOperationException(
                "OANDA leverage is account/regulation controlled and not set per order here."));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(
                new UnsupportedOperationException("OANDA position modification requires order management."));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return failedFuture(
                new UnsupportedOperationException("OANDA partial position closure requires trade-level management."));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return failedFuture(
                new UnsupportedOperationException("OANDA position closure by ID requires trade-level query."));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(
                new UnsupportedOperationException("OANDA position modification requires order management."));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(new UnsupportedOperationException(
                "OANDA trailing stop must be configured through order types, not position modification."));
    }

    // ---------------------------------------------------------------------
    // Capabilities
    // ---------------------------------------------------------------------

    @Override
    public boolean supportsLiveTrading() {
        return true;
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
        return true;
    }

    @Override
    public boolean supportsAccountTrades() {
        return true;
    }

    @Override
    public boolean supportsStopLossTakeProfit() {
        return true;
    }

    @Override
    public boolean supportsBracketOrders() {
        return true;
    }

    @Override
    public boolean supportsLeverage() {
        return true;
    }

    @Override
    public boolean supportsDerivatives() {
        return true;
    }

    @Override
    public boolean supportsForex() {
        return true;
    }

    @Override
    public boolean supportsStocks() {
        return false;
    }

    @Override
    public boolean supportsCrypto() {
        return false;
    }

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

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
        return true;
    }

    @Override
    public boolean supportsPollingFallback() {
        return false;
    }

    @Override
    public void connectStream() {
        connect();
    }

    @Override
    public void disconnectStream() {
        stopAllStreams();
        disconnect();
    }

    @Override
    public boolean isStreamConnected() {
        return isConnected();
    }

    @Override
    public void reconnectStream() {
        reconnect();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        if (subscription == null || consumer == null) {
            return;
        }

        connectStream();

        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                streamTicker(pair, consumer);
            }

            if (subscription.isOrderBook()) {
                streamOrderBook(pair, consumer);
            }

            if (subscription.isTrades()) {
                streamTrades(pair, consumer);
            }

            if (subscription.isCandles()) {
                streamCandles(pair, 60, consumer);
            }
        }

        if (subscription.isAccount()) {
            streamAccount(consumer);
        }

        if (subscription.isBalances()) {
            streamBalances(consumer);
        }

        if (subscription.isOrders()) {
            streamOrders(consumer);
        }

        if (subscription.isPositions()) {
            streamPositions(consumer);
        }

        if (subscription.isFills()) {
            streamFills(consumer);
        }
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
        if (subscription == null) {
            return;
        }

        // Stop market data streams only if they were subscribed
        for (TradePair pair : subscription.getTradePairs()) {
            if (subscription.isTicker()) {
                stopTickerStream(pair);
            }
            if (subscription.isOrderBook()) {
                stopOrderBookStream(pair);
            }
            if (subscription.isTrades()) {
                stopTradesStream(pair);
            }
            if (subscription.isCandles()) {
                stopCandlesStream(pair, 60);
            }
        }

        // Stop account data streams only if they were subscribed
        if (subscription.isAccount()) {
            stopAccountStream();
        }
        if (subscription.isBalances()) {
            stopBalancesStream();
        }
        if (subscription.isOrders()) {
            stopOrdersStream();
        }
        if (subscription.isPositions()) {
            stopPositionsStream();
        }
        if (subscription.isFills()) {
            stopFillsStream();
        }

        stopAccountStream();
        stopBalancesStream();
        stopOrdersStream();
        stopFillsStream();
        stopPositionsStream();
    }

    @Override
    public void stopAllStreams() {
        pollingStreamer.stopAll();
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamTicker(tradePair, consumer);
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        /*
         * OANDA public market trade tape is not available like crypto exchanges.
         * Keep as no-op, not error, so "everything" subscriptions remain stable.
         */
        logger.debug("OANDA streamTrades is no-op for {}", tradePair);
    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrderBook(tradePair, consumer);
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        /*
         * Candle streaming can be added to PollingExchangeStreamer later.
         */
        logger.debug("OANDA streamCandles currently no-op for {} {}", tradePair, secondsPerCandle);
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamAccount(consumer);
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamAccount(consumer);
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrders(consumer);
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {
        logger.debug("OANDA streamFills currently no-op; transaction HTTP stream can be added later.");
    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {
        pollingStreamer.streamPositions(consumer);
    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        pollingStreamer.stopTicker(tradePair);
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {
        logger.debug("OANDA stopTradesStream no-op for {}", tradePair);
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        pollingStreamer.stopOrderBook(tradePair);
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        logger.debug("OANDA stopCandlesStream no-op for {} {}", tradePair, secondsPerCandle);
    }

    @Override
    public void stopAccountStream() {
        pollingStreamer.stopAccount();
    }

    @Override
    public void stopBalancesStream() {
        pollingStreamer.stopAccount();
    }

    @Override
    public void stopOrdersStream() {
        pollingStreamer.stopOrders();
    }

    @Override
    public void stopFillsStream() {
        logger.debug("OANDA stopFillsStream no-op");
    }

    @Override
    public void stopPositionsStream() {
        pollingStreamer.stopPositions();
    }

    @Override
    public boolean supportsTickerStreaming() {
        return true;
    }

    @Override
    public boolean supportsOrderBookStreaming() {
        return true;
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
    public String supportsTimeframe(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 5 -> "S5";
            case 10 -> "S10";
            case 15 -> "S15";
            case 30 -> "S30";

            case 120 -> "M2";
            case 240 -> "M4";
            case 300 -> "M5";
            case 600 -> "M10";
            case 900 -> "M15";
            case 1800 -> "M30";

            case 3600 -> "H1";
            case 7200 -> "H2";
            case 10800 -> "H3";
            case 14400 -> "H4";
            case 21600 -> "H6";
            case 28800 -> "H8";
            case 43200 -> "H12";

            case 86400 -> "D";
            case 604800 -> "W";
            case 2592000 -> "M";

            default -> "M1";
        };
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return List.of(
                Timeframe.M1,
                Timeframe.M5,
                Timeframe.M15,
                Timeframe.M30,
                Timeframe.H1,
                Timeframe.H4,
                Timeframe.D1,
                Timeframe.W1,
                Timeframe.MN);
    }

    @Override
    public boolean supportsAccountStreaming() {
        return true;
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return true;
    }

    @Override
    public boolean supportsOrderStreaming() {
        return true;
    }

    @Override
    public boolean supportsFillStreaming() {
        return false;
    }

    @Override
    public boolean supportsPositionStreaming() {
        return true;
    }

    // =====================================================================
    // ADDITIONAL OANDA API ENDPOINTS (v3)
    // =====================================================================

    // --------- ORDER ENDPOINTS ---------

    /**
     * GET /v3/accounts/{accountID}/orders - List all orders
     */
    @Override
    public CompletableFuture<List<Order>> fetchAllOrders() {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = oandaRoute(ACCOUNT_ORDERS_ROUTE, account);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA fetch all orders failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode orders = OBJECT_MAPPER.readTree(response.body()).path("orders");
                        return orders.isArray()
                                ? parseOrderList(orders)
                                : Collections.emptyList();
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA orders", e);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * GET /v3/accounts/{accountID}/pendingOrders - List pending orders
     */
    @Override
    public CompletableFuture<List<Order>> fetchPendingOrders() {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = oandaRoute(ACCOUNT_PENDING_ORDERS_ROUTE, account);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA fetch pending orders failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode orders = OBJECT_MAPPER.readTree(response.body()).path("orders");
                        return orders.isArray()
                                ? parseOrderList(orders)
                                : Collections.emptyList();
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA pending orders", e);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * PUT /v3/accounts/{accountID}/orders/{orderSpecifier} - Replace an order
     */
    @Override
    public CompletableFuture<String> replaceOrder(String orderId, Order newOrder) {
        Objects.requireNonNull(newOrder, "newOrder must not be null");
        if (orderId.isBlank()) {
            return failedFuture(new IllegalArgumentException("orderId must not be blank"));
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        try {
            String url = oandaRoute(ACCOUNT_ORDER_ROUTE, account, orderId.trim());
            Map<String, Object> payload = buildOrderPayload(newOrder);

            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? orderId : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    /**
     * PUT /v3/accounts/{accountID}/orders/{orderSpecifier}/clientExtensions -
     * Update order client extensions
     */
    public CompletableFuture<String> updateOrderClientExtensions(String orderId, Map<String, String> extensions) {
        if (safe(orderId).isBlank() || extensions == null) {
            return failedFuture(new IllegalArgumentException("orderId and extensions must not be null/blank"));
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        try {
            String url = oandaRoute(ACCOUNT_ORDER_CLIENT_EXTENSIONS_ROUTE, account, orderId.trim());
            Map<String, Object> payload = Map.of("clientExtensions", extensions);

            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? orderId : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    // --------- ACCOUNT ENDPOINTS ---------

    /**
     * GET /v3/accounts - List all accounts accessible to the API token
     */
    public CompletableFuture<List<Account>> listAccounts() {
        String url = oandaRoute(ACCOUNTS_ROUTE);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA list accounts failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode accounts = OBJECT_MAPPER.readTree(response.body()).path("accounts");
                        List<Account> result = new ArrayList<>();
                        if (accounts.isArray()) {
                            for (JsonNode acc : accounts) {
                                Account account = parseAccountNode(acc);
                                if (account != null)
                                    result.add(account);
                            }
                        }
                        return result;
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA accounts list", e);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * GET /v3/accounts/{accountID} - Get full account details (not summary)
     */
    public CompletableFuture<Account> getAccountDetails(String accountID) {
        if (safe(accountID).isBlank()) {
            accountID = resolveAccountId();
        }

        String finalAccountId = accountID;
        if (finalAccountId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String url = oandaRoute(ACCOUNT_ROUTE, finalAccountId);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get account details failed HTTP {}", response.statusCode());
                        return null;
                    }
                    try {
                        JsonNode accountNode = OBJECT_MAPPER.readTree(response.body()).path("account");
                        return parseAccountNode(accountNode);
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA account details", e);
                        return null;
                    }
                });
    }

    /**
     * PATCH /v3/accounts/{accountID}/configuration - Configure account settings
     */
    public CompletableFuture<String> configureAccount(String accountID, Map<String, Object> config) {
        if (safe(accountID).isBlank() || config == null) {
            return failedFuture(new IllegalArgumentException("accountID and config must not be null/blank"));
        }

        try {
            String url = oandaRoute(ACCOUNT_CONFIGURATION_ROUTE, accountID);
            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(config)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? "Configuration updated" : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    /**
     * GET /v3/accounts/{accountID}/changes - Get account changes since a
     * transaction ID
     */
    public CompletableFuture<JsonNode> getAccountChanges(String accountID, String sinceTransactionID) {
        if (safe(accountID).isBlank()) {
            accountID = resolveAccountId();
        }

        String finalAccountId = accountID;
        if (finalAccountId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String url = oandaRoute(ACCOUNT_CHANGES_ROUTE, finalAccountId);
        if (!safe(sinceTransactionID).isBlank()) {
            url += "?sinceTransactionID=" + sinceTransactionID;
        }

        String finalUrl = url;
        HttpRequest request = requestBuilder(finalUrl).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get account changes failed HTTP {}", response.statusCode());
                        return null;
                    }
                    try {
                        return OBJECT_MAPPER.readTree(response.body());
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA account changes", e);
                        return null;
                    }
                });
    }

    // --------- TRADE ENDPOINTS ---------

    /**
     * GET /v3/accounts/{accountID}/trades - Get all trades
     */
    public CompletableFuture<List<Trade>> getAllTrades() {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = "%s/v3/accounts/%s/trades".formatted(oandaApiUrl(), account);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get all trades failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode trades = OBJECT_MAPPER.readTree(response.body()).path("trades");
                        return parseTradeList(trades);
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA trades", e);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * GET /v3/accounts/{accountID}/trades/{tradeSpecifier} - Get trade details
     */
    public CompletableFuture<Optional<Trade>> getTrade(String tradeID) {
        if (safe(tradeID).isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String url = "%s/v3/accounts/%s/trades/%s".formatted(oandaApiUrl(), account, tradeID.trim());
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        return Optional.empty();
                    }
                    try {
                        JsonNode tradeNode = OBJECT_MAPPER.readTree(response.body()).path("trade");
                        return tradeNode.isMissingNode() ? Optional.empty() : Optional.of(parseTradeNode(tradeNode));
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA trade", e);
                        return Optional.empty();
                    }
                });
    }

    /**
     * PUT /v3/accounts/{accountID}/trades/{tradeSpecifier}/close - Close a trade
     */
    public CompletableFuture<String> closeTrade(String tradeID, double units) {
        if (safe(tradeID).isBlank()) {
            return failedFuture(new IllegalArgumentException("tradeID must not be blank"));
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        try {
            String url = "%s/v3/accounts/%s/trades/%s/close"
                    .formatted(oandaApiUrl(), account, tradeID.trim());
            Map<String, Object> payload = Map.of(
                    "units", units == 0 ? "ALL" : String.valueOf((long) units));

            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? tradeID : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    /**
     * PUT /v3/accounts/{accountID}/trades/{tradeSpecifier}/clientExtensions -
     * Update trade client extensions
     */
    public CompletableFuture<String> updateTradeClientExtensions(String tradeID, Map<String, String> extensions) {
        if (safe(tradeID).isBlank() || extensions == null) {
            return failedFuture(new IllegalArgumentException("tradeID and extensions must not be null/blank"));
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        try {
            String url = "%s/v3/accounts/%s/trades/%s/clientExtensions"
                    .formatted(oandaApiUrl(), account, tradeID.trim());
            Map<String, Object> payload = Map.of("clientExtensions", extensions);

            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(payload)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? tradeID : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    /**
     * PUT /v3/accounts/{accountID}/trades/{tradeSpecifier}/orders - Set dependent
     * orders on trade
     */
    public CompletableFuture<String> manageTradeOrders(String tradeID, double stopLoss, double takeProfit) {
        if (safe(tradeID).isBlank()) {
            return failedFuture(new IllegalArgumentException("tradeID must not be blank"));
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture("");
        }

        try {
            String url = "%s/v3/accounts/%s/trades/%s/orders"
                    .formatted(oandaApiUrl(), account, tradeID.trim());
            Map<String, Object> orders = new LinkedHashMap<>();

            if (stopLoss > 0) {
                orders.put("stopLossOnFill", Map.of("price", String.valueOf(stopLoss)));
            }
            if (takeProfit > 0) {
                orders.put("takeProfitOnFill", Map.of("price", String.valueOf(takeProfit)));
            }

            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(orders)))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> isSuccess(response) ? tradeID : "");
        } catch (Exception e) {
            return failedFuture(e);
        }
    }

    // --------- POSITION ENDPOINTS ---------

    /**
     * GET /v3/accounts/{accountID}/positions - Get all positions (not just open)
     */
    public CompletableFuture<List<Position>> getAllPositions() {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = oandaRoute(ACCOUNT_POSITIONS_ROUTE, account);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get all positions failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    return parseOandaAllPositions(response.body());
                });
    }

    /**
     * GET /v3/accounts/{accountID}/positions/{instrument} - Get position details
     * for instrument
     */
    public CompletableFuture<Optional<Position>> getPositionDetails(String instrument) {
        if (safe(instrument).isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        String url = oandaRoute(ACCOUNT_POSITION_ROUTE, account, instrument.trim().toUpperCase());
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        return Optional.empty();
                    }
                    try {
                        JsonNode posNode = OBJECT_MAPPER.readTree(response.body()).path("position");
                        if (posNode.isMissingNode()) {
                            return Optional.empty();
                        }
                        List<Position> positions = new ArrayList<>();
                        String inst = posNode.path("instrument").asText("");
                        addPositionLeg(positions, instrumentToTradePair(inst), inst, posNode.path("long"), Side.BUY);
                        addPositionLeg(positions, instrumentToTradePair(inst), inst, posNode.path("short"), Side.SELL);
                        return positions.isEmpty() ? Optional.empty() : Optional.of(positions.get(0));
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA position details", e);
                        return Optional.empty();
                    }
                });
    }

    // --------- TRANSACTION ENDPOINTS ---------

    /**
     * GET /v3/accounts/{accountID}/transactions - Get transaction history
     */
    
    public CompletableFuture<List<JsonNode>> getTransactions(int maxCount, String sinceTransactionID) {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = "%s?count=%d"
                .formatted(oandaRoute(ACCOUNT_TRANSACTIONS_ROUTE, account), Math.min(maxCount, 1000));
        if (!safe(sinceTransactionID).isBlank()) {
            url += "&sinceTransactionID=" + sinceTransactionID;
        }

        String finalUrl = url;
        HttpRequest request = requestBuilder(finalUrl).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get transactions failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode transactions = OBJECT_MAPPER.readTree(response.body()).path("transactions");
                        if (!transactions.isArray()) {
                            return Collections.emptyList();
                        }
                        List<JsonNode> result = new ArrayList<>();
                        for (JsonNode tx : transactions) {
                            result.add(tx);
                        }
                        return result;
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA transactions", e);
                        return Collections.emptyList();
                    }
                });
    }

    /**
     * GET /v3/accounts/{accountID}/transactions/{transactionID}
     */
    public CompletableFuture<JsonNode> getTransaction(String transactionID) {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return failedFuture(new IllegalStateException("OANDA account id is missing"));
        }
        return transactionClient().getTransaction(account, transactionID);
    }

    /**
     * GET /v3/accounts/{accountID}/transactions/idrange
     */
    public CompletableFuture<List<JsonNode>> getTransactionIdRange(String fromTransactionID, String toTransactionID) {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return transactionClient().getTransactionIdRange(account, fromTransactionID, toTransactionID);
    }

    /**
     * GET /v3/accounts/{accountID}/transactions/sinceid
     */
    public CompletableFuture<List<JsonNode>> getTransactionsSinceId(String sinceTransactionID) {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return transactionClient().getTransactionsSinceId(account, sinceTransactionID);
    }

    /**
     * GET /v3/accounts/{accountID}/transactions/stream
     *
     * <p>
     * This endpoint is served by OANDA's streaming base URL.
     */
    public CompletableFuture<Void> streamTransactions(
            Consumer<JsonNode> onMessage,
            Consumer<Throwable> onError) {
        String account = resolveAccountId();
        if (account.isBlank()) {
            return failedFuture(new IllegalStateException("OANDA account id is missing"));
        }
        return transactionClient().streamTransactions(account, onMessage, onError);
    }

    // --------- PRICING ENDPOINTS ---------

    /**
     * GET /v3/accounts/{accountID}/candles/latest - Get latest candles for multiple
     * instruments
     */
    public CompletableFuture<Map<String, JsonNode>> getLatestCandles(List<String> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyMap());
        }

        String instrumentsParam = String.join(",", instruments);
        String url = "%s/v3/accounts/%s/candles/latest?instruments=%s"
                .formatted(oandaApiUrl(), account, instrumentsParam);
        HttpRequest request = requestBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get latest candles failed HTTP {}", response.statusCode());
                        return Collections.emptyMap();
                    }
                    try {
                        JsonNode candles = OBJECT_MAPPER.readTree(response.body()).path("candles");
                        Map<String, JsonNode> result = new LinkedHashMap<>();
                        if (candles.isArray()) {
                            for (JsonNode candle : candles) {
                                String inst = candle.path("instrument").asText("");
                                if (!inst.isBlank()) {
                                    result.put(inst, candle);
                                }
                            }
                        }
                        return result;
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA latest candles", e);
                        return Collections.emptyMap();
                    }
                });
    }

    /**
     * GET /v3/accounts/{accountID}/instruments/{instrument}/candles - Get candles
     * for an instrument
     */
    public CompletableFuture<List<JsonNode>> getInstrumentCandles(String instrument, String granularity,
            String from, String to, int count) {
        if (safe(instrument).isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = "%s/v3/accounts/%s/instruments/%s/candles?price=MBA"
                .formatted(oandaApiUrl(), account, instrument.trim().toUpperCase());

        if (!safe(granularity).isBlank()) {
            url += "&granularity=" + granularity;
        }
        if (!safe(from).isBlank()) {
            url += "&from=" + from;
        }
        if (!safe(to).isBlank()) {
            url += "&to=" + to;
        }
        if (count > 0) {
            url += "&count=" + Math.min(count, 5000);
        }

        String finalUrl = url;
        HttpRequest request = requestBuilder(finalUrl).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA get instrument candles failed HTTP {}", response.statusCode());
                        return Collections.emptyList();
                    }
                    try {
                        JsonNode candles = OBJECT_MAPPER.readTree(response.body()).path("candles");
                        if (!candles.isArray()) {
                            return Collections.emptyList();
                        }
                        List<JsonNode> result = new ArrayList<>();
                        for (JsonNode candle : candles) {
                            result.add(candle);
                        }
                        return result;
                    } catch (Exception e) {
                        logger.error("Failed to parse OANDA instrument candles", e);
                        return Collections.emptyList();
                    }
                });
    }

    // --------- HELPER METHODS FOR PARSING ---------

    private List<Order> parseOrderList(JsonNode ordersNode) {
        List<Order> result = new ArrayList<>();
        if (!ordersNode.isArray()) {
            return result;
        }
        for (JsonNode node : ordersNode) {
            Order order = parseOrderNode(node);
            if (order != null) {
                result.add(order);
            }
        }
        return result;
    }

    private Order parseOrderNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        double signedUnits = node.path("units").asDouble(0.0);
        Order order = new Order();
        order.setId(parseLong(node.path("id").asText("0")));
        order.setSymbol(node.path("instrument").asText(""));
        order.setQuantity(Math.abs(signedUnits));
        order.setPrice(node.path("price").asDouble(0.0));
        order.setType(node.path("type").asText(""));
        order.setSide(signedUnits < 0 ? Side.SELL : Side.BUY);
        order.setStatus(node.path("state").asText(""));
        order.setDate(java.util.Date.from(parseInstant(node.path("createTime").asText(""))));
        return order;
    }

    private List<Trade> parseTradeList(JsonNode tradesNode) {
        List<Trade> result = new ArrayList<>();
        if (!tradesNode.isArray()) {
            return result;
        }
        for (JsonNode node : tradesNode) {
            Trade trade = parseTradeNode(node);
            if (trade != null) {
                result.add(trade);
            }
        }
        return result;
    }

    private Trade parseTradeNode(JsonNode node) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        double units = node.path("currentUnits").asDouble(node.path("initialUnits").asDouble(0.0));
        TradePair pair = instrumentToTradePair(node.path("instrument").asText(""));
        Trade trade = new Trade(
                pair,
                node.path("price").asDouble(0.0),
                Math.abs(units),
                units < 0 ? Side.SELL : Side.BUY,
                parseLong(node.path("id").asText("0")),
                parseInstant(node.path("openTime").asText("")));
        trade.setFee(node.path("financing").asDouble(0.0));
        return trade;
    }

    private Account parseAccountNode(JsonNode accountNode) {
        if (accountNode == null || accountNode.isMissingNode()) {
            return null;
        }
        String accountId = accountNode.path("id").asText("");
        Account account = new Account(this, accountNode.path("alias").asText("OANDA Account"), "");
        account.setEmail(accountNode.path("id").asText(accountId));
        account.setUsername(accountNode.path("id").asText(accountId));
        account.setAccountId(accountId);
        account.setBrokerName("OANDA");
        account.setExchangeId("oanda");
        account.setBaseCurrency(accountNode.path("currency").asText("USD"));
        account.setTotalBalance(accountNode.path("balance").asDouble(0.0));
        account.setEquity(accountNode.path("NAV").asDouble(0.0));
        account.setNav(accountNode.path("NAV").asDouble(0.0));
        account.setMarginUsed(accountNode.path("marginUsed").asDouble(0.0));
        account.setFreeMargin(accountNode.path("marginAvailable").asDouble(0.0));
        account.setUpdatedAt(Instant.now());
        account.setConnected(true);
        account.setSandbox(accountId.startsWith("101-"));
        account.setPaperTrading(account.isSandbox());
        return account;
    }

    private Map<String, Object> buildOrderPayload(Order order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> orderNode = new LinkedHashMap<>();

        orderNode.put("type", safe(order.getType()).isBlank() ? "MARKET" : order.getType());
        String instrument = safe(order.getSymbol()).replace("/", "_").toUpperCase();
        orderNode.put("instrument", instrument.isBlank() ? "EUR_USD" : instrument);

        double units = normalizeAmount(null, order.getQuantity());
        orderNode.put("units", String.valueOf((long) (order.getSide() == Side.SELL ? -units : units)));

        if (order.getPrice() > 0) {
            orderNode.put("price", String.valueOf(order.getPrice()));
        }

        payload.put("order", orderNode);
        return payload;
    }

    // =====================================================================
    // END ADDITIONAL OANDA API ENDPOINTS
    // =====================================================================

    // =====================================================================
    // CACHING AND DIAGNOSTICS
    // =====================================================================

    private <T> T getCached(String key, ConcurrentHashMap<String, CacheEntry<T>> cache) {
        CacheEntry<T> entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.value;
        }
        cache.remove(key);
        return null;
    }

    private <T> void setCached(String key, T value, long ttlMs, ConcurrentHashMap<String, CacheEntry<T>> cache) {
        cache.put(key, new CacheEntry<>(value, ttlMs));
    }

    public Map<String, Ticker> getPricingCached(String cacheKey) {
        Map<String, Ticker> cached = getCached(cacheKey, pricingCache);
        if (cached != null) {
            cachePricingHits.incrementAndGet();
        } else {
            cachePricingMisses.incrementAndGet();
        }
        return cached;
    }

    public void setPricingCached(String cacheKey, Map<String, Ticker> prices) {
        setCached(cacheKey, prices, 500, pricingCache); // 500ms TTL
    }

    public Account getAccountCached(String cacheKey) {
        Account cached = getCached(cacheKey, accountCache);
        if (cached != null) {
            cacheAccountHits.incrementAndGet();
        } else {
            cacheAccountMisses.incrementAndGet();
        }
        return cached;
    }

    public void setAccountCached(String cacheKey, Account account) {
        setCached(cacheKey, account, 3000, accountCache); // 3s TTL
    }




    public void logDiagnostics() {
        logger.info(
                "OANDA Diagnostics: totalRequests={}, http429Count={}, retryCount={}, pricingCacheHits={}, pricingCacheMisses={}, accountCacheHits={}, accountCacheMisses={}, last429Endpoint={}ms ago: {}",
                totalOandaRequests.get(),
                oanda429Count.get(),
                oandaRetryCount.get(),
                cachePricingHits.get(),
                cachePricingMisses.get(),
                cacheAccountHits.get(),
                cacheAccountMisses.get(),
                System.currentTimeMillis() - last429TimeMs,
                last429Endpoint.isEmpty() ? "none" : last429Endpoint);
    }

    // =====================================================================
    // END CACHING AND DIAGNOSTICS
    // =====================================================================

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * When a 401 is received from the primary OANDA environment, tries the alternate
     * environment (practice ↔ live). If the alternate succeeds, the adapter's
     * internal base URL switches accordingly so all subsequent calls work.
     */
    private Account retryWithAlternateEnvironment(String account, String cacheKey) {
        boolean currentlyLive = !isPaperTrading();
        String alternateBaseUrl = currentlyLive ? OANDA_PRACTICE_API_URL : OANDA_LIVE_API_URL;
        String alternateUrl = alternateBaseUrl + ACCOUNT_SUMMARY_ROUTE.formatted(account);

        log.warn("OANDA 401 on {} environment. Retrying with {} environment: {}",
                currentlyLive ? "LIVE" : "PRACTICE",
                currentlyLive ? "PRACTICE" : "LIVE",
                alternateUrl);

        HttpRequest retryRequest = requestBuilder(alternateUrl).GET().build();
        try {
            HttpResponse<String> retryResponse = sendCritical(retryRequest);
            if (!isSuccess(retryResponse)) {
                log.error("OANDA account auth failed on both environments (HTTP {}). "
                        + "Check that your API token is valid and matches the {} environment. "
                        + "Live API: api-fxtrade.oanda.com  |  Practice API: api-fxpractice.oanda.com",
                        retryResponse.statusCode(),
                        currentlyLive ? "LIVE" : "PRACTICE");
                return null;
            }

            // Success on alternate environment — adapt internal mode and notify
            String suggestedMode = currentlyLive ? "PAPER" : "LIVE";
            log.warn("OANDA credentials validated against {} environment. "
                    + "Consider changing 'Trading Mode' to '{}' in the connection dialog "
                    + "to avoid this auto-detection on every connection.",
                    currentlyLive ? "PRACTICE" : "LIVE", suggestedMode);

            // Override the trading mode so all subsequent calls use the correct URL
            setUserSelectedTradingMode(suggestedMode);

            return parseAndCacheAccount(retryResponse, account, cacheKey);

        } catch (Exception ex) {
            log.error("OANDA alternate-environment retry failed", ex);
            return null;
        }
    }

    /**
     * Parses an OANDA /summary response body into an {@link Account} and caches it.
     */
    private Account parseAndCacheAccount(HttpResponse<String> response, String account, String cacheKey) {
        try {
            JsonNode accountNode = OBJECT_MAPPER.readTree(response.body()).path("account");
            Account userAccount = new Account();
            userAccount.setExchange(this);
            userAccount.setEmail(accountNode.path("id").asText(account));
            userAccount.setUsername(accountNode.path("id").asText(account));
            userAccount.setAccountId(accountNode.path("id").asText(account));
            userAccount.setBrokerName("OANDA");
            userAccount.setExchangeId("oanda");
            userAccount.setBaseCurrency(accountNode.path("currency").asText("USD"));
            userAccount.setTotalBalance(accountNode.path("balance").asDouble(0.0));
            userAccount.setAvailableBalance(accountNode.path("availableMarginClosingOnly").asDouble(0.0));
            userAccount.setEquity(accountNode.path("NAV").asDouble(0.0));
            userAccount.setNav(accountNode.path("NAV").asDouble(0.0));
            userAccount.setMarginUsed(accountNode.path("marginUsed").asDouble(0.0));
            userAccount.setFreeMargin(accountNode.path("marginAvailable").asDouble(0.0));
            userAccount.setMarginAvailable(accountNode.path("marginAvailable").asDouble(0.0));
            userAccount.setUnrealizedPnl(accountNode.path("unrealizedPL").asDouble(0.0));
            userAccount.setRealizedPnl(accountNode.path("realizedPL").asDouble(0.0));
            userAccount.setOpenPositionCount(accountNode.path("openPositionCount").asInt(0));
            userAccount.setOpenOrderCount(accountNode.path("openTradeCount").asInt(0));
            if (accountNode.has("hedgingEnabled")) {
                userAccount.getMetadata().put("hedgingEnabled", accountNode.path("hedgingEnabled").asBoolean());
            }
            if (accountNode.has("marginRate")) {
                userAccount.getMetadata().put("marginRate", accountNode.path("marginRate").asText());
            }
            if (accountNode.has("positionAggregationMode")) {
                userAccount.getMetadata().put("positionAggregationMode",
                        accountNode.path("positionAggregationMode").asText());
            }
            String lastTransactionIDStr = accountNode.path("lastTransactionID").asText("");
            if (!lastTransactionIDStr.isEmpty()) {
                userAccount.getMetadata().put("lastTransactionID", lastTransactionIDStr);
            }
            userAccount.setUpdatedAt(Instant.now());
            userAccount.setConnected(true);
            String resolvedId = accountNode.path("id").asText("");
            userAccount.setSandbox(resolvedId.startsWith("101-"));
            userAccount.setPaperTrading(userAccount.isSandbox());
            setAccountCached(cacheKey, userAccount);
            log.info("OANDA account loaded via alternate-environment fallback: {} ({})",
                    userAccount.getUsername(), userAccount.getAccountId());
            return userAccount;
        } catch (Exception ex) {
            log.error("Failed to parse OANDA account from alternate-environment response", ex);
            return null;
        }
    }

    /** Truncates a string to at most {@code maxLen} chars for safe log output. */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "…";
    }

    private String resolveAccountId() {
        if (!safe(accountId).isBlank()) {
            return accountId;
        }

        if (apiKey.isBlank()) {
            return "";
        }

        String url = oandaRoute(ACCOUNTS_ROUTE);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        try {
            HttpResponse<String> response = send(request);

            if (!isSuccess(response)) {
                logger.warn("OANDA account discovery failed HTTP {}: {}", response.statusCode(), response.body());
                return "";
            }

            JsonNode accounts = OBJECT_MAPPER.readTree(response.body()).path("accounts");

            if (accounts.isArray() && !accounts.isEmpty()) {
                accountId = accounts.get(0).path("id").asText("");
                return accountId;
            }

            return "";

        } catch (Exception exception) {
            if (isConnectivityException(exception)) {
                logConnectivityFailure("OANDA account discovery", url, exception);
            } else {
                logger.warn("OANDA account discovery failed", exception);
            }
            return "";
        }
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer %s".formatted(apiKey))
                .header("Accept-Datetime-Format", "RFC3339")
                .header("User-Agent", "InvestPro/1.0");
    }

    private boolean isAccountConnectivityBackoffActive() {
        return System.currentTimeMillis() < accountConnectivityUnavailableUntilMs;
    }

    static boolean isConnectivityException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof UnresolvedAddressException
                    || current instanceof NoRouteToHostException
                    || current instanceof SocketTimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logConnectivityFailure(String operation, String url, Throwable throwable) {
        long now = System.currentTimeMillis();
        Throwable root = rootCause(throwable);
        String message = root.getMessage() == null || root.getMessage().isBlank()
                ? root.getClass().getSimpleName()
                : root.getMessage();

        if (now - lastConnectivityLogMs >= CONNECTIVITY_LOG_INTERVAL_MS) {
            lastConnectivityLogMs = now;
            log.warn(
                    "{} unavailable at {}: {}. Suppressing repeated connectivity stack traces for {} seconds.",
                    operation,
                    extractEndpoint(url),
                    message,
                    CONNECTIVITY_LOG_INTERVAL_MS / 1000);
            log.debug("{} connectivity failure details", operation, throwable);
            return;
        }

        log.debug("{} unavailable at {}: {}", operation, extractEndpoint(url), message);
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        Throwable root = throwable;
        while (current != null) {
            root = current;
            current = current.getCause();
        }
        return root == null ? new RuntimeException("unknown") : root;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        totalOandaRequests.incrementAndGet();
        try {
            return rateLimiter.execute(() -> sendWithExponentialBackoffSync(request, 3, 1000L, 30000L));
        } catch (Exception e) {
            if (e instanceof IOException || e instanceof InterruptedException) {
                throw (IOException) (e instanceof IOException ? e : new IOException(e));
            }
            throw new IOException(e);
        }
    }

    private HttpResponse<String> sendCritical(HttpRequest request) throws IOException {
        totalOandaRequests.incrementAndGet();
        try {
            return rateLimiter.execute(() -> sendWithExponentialBackoffSync(request, 5, 1000L, 30000L));
        } catch (Exception e) {
            if (e instanceof IOException || e instanceof InterruptedException) {
                throw (IOException) (e instanceof IOException ? e : new IOException(e));
            }
            throw new IOException(e);
        }
    }

    /**
     * Send HTTP request with exponential backoff retry logic (synchronized
     * version).
     * Uses rate limiter for concurrency control.
     * <p>
     * Backoff strategy:
     * - Starts at 1000ms (not 200ms to respect API limits)
     * - Adds jitter ±250-750ms to prevent thundering herd
     * - Respects Retry-After header if present
     * - Logs 429 at WARN once per cooldown window
     * 
     * @param request        The HTTP request to send
     * @param maxRetries     Maximum retries (3 for polling, 5 for critical)
     * @param initialDelayMs Base delay (1000ms)
     * @param maxDelayMs     Max delay cap (30000ms)
     * @return The HTTP response
     */
    private HttpResponse<String> sendWithExponentialBackoffSync(
            HttpRequest request,
            int maxRetries,
            long initialDelayMs,
            long maxDelayMs) throws IOException, InterruptedException {

        int attemptCount = 0;
        long lastLogTime = 0;

        while (true) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // If successful or not a rate limit error, return immediately
            if (response.statusCode() != 429) {
                return response;
            }

            // If we've exhausted retries, return the 429 response
            if (attemptCount >= maxRetries) {
                long now = System.currentTimeMillis();
                if (now - lastLogTime > 60000) { // Log once per minute to avoid spam
                    logger.warn("OANDA 429: Max retries ({}) exhausted. Endpoint: {}",
                            maxRetries, extractEndpoint(request.uri().toString()));
                    last429TimeMs = now;
                    last429Endpoint = extractEndpoint(request.uri().toString());
                }
                oanda429Count.incrementAndGet();
                return response;
            }

            oandaRetryCount.incrementAndGet();

            // Check for Retry-After header
            long delayMs = parseRetryAfterHeader(response);
            if (delayMs <= 0) {
                // Calculate exponential backoff: 1000 * 2^attempt
                delayMs = Math.min(initialDelayMs * (long) Math.pow(2, attemptCount), maxDelayMs);
            }

            // Add jitter to prevent thundering herd: ±250-750ms
            long jitter = 250 + jitterRandom.nextLong(501);
            delayMs = Math.min(delayMs + jitter, maxDelayMs);

            long now = System.currentTimeMillis();
            if (now - lastLogTime > 60000) { // Log once per minute
                logger.warn("OANDA 429: Retrying after {}ms with jitter (attempt {}/{}). Endpoint: {}",
                        delayMs, attemptCount + 1, maxRetries, extractEndpoint(request.uri().toString()));
                lastLogTime = now;
                last429TimeMs = now;
                last429Endpoint = extractEndpoint(request.uri().toString());
            }

            Thread.sleep(delayMs);
            attemptCount++;
        }
    }

    private long parseRetryAfterHeader(HttpResponse<?> response) {
        if (response == null)
            return 0;
        return response.headers()
                .firstValue("Retry-After")
                .map(s -> {
                    try {
                        return Long.parseLong(s) * 1000L; // Convert seconds to ms
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .orElse(0L);
    }

    private String extractEndpoint(String url) {
        try {
            String path = new java.net.URI(url).getPath();
            // Extract meaningful part: /v3/accounts/123/summary -> summary
            String[] parts = path.split("/");
            return parts.length > 0 ? parts[parts.length - 1] : url;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean isSuccess(HttpResponse<?> response) {
        return response != null && response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private String toInstrument(TradePair pair) {
        if (pair == null) {
            return "";
        }

        return "%s_%s".formatted(
                pair.getBaseCurrency().getCode().replace("/", "").replace("-", "").toUpperCase(Locale.ROOT),
                pair.getCounterCurrency().getCode().replace("/", "").replace("-", "").toUpperCase(Locale.ROOT));
    }

    private TradePair instrumentToTradePair(String instrument) {
        String normalized = safe(instrument).replace('-', '_').replace('/', '_').toUpperCase(Locale.ROOT);
        String[] parts = normalized.split("_");

        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            parts = new String[] { "EUR", "USD" };
        }

        try {
            TradePair pair = TradePair.fromSymbol(parts[0] + "_" + parts[1]);
            pair.setNativeSymbol(normalized);
            pair.setTradingSession(OandaTradingSessionFactory.forInstrument(normalized));
            return pair;
        } catch (Exception exception) {
            try {
                TradePair fallback = TradePair.fromSymbol("EUR_USD");
                fallback.setNativeSymbol("EUR_USD");
                fallback.setTradingSession(OandaTradingSessionFactory.forInstrument("EUR_USD"));
                return fallback;
            } catch (Exception fallbackException) {
                throw new IllegalStateException("Unable to create OANDA trade pair for %s".formatted(instrument),
                        fallbackException);
            }
        }
    }

    private void addPositionLeg(List<Position> result, TradePair pair, String instrument, JsonNode leg, Side side) {
        if (leg == null || leg.isMissingNode()) {
            return;
        }

        double units = Math.abs(leg.path("units").asDouble(0.0));

        if (units <= 0.0) {
            return;
        }

        Position position = new Position(pair, side, units, leg.path("averagePrice").asDouble(0.0));
        position.setPositionId("%s-%s".formatted(instrument, side));
        position.setCurrentPrice(leg.path("averagePrice").asDouble(0.0));
        position.setUnrealizedPnl(leg.path("unrealizedPL").asDouble(0.0));
        position.setRealizedPnl(leg.path("pl").asDouble(0.0));
        position.setIsOpen(true);
        result.add(position);
    }

    private OpenOrder.OrderType toOpenOrderType(String type) {
        return switch (safe(type).toUpperCase(Locale.ROOT)) {
            case "MARKET" -> OpenOrder.OrderType.MARKET;
            case "STOP", "STOP_LOSS" -> OpenOrder.OrderType.STOP_LOSS;
            case "TAKE_PROFIT" -> OpenOrder.OrderType.TAKE_PROFIT;
            case "TRAILING_STOP_LOSS" -> OpenOrder.OrderType.TRAILING_STOP;
            default -> OpenOrder.OrderType.LIMIT;
        };
    }

    private OpenOrder.OrderStatus toOpenOrderStatus(String state) {
        return switch (safe(state).toUpperCase(Locale.ROOT)) {
            case "FILLED" -> OpenOrder.OrderStatus.FILLED;
            case "CANCELLED" -> OpenOrder.OrderStatus.CANCELLED;
            case "REJECTED" -> OpenOrder.OrderStatus.REJECTED;
            case "EXPIRED" -> OpenOrder.OrderStatus.EXPIRED;
            default -> OpenOrder.OrderStatus.OPEN;
        };
    }

    private Instant parseInstant(String value) {
        String text = safe(value);

        if (text.isBlank()) {
            return Instant.now();
        }

        try {
            return Instant.parse(text);
        } catch (Exception exception) {
            return Instant.now();
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(safe(value));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String toOandaGranularity(int seconds) {
        return switch (seconds) {
            case 5 -> "S5";
            case 10 -> "S10";
            case 15 -> "S15";
            case 30 -> "S30";
            case 120 -> "M2";
            case 240 -> "M4";
            case 300 -> "M5";
            case 600 -> "M10";
            case 900 -> "M15";
            case 1800 -> "M30";
            case 3600 -> "H1";
            case 7200 -> "H2";
            case 10800 -> "H3";
            case 14400 -> "H4";
            case 21600 -> "H6";
            case 28800 -> "H8";
            case 43200 -> "H12";
            case 86400 -> "D";
            case 604800 -> "W";
            default -> "M1";
        };
    }

    private double firstPrice(JsonNode array) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            return 0.0;
        }

        return array.get(0).path("price").asDouble(0.0);
    }

    // =====================================================================
    // BATCH PRICING AND ASYNC HELPERS
    // =====================================================================

    /**
     * Fetch latest prices for multiple instruments in a single batch request.
     * Results are cached with 500ms TTL.
     */
    public CompletableFuture<Map<String, Ticker>> getLatestPrices(List<TradePair> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }

        String account = resolveAccountId();
        if (account.isBlank()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }

        // Check cache for all pairs
        String cacheKey = "prices_" + pairs.hashCode();
        Map<String, Ticker> cached = getPricingCached(cacheKey);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        // Batch instruments: EUR_USD,USD_JPY,...
        String instruments = pairs.stream()
                .filter(Objects::nonNull)
                .map(this::toInstrument)
                .distinct()
                .collect(java.util.stream.Collectors.joining(","));

        if (instruments.isBlank()) {
            return CompletableFuture.completedFuture(new LinkedHashMap<>());
        }

        String url = "%s/v3/accounts/%s/pricing?instruments=%s"
                .formatted(oandaApiUrl(), account, instruments);

        HttpRequest request = requestBuilder(url).GET().build();

        return sendWithExponentialBackoff(request)
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA batch pricing failed HTTP {}", response.statusCode());
                        return new LinkedHashMap<>();
                    }

                    try {
                        Map<String, Ticker> result = new LinkedHashMap<>();
                        JsonNode pricesArray = OBJECT_MAPPER.readTree(response.body()).path("prices");

                        if (pricesArray.isArray()) {
                            for (JsonNode price : pricesArray) {
                                String instrument = price.path("instrument").asText("");
                                double bid = firstPrice(price.path("bids"));
                                double ask = firstPrice(price.path("asks"));
                                long timestamp = Instant.parse(price.path("time").asText(Instant.now().toString()))
                                        .toEpochMilli();

                                Ticker ticker = new Ticker();
                                ticker.setBidPrice(bid);
                                ticker.setAskPrice(ask);
                                ticker.setTimestamp(timestamp);
                                ticker.setVolume(0);

                                result.put(instrument, ticker);
                            }
                        }

                        // Cache the result
                        setPricingCached(cacheKey, result);
                        return result;

                    } catch (Exception e) {
                        logger.error("Failed to parse batch pricing response", e);
                        return new LinkedHashMap<>();
                    }
                });
    }

    private Ticker emptyTicker() {
        Ticker ticker = new Ticker();
        ticker.setBidPrice(0.0);
        ticker.setAskPrice(0.0);
        ticker.setVolume(0.0);
        ticker.setTimestamp(Instant.now().toEpochMilli());
        return ticker;
    }

    public String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String mask(String value) {
        String text = safe(value);

        if (text.length() <= 6) {
            return "***";
        }

        return "%s***%s".formatted(text.substring(0, 3), text.substring(text.length() - 3));
    }

    public static <T> @NotNull CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }

    /**
     * Convenience method with default backoff parameters.
     * Initial delay: 1000ms, Max delay: 30 seconds, Max retries: 3 (polling)
     */
    private CompletableFuture<HttpResponse<String>> sendWithExponentialBackoff(HttpRequest request) {
        totalOandaRequests.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return rateLimiter.execute(() -> sendWithExponentialBackoffSync(request, 3, 1000L, 30000L));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, oandaExecutor);
    }


}
