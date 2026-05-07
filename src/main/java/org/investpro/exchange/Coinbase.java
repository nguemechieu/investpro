package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.investpro.models.trading.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.CoinbaseJwtSigner;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.coinbase.CoinbaseCandleDataSupplier;
import org.investpro.exchange.websocket.CoinbaseExchangeWebSocketClient;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.exchange.infrastructure.PollingExchangeStreamer;
import org.investpro.exchange.infrastructure.BotTradingConfig;
import org.investpro.exchange.infrastructure.SignalProcessor;
import org.investpro.exchange.infrastructure.OrderCommandConsumer;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

@Slf4j
@Getter
@Setter
public class Coinbase extends Exchange {

    // Removed old logger field - @Slf4j provides static log field

    private static final String REST_BASE_URL = "https://api.coinbase.com/api/v3/brokerage";
    private static final String PUBLIC_PRODUCTS_URL = "%s/market/products".formatted(REST_BASE_URL);
    private static final String PUBLIC_PRODUCT_BOOK_URL = "%s/market/product_book".formatted(REST_BASE_URL);
    private static final String PUBLIC_SERVER_TIME_URL = "%s/time".formatted(REST_BASE_URL);
    private static final String ORDERS_URL = "%s/orders".formatted(REST_BASE_URL);
    private static final String CANCEL_ORDERS_URL = "%s/orders/batch_cancel".formatted(REST_BASE_URL);
    private static final String ACCOUNTS_URL = "%s/accounts".formatted(REST_BASE_URL);
    private static final String MARKET_DATA_WS_URL = "wss://advanced-trade-ws.coinbase.com";

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final HttpClient httpClient;
    private HttpRequest.Builder requestBuilder;
    private final CoinbaseJwtSigner jwtSigner;

    private final PollingExchangeStreamer pollingStreamer;

    private ExchangeWebSocketClient websocketClient;
    private TradePair tradePair;

    private String apiSecret;
    private String apiKey;

    // Bot trading components

    private final BotTradingConfig botConfig;
    private final SignalProcessor signalProcessor;

    public Coinbase(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);

        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.apiSecret = apiSecret == null ? "" : apiSecret.trim();

        if (!this.apiKey.isEmpty() && !this.apiSecret.isEmpty()) {
            log.info("Coinbase: Using credentials from provided parameters");
        } else {
            // Fallback to environment variables only when no explicit credentials were supplied.
            String envKeyName = CoinbaseCredentialProvider.getKeyName();
            String envPrivateKey = CoinbaseCredentialProvider.getPrivateKey();

            if (envKeyName != null && !envKeyName.isBlank() && envPrivateKey != null && !envPrivateKey.isBlank()) {
                this.apiKey = envKeyName.trim();
                this.apiSecret = envPrivateKey.trim();
                log.info(
                        "Coinbase: Using credentials from environment variables (COINBASE_KEY_NAME, COINBASE_PRIVATE_KEY)");
            } else {
                log.warn(
                        "Coinbase: No credentials provided. Set COINBASE_KEY_NAME and COINBASE_PRIVATE_KEY environment variables, or provide apiKey/apiSecret parameters.");
            }
        }

        this.jwtSigner = createJwtSigner(this.apiKey, this.apiSecret);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        this.pollingStreamer = new PollingExchangeStreamer(this);

        // Initialize bot trading components
        this.botConfig = new BotTradingConfig();
        this.botConfig.loadFromPreferences();
        // Try to get account balance for risk management, default to $10k if
        // unavailable
        double accountBalance = 500.0;
        try {
            Account account = getUserAccountDetails();
            if (account != null) {
                accountBalance = account.getAvailableBalance();
            }
        } catch (Exception e) {
            log.debug("Could not fetch account balance, using default $10k: {}", e.getMessage());
        }

        this.signalProcessor = new SignalProcessor(this, this.botConfig, accountBalance);

        String websocketJwt = websocketJwt();
        log.info("Coinbase WebSocket JWT created: {} characters, empty={}", websocketJwt.length(),
                websocketJwt.isEmpty());
        this.websocketClient = new CoinbaseExchangeWebSocketClient(
                URI.create(MARKET_DATA_WS_URL),
                new Draft_6455(),
                websocketJwt);
    }

    @Override
    public String getName() {
        return "Coinbase";
    }

    @Override
    public String getSignal() {
        return "Coinbase Advanced Trade";
    }

    @Override
    public String getExchangeId() {
        return "coinbase";
    }

    @Override
    public String getDisplayName() {
        return "Coinbase Advanced Trade";
    }

    @Override
    public boolean isSandbox() {
        return false;
    }

    @Override
    public boolean isPaperTrading() {
        return false;
    }

    @Override
    public boolean supportsMarketType(MARKET_TYPES marketType) {
        if (marketType == null) {
            return false;
        }

        String name = marketType.name().toUpperCase(Locale.ROOT);
        return name.contains("CRYPTO") || name.contains("SPOT") || name.contains("DERIVATIVE");
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        List<MARKET_TYPES> supported = new ArrayList<>();

        for (MARKET_TYPES type : MARKET_TYPES.values()) {
            if (supportsMarketType(type)) {
                supported.add(type);
            }
        }

        return supported;
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
    public void connect() {
        try {
            log.info("Coinbase connect() called. websocketClient={}, isOpen={}",
                    websocketClient, websocketClient != null ? websocketClient.isOpen() : "null");

            if (websocketClient != null && !websocketClient.isOpen()) {
                log.info("Attempting to establish WebSocket connection to Coinbase...");
                // Use connectBlocking() to wait for actual connection establishment
                // with timeout to prevent hanging on stuck connections
                boolean connected = websocketClient.connectBlocking(10, java.util.concurrent.TimeUnit.SECONDS);
                if (!connected) {
                    log.error("WebSocket connection timeout after 10 seconds");
                    throw new RuntimeException("WebSocket connection timeout after 10 seconds");
                }
                log.info("Successfully connected to Coinbase WebSocket");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Coinbase WebSocket connection interrupted", exception);
            throw new RuntimeException("Coinbase WebSocket connection interrupted", exception);
        } catch (Exception exception) {
            log.error("Unable to connect Coinbase WebSocket: {}", exception.getMessage(), exception);
            throw new RuntimeException("Unable to connect Coinbase WebSocket: %s".formatted(exception.getMessage()),
                    exception);
        }
    }

    @Override
    public void disconnect() {
        stopAllStreams();

        try {
            if (websocketClient != null && websocketClient.isOpen()) {
                websocketClient.close();
            }
        } catch (Exception exception) {
            log.warn("Unable to close Coinbase websocket", exception);
        }
    }

    @Override
    public void reconnect() {
        disconnect();

        // Pass JWT token to WebSocket client for authenticated endpoints
        String jwt = websocketJwt();
        websocketClient = new CoinbaseExchangeWebSocketClient(
                URI.create(MARKET_DATA_WS_URL),
                new Draft_6455(),
                jwt);

        connect();
    }

    @Override
    public Boolean isConnected() {
        try {
            return websocketClient != null
                    && websocketClient.connectionEstablished != null
                    && websocketClient.connectionEstablished.get();
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return websocketClient;
    }

    @Override
    public boolean supportsWebSocket() {
        return true;
    }

    @Override
    public boolean isWebsocketAvailable() {
        return websocketClient != null && websocketClient.isOpen();
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
    public TradePair getSelectedTradePair() {
        return tradePair;
    }

    @Override
    public double getLivePrice() {
        if (tradePair == null) {
            return 0.0;
        }

        Ticker ticker = getLivePrice(tradePair);

        if (ticker == null) {
            return 0.0;
        }

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();

        if (bid > 0 && ask > 0) {
            return (bid + ask) / 2.0;
        }

        return Math.max(bid, ask);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }

    // ---------------------------------------------------------------------
    // HTTP helpers
    // ---------------------------------------------------------------------

    private HttpRequest.Builder baseRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro/1.0")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Cache-Control", "no-cache");
    }

    private HttpRequest.Builder authenticatedRequest(String method, String url) {
        HttpRequest.Builder builder = baseRequest(url);

        String authorization = authorizationHeader(method, url);

        if (!authorization.isBlank()) {
            builder.header("Authorization", authorization);
        }

        return builder;
    }

    private boolean looksLikePrivateKey(String value) {
        boolean result = value != null
                && value.contains("BEGIN")
                && value.contains("PRIVATE KEY");
        if (!result) {
            log.debug("Private key validation failed: contains BEGIN={}, contains PRIVATE KEY={}",
                    value != null && value.contains("BEGIN"),
                    value != null && value.contains("PRIVATE KEY"));
        }
        return result;
    }

    private CoinbaseJwtSigner createJwtSigner(String keyName, String privateKey) {
        if (keyName == null || keyName.isBlank() || !looksLikePrivateKey(privateKey)) {
            return null;
        }

        return new CoinbaseJwtSigner(keyName, privateKey);
    }

    private String websocketJwt() {
        if (jwtSigner == null) {
            log.warn(
                    "JWT signer is null - unable to generate WebSocket JWT. Public channels will work but authenticated channels won't.");
            return "";
        }

        String jwt = jwtSigner.buildWebSocketJwt();
        log.debug("Generated WebSocket JWT: {} characters", jwt.length());
        return jwt;
    }

    private String authorizationHeader(String method, String url) {
        if (jwtSigner != null) {
            return jwtSigner.buildAuthorizationHeaderForUrl(method, url);
        }

        String token = bearerToken();

        if (!token.isBlank()) {
            return "Bearer %s".formatted(token);
        }

        return "";
    }

    private @NotNull String bearerToken() {
        String token = apiSecret == null ? "" : apiSecret.trim();

        if (looksLikePrivateKey(token)) {
            return "";
        }

        if (token.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return token.substring("Bearer ".length()).trim();
        }

        return token;
    }

    private boolean hasPrivateEndpointAuth() {
        return jwtSigner != null || !bearerToken().isBlank();
    }

    private void requirePrivateEndpointAuth(String action) {
        if (!hasPrivateEndpointAuth()) {
            throw new IllegalStateException(
                    "%s requires Coinbase Advanced Trade authentication. Set apiKey to the CDP key name and apiSecret to the EC private key PEM, or pass a prebuilt Bearer JWT as apiSecret."
                            .formatted(action));
        }
    }

    private @NotNull HttpResponse<String> send(HttpRequest request) {
        try {
            HttpResponse<String> httpResponse = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() >= 400) {
                String errorMsg = "Coinbase HTTP %d for %s: %s"
                        .formatted(httpResponse.statusCode(), request.uri(), httpResponse.body());
                log.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }

            return httpResponse;
        } catch (IOException exception) {
            throw new RuntimeException("Coinbase HTTP request failed: %s".formatted(request.uri()), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Coinbase HTTP request interrupted: %s".formatted(request.uri()), exception);
        }
    }

    private @NotNull CompletableFuture<String> sendAsync(HttpRequest request) {
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(httpResponse -> {
                    if (httpResponse.statusCode() >= 400) {
                        throw new RuntimeException(
                                "Coinbase HTTP %d for %s: %s"
                                        .formatted(httpResponse.statusCode(), request.uri(), httpResponse.body()));
                    }

                    return httpResponse.body();
                });
    }

    private static JsonNode readJson(String body) {
        try {
            return OBJECT_MAPPER.readTree(body == null ? "" : body);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Unable to parse Coinbase JSON response", exception);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private @NotNull String productId(TradePair tradePair) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        return tradePair.toString('-').toUpperCase(Locale.ROOT);
    }

    private int fractionalDigitsFromIncrement(String increment) {
        if (increment == null || increment.isBlank()) {
            return 8;
        }

        try {
            BigDecimal decimal = new BigDecimal(increment.strip());
            return Math.max(0, decimal.stripTrailingZeros().scale());
        } catch (Exception exception) {
            return 8;
        }
    }

    @Contract("_, _ -> new")
    private @NotNull CryptoCurrency currencyFromCode(String code, String increment) {
        String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        int fractionalDigits = fractionalDigitsFromIncrement(increment);

        try {
            return new CryptoCurrency(
                    normalized,
                    normalized,
                    normalized,
                    fractionalDigits,
                    normalized,
                    normalized);
        } catch (SQLException | ClassNotFoundException exception) {
            throw new RuntimeException(exception);
        }
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }

        for (String name : names) {
            JsonNode value = node.get(name);

            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText("").trim();
            }
        }

        return "";
    }

    private static String firstDecimalText(JsonNode node, String... names) {
        if (node == null || names == null) {
            return "";
        }

        for (String name : names) {
            JsonNode value = node.get(name);

            if (value == null || value.isNull()) {
                continue;
            }

            if (value.isNumber()) {
                return value.decimalValue().stripTrailingZeros().toPlainString();
            }

            String text = value.asText("").trim();

            if (!text.isBlank()) {
                try {
                    return new BigDecimal(text).stripTrailingZeros().toPlainString();
                } catch (NumberFormatException exception) {
                    return text;
                }
            }
        }

        return "";
    }

    private double parseDouble(String text, double fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }

        try {
            return Double.parseDouble(text);
        } catch (Exception exception) {
            return fallback;
        }
    }

    private long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(text);
        } catch (Exception exception) {
            return 0L;
        }
    }

    private long parseInstantMillis(String text) {
        if (text == null || text.isBlank()) {
            return System.currentTimeMillis();
        }

        try {
            return Instant.parse(text).toEpochMilli();
        } catch (Exception exception) {
            return System.currentTimeMillis();
        }
    }

    private long parseTradeId(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (Exception ignored) {
            return Math.abs(value.hashCode());
        }
    }

    private Side parseSide(String value) {
        try {
            return Side.getSide(String.valueOf(value).toLowerCase(Locale.ROOT));
        } catch (Exception exception) {
            return Side.getSide("buy");
        }
    }

    // ---------------------------------------------------------------------
    // Public market data
    // ---------------------------------------------------------------------

    @Override
    public List<TradePair> getTradePairSymbol() {
        HttpRequest request = authenticatedRequest("GET", PUBLIC_PRODUCTS_URL)
                .GET()
                .build();

        HttpResponse<String> httpResponse = send(request);
        JsonNode root = readJson(httpResponse.body());

        ArrayList<TradePair> tradePairs = new ArrayList<>();

        if (root.has("message")) {
            log.warn("Coinbase products API returned error: {}", root.get("message").asText());
            return tradePairs;
        }

        JsonNode products = root.has("products") ? root.get("products") : root;

        if (products == null || !products.isArray()) {
            log.warn("Coinbase products API returned unexpected payload: {}", root);
            return tradePairs;
        }

        for (JsonNode product : products) {
            if (product == null || !product.isObject()) {
                continue;
            }

            boolean disabled = product.path("is_disabled").asBoolean(false);
            String status = product.path("status").asText("");

            if (disabled || "offline".equalsIgnoreCase(status) || "delisted".equalsIgnoreCase(status)) {
                continue;
            }

            String baseCode = firstText(
                    product,
                    "base_currency_id",
                    "base_currency",
                    "base_currency_code");

            String quoteCode = firstText(
                    product,
                    "quote_currency_id",
                    "quote_currency",
                    "quote_currency_code");

            if (baseCode.isBlank() || quoteCode.isBlank()) {
                continue;
            }

            String baseIncrement = firstText(product, "base_increment", "base_min_size");
            String quoteIncrement = firstText(product, "quote_increment", "quote_min_size");

            CryptoCurrency baseCurrency = currencyFromCode(baseCode, baseIncrement);
            CryptoCurrency counterCurrency = currencyFromCode(quoteCode, quoteIncrement);

            try {
                tradePairs.add(new TradePair(baseCurrency, counterCurrency));
            } catch (SQLException | ClassNotFoundException exception) {
                throw new RuntimeException(exception);
            }
        }

        tradePairs.sort(Comparator.comparing(pair -> pair.toString('/')));
        return tradePairs;
    }

    @Override
    public List<TradePair> getTradablePairs() {
        return getTradePairSymbol();
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        if (tradePair == null) {
            return false;
        }

        String target = productId(tradePair);

        try {
            return getTradePairSymbol()
                    .stream()
                    .map(this::productId)
                    .anyMatch(target::equalsIgnoreCase);
        } catch (Exception exception) {
            log.debug("Unable to check Coinbase pair support for {}", tradePair, exception);
            return false;
        }
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) {
        String url = "%s?product_id=%s&limit=50"
                .formatted(PUBLIC_PRODUCT_BOOK_URL, encode(productId(tradePair)));

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request).thenApply(response -> parseOrderBook(response, tradePair));
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        String product = productId(tradePair);
        String url = "%s?product_id=%s&limit=50".formatted(PUBLIC_PRODUCT_BOOK_URL, encode(product));

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request).thenApply(response -> parseOrderBook(response, tradePair));
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        String url = "%s/%s/ticker".formatted(PUBLIC_PRODUCTS_URL, encode(productId(tradePair)));

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        HttpResponse<String> httpResponse = send(request);
        JsonNode root = readJson(httpResponse.body());

        Ticker ticker = new Ticker();

        double bestBid = parseDouble(root.path("best_bid").asText(null), 0.0);
        double bestAsk = parseDouble(root.path("best_ask").asText(null), 0.0);

        if ((bestBid <= 0 || bestAsk <= 0) && root.has("trades") && root.get("trades").isArray()) {
            JsonNode firstTrade = root.get("trades").isEmpty() ? null : root.get("trades").get(0);

            if (firstTrade != null) {
                double lastPrice = parseDouble(firstTrade.path("price").asText(null), 0.0);

                if (bestBid <= 0) {
                    bestBid = lastPrice;
                }

                if (bestAsk <= 0) {
                    bestAsk = lastPrice;
                }
            }
        }

        ticker.setBidPrice(bestBid);
        ticker.setAskPrice(bestAsk);

        if (root.has("trades") && root.get("trades").isArray() && !root.get("trades").isEmpty()) {
            JsonNode firstTrade = root.get("trades").get(0);
            ticker.setVolume(parseDouble(firstTrade.path("size").asText(null), 0.0));
            ticker.setTimestamp(parseInstantMillis(firstTrade.path("time").asText(null)));
        } else {
            ticker.setVolume(0.0);
            ticker.setTimestamp(System.currentTimeMillis());
        }

        return ticker;
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        try {
            return CompletableFuture.completedFuture(getLivePrice(tradePair));
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.completedFuture(
                tradePairs.stream()
                        .filter(Objects::nonNull)
                        .map(this::getLivePrice)
                        .filter(Objects::nonNull)
                        .toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(stopAt, "stopAt must not be null");

        if (stopAt.isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            String url = "%s/%s/ticker?limit=100".formatted(PUBLIC_PRODUCTS_URL, encode(productId(tradePair)));

            HttpRequest request = authenticatedRequest("GET", url)
                    .GET()
                    .build();

            HttpResponse<String> httpResponse = send(request);
            JsonNode root = readJson(httpResponse.body());

            JsonNode tradesNode = root.get("trades");

            if (tradesNode == null || !tradesNode.isArray()) {
                return Collections.emptyList();
            }

            List<Trade> trades = new ArrayList<>();

            for (JsonNode tradeNode : tradesNode) {
                Instant time;

                try {
                    time = Instant.from(ISO_INSTANT.parse(tradeNode.path("time").asText()));
                } catch (Exception exception) {
                    continue;
                }

                if (time.compareTo(stopAt) <= 0) {
                    break;
                }

                double price = parseDouble(tradeNode.path("price").asText("0"), 0.0);
                double size = parseDouble(tradeNode.path("size").asText("0"), 0.0);
                Side side = parseSide(tradeNode.path("side").asText("buy"));
                long tradeId = parseTradeId(tradeNode.path("trade_id").asText("0"));

                trades.add(new Trade(
                        tradePair,
                        price,
                        size,
                        side,
                        tradeId,
                        time));
            }

            return trades;
        });
    }

    @Contract(pure = true)
    private @NotNull String granularityName(int seconds) {
        return switch (seconds) {
            case 300 -> "FIVE_MINUTE";
            case 900 -> "FIFTEEN_MINUTE";
            case 1800 -> "THIRTY_MINUTE";
            case 3600 -> "ONE_HOUR";
            case 7200 -> "TWO_HOUR";
            case 14400 -> "TWO_HOUR"; // Coinbase does not support FOUR_HOUR
            case 21600 -> "SIX_HOUR";
            case 86400 -> "ONE_DAY";
            default -> "ONE_MINUTE";
        };
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair,
            Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle,
            int secondsPerCandle) {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(currentCandleStartedAt, "currentCandleStartedAt must not be null");

        long startEpoch = currentCandleStartedAt.getEpochSecond();
        long endEpoch = Math.min(Instant.now().getEpochSecond(), startEpoch + Math.max(1, secondsPerCandle));

        int closestGranularity = getCandleDataSupplier(secondsPerCandle, tradePair)
                .getSupportedGranularities()
                .stream()
                .min(Comparator.comparingInt(value -> Math.abs(value - Math.max(60, secondsPerCandle / 10))))
                .orElse(secondsPerCandle);

        String url = "%s/%s/candles?start=%d&end=%d&granularity=%s".formatted(PUBLIC_PRODUCTS_URL,
                encode(productId(tradePair)), startEpoch, endEpoch, encode(granularityName(closestGranularity)));

        HttpRequest request = authenticatedRequest("GET", url)

                .GET()
                .build();

        return sendAsync(request).thenApply(body -> {
            JsonNode root = readJson(String.valueOf(body));

            if (root.has("message")) {
                throw new RuntimeException(
                        "Invalid Coinbase candle response: %s".formatted(root.get("message").asText()));
            }

            JsonNode candles = root.has("candles") ? root.get("candles") : root;

            if (candles == null || !candles.isArray() || candles.isEmpty()) {
                return Optional.empty();
            }

            double openPrice = -1.0;
            double highSoFar = -1.0;
            double lowSoFar = Double.MAX_VALUE;
            double volumeSoFar = 0.0;
            double lastTradePrice = -1.0;
            int currentTill = -1;
            boolean foundFirst = false;

            for (JsonNode candle : candles) {
                long candleStart = parseLong(candle.path("start").asText("0"));

                if (candleStart < startEpoch || candleStart >= startEpoch + secondsPerCandle) {
                    continue;
                }

                double open = parseDouble(candle.path("open").asText(null), -1.0);
                double high = parseDouble(candle.path("high").asText(null), -1.0);
                double low = parseDouble(candle.path("low").asText(null), Double.MAX_VALUE);
                double close = parseDouble(candle.path("close").asText(null), -1.0);
                double volume = parseDouble(candle.path("volume").asText(null), 0.0);

                if (!foundFirst) {
                    openPrice = open;
                    foundFirst = true;
                }

                if (high > highSoFar) {
                    highSoFar = high;
                }

                if (low < lowSoFar) {
                    lowSoFar = low;
                }

                volumeSoFar += volume;

                if (candleStart >= currentTill) {
                    currentTill = (int) candleStart;
                    lastTradePrice = close;
                }
            }

            if (!foundFirst) {
                return Optional.empty();
            }

            int openTime = (int) currentCandleStartedAt.getEpochSecond();

            return Optional.of(new InProgressCandleData(
                    openTime,
                    openPrice,
                    highSoFar,
                    lowSoFar,
                    currentTill,
                    lastTradePrice,
                    volumeSoFar));
        });
    }

    // ---------------------------------------------------------------------
    // Account / balances
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<Account> fetchAccount() {
        try {
            return CompletableFuture.completedFuture(getUserAccountDetails());
        } catch (Exception exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public Account getUserAccountDetails() {
        requirePrivateEndpointAuth("Coinbase account details");

        HttpRequest request = authenticatedRequest("GET", ACCOUNTS_URL)
                .GET()
                .build();

        HttpResponse<String> response = send(request);

        try {
            JsonNode root = readJson(response.body());
            JsonNode accounts = root.path("accounts");

            if (accounts.isArray() && !accounts.isEmpty()) {
                JsonNode first = accounts.get(0);
                return OBJECT_MAPPER.treeToValue(first, Account.class);
            }

            return OBJECT_MAPPER.readValue(response.body(), Account.class);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("Unable to parse Coinbase accounts response", exception);
        }
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return fetchAccountsBalance(currencyCode, "available_balance");
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAccountsBalance(currencyCode, "hold");
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return CompletableFuture.completedFuture(0.0);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return CompletableFuture.completedFuture(0.0);
    }

    private CompletableFuture<Double> fetchAccountsBalance(String currencyCode, String field) {
        if (!hasPrivateEndpointAuth()) {
            return CompletableFuture.completedFuture(0.0);
        }

        return listAccountsRaw()
                .thenApply(Coinbase::readJson)
                .thenApply(root -> {
                    JsonNode accounts = root.path("accounts");

                    if (!accounts.isArray()) {
                        return 0.0;
                    }

                    double total = 0.0;

                    for (JsonNode account : accounts) {
                        String currency = firstText(account, "currency", "currency_code");

                        if (currencyCode != null
                                && !currencyCode.isBlank()
                                && !currency.equalsIgnoreCase(currencyCode)) {
                            continue;
                        }

                        JsonNode valueNode = account.path(field).path("value");

                        if (valueNode.isMissingNode()) {
                            valueNode = account.path(field);
                        }

                        total += parseDouble(valueNode.asText("0"), 0.0);
                    }

                    return total;
                });
    }

    // ---------------------------------------------------------------------
    // Private account / order endpoints
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        Objects.requireNonNull(order, "order must not be null");

        requirePrivateEndpointAuth("Coinbase create order");

        JsonNode rawOrder = OBJECT_MAPPER.valueToTree(order);
        String requestBody = normalizeOrderPayload(rawOrder);

        HttpRequest request = authenticatedRequest("POST", ORDERS_URL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendAsync(request).thenApply(Coinbase::requireSuccessfulOrderResponse);
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
        Objects.requireNonNull(tradePair, "tradePair must not be null");

        Order order = new Order();
        order.setSymbol(productId(tradePair));
        order.setType(type == null || type.isBlank() ? "MARKET" : type.trim().toUpperCase(Locale.ROOT));
        order.setSide(side == Side.SELL ? Side.SELL : Side.BUY);
        order.setQuantity(normalizeAmount(tradePair, amount));
        order.setPrice(normalizePrice(tradePair, price));
        order.setStopLoss(stopLoss);
        order.setTakeProfit(takeProfit);
        order.setStatus("PENDING");

        return order;
    }

    @Override
    public CompletableFuture<String> createMarketOrder(
            TradePair tradePair,
            Side side,
            double amount) {
        Order order = createOrder(
                0,
                tradePair,
                "MARKET",
                0.0,
                amount,
                side,
                0.0,
                0.0,
                0.0);

        try {
            return createOrder(order);
        } catch (JsonProcessingException exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<String> createLimitOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double limitPrice) {
        Order order = createOrder(
                UUID.randomUUID().hashCode(),
                tradePair,
                "LIMIT",
                limitPrice,
                amount,
                side,
                0.0,
                0.0,
                0.0);

        try {
            return createOrder(order);
        } catch (JsonProcessingException exception) {
            return failedFuture(exception);
        }
    }

    @Override
    public CompletableFuture<String> createStopOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double stopPrice) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase stop order helper is not implemented yet."));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(
            TradePair tradePair,
            Side side,
            double amount,
            double entryPrice,
            double stopLoss,
            double takeProfit) {
        if (entryPrice > 0) {
            return createLimitOrder(tradePair, side, amount, entryPrice);
        }

        return createMarketOrder(tradePair, side, amount);
    }

    static String normalizeOrderPayload(JsonNode rawOrder) throws JsonProcessingException {
        if (rawOrder == null || !rawOrder.isObject()) {
            throw new IllegalArgumentException("Order cannot be serialized to a JSON object.");
        }

        if (rawOrder.has("order_configuration") && rawOrder.has("product_id") && rawOrder.has("side")) {
            return OBJECT_MAPPER.writeValueAsString(rawOrder);
        }

        String product = firstText(rawOrder, "product_id", "productId", "symbol", "instrument");
        String side = firstText(rawOrder, "side");
        String type = firstText(rawOrder, "order_type", "orderType", "type");
        String price = firstDecimalText(rawOrder, "price", "limit_price", "limitPrice");
        String size = firstDecimalText(rawOrder, "base_size", "baseSize", "size", "quantity", "amount");
        String quoteSize = firstDecimalText(rawOrder, "quote_size", "quoteSize", "funds", "notional");

        if (side.isBlank() && ("BUY".equalsIgnoreCase(type) || "SELL".equalsIgnoreCase(type))) {
            side = type;
            type = "";
        }

        if (product.contains("/")) {
            product = product.replace("/", "-");
        }

        product = product.toUpperCase(Locale.ROOT);
        side = side.toUpperCase(Locale.ROOT);

        if (product.isBlank()) {
            throw new IllegalArgumentException("Coinbase order requires a product id.");
        }

        if (!side.equals("BUY") && !side.equals("SELL")) {
            side = "BUY";
        }

        type = type.isBlank() ? "market" : type.toLowerCase(Locale.ROOT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_order_id", UUID.randomUUID().toString());
        payload.put("product_id", product);
        payload.put("side", side);

        Map<String, Object> orderConfiguration = new LinkedHashMap<>();

        if (type.contains("limit") && !price.isBlank()) {
            Map<String, Object> limit = new LinkedHashMap<>();

            if (!size.isBlank()) {
                limit.put("base_size", size);
            }

            if (!quoteSize.isBlank()) {
                limit.put("quote_size", quoteSize);
            }

            limit.put("limit_price", price);
            limit.put("post_only", false);

            orderConfiguration.put("limit_limit_gtc", limit);
        } else {
            Map<String, Object> market = new LinkedHashMap<>();

            if (!quoteSize.isBlank()) {
                market.put("quote_size", quoteSize);
            }

            if (!size.isBlank()) {
                market.put("base_size", size);
            }

            orderConfiguration.put("market_market_ioc", market);
        }

        payload.put("order_configuration", orderConfiguration);

        return OBJECT_MAPPER.writeValueAsString(payload);
    }

    static String requireSuccessfulOrderResponse(String responseBody) {
        JsonNode root = readJson(responseBody);

        if (root.has("success") && !root.path("success").asBoolean(false)) {
            JsonNode error = root.path("error_response");
            String message = firstText(error, "message", "error", "error_details");

            if (message.isBlank()) {
                message = root.toString();
            }

            throw new RuntimeException("Coinbase order rejected: " + message);
        }

        String orderId = firstText(root.path("success_response"), "order_id", "orderId");

        if (orderId.isBlank()) {
            orderId = firstText(root, "order_id", "orderId", "id");
        }

        return orderId.isBlank() ? responseBody : orderId;
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        requirePrivateEndpointAuth("Coinbase cancel order");

        String requestBody;

        try {
            requestBody = OBJECT_MAPPER.writeValueAsString(Map.of("order_ids", List.of(orderId)));
        } catch (JsonProcessingException exception) {
            return failedFuture(new RuntimeException("Unable to serialize Coinbase cancel order payload", exception));
        }

        HttpRequest request = authenticatedRequest("POST", CANCEL_ORDERS_URL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendAsync(request);
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        Objects.requireNonNull(orderIds, "orderIds must not be null");

        if (orderIds.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        requirePrivateEndpointAuth("Coinbase cancel orders");

        String requestBody;

        try {
            requestBody = OBJECT_MAPPER.writeValueAsString(Map.of("order_ids", orderIds));
        } catch (JsonProcessingException exception) {
            return failedFuture(new RuntimeException("Unable to serialize cancel orders payload", exception));
        }

        HttpRequest request = authenticatedRequest("POST", CANCEL_ORDERS_URL)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return sendAsync(request)
                .thenApply(response -> {
                    JsonNode root = readJson(response);
                    List<String> results = new ArrayList<>();

                    if (root.isArray()) {
                        for (JsonNode item : root) {
                            results.add(item.asText(""));
                        }
                    } else {
                        JsonNode resultsNode = root.path("results");

                        if (resultsNode.isArray()) {
                            for (JsonNode item : resultsNode) {
                                String id = firstText(item, "order_id", "id");

                                if (!id.isBlank()) {
                                    results.add(id);
                                }
                            }
                        }
                    }

                    return results;
                });
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        requirePrivateEndpointAuth("Coinbase cancel all orders");

        return listOrdersRaw()
                .thenApply(Coinbase::readJson)
                .thenCompose(ordersJson -> {
                    List<String> orderIds = new ArrayList<>();
                    JsonNode orders = ordersJson.has("orders") ? ordersJson.get("orders") : ordersJson;

                    if (orders != null && orders.isArray()) {
                        for (JsonNode order : orders) {
                            String orderId = firstText(order, "order_id", "id");

                            if (!orderId.isEmpty()) {
                                orderIds.add(orderId);
                            }
                        }
                    }

                    if (orderIds.isEmpty()) {
                        return CompletableFuture.completedFuture("No open orders to cancel.");
                    }

                    return cancelOrders(orderIds)
                            .thenApply(cancelled -> "Cancelled %d orders.".formatted(cancelled.size()));
                });
    }

    @Override
    public void cancelALL() {
        cancelAllOrders()
                .thenAccept(result -> log.info("Coinbase cancelALL result: {}", result))
                .exceptionally(exception -> {
                    log.warn("Coinbase cancelALL failed", exception);
                    return null;
                });
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        requirePrivateEndpointAuth("Coinbase fetch order");

        return getOrderRaw(orderId)
                .thenApply(Coinbase::readJson)
                .thenApply(jsonNode -> {
                    try {
                        JsonNode orderNode = jsonNode.has("order") ? jsonNode.get("order") : jsonNode;

                        if (orderNode == null || orderNode.isMissingNode()) {
                            return Optional.empty();
                        }

                        Order order = OBJECT_MAPPER.treeToValue(orderNode, Order.class);
                        return Optional.of(order);
                    } catch (JsonProcessingException exception) {
                        log.error("Unable to parse order response", exception);
                        return Optional.empty();
                    }
                });
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        requirePrivateEndpointAuth("Coinbase open orders");

        String url = "%s/orders/historical/batch?product_id=%s&order_status=OPEN"
                .formatted(REST_BASE_URL, encode(productId(tradePair)));

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request).thenApply(response -> parseOpenOrders(response, tradePair));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        requirePrivateEndpointAuth("Coinbase all open orders");

        String url = "%s/orders/historical/batch?order_status=OPEN".formatted(REST_BASE_URL);

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request).thenApply(this::parseOpenOrdersAll);
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        requirePrivateEndpointAuth("Coinbase order history");

        String url = "%s/orders/historical/batch".formatted(REST_BASE_URL);

        List<String> query = new ArrayList<>();

        if (tradePair != null) {
            query.add("product_id=%s".formatted(encode(productId(tradePair))));
        }

        if (since != null) {
            query.add("start_date=%s".formatted(encode(since.toString())));
        }

        if (!query.isEmpty()) {
            url += "?%s".formatted(String.join("&", query));
        }

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request)
                .thenApply(body -> {
                    List<Order> orders = new ArrayList<>();
                    JsonNode root = readJson(body);
                    JsonNode orderNodes = root.has("orders") ? root.get("orders") : root;

                    if (orderNodes == null || !orderNodes.isArray()) {
                        return orders;
                    }

                    for (JsonNode node : orderNodes) {
                        try {
                            Order order = new Order();

                            order.setSymbol(firstText(node, "product_id", "symbol"));
                            order.setType(firstText(node, "side", "order_side"));
                            order.setStatus(firstText(node, "status", "completion_percentage"));

                            String created = firstText(node, "created_time", "created_at");

                            if (!created.isBlank()) {
                                order.setDate(Date.from(Instant.parse(created)));
                            }

                            JsonNode orderConfig = node.path("order_configuration");

                            double size = parseDouble(firstText(node, "base_size", "size", "filled_size"), 0.0);
                            double price = parseDouble(firstText(node, "limit_price", "price", "average_filled_price"),
                                    0.0);

                            if (size <= 0) {
                                size = parseDouble(orderConfig.findPath("base_size").asText("0"), 0.0);
                            }

                            if (price <= 0) {
                                price = parseDouble(orderConfig.findPath("limit_price").asText("0"), 0.0);
                            }

                            order.setQuantity(size);
                            order.setPrice(price);

                            orders.add(order);
                        } catch (Exception exception) {
                            log.debug("Unable to parse Coinbase order history node: {}", node, exception);
                        }
                    }

                    return orders;
                });
    }

    // ---------------------------------------------------------------------
    // Positions / fills
    // ---------------------------------------------------------------------

    @Override
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        requirePrivateEndpointAuth("Coinbase positions");

        HttpRequest request = authenticatedRequest("GET", ACCOUNTS_URL)
                .GET()
                .build();

        return sendAsync(request)
                .thenApply(response -> parsePositionsFromAccounts(response, tradePair));
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        requirePrivateEndpointAuth("Coinbase all positions");

        HttpRequest request = authenticatedRequest("GET", ACCOUNTS_URL)
                .GET()
                .build();

        return sendAsync(request).thenApply(this::parseAllPositionsFromAccounts);
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return fetchPositions(tradePair)
                .thenApply(positions -> {
                    if (positions == null || positions.isEmpty()) {
                        return Optional.empty();
                    }

                    return Optional.ofNullable(positions.get(0));
                });
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(
                new UnsupportedOperationException(
                        "Coinbase closePosition requires confirmed base balance mapping before execution."));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return failedFuture(
                new UnsupportedOperationException(
                        "Coinbase closeAllPositions is disabled until position-to-order sizing is fully mapped."));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        if (tradePair == null) {
            return failedFuture(new IllegalArgumentException("tradePair must not be null"));
        }

        requirePrivateEndpointAuth("Coinbase account trades");

        String product = productId(tradePair);
        String url = "%s/orders/historical/fills?product_id=%s".formatted(REST_BASE_URL, encode(product));

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request).thenApply(response -> parseTradesFromFills(response, tradePair));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        if (tradePair == null || since == null) {
            return failedFuture(new IllegalArgumentException("tradePair and since must not be null"));
        }

        return fetchAccountTrades(tradePair)
                .thenApply(trades -> trades.stream()
                        .filter(Objects::nonNull)
                        .filter(trade -> trade.getTimestamp() != null)
                        .filter(trade -> trade.getTimestamp().isAfter(since))
                        .toList());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(
            TradePair tradePair,
            Instant from,
            Instant to) {
        if (tradePair == null || from == null || to == null) {
            return failedFuture(new IllegalArgumentException("tradePair, from, and to must not be null"));
        }

        if (from.isAfter(to)) {
            return failedFuture(new IllegalArgumentException("from must be before to"));
        }

        return fetchAccountTrades(tradePair)
                .thenApply(trades -> trades.stream()
                        .filter(Objects::nonNull)
                        .filter(trade -> trade.getTimestamp() != null)
                        .filter(trade -> !trade.getTimestamp().isBefore(from))
                        .filter(trade -> !trade.getTimestamp().isAfter(to))
                        .toList());
    }

    // ---------------------------------------------------------------------
    // Manual trading / validation
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
                .thenAccept(orderId -> log.info("Coinbase BUY submitted: {}", orderId))
                .exceptionally(exception -> {
                    log.warn("Coinbase BUY failed for {}", tradePair, exception);
                    return null;
                });
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
                .thenAccept(orderId -> log.info("Coinbase SELL submitted: {}", orderId))
                .exceptionally(exception -> {
                    log.warn("Coinbase SELL failed for {}", tradePair, exception);
                    return null;
                });
    }

    @Override
    public void autoTrading(@NotNull Boolean auto, String signal) {
        if (auto) {
            // Enable bot trading and process signal
            botConfig.setEnabled(true);
            log.info("Bot trading ENABLED for Coinbase. Configured symbols: {}",
                    signalProcessor.getConfiguredSymbolsAsString());

            // Process signal if provided
            if (signal != null && !signal.isBlank()) {
                log.info("Processing signal: {}", signal);
                signalProcessor.processSignal(signal);
            }
        } else {
            // Disable bot trading
            botConfig.setEnabled(false);
            log.info("Bot trading DISABLED for Coinbase");
        }
    }

    /**
     * Process incoming trading signal
     */
    public void processTradeSignal(String signal) {
        if (botConfig.isEnabled()) {
            signalProcessor.processSignal(signal);
        } else {
            log.debug("Bot trading is disabled, ignoring signal: {}", signal);
        }
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
                && size >= getMinOrderAmount(tradePair)
                && stopLoss >= 0
                && takeProfit >= 0
                && slippage >= 0;

        return CompletableFuture.completedFuture(valid);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        if (!Double.isFinite(amount) || amount <= 0) {
            return 0.0;
        }

        return amount;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        if (!Double.isFinite(price) || price < 0) {
            return 0.0;
        }

        return price;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0.00000001;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 1.0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 500;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return CompletableFuture.completedFuture(1.0);
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase spot trading does not support per-symbol leverage."));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase does not support position modification API."));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase does not support partial position closure API."));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase does not support position closure by ID."));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase does not support position modification API."));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(
                new UnsupportedOperationException("Coinbase does not support trailing stop orders."));
    }

    // ---------------------------------------------------------------------
    // Order command consumer
    // ---------------------------------------------------------------------

    @Override
    public void createOrderAsync(Order order, OrderCommandConsumer consumer) {
        if (consumer != null) {
            consumer.onSubmitted(order);
        }

        try {
            createOrder(order)
                    .thenAccept(orderResponse -> {
                        String orderId = orderResponse == null ? "" : orderResponse.trim();

                        if (orderId.startsWith("{")) {
                            try {
                                JsonNode root = readJson(orderResponse);
                                orderId = firstText(root, "order_id", "orderId");

                                if (orderId.isBlank()) {
                                    orderId = root.path("success_response").path("order_id").asText("");
                                }
                            } catch (Exception exception) {
                                log.debug("Unable to parse Coinbase order id from response: {}", orderResponse,
                                        exception);
                            }
                        }

                        if (consumer != null) {
                            consumer.onAccepted(orderId, order);
                        }
                    })
                    .exceptionally(exception -> {
                        if (consumer != null) {
                            consumer.onError(order, exception);
                        }
                        return null;
                    });
        } catch (Exception exception) {
            if (consumer != null) {
                consumer.onError(order, exception);
            }
        }
    }

    // ---------------------------------------------------------------------
    // Convenience helpers
    // ---------------------------------------------------------------------

    public CompletableFuture<String> getServerTime() {
        HttpRequest request = baseRequest(PUBLIC_SERVER_TIME_URL)
                .GET()
                .build();

        return sendAsync(request);
    }

    public CompletableFuture<String> listAccountsRaw() {
        requirePrivateEndpointAuth("Coinbase list accounts");

        HttpRequest request = authenticatedRequest("GET", ACCOUNTS_URL)
                .GET()
                .build();

        return sendAsync(request);
    }

    public CompletableFuture<String> listOrdersRaw() {
        requirePrivateEndpointAuth("Coinbase list orders");

        String url = "%s/orders/historical/batch".formatted(REST_BASE_URL);

        HttpRequest request = authenticatedRequest("GET", url)
                .GET()
                .build();

        return sendAsync(request);
    }

    public CompletableFuture<String> getOrderRaw(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        requirePrivateEndpointAuth("Coinbase get order");

        HttpRequest request = authenticatedRequest(
                "GET",
                "%s/orders/historical/%s".formatted(REST_BASE_URL, encode(orderId))).GET().build();

        return sendAsync(request);
    }

    // ---------------------------------------------------------------------
    // Capabilities
    // ---------------------------------------------------------------------

    @Override
    public boolean supportsLiveTrading() {
        return hasPrivateEndpointAuth();
    }

    @Override
    public boolean supportsPaperTradingMode() {
        return false;
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
        return hasPrivateEndpointAuth();
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

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

    @Override
    public StreamTransport getStreamTransport() {
        return StreamTransport.WEBSOCKET;
    }

    @Override
    public boolean supportsNativeWebSocket() {
        return true;
    }

    @Override
    public boolean supportsHttpStreaming() {
        return false;
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
        return websocketClient != null && websocketClient.isOpen();
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

            if (subscription.isTrades()) {
                streamTrades(pair, consumer);
            }

            if (subscription.isOrderBook()) {
                streamOrderBook(pair, consumer);
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

        if (subscription.isFills()) {
            streamFills(consumer);
        }

        if (subscription.isPositions()) {
            streamPositions(consumer);
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
            if (subscription.isTrades()) {
                stopTradesStream(pair);
            }
            if (subscription.isOrderBook()) {
                stopOrderBookStream(pair);
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
        if (subscription.isFills()) {
            stopFillsStream();
        }
        if (subscription.isPositions()) {
            stopPositionsStream();
        }
    }

    @Override
    public void stopAllStreams() {
        try {
            if (websocketClient != null) {
                websocketClient.stopAllStreamLiveTrades();
            }
        } catch (Exception exception) {
            log.debug("Coinbase stopAllStreamLiveTrades failed", exception);
        }

        pollingStreamer.stopAll();
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamTicker(tradePair, consumer);
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {
        if (tradePair == null || consumer == null) {
            return;
        }

        connectStream();

        websocketClient.setTradePair(tradePair);

        websocketClient.streamLiveTrades(tradePair, new LiveTradesConsumer() {
            @Override
            public boolean containsKey(TradePair pair) {
                return pair != null && pair.equals(tradePair);
            }

            @Override
            public void remove(TradePair pair) {
            }

            @Override
            public void put(TradePair pair) {
            }

            @Override
            public Trade get(TradePair pair) {
                return null;
            }

            @Override
            public void accept(Trade trade) {
                if (trade != null) {
                    consumer.onTrade(getName(), tradePair, trade);
                }
            }

            @Override
            public void acceptTrades(Trade trade) {
                if (trade != null) {
                    consumer.onTrade(getName(), tradePair, trade);
                }
            }
        });
    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        pollingStreamer.streamOrderBook(tradePair, consumer);
    }

    @Override
    public void streamCandles(
            TradePair tradePair,
            int secondsPerCandle,
            ExchangeStreamConsumer consumer) {
        log.debug("Coinbase streamCandles currently no-op for {} {}", tradePair, secondsPerCandle);
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
        pollingStreamer.streamOrders(consumer);
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
        if (websocketClient != null) {
            websocketClient.stopStreamLiveTrades(tradePair);
        }
    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        pollingStreamer.stopOrderBook(tradePair);
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        log.debug("Coinbase stopCandlesStream no-op for {} {}", tradePair, secondsPerCandle);
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
        pollingStreamer.stopOrders();
    }

    @Override
    public void stopPositionsStream() {
        pollingStreamer.stopPositions();
    }

    @Override
    public boolean supportsAccountStreaming() {
        return hasPrivateEndpointAuth();
    }

    @Override
    public boolean supportsOrderStreaming() {
        return hasPrivateEndpointAuth();
    }

    @Override
    public boolean supportsPositionStreaming() {
        return hasPrivateEndpointAuth();
    }

    @Override
    public boolean supportsBalanceStreaming() {
        return hasPrivateEndpointAuth();
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
        return true;
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        if (secondsPerCandle <= 0) {
            return "ONE_MINUTE";
        }

        return switch (secondsPerCandle) {
            case 60 -> "ONE_MINUTE";
            case 300 -> "FIVE_MINUTE";
            case 900 -> "FIFTEEN_MINUTE";
            case 1800 -> "THIRTY_MINUTE";
            case 3600 -> "ONE_HOUR";
            case 7200 -> "TWO_HOUR";
            case 14400 -> {
                log.warn("Coinbase does not support 4h candles directly. Falling back to TWO_HOUR.");
                yield "TWO_HOUR";
            }
            case 21600 -> "SIX_HOUR";
            case 86400 -> "ONE_DAY";
            default -> {
                log.warn("Unsupported Coinbase timeframe: {} seconds. Falling back to ONE_MINUTE.",
                        secondsPerCandle);
                yield "ONE_MINUTE";
            }
        };
    }

    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public boolean supportsFillStreaming() {
        return hasPrivateEndpointAuth();
    }

    // ---------------------------------------------------------------------
    // Parsing helpers
    // ---------------------------------------------------------------------

    private @NotNull OrderBook parseOrderBook(String jsonResponse, TradePair tradePair) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            OrderBook orderBook = new OrderBook(tradePair);

            JsonNode priceBook = root.has("product_book") ? root.get("product_book") : root;

            JsonNode bidsNode = priceBook.path("bids");

            if (bidsNode.isArray()) {
                for (JsonNode bid : bidsNode) {
                    double price = parseDouble(firstText(bid, "price"), 0.0);
                    double size = parseDouble(firstText(bid, "size"), 0.0);

                    if (price <= 0 && bid.isArray() && bid.size() >= 2) {
                        price = bid.get(0).asDouble(0.0);
                        size = bid.get(1).asDouble(0.0);
                    }

                    if (price > 0 && size > 0) {
                        orderBook.getBids().add(new OrderBook.PriceLevel(price, size));
                    }
                }
            }

            JsonNode asksNode = priceBook.path("asks");

            if (asksNode.isArray()) {
                for (JsonNode ask : asksNode) {
                    double price = parseDouble(firstText(ask, "price"), 0.0);
                    double size = parseDouble(firstText(ask, "size"), 0.0);

                    if (price <= 0 && ask.isArray() && ask.size() >= 2) {
                        price = ask.get(0).asDouble(0.0);
                        size = ask.get(1).asDouble(0.0);
                    }

                    if (price > 0 && size > 0) {
                        orderBook.getAsks().add(new OrderBook.PriceLevel(price, size));
                    }
                }
            }

            orderBook.setTimestamp(Instant.now());
            return orderBook;
        } catch (Exception exception) {
            log.error("Failed to parse Coinbase order book", exception);
            return new OrderBook(tradePair);
        }
    }

    private List<OpenOrder> parseOpenOrders(String jsonResponse, TradePair tradePair) {
        List<OpenOrder> orders = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            JsonNode ordersNode = root.has("orders") ? root.get("orders") : root;

            if (ordersNode != null && ordersNode.isArray()) {
                for (JsonNode orderNode : ordersNode) {
                    OpenOrder order = parseOpenOrderNode(orderNode, tradePair);

                    if (order != null) {
                        orders.add(order);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("Failed to parse Coinbase open orders", exception);
        }

        return orders;
    }

    private @NotNull List<OpenOrder> parseOpenOrdersAll(String jsonResponse) {
        List<OpenOrder> orders = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            JsonNode ordersNode = root.has("orders") ? root.get("orders") : root;

            if (ordersNode != null && ordersNode.isArray()) {
                for (JsonNode orderNode : ordersNode) {
                    OpenOrder order = parseOpenOrderNodeWithTradePair(orderNode);

                    if (order != null) {
                        orders.add(order);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("Failed to parse all Coinbase open orders", exception);
        }

        return orders;
    }

    private OpenOrder parseOpenOrderNode(JsonNode node, TradePair tradePair) {
        try {
            OpenOrder order = new OpenOrder();

            order.setOrderId(firstText(node, "order_id", "id"));
            order.setTradePair(tradePair);
            order.setExchange("Coinbase");

            String created = firstText(node, "created_time", "created_at");
            if (!created.isBlank()) {
                order.setCreatedAt(Instant.parse(created));
            }

            String sideText = firstText(node, "side");
            order.setSide("SELL".equalsIgnoreCase(sideText) ? Side.SELL : Side.BUY);

            String orderTypeText = firstText(node, "order_type", "type");
            if (!orderTypeText.isBlank()) {
                try {
                    order.setOrderType(
                            OpenOrder.OrderType.valueOf(orderTypeText.toUpperCase(Locale.ROOT).replace("-", "_")));
                } catch (Exception ignored) {
                    // Keep default if model has one.
                }
            }

            double price = parseDouble(firstText(node, "price", "limit_price"), 0.0);
            double size = parseDouble(firstText(node, "size", "base_size", "filled_size"), 0.0);
            double filledSize = parseDouble(firstText(node, "filled_size"), 0.0);

            JsonNode config = node.path("order_configuration");

            if (price <= 0) {
                price = parseDouble(config.findPath("limit_price").asText("0"), 0.0);
            }

            if (size <= 0) {
                size = parseDouble(config.findPath("base_size").asText("0"), 0.0);
            }

            order.setPrice(price);
            order.setSize(size);
            order.setFilledSize(filledSize);

            String status = firstText(node, "status");
            if (!status.isBlank()) {
                try {
                    order.setStatus(OpenOrder.OrderStatus.valueOf(status.toUpperCase(Locale.ROOT)));
                } catch (Exception ignored) {
                    // Keep default if model has one.
                }
            }

            return order;
        } catch (Exception exception) {
            log.debug("Failed to parse Coinbase open order node: {}", node, exception);
            return null;
        }
    }

    private OpenOrder parseOpenOrderNodeWithTradePair(JsonNode node) {
        try {
            String product = firstText(node, "product_id", "productId");
            TradePair pair = parseTradePairFromProductId(product);
            return parseOpenOrderNode(node, pair);
        } catch (Exception exception) {
            log.debug("Failed to parse Coinbase open order node with pair: {}", node, exception);
            throw new RuntimeException("Failed to parse Coinbase open order node with pair: %s".formatted(node),
                    exception);
        }
    }

    private List<Position> parsePositionsFromAccounts(String jsonResponse, TradePair tradePair) {
        List<Position> positions = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            JsonNode accountsNode = root.has("accounts") ? root.get("accounts") : root;

            if (accountsNode != null && accountsNode.isArray()) {
                String baseSymbol = tradePair == null ? "" : String.valueOf(tradePair.getBaseCurrency());

                for (JsonNode accountNode : accountsNode) {
                    String currency = firstText(accountNode, "currency", "currency_code");

                    if (!baseSymbol.isBlank() && !currency.equalsIgnoreCase(baseSymbol)) {
                        continue;
                    }

                    Position position = parsePositionFromAccount(accountNode, tradePair);

                    if (position != null) {
                        positions.add(position);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("Failed to parse Coinbase positions from accounts", exception);
        }

        return positions;
    }

    private @NotNull List<Position> parseAllPositionsFromAccounts(String jsonResponse) {
        List<Position> positions = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            JsonNode accountsNode = root.has("accounts") ? root.get("accounts") : root;

            if (accountsNode != null && accountsNode.isArray()) {
                for (JsonNode accountNode : accountsNode) {
                    Position position = parsePositionFromAccountAll(accountNode);

                    if (position != null) {
                        positions.add(position);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("Failed to parse all Coinbase positions from accounts", exception);
        }

        return positions;
    }

    private @Nullable Position parsePositionFromAccount(JsonNode accountNode, TradePair tradePair) {
        try {
            double balance = accountBalance(accountNode);

            if (balance == 0) {
                return null;
            }

            Position position = new Position();
            position.setPositionId(firstText(accountNode, "uuid", "id"));
            position.setTradePair(tradePair);
            position.setQuantity(balance);
            position.setOpenTime(Instant.now());
            position.setIsOpen(balance > 0);
            position.setSide(balance > 0 ? Side.BUY : Side.SELL);
            position.setEntryPrice(1.0);
            position.setCurrentPrice(1.0);
            position.setUnrealizedPnl(0.0);

            return position;
        } catch (Exception exception) {
            log.debug("Failed to parse Coinbase position from account: {}", accountNode, exception);
            return null;
        }
    }

    private @Nullable Position parsePositionFromAccountAll(JsonNode accountNode) {
        try {
            double balance = accountBalance(accountNode);

            if (balance == 0) {
                return null;
            }

            String currency = firstText(accountNode, "currency", "currency_code");

            Position position = new Position();
            position.setPositionId(firstText(accountNode, "uuid", "id"));
            position.setQuantity(balance);
            position.setOpenTime(Instant.now());
            position.setIsOpen(balance > 0);
            position.setSide(balance > 0 ? Side.BUY : Side.SELL);
            position.setTradePair(new TradePair(currency, "USD"));
            position.setEntryPrice(1.0);
            position.setCurrentPrice(1.0);
            position.setUnrealizedPnl(0.0);

            return position;
        } catch (Exception exception) {
            log.debug("Failed to parse all Coinbase position from account: {}", accountNode, exception);
            return null;
        }
    }

    private double accountBalance(@NotNull JsonNode accountNode) {
        JsonNode available = accountNode.path("available_balance").path("value");

        if (!available.isMissingNode()) {
            return parseDouble(available.asText("0"), 0.0);
        }

        JsonNode value = accountNode.path("available_balance");

        if (!value.isMissingNode()) {
            return parseDouble(value.asText("0"), 0.0);
        }

        return 0.0;
    }

    private @NotNull List<Trade> parseTradesFromFills(String jsonResponse, TradePair tradePair) {
        List<Trade> trades = new ArrayList<>();

        try {
            JsonNode root = OBJECT_MAPPER.readTree(jsonResponse);
            JsonNode fillsNode = root.has("fills") ? root.get("fills") : root;

            if (fillsNode != null && fillsNode.isArray()) {
                for (JsonNode fillNode : fillsNode) {
                    Trade trade = parseTradeFromFill(fillNode, tradePair);

                    if (trade != null) {
                        trades.add(trade);
                    }
                }
            }
        } catch (Exception exception) {
            log.error("Failed to parse Coinbase trades from fills", exception);
        }

        return trades;
    }

    private @Nullable Trade parseTradeFromFill(JsonNode fillNode, TradePair tradePair) {
        try {
            double price = parseDouble(firstText(fillNode, "price"), 0.0);
            double size = parseDouble(firstText(fillNode, "size"), 0.0);
            Side side = parseSide(firstText(fillNode, "side"));
            long tradeId = parseTradeId(firstText(fillNode, "trade_id", "fill_id", "entry_id"));

            String timeText = firstText(fillNode, "trade_time", "created_at", "time");
            Instant timestamp = timeText.isBlank() ? Instant.now() : Instant.parse(timeText);

            Trade trade = new Trade(
                    tradePair,
                    price,
                    size,
                    side,
                    tradeId,
                    timestamp);

            try {
                trade.setFee(parseDouble(firstText(fillNode, "commission", "fee"), 0.0));
            } catch (Exception ignored) {
                // Fee setter may not exist in every Trade version.
            }

            return trade;
        } catch (Exception exception) {
            log.debug("Failed to parse Coinbase trade from fill: {}", fillNode, exception);
            return null;
        }
    }

    @Contract("_ -> new")
    private @NotNull TradePair parseTradePairFromProductId(String productId)
            throws SQLException, ClassNotFoundException {
        String[] parts = productId == null ? new String[0] : productId.split("-");

        if (parts.length >= 2) {
            return new TradePair(parts[0], parts[1]);
        }

        return new TradePair("UNKNOWN", "UNKNOWN");
    }

    public static <T> @NotNull CompletableFuture<T> failedFuture(Throwable throwable) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
