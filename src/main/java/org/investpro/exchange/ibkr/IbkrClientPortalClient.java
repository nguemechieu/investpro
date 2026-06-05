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
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
    private static final long AUTH_STATUS_CACHE_SECONDS = 15L;
    private static final long TICKLE_REFRESH_SECONDS = 55L;
    private static final long UNAUTH_WARN_INTERVAL_SECONDS = 60L;

    private final ExchangeCredentials credentials;
    private final Map<String, String> conidCache = new ConcurrentHashMap<>();
    private volatile String discoveredAccountId;
    private volatile Instant authCheckedAt = Instant.EPOCH;
    private volatile boolean authenticated;
    private volatile Instant tickledAt = Instant.EPOCH;
    private volatile Instant unauthenticatedWarnedAt = Instant.EPOCH;
    private volatile String lastAuthFailureReason = "";
    private volatile String activeClientPortalBaseUrl;

    public IbkrClientPortalClient(ExchangeCredentials credentials) {
        this.credentials = credentials;
    }

    public boolean isAuthenticated() {
        if (!isClientPortalAuthMode()) {
            authenticated = false;
            authCheckedAt = Instant.now();
            lastAuthFailureReason = "Client Portal authentication checks are disabled in gateway mode.";
            return false;
        }

        Instant now = Instant.now();
        if (authCheckedAt.plusSeconds(AUTH_STATUS_CACHE_SECONDS).isAfter(now)) {
            if (authenticated) {
                refreshSessionKeepAliveIfNeeded(now);
            }
            return authenticated;
        }

        try {
            JsonNode payload = readAuthStatusPayload();
            authenticated = parseAuthenticated(payload);

            if (!authenticated) {
                boolean initialized = initializeBrokerageSession(payload);
                if (initialized) {
                    payload = readAuthStatusPayload();
                    authenticated = parseAuthenticated(payload);
                }
            }

            if (authenticated) {
                lastAuthFailureReason = "";
                refreshSessionKeepAliveIfNeeded(now);
            } else {
                lastAuthFailureReason = describeAuthFailure(payload);
            }

            authCheckedAt = now;
            return authenticated;
        } catch (Exception exception) {
            authenticated = false;
            authCheckedAt = now;
            String detail = rootCauseMessage(exception);
            lastAuthFailureReason = "Unable to verify IBKR authentication status: "
                    + explainConnectivityFailure(detail);
            log.warn("Unable to verify IBKR authentication status using {}: {}",
                    baseUrlCandidates(),
                    detail);
            return false;
        }
    }

    public String authenticationFailureReason() {
        if (notBlank(lastAuthFailureReason)) {
            return lastAuthFailureReason;
        }
        return "Client Portal session is not authenticated. Open the IBKR Gateway login page in a browser, complete 2FA, and retry.";
    }

    public Optional<IbkrAccountSnapshot> fetchAccountSnapshot(boolean paper) {
        if (!paper && !isClientPortalAuthMode()) {
            return Optional.empty();
        }
        if (!paper && !isAuthenticated()) {
            warnUnauthenticatedAccessDenied();
            return Optional.empty();
        }
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
            double availableFunds = accountSummaryValue(body, "AvailableFunds", 0.0);
            double marginUsed = accountSummaryValue(body, "MaintMarginReq", 0.0);
            double buyingPower = accountSummaryValue(body, "BuyingPower", 0.0);
            double cash = accountSummaryValue(body, "TotalCashValue", 0.0);

            Map<String, Double> balances = extractCurrencyBalances(body);
            if (balances.isEmpty()) {
                balances.put("USD", positiveOr(equity, cash));
            } else if (!balances.containsKey("USD")) {
                balances.put("USD", positiveOr(equity, cash));
            }

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
        if (!isAuthenticated()) {
            return Optional.empty();
        }
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
        if (!isAuthenticated()) {
            return Optional.empty();
        }
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
        if (!isAuthenticated()) {
            return Optional.empty();
        }
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
        return requestForBase(resolveRequestBaseUrl(), path);
    }

    private HttpRequest.Builder requestForBase(String baseUrl, String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro/1.0");
        String token = bearerToken();
        if (notBlank(token)) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder;
    }

    private JsonNode readAuthStatusPayload() throws Exception {
        if (!isAnyClientPortalListenerReachable()) {
            throw new IllegalStateException(
                    "No Client Portal Gateway listener detected on localhost/127.0.0.1 port 5000. "
                            + "Start IBKR Client Portal Gateway and complete browser login first.");
        }

        List<String> diagnostics = new ArrayList<>();

        for (String baseUrl : baseUrlCandidates()) {
            try {
                HttpRequest postRequest = requestForBase(baseUrl, "/iserver/auth/status")
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"))
                        .build();
                HttpResponse<String> response = HTTP_CLIENT.send(postRequest, HttpResponse.BodyHandlers.ofString());

                if (isSuccess(response.statusCode())) {
                    activeClientPortalBaseUrl = baseUrl;
                    return OBJECT_MAPPER.readTree(response.body());
                }

                HttpResponse<String> fallback = HTTP_CLIENT.send(
                        requestForBase(baseUrl, "/iserver/auth/status").GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (isSuccess(fallback.statusCode())) {
                    activeClientPortalBaseUrl = baseUrl;
                    return OBJECT_MAPPER.readTree(fallback.body());
                }

                diagnostics
                        .add("%s -> POST=%d GET=%d".formatted(baseUrl, response.statusCode(), fallback.statusCode()));
            } catch (Exception exception) {
                diagnostics.add(baseUrl + " -> " + rootCauseMessage(exception));
            }
        }

        throw new IllegalStateException("Auth status probe failed across all Client Portal endpoints: "
                + String.join(" | ", diagnostics));
    }

    private boolean isAnyClientPortalListenerReachable() {
        for (String baseUrl : baseUrlCandidates()) {
            try {
                URI uri = URI.create(baseUrl);
                String host = uri.getHost();
                int port = uri.getPort() > 0 ? uri.getPort() : 5000;
                if (!notBlank(host)) {
                    continue;
                }

                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 750);
                    return true;
                }
            } catch (Exception ignored) {
                // Try the next candidate host/port.
            }
        }
        return false;
    }

    private boolean parseAuthenticated(JsonNode payload) {
        return firstBoolean(payload, "authenticated", "isAuthenticated");
    }

    private boolean initializeBrokerageSession(JsonNode authStatusPayload) {
        if (authStatusPayload == null || authStatusPayload.isNull()) {
            return false;
        }

        boolean connected = firstBoolean(authStatusPayload, "connected");
        boolean competing = firstBoolean(authStatusPayload, "competing");

        if (!connected) {
            lastAuthFailureReason = "Gateway session is not connected. Open https://localhost:5000, sign in, and complete 2FA first.";
            return false;
        }

        if (competing && !allowCompetingSessionTakeover()) {
            lastAuthFailureReason = "Competing IBKR brokerage session detected. Close TWS/Client Portal/Mobile or enable session takeover.";
            return false;
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("publish", true);
            body.put("compete", competing && allowCompetingSessionTakeover());

            HttpResponse<String> initResponse = postJson("/iserver/auth/ssodh/init", body);
            if (!isSuccess(initResponse.statusCode())) {
                log.debug("IBKR /iserver/auth/ssodh/init returned HTTP {}", initResponse.statusCode());
                return false;
            }

            tickleSession();
            return true;
        } catch (Exception exception) {
            log.debug("Unable to initialize IBKR brokerage session: {}", exception.getMessage());
            return false;
        }
    }

    private HttpResponse<String> postJson(String path, Map<String, Object> params) throws Exception {
        String body = OBJECT_MAPPER.writeValueAsString(params == null ? Map.of() : params);
        HttpRequest request = request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void tickleSession() {
        try {
            HttpResponse<String> tickleResponse = postJson("/tickle", Map.of());
            if (isSuccess(tickleResponse.statusCode())) {
                tickledAt = Instant.now();
            }
        } catch (Exception exception) {
            log.debug("Unable to tickle IBKR session: {}", exception.getMessage());
        }
    }

    private void refreshSessionKeepAliveIfNeeded(Instant now) {
        if (tickledAt.plusSeconds(TICKLE_REFRESH_SECONDS).isAfter(now)) {
            return;
        }
        tickleSession();
    }

    private String bearerToken() {
        String token = firstNonBlank(
                System.getenv("IBKR_ACCESS_TOKEN"),
                System.getenv("IBK_ACCESS_TOKEN"),
                credentials == null ? null : credentials.accessToken());
        if (!looksLikeBearerToken(token)) {
            return null;
        }
        return token;
    }

    private boolean allowCompetingSessionTakeover() {
        return Boolean.parseBoolean(firstNonBlank(
                System.getProperty("investpro.ibkr.allowCompeteTakeover"),
                System.getenv("IBKR_ALLOW_COMPETE_TAKEOVER"),
                "false"));
    }

    private String describeAuthFailure(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return "Client Portal session is unavailable. Ensure the IBKR Gateway is running and browser login is complete.";
        }

        boolean connected = firstBoolean(payload, "connected");
        boolean competing = firstBoolean(payload, "competing");
        String message = firstText(payload, "message", "fail");

        if (!connected) {
            return "Gateway is not connected. Open https://localhost:5000, sign in via browser, and complete 2FA.";
        }
        if (competing) {
            return "Competing brokerage session detected. Close other IBKR sessions or enable takeover (investpro.ibkr.allowCompeteTakeover=true).";
        }
        if (notBlank(message)) {
            return "IBKR authentication pending: " + message;
        }
        return "IBKR session is connected but not authenticated for /iserver endpoints. Complete gateway browser login and retry.";
    }

    private boolean looksLikeBearerToken(String value) {
        if (!notBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("Bearer ")) {
            return true;
        }
        return trimmed.length() > 40 && (trimmed.contains(".") || trimmed.chars().filter(ch -> ch == '_').count() > 1);
    }

    private String clientPortalBaseUrl() {
        String configured = firstNonBlank(
                System.getenv("IBKR_CLIENT_PORTAL_URL"),
                System.getProperty("investpro.ibkr.clientPortalUrl"));
        if (!notBlank(configured)) {
            return DEFAULT_CLIENT_PORTAL_URL;
        }

        String normalized = configured.trim().replaceAll("/+$", "");
        try {
            URI uri = URI.create(normalized);
            int port = uri.getPort();
            if (port == IbkrConnectionManager.LIVE_PORT || port == IbkrConnectionManager.PAPER_PORT) {
                log.warn("Ignoring IBKR Client Portal URL '{}' because port {} is reserved for Gateway socket API. "
                        + "Use https://localhost:5000/v1/api for Client Portal.", normalized, port);
                return DEFAULT_CLIENT_PORTAL_URL;
            }
            return normalized;
        } catch (Exception exception) {
            log.warn("Invalid IBKR Client Portal URL '{}'; falling back to {}",
                    normalized,
                    DEFAULT_CLIENT_PORTAL_URL);
            return DEFAULT_CLIENT_PORTAL_URL;
        }
    }

    private String resolveRequestBaseUrl() {
        String active = activeClientPortalBaseUrl;
        return notBlank(active) ? active : clientPortalBaseUrl();
    }

    private List<String> baseUrlCandidates() {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(clientPortalBaseUrl());

        String configured = clientPortalBaseUrl();
        if (configured.contains("localhost")) {
            candidates.add(configured.replace("localhost", "127.0.0.1"));
        } else if (configured.contains("127.0.0.1")) {
            candidates.add(configured.replace("127.0.0.1", "localhost"));
        }

        candidates.add("https://localhost:5000/v1/api");
        candidates.add("https://127.0.0.1:5000/v1/api");
        candidates.add("http://localhost:5000/v1/api");
        candidates.add("http://127.0.0.1:5000/v1/api");
        return List.copyOf(candidates);
    }

    private String rootCauseMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }

        if (current == null) {
            return "Unknown error";
        }

        String message = current.getMessage();
        if (notBlank(message)) {
            return message;
        }

        if (current instanceof ClosedChannelException) {
            return "ClosedChannelException";
        }

        if (current instanceof ConnectException) {
            return "Connection refused. Ensure Client Portal Gateway is running on port 5000.";
        }

        return current.getClass().getSimpleName();
    }

    private String explainConnectivityFailure(String detail) {
        String normalized = safeUpper(detail);
        if (normalized.contains("CLOSEDCHANNELEXCEPTION")) {
            return "Client Portal Gateway closed the connection before /iserver/auth/status completed. "
                    + "Open Client Portal Gateway in a browser (https://localhost:5000), complete login/2FA, "
                    + "then retry. If already open, restart Client Portal Gateway and retry.";
        }
        if (normalized.contains("SSL") || normalized.contains("HANDSHAKE")) {
            return "TLS handshake failed while contacting Client Portal Gateway. "
                    + "Restart the gateway and ensure localhost HTTPS traffic is not intercepted.";
        }
        return detail;
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
        if (!isAuthenticated()) {
            return Optional.empty();
        }
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

    private Map<String, Double> extractCurrencyBalances(JsonNode root) {
        Map<String, Double> balances = new LinkedHashMap<>();
        collectCurrencyBalances(root, balances);
        return balances;
    }

    private void collectCurrencyBalances(JsonNode node, Map<String, Double> balances) {
        if (node == null || node.isNull() || balances == null) {
            return;
        }

        if (node.isObject()) {
            for (var fields = node.fields(); fields.hasNext();) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = safeUpper(entry.getKey());
                JsonNode value = entry.getValue();

                if (key.matches("^[A-Z]{3}$")) {
                    double parsed = value.isNumber() ? value.asDouble(0.0) : parseDouble(value.asText(), 0.0);
                    if (parsed > 0.0) {
                        balances.put(key, parsed);
                    }
                }

                collectCurrencyBalances(value, balances);
            }
            return;
        }

        if (node.isArray()) {
            for (JsonNode child : node) {
                collectCurrencyBalances(child, balances);
            }
        }
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

    private boolean firstBoolean(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return false;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isBoolean()) {
                return value.asBoolean(false);
            }
            if (value.isTextual()) {
                String text = value.asText("").trim();
                if ("true".equalsIgnoreCase(text)) {
                    return true;
                }
                if ("false".equalsIgnoreCase(text)) {
                    return false;
                }
            }
            if (value.isNumber()) {
                return value.asInt(0) != 0;
            }
        }
        return false;
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

    private boolean isClientPortalAuthMode() {
        String mode = firstNonBlank(
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void warnUnauthenticatedAccessDenied() {
        if (!isClientPortalAuthMode()) {
            return;
        }
        Instant now = Instant.now();
        if (unauthenticatedWarnedAt.plusSeconds(UNAUTH_WARN_INTERVAL_SECONDS).isAfter(now)) {
            return;
        }
        unauthenticatedWarnedAt = now;
        log.warn("IBKR access denied: {}", authenticationFailureReason());
    }
}
