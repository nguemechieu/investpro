package org.investpro.exchange.ibkr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class IbkrClientPortalClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final String DEFAULT_CLIENT_PORTAL_URL = "https://localhost:5000/v1/api";

    private final ExchangeCredentials credentials;
    private final Map<String, String> conidCache = new ConcurrentHashMap<>();
    private volatile String discoveredAccountId;

    public IbkrClientPortalClient(ExchangeCredentials credentials) {
        this.credentials = credentials;
    }

    public Optional<IbkrAccountSnapshot> fetchAccountSnapshot(boolean paper) {
        try {
            String accountId = resolveAccountId();
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request("/portfolio/%s/summary".formatted(url(accountId))).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.debug("IBKR account summary request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode body = OBJECT_MAPPER.readTree(response.body());
            double equity = accountSummaryValue(body, "NetLiquidation", 0.0);
            double availableFunds = accountSummaryValue(body, "AvailableFunds", equity);
            double marginUsed = accountSummaryValue(body, "MaintMarginReq", 0.0);
            double buyingPower = accountSummaryValue(body, "BuyingPower", availableFunds);
            double cash = accountSummaryValue(body, "TotalCashValue", availableFunds);

            Map<String, Double> balances = new LinkedHashMap<>();
            balances.put("USD", equity > 0.0 ? equity : cash);

            return Optional.of(new IbkrAccountSnapshot(
                    accountId,
                    "Interactive Brokers",
                    paper,
                    positiveOr(equity, cash),
                    positiveOr(availableFunds, cash),
                    Math.max(0.0, marginUsed),
                    positiveOr(buyingPower, availableFunds),
                    balances,
                    Instant.now()));
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR live account snapshot: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<Ticker> fetchTicker(TradePair tradePair) {
        try {
            String conid = resolveConid(tradePair).orElse(null);
            if (!notBlank(conid)) {
                return Optional.empty();
            }

            String path = "/iserver/marketdata/snapshot?conids=%s&fields=31,84,85,86,88,70,71,87"
                    .formatted(url(conid));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.debug("IBKR ticker request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode tickerNode = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            double last = firstDouble(tickerNode, "31", "last", "lastPrice", "last_price");
            double bid = firstDouble(tickerNode, "84", "bid", "bidPrice");
            double ask = firstDouble(tickerNode, "86", "ask", "askPrice");
            double high = firstDouble(tickerNode, "70", "high", "highPrice");
            double low = firstDouble(tickerNode, "71", "low", "lowPrice");
            double volume = firstDouble(tickerNode, "87", "volume");
            double mid = positiveOr(last, bid > 0 && ask > 0 ? (bid + ask) / 2.0 : 0.0);
            if (mid <= 0.0) {
                return Optional.empty();
            }

            return Optional.of(new Ticker(mid, bid, ask, mid, high, low, volume, System.currentTimeMillis()));
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR live ticker for {}: {}", tradePair, exception.getMessage());
            return Optional.empty();
        }
    }

    public Optional<OrderBook> fetchOrderBook(TradePair tradePair) {
        try {
            String conid = resolveConid(tradePair).orElse(null);
            if (!notBlank(conid)) {
                return Optional.empty();
            }

            String path = "/iserver/marketdata/snapshot?conids=%s&fields=84,85,86,88,31"
                    .formatted(url(conid));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                log.debug("IBKR order book request returned HTTP {}: {}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode node = root.isArray() && !root.isEmpty() ? root.get(0) : root;
            double bid = firstDouble(node, "84", "bid", "bidPrice");
            double ask = firstDouble(node, "86", "ask", "askPrice");
            double bidSize = firstDouble(node, "88", "bidSize", "bid_size");
            double askSize = firstDouble(node, "85", "askSize", "ask_size");
            double last = firstDouble(node, "31", "last", "lastPrice");

            if (bid <= 0.0 && ask <= 0.0 && last <= 0.0) {
                return Optional.empty();
            }

            OrderBook orderBook = new OrderBook(tradePair);
            if (bid > 0.0) {
                orderBook.setBids(List.of(new OrderBook.PriceLevel(bid, Math.max(1.0, bidSize), 1)));
            }
            if (ask > 0.0) {
                orderBook.setAsks(List.of(new OrderBook.PriceLevel(ask, Math.max(1.0, askSize), 1)));
            }
            orderBook.setTimestamp(Instant.now());
            orderBook.setSequence("ibkr-live-" + System.currentTimeMillis());
            return Optional.of(orderBook);
        } catch (Exception exception) {
            log.debug("Unable to fetch IBKR live order book for {}: {}", tradePair, exception.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> resolveConid(TradePair tradePair) {
        if (tradePair == null) {
            return Optional.empty();
        }
        String symbol = ibkrSymbol(tradePair);
        String cached = conidCache.get(symbol);
        if (notBlank(cached)) {
            return Optional.of(cached);
        }
        try {
            String path = "/iserver/secdef/search?symbol=%s&name=false".formatted(url(symbol.replace("/", "")));
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request(path).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (!isSuccess(response.statusCode())) {
                return Optional.empty();
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                return Optional.empty();
            }

            JsonNode best = root.get(0);
            String conid = firstText(best, "conid", "con_id");
            if (notBlank(conid)) {
                conidCache.put(symbol, conid);
                return Optional.of(conid);
            }
        } catch (Exception exception) {
            log.debug("Unable to resolve IBKR conid for {}: {}", tradePair, exception.getMessage());
        }
        return Optional.empty();
    }

    private HttpRequest.Builder request(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(clientPortalBaseUrl() + path))
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro/1.0");
        String token = firstNonBlank(
                credentials == null ? null : credentials.accessToken(),
                credentials == null ? null : credentials.apiKey(),
                System.getenv("IBKR_ACCESS_TOKEN"));
        if (notBlank(token)) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private String clientPortalBaseUrl() {
        String configured = firstNonBlank(
                System.getenv("IBKR_CLIENT_PORTAL_URL"),
                System.getProperty("investpro.ibkr.clientPortalUrl"));
        return configured == null ? DEFAULT_CLIENT_PORTAL_URL : configured.replaceAll("/+$", "");
    }

    private String resolveAccountId() {
        String accountId = firstNonBlank(
                discoveredAccountId,
                credentials == null ? null : credentials.accountId(),
                System.getenv("IBKR_ACCOUNT_ID"),
                System.getProperty("investpro.ibkr.accountId"));
        if (!notBlank(accountId)) {
            accountId = discoverAccountId().orElse(null);
        }
        if (!notBlank(accountId)) {
            throw new IllegalStateException("IBKR account id is not configured for live mode.");
        }
        discoveredAccountId = accountId.trim();
        return discoveredAccountId;
    }

    private Optional<String> discoverAccountId() {
        for (String path : List.of("/portfolio/accounts", "/portfolio/subaccounts")) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(
                        request(path).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (!isSuccess(response.statusCode())) {
                    continue;
                }

                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                String accountId = firstAccountId(root);
                if (notBlank(accountId)) {
                    log.debug("Discovered IBKR account id from {}", path);
                    return Optional.of(accountId.trim());
                }
            } catch (Exception exception) {
                log.debug("Unable to discover IBKR account id from {}: {}", path, exception.getMessage());
            }
        }
        return Optional.empty();
    }

    private String firstAccountId(JsonNode root) {
        if (root == null || root.isNull() || root.isMissingNode()) {
            return "";
        }
        if (root.isTextual()) {
            return root.asText("");
        }
        if (root.isArray()) {
            for (JsonNode child : root) {
                String value = firstAccountId(child);
                if (notBlank(value)) {
                    return value;
                }
            }
            return "";
        }
        if (root.isObject()) {
            String direct = firstText(root, "accountId", "acctId", "account", "id");
            if (notBlank(direct)) {
                return direct;
            }
            for (var fields = root.fields(); fields.hasNext();) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String value = firstAccountId(entry.getValue());
                if (notBlank(value)) {
                    return value;
                }
            }
        }
        return "";
    }

    private String ibkrSymbol(TradePair tradePair) {
        String base = safeUpper(tradePair.getBaseCode());
        String quote = safeUpper(tradePair.getCounterCode());
        if (!quote.isBlank() && quote.length() == 3 && base.length() == 3) {
            return base + "/" + quote;
        }
        return base;
    }

    private double accountSummaryValue(JsonNode root, String key, double fallback) {
        JsonNode node = findAccountSummaryNode(root, key);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        double value = firstDouble(node, "amount", "value");
        if (value > 0.0) {
            return value;
        }
        if (node.isTextual()) {
            return parseDouble(node.asText(), fallback);
        }
        if (node.isNumber()) {
            return node.asDouble(fallback);
        }
        return fallback;
    }

    private JsonNode findAccountSummaryNode(JsonNode root, String key) {
        if (root == null || key == null || key.isBlank()) {
            return null;
        }
        if (root.isObject()) {
            JsonNode direct = root.get(key);
            if (direct != null) {
                return direct;
            }
            for (var fields = root.fields(); fields.hasNext();) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode found = findAccountSummaryNode(entry.getValue(), key);
                if (found != null) {
                    return found;
                }
            }
        } else if (root.isArray()) {
            for (JsonNode child : root) {
                JsonNode found = findAccountSummaryNode(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return "";
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText("");
                if (notBlank(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private double firstDouble(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return 0.0;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asDouble();
            }
            if (value.isTextual()) {
                double parsed = parseDouble(value.asText(), Double.NaN);
                if (Double.isFinite(parsed)) {
                    return parsed;
                }
            }
        }
        return 0.0;
    }

    private double parseDouble(String value, double fallback) {
        if (!notBlank(value)) {
            return fallback;
        }
        try {
            String sanitized = value.replace(",", "").trim();
            return Double.parseDouble(sanitized);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    private double positiveOr(double preferred, double fallback) {
        return preferred > 0.0 ? preferred : Math.max(0.0, fallback);
    }

    private String safeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (notBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
