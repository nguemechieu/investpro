package org.investpro.exchange;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.Account;
import org.investpro.data.InProgressCandleData;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.investpro.exchange.oanda.OandaCandleDataSupplier;
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
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@EqualsAndHashCode(callSuper = true)

@Getter
@Setter
public class Oanda extends Exchange {

    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(Oanda.class);

    private static final String OANDA_API_URL = "https://api-fxtrade.oanda.com";
    // "https://stream-fxtrade.oanda.com/v3/accounts/<ACCOUNT>/pricing/stream?instruments=EUR_USD%2CUSD_CAD"

    private static final String OANDA_STREAM_URL = "https://stream-fxtrade.oanda.com";

    private final HttpClient httpClient;
    private final PollingExchangeStreamer pollingStreamer;

    private OandaWebSocketClient websocketClient;

    private TradePair tradePair;
    private String apiKey;
    private String apiSecret;
    private String accountId;
    private boolean websocketAvailable;

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

    public Oanda(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);

        this.apiKey = safe(apiKey);
        this.apiSecret = safe(apiSecret);

        /*
         * For this adapter:
         * - apiKey = OANDA token
         * - apiSecret = OANDA account id, if known
         *
         * If apiSecret is blank, the adapter will auto-detect account id from
         * /v3/accounts.
         */
        this.accountId = safe(apiSecret);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        this.pollingStreamer = new PollingExchangeStreamer(this);

        try {
            this.websocketClient = new OandaWebSocketClient(
                    URI.create(OANDA_STREAM_URL + "/v3/accounts/%s/pricing/stream".formatted(this.accountId)),
                    new Draft_6455());
            this.websocketClient.addHeader("Authorization", "Bearer %s".formatted(this.apiKey));
            this.websocketAvailable = false;
        } catch (Exception exception) {
            logger.warn("OANDA WebSocket-style client unavailable. Polling/HTTP streaming fallback will be used.",
                    exception);
            this.websocketAvailable = false;
        }
    }

    /**
     * Constructor with notification credentials
     */
    public Oanda(String apiKey, String apiSecret, String telegramToken, String emailNotification) {
        this(apiKey, apiSecret);
        this.setTelegramToken(telegramToken);
        this.setEmailNotification(emailNotification);
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
            logger.warn("OANDA token is empty. Adapter cannot connect.");
            return;
        }

        resolveAccountId();

        if (accountId.isBlank()) {
            logger.warn("OANDA account id could not be resolved.");
            return;
        }

        logger.info("OANDA adapter connected using account {}", mask(accountId));
    }

    @Override
    public void disconnect() {
        stopAllStreams();

        try {
            if (websocketClient != null && websocketClient.isOpen()) {
                websocketClient.close();
            }
        } catch (Exception exception) {
            logger.warn("Failed to close OANDA websocket client", exception);
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

        Currency base = new FiatCurrency("EUR", "EUR", "EUR", 2, "EUR", "EUR");
        Currency quote = new FiatCurrency("USD", "USD", "USD", 2, "USD", "USD");

        tradePair = new TradePair(base, quote);
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

        String url = "%s/v3/accounts/%s/instruments".formatted(OANDA_API_URL, resolvedAccountId);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        try {
            HttpResponse<String> response = send(request);

            if (!isSuccess(response)) {
                logger.warn("OANDA instruments failed HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode instruments = root.path("instruments");

            if (!instruments.isArray()) {
                logger.warn("OANDA instruments response had unexpected format: {}", response.body());
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
                    Currency base = new FiatCurrency(parts[0], parts[0], parts[0], 2, parts[0], parts[0]);
                    Currency quote = new FiatCurrency(parts[1], parts[1], parts[1], 2, parts[1], parts[1]);
                    pairs.add(new TradePair(base, quote));
                } catch (SQLException | ClassNotFoundException exception) {
                    logger.debug("Skipping OANDA instrument {}", name, exception);
                }
            }

            return pairs;

        } catch (Exception exception) {
            logger.error("Failed to load OANDA trade pairs", exception);
            return Collections.emptyList();
        }
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
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair, apiKey);
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
                .formatted(OANDA_API_URL, toInstrument(tradePair), from, to, granularity);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA in-progress candle failed HTTP {}: {}", response.statusCode(),
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

        String url = "%s/v3/instruments/%s/orderbook".formatted(OANDA_API_URL, tradePair.toString('_'));

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA orderbook failed HTTP {}: {}", response.statusCode(), response.body());
                        return new OrderBook(tradePair);
                    }
                    return parseOandaOrderBook(response.body(), tradePair);
                });
    }

    @Contract("_, _ -> new")
    private @NotNull OrderBook parseOandaOrderBook(String body, TradePair tradePair) {
        /*
         * Keep this safe until your OrderBook model exposes bid/ask mutators.
         * Returning a typed empty OrderBook is better than null and keeps UI stable.
         */

        return new OrderBook(tradePair);
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        try {
            return fetchTicker(tradePair).join();
        } catch (Exception exception) {
            logger.warn("OANDA live price fetch failed for {}", tradePair, exception);
            return emptyTicker();
        }
    }

    @Override
    public double getLivePrice() {
        if (tradePair == null) {
            return 0.0;
        }

        Ticker ticker = getLivePrice(tradePair);

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

        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(emptyTicker());
        }

        String url = "%s/v3/accounts/%s/pricing?instruments=%s"
                .formatted(OANDA_API_URL, account, toInstrument(tradePair));

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA pricing failed HTTP {}: {}", response.statusCode(), response.body());
                        return emptyTicker();
                    }

                    try {
                        JsonNode prices = OBJECT_MAPPER.readTree(response.body()).path("prices");

                        if (!prices.isArray() || prices.isEmpty()) {
                            return emptyTicker();
                        }

                        JsonNode price = prices.get(0);

                        double bid = firstPrice(price.path("bids"));
                        double ask = firstPrice(price.path("asks"));
                        long timestamp = Instant.parse(price.path("time").asText(Instant.now().toString()))
                                .toEpochMilli();

                        Ticker ticker = new Ticker();
                        ticker.setBidPrice(bid);
                        ticker.setAskPrice(ask);
                        ticker.setTimestamp(timestamp);
                        ticker.setVolume(0);

                        return ticker;

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        if (tradePairs == null || tradePairs.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        List<CompletableFuture<Ticker>> futures = tradePairs.stream()
                .filter(Objects::nonNull)
                .map(this::fetchTicker)
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> futures.stream()
                        .map(CompletableFuture::join)
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
            return null;
        }

        String url = "%s/v3/accounts/%s/summary".formatted(OANDA_API_URL, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        try {
            HttpResponse<String> response = send(request);

            if (!isSuccess(response)) {
                logger.warn("OANDA account summary failed HTTP {}: {}", response.statusCode(), response.body());
                return null;
            }

            JsonNode accountNode = OBJECT_MAPPER.readTree(response.body()).path("account");

            Account userAccount = new Account(this, accountNode.path("alias").asText("OANDA Account"), "");
            userAccount.setEmail(accountNode.path("id").asText(account));

            return userAccount;

        } catch (Exception exception) {
            logger.error("Failed to fetch OANDA account details", exception);
            return null;
        }
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        return CompletableFuture.supplyAsync(this::getUserAccountDetails);
    }

    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        return fetchAccountSummaryDouble("NAV");
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAccountSummaryDouble("balance");
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return fetchAccountSummaryDouble("NAV");
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return fetchAccountSummaryDouble("marginUsed");
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchAccountSummaryDouble("marginAvailable");
    }

    private CompletableFuture<Double> fetchAccountSummaryDouble(String fieldName) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(0.0);
        }

        String url = "%s/v3/accounts/%s/summary".formatted(OANDA_API_URL, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA account field {} failed HTTP {}", fieldName, response.statusCode());
                        return 0.0;
                    }

                    try {
                        return OBJECT_MAPPER.readTree(response.body())
                                .path("account")
                                .path(fieldName)
                                .asDouble(0.0);
                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
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

                String url = "%s/v3/accounts/%s/orders".formatted(OANDA_API_URL, account);

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
                    logger.warn("OANDA create order failed HTTP {}: {}", response.statusCode(), response.body());
                    return "";
                }

                JsonNode root = OBJECT_MAPPER.readTree(response.body());

                String fillId = root.path("orderFillTransaction").path("id").asText("");
                String createId = root.path("orderCreateTransaction").path("id").asText("");

                return !fillId.isBlank() ? fillId : createId;

            } catch (Exception exception) {
                logger.error("Failed to create OANDA order", exception);
                return "";
            }
        });
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

        String url = "%s/v3/accounts/%s/orders/%s/cancel".formatted(OANDA_API_URL, account, orderId.trim());

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
                .filter(id -> !safe(id).isBlank())
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
        cancelAllOrders().thenAccept(result -> logger.info("OANDA cancelALL result: {}", result));
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

        String url = "%s/v3/accounts/%s/orders/%s".formatted(OANDA_API_URL, account, orderId.trim());

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

        String url = "%s/v3/accounts/%s/orders?state=PENDING".formatted(OANDA_API_URL, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA open orders failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    return parseOandaAllOpenOrders(response.body());
                });
    }

    private List<OpenOrder> parseOandaAllOpenOrders(String body) {
        try {
            JsonNode orders = OBJECT_MAPPER.readTree(body).path("orders");

            if (!orders.isArray() || orders.isEmpty()) {
                return Collections.emptyList();
            }

            List<OpenOrder> result = new ArrayList<>();

            for (JsonNode node : orders) {
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

                result.add(order);
            }

            return result;

        } catch (Exception exception) {
            logger.warn("Unable to parse OANDA open orders", exception);
            return Collections.emptyList();
        }
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String instrumentQuery = tradePair == null ? "" : "&instrument=%s".formatted(toInstrument(tradePair));
        String url = "%s/v3/accounts/%s/orders?state=ALL&count=100%s".formatted(OANDA_API_URL, account,
                instrumentQuery);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA order history failed HTTP {}: {}", response.statusCode(), response.body());
                        return Collections.emptyList();
                    }

                    try {
                        JsonNode orders = OBJECT_MAPPER.readTree(response.body()).path("orders");
                        if (!orders.isArray() || orders.isEmpty()) {
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

                        return result;

                    } catch (Exception exception) {
                        throw new RuntimeException(exception);
                    }
                });
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
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String url = "%s/v3/accounts/%s/openPositions".formatted(OANDA_API_URL, account);

        HttpRequest request = requestBuilder(url)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response)) {
                        logger.warn("OANDA positions failed HTTP {}: {}", response.statusCode(), response.body());
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
                        : Optional.ofNullable(positions.getFirst()));
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

        String url = "%s/v3/accounts/%s/positions/%s/close"
                .formatted(OANDA_API_URL, account, toInstrument(tradePair));

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
                .thenApply(_ -> "Close-all positions requires Position -> TradePair mapping.");
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        String account = resolveAccountId();

        if (account.isBlank()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String instrumentQuery = tradePair == null ? "" : "&instrument=%s".formatted(toInstrument(tradePair));
        String url = "%s/v3/accounts/%s/trades?state=OPEN%s".formatted(OANDA_API_URL, account, instrumentQuery);

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
    public void autoTrading(@NotNull Boolean auto, String signal) {

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
    public double getSize() {
        return 0;
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

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private String resolveAccountId() {
        if (!safe(accountId).isBlank()) {
            return accountId;
        }

        if (apiKey.isBlank()) {
            return "";
        }

        String url = "%s/v3/accounts".formatted(OANDA_API_URL);

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
            logger.warn("OANDA account discovery failed", exception);
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

    private HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
            Currency base = new FiatCurrency(parts[0], parts[0], parts[0], 2, parts[0], parts[0]);
            Currency quote = new FiatCurrency(parts[1], parts[1], parts[1], 2, parts[1], parts[1]);
            return new TradePair(base, quote);
        } catch (Exception exception) {
            try {
                return new TradePair("EUR", "USD");
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
}
