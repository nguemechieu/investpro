package org.investpro.exchange.kraken;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.InProgressCandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.exchange.infrastructure.StreamTransport;
import org.investpro.exchange.models.AuthCheckResult;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.exchange.websocket.ExchangeWebSocketClient;
import org.investpro.models.Account;
import org.investpro.models.trading.*;
import org.investpro.service.AuthResult;
import org.investpro.utils.CandleDataSupplier;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.investpro.exchange.oanda.Oanda.OBJECT_MAPPER;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class Kraken extends Exchange {

    private static final String KRAKEN_REST_URL = "https://api.kraken.com";
    private final HttpClient httpClient;
    private final String restUrl;
    private volatile boolean connected;
    private volatile boolean authenticated;
    private volatile boolean streamConnected;
    private final org.investpro.exchange.infrastructure.PollingExchangeStreamer polling = new org.investpro.exchange.infrastructure.PollingExchangeStreamer(this);
    private static final String API_KEY_HEADER = "API-Key";
    private static final String API_SIGN_HEADER = "API-Sign";
    private final AtomicLong nonceCounter = new AtomicLong(System.currentTimeMillis() * 1000L);

    public Kraken(ExchangeCredentials exchangeCredentials) {
        this(exchangeCredentials, HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build(), KRAKEN_REST_URL);
    }
    Kraken(ExchangeCredentials exchangeCredentials, HttpClient client, String baseUrl) {
        super(exchangeCredentials);
        httpClient = Objects.requireNonNull(client);
        restUrl = Objects.requireNonNull(baseUrl);
    }

    @Override
    public void buy(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        if (!supportsMarketType(marketType) || stopLoss > 0 || takeProfit > 0) throw new UnsupportedOperationException("Kraken protected legacy entries are not implemented"); createMarketOrder(tradePair, Side.BUY, size).join();
    }

    @Override
    public void sell(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        if (!supportsMarketType(marketType) || stopLoss > 0 || takeProfit > 0) throw new UnsupportedOperationException("Kraken protected legacy entries are not implemented"); createMarketOrder(tradePair, Side.SELL, size).join();
    }

    @Override
    public String getName() {
        return "Kraken";
    }

    @Override
    public String getSignal() {
        return "Kraken Spot Trading";
    }

    @Override
    public String getExchangeId() {
        return "kraken";
    }

    @Override
    public String getDisplayName() {
        return "Kraken";
    }

    @Override
    public boolean isSandbox() {
        return false;
    }

    @Override
    public boolean isPaperTrading() {
        if (modeRequestsLocalPaper()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return !hasLiveCredentials();
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
        return marketType == MARKET_TYPES.SPOT || marketType == MARKET_TYPES.CRYPTO;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.SPOT, MARKET_TYPES.CRYPTO);
    }

    @Override
    public @NotNull ExchangeCapability getCapability() {
        return ExchangeCapability.builder().exchangeId(getExchangeId()).exchangeName(getName()).displayName(getName()).apiBaseUrl(restUrl).authenticationType("API_KEY").supportsSpot(true).supportsCrypto(true).supportsLiveTrading(true).supportsPaperTradingMode(true).supportsAccountInfo(true).supportsBalances(true).supportsMarketOrders(true).supportsLimitOrders(true).supportsStopOrders(true).supportsOpenOrders(true).supportsTicker(true).supportsTickers(true).supportsHistoricalCandles(true).supportsOrderBook(true).supportsPollingFallback(true).supportsStreamingPrices(true).build();
    }

    @Override
    public AuthCheckResult checkAuthentication() {
        return AuthCheckResult.builder().exchangeName(getName()).success(authenticated).credentialIssue(!hasLiveCredentials()).endpointTested("/0/private/Balance").message(authenticated ? "Kraken private access verified" : "Kraken private access not verified").checkedAt(Instant.now()).build();
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
    public AuthResult AuthCheckResult(String selectedExchange) {
        authenticated = false; if (isPaperTrading()) return AuthResult.success("Local paper trading enabled; private access is not authenticated."); if (!hasLiveCredentials()) return AuthResult.failure("Kraken API key and secret are required."); try { postPrivate("/0/private/Balance", Map.of()); authenticated = true; return AuthResult.success("Kraken private account access verified."); } catch (Exception error) { return AuthResult.failure("Kraken authentication failed: " + error.getMessage()); }
    }

    @Override
    public TradePair getSelectedTradePair() throws SQLException, ClassNotFoundException {
        var pairs = getTradePairSymbol(); return pairs.isEmpty() ? null : pairs.getFirst();
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        List<TradePair> pairs = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl + "/0/public/AssetPairs"))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return pairs;
            }

            JsonNode result = OBJECT_MAPPER.readTree(response.body()).path("result");
            if (!result.isObject()) {
                return pairs;
            }

            result.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                String wsName = value.path("wsname").asText("");
                if (wsName.isBlank() || !wsName.contains("/")) {
                    return;
                }

                String[] parts = wsName.split("/");
                if (parts.length != 2) {
                    return;
                }

                try {
                    String base = normalizeSymbolFromKraken(parts[0]);
                    String quote = normalizeSymbolFromKraken(parts[1]);
                    TradePair pair = TradePair.fromSymbol(base + "_" + quote);
                    pair.setNativeSymbol(entry.getKey());
                    pairs.add(pair);
                } catch (SQLException | ClassNotFoundException ignored) {
                    // Skip symbols that cannot be represented in local TradePair registry.
                }
            });

            return pairs;
        } catch (Exception exception) {
            log.debug("Unable to fetch Kraken trade pairs", exception);
            return pairs;
        }
    }

    @Override
    public List<TradePair> getTradablePairs() throws SQLException, ClassNotFoundException {
        return getTradePairSymbol();
    }

    @Override
    public boolean supportsTradePair(TradePair tradePair) {
        return tradePair != null && getTradePairSymbol().stream().anyMatch(pair -> pair.toString('/').equalsIgnoreCase(tradePair.toString('/')));
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        if (tradePair == null) {
            return Ticker.empty();
        }
        try {
            String krakenPair = toKrakenPair(tradePair);
            String url = restUrl + "/0/public/Ticker?pair=" + url(krakenPair);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Kraken ticker request failed");
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode result = root.path("result");
            if (!result.isObject() || result.isEmpty()) {
                throw new IllegalStateException("Kraken ticker request failed");
            }

            JsonNode tickerNode = firstObjectValue(result);
            double last = firstArrayDouble(tickerNode, "c");
            double bid = firstArrayDouble(tickerNode, "b");
            double ask = firstArrayDouble(tickerNode, "a");
            double volume = firstArrayDouble(tickerNode, "v", 1);

            double fallback = getLivePrice();
            double price = last > 0 ? last : fallback;

            Ticker ticker = new Ticker();
            ticker.setLastPrice(price);
            ticker.setBidPrice(bid > 0 ? bid : price);
            ticker.setAskPrice(ask > 0 ? ask : price);
            ticker.setVolume(Math.max(0.0, volume));
            ticker.setTimestamp(System.currentTimeMillis());
            return ticker;
        } catch (Exception exception) {
            log.debug("Unable to fetch Kraken ticker for {}", tradePair, exception);
            throw new IllegalStateException("Kraken ticker request failed");
        }
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> getLivePrice(tradePair));
    }

    @Override
    public CompletableFuture<List<Ticker>> fetchTickers(List<TradePair> tradePairs) {
        return CompletableFuture.supplyAsync(() -> tradePairs.stream().map(this::getLivePrice).toList());
    }

    @Override
    public CompletableFuture<List<Ticker>> getTicker(TradePair pair) {
        return fetchTicker(pair).thenApply(List::of);
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new KrakenCandleDataSupplier(this, secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchCandleDataForInProgressCandle is not implemented"));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchRecentTradesUntil is not implemented"));
    }

    @Override
    public CompletableFuture<?> getOrderBook(TradePair tradePair) {
        return fetchOrderBook(tradePair);
    }

    @Override
    public Account getUserAccountDetails() throws ExecutionException, InterruptedException {
        return fetchAccount().get();
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (isPaperTrading()) {
            return CompletableFuture.completedFuture(localPaperAccount());
        }
        return CompletableFuture.supplyAsync(this::fetchLiveAccountFromKraken);
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        if (isPaperTrading()) {
            return CompletableFuture.completedFuture(localPaperAccount().getAvailableBalances().getOrDefault(normalizeAssetFromKraken(currencyCode), 0.0));
        }
        String normalized = normalizeAssetFromKraken(currencyCode);
        return fetchAccount().thenApply(account -> account.getBalances().getOrDefault(normalized, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return CompletableFuture.completedFuture(localPaperAccount().getAvailableBalances().getOrDefault(normalizeAssetFromKraken(currencyCode), 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchEquity() {
        return fetchAccount().thenApply(Account::getEquity);
    }

    @Override
    public CompletableFuture<Double> fetchMarginUsed() {
        return fetchAccount().thenApply(Account::getMarginUsed);
    }

    @Override
    public CompletableFuture<Double> fetchFreeMargin() {
        return fetchAccount().thenApply(Account::getFreeMargin);
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
    public CompletableFuture<String> createOrder(Order order) throws JsonProcessingException {
        if (order == null || order.getSide() == null) return failedFuture(new IllegalArgumentException("Order and side are required")); try { TradePair pair = TradePair.fromSymbol(order.getSymbol()); String type = order.getType() == null ? "MARKET" : order.getType().trim().toUpperCase(Locale.ROOT); return switch(type) { case "MARKET" -> createMarketOrder(pair, order.getSide(), order.getQuantity()); case "LIMIT" -> createLimitOrder(pair, order.getSide(), order.getQuantity(), order.getPrice()); default -> failedFuture(new UnsupportedOperationException("Unsupported Kraken order type: " + type)); }; } catch (Exception error) { return failedFuture(error); }
    }

    @Override
    public Order createOrder(int id, TradePair tradePair, String type, double price, double amount, Side side, double stopLoss, double takeProfit, double slippage) {
        return super.createOrder((long) id, tradePair, type, price, amount, side, stopLoss, takeProfit, slippage);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (isPaperTrading()) return localPaperOrderExecution().createMarketOrder(tradePair, side, amount); return CompletableFuture.supplyAsync(() -> submitKrakenOrder(tradePair, side, amount, null, "market"));
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        if (isPaperTrading()) {
            return localPaperOrderExecution().createLimitOrder(tradePair, side, amount, limitPrice);
        }
        return CompletableFuture
                .supplyAsync(() -> submitKrakenOrder(tradePair, side, amount, limitPrice, "limit"));
    }

    @Override
    public CompletableFuture<String> createStopOrder(TradePair tradePair, Side side, double amount, double stopPrice) {
        if (isPaperTrading()) return localPaperOrderExecution().createStopOrder(tradePair, side, amount, stopPrice); return CompletableFuture.supplyAsync(() -> submitKrakenOrder(tradePair, side, amount, stopPrice, "stop-loss"));
    }

    @Override
    public CompletableFuture<String> createBracketOrder(TradePair tradePair, Side side, double amount, double entryPrice, double stopLoss, double takeProfit) {
        if (isPaperTrading()) return localPaperOrderExecution().createBracketOrder(tradePair, side, amount, entryPrice, stopLoss, takeProfit); return failedFuture(new UnsupportedOperationException("Kraken bracket orders are not implemented; no entry was submitted"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        if (isPaperTrading()) {
            return localPaperOrderExecution().cancelOrder(orderId);
        }
        if (orderId == null || orderId.isBlank()) {
            return failedFuture(new IllegalArgumentException("orderId must not be blank"));
        }
        return CompletableFuture.supplyAsync(() -> {
            JsonNode root = postPrivate("/0/private/CancelOrder", Map.of("txid", orderId.trim()));
            JsonNode count = root.path("result").path("count");
            return count.isNumber() && count.asInt() > 0 ? "CANCELLED" : "NOT_FOUND";
        });
    }

    @Override
    public CompletableFuture<List<String>> cancelOrders(List<String> orderIds) {
        Objects.requireNonNull(orderIds); var futures = orderIds.stream().map(this::cancelOrder).toList(); return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenApply(_ -> futures.stream().map(CompletableFuture::join).toList());
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() {
        if (isPaperTrading()) return localPaperOrderExecution().cancelAllOrders(); return CompletableFuture.supplyAsync(() -> postPrivate("/0/private/CancelAll", Map.of()).path("result").path("count").asText());
    }

    @Override
    public CompletableFuture<Optional<Order>> fetchOrder(String orderId) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchOrder is not implemented"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        if (isPaperTrading()) {
            return localPaperOrderExecution().fetchAllOpenOrders();
        }
        return CompletableFuture.supplyAsync(this::fetchKrakenOpenOrders);
    }

    @Override
    public CompletableFuture<List<Order>> fetchOrderHistory(TradePair tradePair, Instant since) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchOrderHistory is not implemented"));
    }

    @Override
    public CompletableFuture<List<OpenOrder>> fetchOpenOrders(TradePair tradePair) {
        if (tradePair == null) {
            return fetchAllOpenOrders();
        }
        return fetchAllOpenOrders().thenApply(orders -> orders.stream()
                .filter(order -> order.getTradePair() != null
                        && order.getTradePair().toString('/').equalsIgnoreCase(tradePair.toString('/')))
                .toList());
    }

    @Override
    public CompletableFuture<OrderBook> fetchOrderBook(TradePair tradePair) {
        if (tradePair == null) {
            return CompletableFuture.completedFuture(new OrderBook(tradePair));
        }

        try {
            String krakenPair = toKrakenPair(tradePair);
            String url = restUrl + "/0/public/Depth?pair=" + url(krakenPair) + "&count=20";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return failedFuture(new IllegalStateException("Kraken order book request failed"));
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode result = root.path("result");
            if (!result.isObject() || result.isEmpty()) {
                return failedFuture(new IllegalStateException("Kraken order book request failed"));
            }

            JsonNode orderBookNode = firstObjectValue(result);
            OrderBook orderBook = new OrderBook(tradePair);
            orderBook.setBids(parsePriceLevels(orderBookNode.path("bids")));
            orderBook.setAsks(parsePriceLevels(orderBookNode.path("asks")));
            orderBook.setTimestamp(Instant.now());
            orderBook.setSequence("kraken-" + System.currentTimeMillis());
            return CompletableFuture.completedFuture(orderBook);
        } catch (Exception exception) {
            log.debug("Unable to fetch Kraken order book for {}", tradePair, exception);
            return failedFuture(new IllegalStateException("Kraken order book request failed"));
        }
    }

    @Override
    public String supportsTimeframe(int secondsPerCandle) {
        return KrakenCandleDataSupplier.INTERVALS.contains(secondsPerCandle) ? "SUPPORTED" : "UNSUPPORTED";
    }

    @Override
    public List<Timeframe> getSupportedTimeframes() {
        return Arrays.stream(Timeframe.values()).filter(timeframe -> KrakenCandleDataSupplier.INTERVALS.contains(timeframe.getSeconds())).toList();
    }

    List<org.investpro.data.CandleData> fetchCandles(TradePair pair, int seconds) {
        if (!KrakenCandleDataSupplier.INTERVALS.contains(seconds)) throw new IllegalArgumentException("Unsupported Kraken interval");
        try {
            var request = HttpRequest.newBuilder(URI.create(restUrl + "/0/public/OHLC?pair=" + url(toKrakenPair(pair)) + "&interval=" + seconds / 60))
                    .timeout(java.time.Duration.ofSeconds(15)).GET().build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Kraken OHLC HTTP " + response.statusCode());
            var root = OBJECT_MAPPER.readTree(response.body());
            if (!root.path("error").isEmpty()) throw new IllegalStateException("Kraken OHLC request rejected");
            var fields = root.path("result").fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (!field.getValue().isArray()) continue;
                List<org.investpro.data.CandleData> candles = new ArrayList<>();
                var rows = field.getValue();
                // The last Kraken OHLC row is the uncommitted current candle.
                for (int i = 0; i < rows.size() - 1; i++) {
                    var row = rows.get(i);
                    candles.add(new org.investpro.data.CandleData(row.get(1).asDouble(), row.get(4).asDouble(),
                            row.get(2).asDouble(), row.get(3).asDouble(), row.get(0).asInt(), row.get(6).asDouble()));
                }
                return List.copyOf(candles);
            }
            throw new IllegalStateException("Kraken returned no OHLC series");
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new java.util.concurrent.CancellationException("Kraken candles cancelled");
        } catch (Exception error) { throw new IllegalStateException("Kraken candles unavailable", error); }
    }
    private List<OrderBook.PriceLevel> parsePriceLevels(JsonNode levelsNode) {
        List<OrderBook.PriceLevel> levels = new ArrayList<>();
        if (!levelsNode.isArray()) {
            return levels;
        }

        for (JsonNode level : levelsNode) {
            if (!level.isArray() || level.size() < 2) {
                continue;
            }
            double price = parseDouble(level.get(0).asText("0"));
            double quantity = parseDouble(level.get(1).asText("0"));
            if (price > 0 && quantity > 0) {
                levels.add(new OrderBook.PriceLevel(price, quantity));
            }
        }
        return levels;
    }

    private String toKrakenPair(TradePair pair) {
        String base = normalizeSymbolToKraken(pair.getBaseCode());
        String quote = normalizeSymbolToKraken(pair.getCounterCode());
        return base + quote;
    }

    private Account fetchLiveAccountFromKraken() {
        JsonNode root = postPrivate("/0/private/Balance", Map.of());
        JsonNode result = root.path("result");

        Map<String, Double> balances = new LinkedHashMap<>();
        if (result.isObject()) {
            result.fields().forEachRemaining(entry -> {
                double value = parseDouble(entry.getValue().asText("0"));
                if (value <= 0.0) {
                    return;
                }
                balances.put(normalizeAssetFromKraken(entry.getKey()), value);
            });
        }

        Account account = new Account();
        account.setExchange(this);
        account.setExchangeId(getExchangeId());
        account.setBrokerName(getDisplayName());
        account.setAccountId(getCredentials() == null ? "" : safe(getCredentials().accountId()));
        account.setAccount(account.getAccountId());
        account.setPaperTrading(false);
        account.setSandbox(false);
        account.setConnected(Boolean.TRUE.equals(isConnected()));
        account.setBalances(balances);
        account.setAvailableBalances(new LinkedHashMap<>(balances));
        account.setLockedBalances(new LinkedHashMap<>());
        account.recalculateBalanceTotals();

        double usd = balances.getOrDefault("USD", balances.getOrDefault("USDT", 0.0));

        JsonNode tradeBalance = fetchTradeBalanceSafely();
        double equivalentBalance = readTradeBalanceMetric(tradeBalance, "eb", "tb", "e", "v");
        double tradeBalanceValue = readTradeBalanceMetric(tradeBalance, "tb", "eb", "e");
        double equity = readTradeBalanceMetric(tradeBalance, "e", "tb", "eb", "v");
        double marginUsed = readTradeBalanceMetric(tradeBalance, "m");
        double freeMargin = readTradeBalanceMetric(tradeBalance, "mf", "tb", "eb");
        double marginLevel = readTradeBalanceMetric(tradeBalance, "ml");

        double resolvedPortfolio = equivalentBalance > 0.0 ? equivalentBalance
                : (tradeBalanceValue > 0.0 ? tradeBalanceValue : account.getTotalBalance());
        double resolvedEquity = equity > 0.0 ? equity : resolvedPortfolio;
        double resolvedFreeMargin = freeMargin > 0.0 ? freeMargin : usd;

        account.setCash(usd);
        account.setAvailableBalance(resolvedFreeMargin > 0.0 ? resolvedFreeMargin : usd);
        account.setTotalBalance(resolvedPortfolio > 0.0 ? resolvedPortfolio : usd);
        account.setPortfolioValue(resolvedPortfolio > 0.0 ? resolvedPortfolio : usd);
        account.setEquity(resolvedEquity > 0.0 ? resolvedEquity : account.getPortfolioValue());
        account.setNav(account.getPortfolioValue());
        account.setMarginUsed(Math.max(0.0, marginUsed));
        account.setFreeMargin(Math.max(0.0, resolvedFreeMargin));
        account.setMarginAvailable(account.getFreeMargin());
        account.setBuyingPower(account.getFreeMargin());
        if (marginLevel > 0.0) {
            account.getMetadata().put("krakenMarginLevel", marginLevel);
        }
        account.getMetadata().put("krakenTradeBalanceAsset", "ZUSD");
        account.setUpdatedAt(Instant.now());
        return account;
    }

    private JsonNode fetchTradeBalanceSafely() {
        try {
            JsonNode root = postPrivate("/0/private/TradeBalance", Map.of("asset", "ZUSD"));
            JsonNode result = root.path("result");
            return result.isObject() ? result : OBJECT_MAPPER.createObjectNode();
        } catch (Exception exception) {
            // Some API keys do not have margin/trade-balance scope; keep account snapshot
            // functional.
            log.debug("Kraken TradeBalance unavailable, falling back to balance-only metrics", exception);
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private double readTradeBalanceMetric(JsonNode tradeBalance, String... keys) {
        if (tradeBalance == null || !tradeBalance.isObject() || keys == null) {
            return 0.0;
        }
        for (String key : keys) {
            if (key == null || key.isBlank()) {
                continue;
            }
            JsonNode node = tradeBalance.path(key);
            if (node.isMissingNode() || node.isNull()) {
                continue;
            }
            double parsed = parseDouble(node.asText("0"));
            if (parsed > 0.0) {
                return parsed;
            }
        }
        return 0.0;
    }

    private List<OpenOrder> fetchKrakenOpenOrders() {
        JsonNode root = postPrivate("/0/private/OpenOrders", Map.of("trades", "false"));
        JsonNode open = root.path("result").path("open");
        if (!open.isObject()) {
            return List.of();
        }

        List<OpenOrder> orders = new ArrayList<>();
        open.fields().forEachRemaining(entry -> {
            OpenOrder parsed = parseKrakenOpenOrder(entry.getKey(), entry.getValue());
            if (parsed != null) {
                orders.add(parsed);
            }
        });

        orders.sort(Comparator.comparing(OpenOrder::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return orders;
    }

    private OpenOrder parseKrakenOpenOrder(String txid, JsonNode node) {
        try {
            if (node == null || !node.isObject()) {
                return null;
            }

            JsonNode descr = node.path("descr");
            String pairString = descr.path("pair").asText("");
            TradePair pair = parseKrakenTradePair(pairString);

            OpenOrder order = new OpenOrder();
            order.setOrderId(txid);
            order.setTradePair(pair);
            order.setExchange(getExchangeId());
            order.setSide("sell".equalsIgnoreCase(descr.path("type").asText("buy")) ? Side.SELL : Side.BUY);
            order.setOrderType(resolveOrderType(descr.path("ordertype").asText("limit")));
            order.setPrice(parseDouble(descr.path("price").asText("0")));
            order.setSize(parseDouble(node.path("vol").asText("0")));
            order.setFilledSize(parseDouble(node.path("vol_exec").asText("0")));
            order.setRemainingSize(Math.max(0.0, order.getSize() - order.getFilledSize()));
            order.setStatus(OpenOrder.OrderStatus.OPEN);

            long openSeconds = node.path("opentm").asLong(0);
            if (openSeconds > 0) {
                Instant created = Instant.ofEpochSecond(openSeconds);
                order.setCreatedAt(created);
                order.setUpdatedAt(created);
            }
            return order;
        } catch (Exception exception) {
            log.debug("Unable to parse Kraken open order {}", txid, exception);
            return null;
        }
    }

    private TradePair parseKrakenTradePair(String pairString) {
        if (pairString == null || pairString.isBlank()) {
            return null;
        }
        String[] parts = pairString.split("/");
        if (parts.length != 2) {
            return null;
        }
        try {
            String base = normalizeSymbolFromKraken(parts[0]);
            String quote = normalizeSymbolFromKraken(parts[1]);
            return TradePair.fromSymbol(base + "_" + quote);
        } catch (Exception exception) {
            return null;
        }
    }

    private OpenOrder.OrderType resolveOrderType(String orderTypeText) {
        if (orderTypeText == null) {
            return OpenOrder.OrderType.LIMIT;
        }
        String normalized = orderTypeText.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "market" -> OpenOrder.OrderType.MARKET;
            case "stop-loss" -> OpenOrder.OrderType.STOP_LOSS;
            case "take-profit" -> OpenOrder.OrderType.TAKE_PROFIT;
            case "stop-loss-limit" -> OpenOrder.OrderType.STOP_LIMIT;
            case "trailing-stop" -> OpenOrder.OrderType.TRAILING_STOP;
            default -> OpenOrder.OrderType.LIMIT;
        };
    }

    private String submitKrakenOrder(TradePair tradePair,
            Side side,
            double amount,
            Double price,
            String orderType) {
        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

        if (side != Side.BUY && side != Side.SELL) throw new IllegalArgumentException("BUY or SELL required");
        if (!"market".equals(orderType) && (price == null || !Double.isFinite(price) || price <= 0)) throw new IllegalArgumentException("Positive finite price required");
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("pair", toKrakenPair(tradePair));
        payload.put("type", side == Side.SELL ? "sell" : "buy");
        payload.put("ordertype", orderType);
        payload.put("volume", stripTrailingZeros(amount));
        if (price != null && price > 0.0) {
            payload.put("price", stripTrailingZeros(price));
        }

        JsonNode root = postPrivate("/0/private/AddOrder", payload);
        JsonNode txids = root.path("result").path("txid");
        if (txids.isArray() && !txids.isEmpty()) {
            return txids.get(0).asText("KRK-UNKNOWN");
        }
        throw new IllegalStateException("Kraken AddOrder returned no txid");
    }

    private JsonNode postPrivate(String path, Map<String, String> params) {
        ExchangeCredentials credentials = getCredentials();
        if (!hasLiveCredentials()) {
            throw new IllegalStateException("Kraken credentials are not configured for private endpoint access");
        }

        try {
            String nonce = nextNonce();
            String body = buildBodyWithNonce(nonce, params);
            String signature = sign(path, nonce, body, credentials.apiSecret());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl + path))
                    .timeout(java.time.Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header(API_KEY_HEADER, credentials.apiKey().trim())
                    .header(API_SIGN_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Kraken private request failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode errors = root.path("error");
            if (errors.isArray() && !errors.isEmpty()) {
                throw new IllegalStateException("Kraken private request returned errors: " + errors);
            }
            return root;
        } catch (Exception exception) {
            throw new IllegalStateException("Kraken private request failed for " + path + ": " + exception.getMessage(),
                    exception);
        }
    }

    private String buildBodyWithNonce(String nonce, Map<String, String> params) {
        StringBuilder body = new StringBuilder("nonce=").append(url(nonce));
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                    continue;
                }
                String value = entry.getValue() == null ? "" : entry.getValue();
                body.append('&').append(url(entry.getKey())).append('=').append(url(value));
            }
        }
        return body.toString();
    }

    private String sign(String path, String nonce, String body, String apiSecret) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest((nonce + body).getBytes(StandardCharsets.UTF_8));

        byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
        byte[] message = new byte[pathBytes.length + hash.length];
        System.arraycopy(pathBytes, 0, message, 0, pathBytes.length);
        System.arraycopy(hash, 0, message, pathBytes.length, hash.length);

        byte[] secret = Base64.getDecoder().decode(apiSecret.trim());
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret, "HmacSHA512"));
        byte[] signed = mac.doFinal(message);
        return Base64.getEncoder().encodeToString(signed);
    }

    private String nextNonce() {
        return String.valueOf(
                nonceCounter.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis() * 1000L)));
    }

    private boolean hasLiveCredentials() {
        ExchangeCredentials credentials = getCredentials();
        return credentials != null
                && credentials.apiKey() != null
                && !credentials.apiKey().isBlank()
                && credentials.apiSecret() != null
                && !credentials.apiSecret().isBlank();
    }

    private String normalizeAssetFromKraken(String asset) {
        if (asset == null || asset.isBlank()) {
            return "";
        }
        String normalized = asset.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "XXBT", "XBT" -> "BTC";
            case "XETH" -> "ETH";
            case "XXDG", "XDG" -> "DOGE";
            case "ZEUR" -> "EUR";
            case "ZUSD" -> "USD";
            case "ZCAD" -> "CAD";
            case "ZGBP" -> "GBP";
            case "ZJPY" -> "JPY";
            default -> {
                if (normalized.length() == 4 && (normalized.startsWith("X") || normalized.startsWith("Z"))) {
                    yield normalized.substring(1);
                }
                yield normalized;
            }
        };
    }

    private String stripTrailingZeros(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeSymbolToKraken(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BTC" -> "XBT";
            case "XETH" -> "XETH";
            default -> normalized;
        };
    }

    private String normalizeSymbolFromKraken(String symbol) {
        if (symbol == null) {
            return "";
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "XBT" -> "BTC";
            case "XDG" -> "DOGE";
            default -> normalized;
        };
    }

    private JsonNode firstObjectValue(JsonNode objectNode) {
        if (objectNode == null || !objectNode.isObject()) {
            return OBJECT_MAPPER.createObjectNode();
        }
        return objectNode.fields().hasNext()
                ? objectNode.fields().next().getValue()
                : OBJECT_MAPPER.createObjectNode();
    }

    private double firstArrayDouble(JsonNode node, String field) {
        return firstArrayDouble(node, field, 0);
    }

    private double firstArrayDouble(JsonNode node, String field, int index) {
        if (node == null || field == null) {
            return 0.0;
        }
        JsonNode value = node.path(field);
        if (!value.isArray() || value.size() <= index) {
            return 0.0;
        }
        return parseDouble(value.get(index).asText("0"));
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return 0.0;
        }
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    public void connect() {
        connected = isPaperTrading() || AuthCheckResult(getName()).success();
    }

    @Override
    public void disconnect() {
        connected = false; authenticated = false; stopAllStreams();
    }

    @Override
    public void reconnect() {
        disconnect(); connect();
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
    public CompletableFuture<List<Position>> fetchPositions(TradePair tradePair) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchPositions is not implemented"));
    }

    @Override
    public CompletableFuture<List<Position>> fetchAllPositions() {
        return failedFuture(new UnsupportedOperationException("Kraken fetchAllPositions is not implemented"));
    }

    @Override
    public CompletableFuture<Optional<Position>> fetchPosition(TradePair tradePair) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchPosition is not implemented"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair tradePair) {
        return failedFuture(new UnsupportedOperationException("Kraken closePosition is not implemented"));
    }

    @Override
    public CompletableFuture<String> closeAllPositions() {
        return failedFuture(new UnsupportedOperationException("Kraken closeAllPositions is not implemented"));
    }

    @Override
    public CompletableFuture<String> closePosition(TradePair symbol, String positionId) {
        return failedFuture(new UnsupportedOperationException("Kraken closePosition is not implemented"));
    }

    @Override
    public CompletableFuture<String> closePartialPosition(TradePair symbol, String positionId, double quantity) {
        return failedFuture(new UnsupportedOperationException("Kraken closePartialPosition is not implemented"));
    }

    @Override
    public CompletableFuture<String> modifyStopLoss(TradePair symbol, String positionId, double stopLoss) {
        return failedFuture(new UnsupportedOperationException("Kraken modifyStopLoss is not implemented"));
    }

    @Override
    public CompletableFuture<String> modifyTakeProfit(TradePair symbol, String positionId, double takeProfit) {
        return failedFuture(new UnsupportedOperationException("Kraken modifyTakeProfit is not implemented"));
    }

    @Override
    public CompletableFuture<String> enableTrailingStop(TradePair symbol, String positionId, double trailingDistance) {
        return failedFuture(new UnsupportedOperationException("Kraken enableTrailingStop is not implemented"));
    }

    @Override
    public CompletableFuture<Boolean> validateOrder(TradePair tradePair, MARKET_TYPES marketType, double size, double side, double stopLoss, double takeProfit, double slippage) {
        return CompletableFuture.completedFuture(tradePair != null && supportsMarketType(marketType) && Double.isFinite(size) && size > 0 && Double.isFinite(slippage) && slippage >= 0);
    }

    @Override
    public double normalizeAmount(TradePair tradePair, double amount) {
        return Double.isFinite(amount) && amount > 0 ? amount : 0;
    }

    @Override
    public double normalizePrice(TradePair tradePair, double price) {
        return Double.isFinite(price) && price > 0 ? price : 0;
    }

    @Override
    public double getMinOrderAmount(TradePair tradePair) {
        return 0;
    }

    @Override
    public double getMinOrderNotional(TradePair tradePair) {
        return 0;
    }

    @Override
    public double getMaxLeverage(TradePair tradePair) {
        return 0;
    }

    @Override
    public CompletableFuture<Double> fetchLeverage(TradePair tradePair) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchLeverage is not implemented"));
    }

    @Override
    public CompletableFuture<String> setLeverage(TradePair tradePair, double leverage) {
        return failedFuture(new UnsupportedOperationException("Kraken setLeverage is not implemented"));
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
        streamConnected = true;
    }

    @Override
    public void disconnectStream() {
        stopAllStreams();
    }

    @Override
    public boolean isStreamConnected() {
        return streamConnected;
    }

    @Override
    public void reconnectStream() {
        connectStream();
    }

    @Override
    public void stream(ExchangeStreamSubscription subscription, ExchangeStreamConsumer consumer) {
        Objects.requireNonNull(subscription); Objects.requireNonNull(consumer); for (TradePair pair : subscription.getTradePairs()) { if (subscription.isTicker()) streamTicker(pair, consumer); if (subscription.isOrderBook()) streamOrderBook(pair, consumer); if (subscription.isCandles()) streamCandles(pair, subscription.getSecondsPerCandle(), consumer); } if (hasPrivateAuthentication()) { if (subscription.isAccount() || subscription.isBalances()) polling.streamAccount(consumer); if (subscription.isOrders()) polling.streamOrders(consumer); } streamConnected = true;
    }

    @Override
    public void stopStreaming(ExchangeStreamSubscription subscription) {
        for (TradePair pair : subscription.getTradePairs()) { if (subscription.isTicker()) polling.stopTicker(pair); if (subscription.isOrderBook()) polling.stopOrderBook(pair); if (subscription.isCandles()) polling.stopCandles(pair, subscription.getSecondsPerCandle()); } if (subscription.isAccount() || subscription.isBalances()) polling.stopAccount(); if (subscription.isOrders()) polling.stopOrders();
    }

    @Override
    public void stopAllStreams() {
        polling.stopAll(); streamConnected = false;
    }

    @Override
    public void streamTicker(TradePair tradePair, ExchangeStreamConsumer consumer) {
        polling.streamTicker(tradePair, consumer); streamConnected = true;
    }

    @Override
    public void streamTrades(TradePair tradePair, ExchangeStreamConsumer consumer) {

    }

    @Override
    public void subscribeTrades(@NotNull TradePair tradePair, @NotNull ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamOrderBook(TradePair tradePair, ExchangeStreamConsumer consumer) {
        polling.streamOrderBook(tradePair, consumer); streamConnected = true;
    }

    @Override
    public void streamCandles(TradePair tradePair, int secondsPerCandle, ExchangeStreamConsumer consumer) {
        polling.streamCandles(tradePair, secondsPerCandle, consumer); streamConnected = true;
    }

    @Override
    public void streamAccount(ExchangeStreamConsumer consumer) {
        if (hasPrivateAuthentication()) polling.streamAccount(consumer);
    }

    @Override
    public void streamBalances(ExchangeStreamConsumer consumer) {
        if (hasPrivateAuthentication()) polling.streamAccount(consumer);
    }

    @Override
    public void streamOrders(ExchangeStreamConsumer consumer) {
        if (hasPrivateAuthentication()) polling.streamOrders(consumer);
    }

    @Override
    public void streamFills(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void streamPositions(ExchangeStreamConsumer consumer) {

    }

    @Override
    public void stopTickerStream(TradePair tradePair) {
        polling.stopTicker(tradePair);
    }

    @Override
    public void stopTradesStream(TradePair tradePair) {

    }

    @Override
    public void stopOrderBookStream(TradePair tradePair) {
        polling.stopOrderBook(tradePair);
    }

    @Override
    public void stopCandlesStream(TradePair tradePair, int secondsPerCandle) {
        polling.stopCandles(tradePair, secondsPerCandle);
    }

    @Override
    public void stopAccountStream() {
        polling.stopAccount();
    }

    @Override
    public void stopBalancesStream() {
        polling.stopAccount();
    }

    @Override
    public void stopOrdersStream() {
        polling.stopOrders();
    }

    @Override
    public void stopFillsStream() {

    }

    @Override
    public void stopPositionsStream() {

    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTrades(TradePair tradePair) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchAccountTrades is not implemented"));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesSince(TradePair tradePair, Instant since) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchAccountTradesSince is not implemented"));
    }

    @Override
    public CompletableFuture<List<Trade>> fetchAccountTradesBetween(TradePair tradePair, Instant from, Instant to) {
        return failedFuture(new UnsupportedOperationException("Kraken fetchAccountTradesBetween is not implemented"));
    }
}
