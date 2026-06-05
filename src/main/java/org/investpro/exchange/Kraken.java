package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.Account;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.service.AuthResult;
import org.investpro.utils.Side;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CompletableFuture;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class Kraken extends Bitfinex {

    private static final String KRAKEN_REST_URL = "https://api.kraken.com";
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String API_KEY_HEADER = "API-Key";
    private static final String API_SIGN_HEADER = "API-Sign";
    private final AtomicLong nonceCounter = new AtomicLong(System.currentTimeMillis() * 1000L);

    public Kraken(ExchangeCredentials exchangeCredentials) {
        super(exchangeCredentials);
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
    public boolean isPaperTrading() {
        if (modeRequestsPaperNetwork()) {
            return true;
        }
        if (modeRequestsLiveNetwork()) {
            return false;
        }
        return !hasLiveCredentials();
    }

    @Override
    public boolean supportsLiveTrading() {
        return hasLiveCredentials();
    }

    @Override
    public AuthResult AuthCheckResult(String selectedExchange) {
        if (isPaperTrading()) {
            return AuthResult.success("Kraken paper trading mode enabled.");
        }

        ExchangeCredentials credentials = getCredentials();
        if (credentials == null || credentials.apiKey() == null || credentials.apiKey().isBlank()) {
            return AuthResult.failure("Kraken API key is missing.");
        }
        if (credentials.apiSecret() == null || credentials.apiSecret().isBlank()) {
            return AuthResult.failure("Kraken API secret is missing.");
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KRAKEN_REST_URL + "/0/public/SystemStatus"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                JsonNode status = root.path("result").path("status");
                if (!status.isMissingNode() && !status.asText("").isBlank()) {
                    return AuthResult.success("Kraken connectivity verified. System status: " + status.asText());
                }
                return AuthResult.success("Kraken connectivity verified.");
            }
            return AuthResult.failure("Kraken connectivity check failed (HTTP " + response.statusCode() + ").");
        } catch (Exception exception) {
            log.warn("Kraken auth/connectivity check failed", exception);
            return AuthResult.failure("Kraken connectivity check failed: " + exception.getMessage());
        }
    }

    @Override
    public List<TradePair> getTradePairSymbol() {
        List<TradePair> pairs = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(KRAKEN_REST_URL + "/0/public/AssetPairs"))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

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
    public Ticker getLivePrice(TradePair tradePair) {
        if (tradePair == null) {
            return Ticker.empty();
        }
        try {
            String krakenPair = toKrakenPair(tradePair);
            String url = KRAKEN_REST_URL + "/0/public/Ticker?pair=" + url(krakenPair);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return super.getLivePrice(tradePair);
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode result = root.path("result");
            if (!result.isObject() || result.isEmpty()) {
                return super.getLivePrice(tradePair);
            }

            JsonNode tickerNode = firstObjectValue(result);
            double last = firstArrayDouble(tickerNode, "c");
            double bid = firstArrayDouble(tickerNode, "b");
            double ask = firstArrayDouble(tickerNode, "a");
            double volume = firstArrayDouble(tickerNode, "v", 1);

            double fallback = super.getLivePrice(tradePair).getLastPrice();
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
            return super.getLivePrice(tradePair);
        }
    }

    @Override
    public CompletableFuture<Ticker> fetchTicker(TradePair tradePair) {
        return CompletableFuture.completedFuture(getLivePrice(tradePair));
    }

    @Override
    public CompletableFuture<Account> fetchAccount() {
        if (isPaperTrading()) {
            return super.fetchAccount();
        }
        return CompletableFuture.supplyAsync(this::fetchLiveAccountFromKraken);
    }

    @Override
    public CompletableFuture<Double> fetchAvailableBalance(String currencyCode) {
        if (isPaperTrading()) {
            return super.fetchAvailableBalance(currencyCode);
        }
        String normalized = normalizeAssetFromKraken(currencyCode);
        return fetchAccount().thenApply(account -> account.getBalances().getOrDefault(normalized, 0.0));
    }

    @Override
    public CompletableFuture<Double> fetchTotalBalance(String currencyCode) {
        return fetchAvailableBalance(currencyCode);
    }

    @Override
    public CompletableFuture<String> createMarketOrder(TradePair tradePair, Side side, double amount) {
        if (isPaperTrading()) {
            return super.createMarketOrder(tradePair, side, amount);
        }
        return CompletableFuture.supplyAsync(() -> submitKrakenOrder(tradePair, side, amount, null, "market"));
    }

    @Override
    public CompletableFuture<String> createLimitOrder(TradePair tradePair, Side side, double amount,
            double limitPrice) {
        if (isPaperTrading()) {
            return super.createLimitOrder(tradePair, side, amount, limitPrice);
        }
        return CompletableFuture
                .supplyAsync(() -> submitKrakenOrder(tradePair, side, amount, limitPrice, "limit"));
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        if (isPaperTrading()) {
            return super.cancelOrder(orderId);
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
    public CompletableFuture<List<OpenOrder>> fetchAllOpenOrders() {
        if (isPaperTrading()) {
            return super.fetchAllOpenOrders();
        }
        return CompletableFuture.supplyAsync(this::fetchKrakenOpenOrders);
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
            String url = KRAKEN_REST_URL + "/0/public/Depth?pair=" + url(krakenPair) + "&count=20";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return super.fetchOrderBook(tradePair);
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode result = root.path("result");
            if (!result.isObject() || result.isEmpty()) {
                return super.fetchOrderBook(tradePair);
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
            return super.fetchOrderBook(tradePair);
        }
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
        if (amount <= 0.0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }

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
                    .uri(URI.create(KRAKEN_REST_URL + path))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header(API_KEY_HEADER, credentials.apiKey().trim())
                    .header(API_SIGN_HEADER, signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Kraken private request failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode errors = root.path("error");
            if (errors.isArray() && !errors.isEmpty()) {
                throw new IllegalStateException("Kraken private request returned errors: " + errors.toString());
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
}
