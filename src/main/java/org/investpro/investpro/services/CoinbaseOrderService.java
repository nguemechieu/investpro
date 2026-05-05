package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.investpro.CreateOrderRequest;
import org.investpro.investpro.ENUM_ORDER_STATUS;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.models.Order;
import org.investpro.investpro.models.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class CoinbaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseOrderService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private final HttpClient httpClient;
    private final CoinbaseExchangeAuth auth;

    public CoinbaseOrderService(String apiKey, String apiSecret, String passphrase, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.passphrase = passphrase;
        this.httpClient = httpClient;
        this.auth = new CoinbaseExchangeAuth(apiKey, apiSecret, passphrase);
    }

    public List<Order> getOrders(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        URI uri = buildOrdersUri(tradePair, null);
        HttpRequest request = signedRequest("GET", uri, "", false);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.warn("Unable to fetch Coinbase orders. HTTP {}: {}", response.statusCode(), response.body());
            return List.of();
        }
        return parseOrders(response.body());
    }

    public List<Order> getOpenOrder(TradePair tradePair) throws IOException, InterruptedException {
        URI uri = buildOrdersUri(tradePair, "OPEN");
        HttpRequest request = signedRequest("GET", uri, "", false);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.warn("Unable to fetch Coinbase open orders for {}. HTTP {}: {}", tradePair, response.statusCode(), response.body());
            return List.of();
        }
        return parseOrders(response.body());
    }

    public List<Order> getPendingOrders() {
        try {
            return getOrders(null).stream().filter(Order::isOrderActive).toList();
        } catch (Exception e) {
            logger.warn("Unable to fetch Coinbase pending orders", e);
            return List.of();
        }
    }

    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType, double price, double size,
                            Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('/'), side, orderType, price, size, timestamp, stopLoss, takeProfit);
        String body = objectMapper.writeValueAsString(buildCreateOrderPayload(orderRequest, tradePair));
        URI uri = URI.create(Coinbase.BROKERAGE_API_URL + "/orders");

        HttpRequest request = signedRequest("POST", uri, body, true);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201) {
            throw new RuntimeException("Error creating order: " + response.body());
        }
        logger.info("Order created: {}", response.body());
    }

    public void cancelOrder(String orderId) throws IOException, InterruptedException {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("order_ids").add(orderId);
        URI uri = URI.create(Coinbase.BROKERAGE_API_URL + "/orders/batch_cancel");
        HttpRequest request = signedRequest("POST", uri, objectMapper.writeValueAsString(body), true);

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 204) {
            throw new RuntimeException("Error cancelling order: " + response.body());
        }
        logger.info("Order cancelled: {}", orderId);
    }

    public void cancelAllOrders() {
        logger.warn("cancelAllOrders() not implemented. Coinbase API does not support it directly.");
    }

    private List<Order> parseOrders(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode ordersNode = root.has("orders") ? root.get("orders") : root;
        if (ordersNode == null || !ordersNode.isArray()) {
            return List.of();
        }

        List<Order> orders = new ArrayList<>();
        for (JsonNode orderNode : ordersNode) {
            orders.add(mapOrder(orderNode));
        }
        return orders;
    }

    private HttpRequest signedRequest(String method, URI uri, String body, boolean jsonBody) throws IOException {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder().uri(uri);
            auth.authorize(builder, method, uri, body);
            if (jsonBody) {
                builder.header("Content-Type", "application/json");
            }
            return builder.method(method, body == null || body.isEmpty()
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body)).build();
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Coinbase credentials are not in the expected Advanced Trade format.", e);
        }
    }

    private static URI buildOrdersUri(TradePair tradePair, String orderStatus) {
        StringBuilder uri = new StringBuilder(Coinbase.BROKERAGE_API_URL).append("/orders/historical/batch");
        List<String> query = new ArrayList<>();
        if (tradePair != null) {
            query.add("product_ids=" + URLEncoder.encode(tradePair.toString('-'), StandardCharsets.UTF_8));
        }
        if (orderStatus != null && !orderStatus.isBlank()) {
            query.add("order_status=" + URLEncoder.encode(orderStatus, StandardCharsets.UTF_8));
        }
        if (!query.isEmpty()) {
            uri.append("?").append(String.join("&", query));
        }
        return URI.create(uri.toString());
    }

    private ObjectNode buildCreateOrderPayload(CreateOrderRequest request, TradePair tradePair) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("client_order_id", UUID.randomUUID().toString());
        root.put("product_id", tradePair.toString('-'));
        root.put("side", request.side().name());

        ObjectNode configuration = root.putObject("order_configuration");
        switch (request.orderType()) {
            case MARKET -> {
                ObjectNode market = configuration.putObject("market_market_ioc");
                market.put("base_size", formatDecimal(request.size()));
            }
            case LIMIT -> {
                ObjectNode limit = configuration.putObject("limit_limit_gtc");
                limit.put("base_size", formatDecimal(request.size()));
                limit.put("limit_price", formatDecimal(request.price()));
                limit.put("post_only", false);
            }
            default -> {
                logger.warn("Coinbase Advanced Trade stop/bracket details are not fully modeled yet; submitting {} as a limit order.", request.orderType());
                ObjectNode limit = configuration.putObject("limit_limit_gtc");
                limit.put("base_size", formatDecimal(request.size()));
                limit.put("limit_price", formatDecimal(request.price()));
                limit.put("post_only", false);
            }
        }
        return root;
    }

    private Order mapOrder(JsonNode node) {
        Order order = new Order();
        order.setSymbol(node.path("product_id").asText("").replace('-', '/'));
        order.setLastTransactionID(node.path("order_id").asText(""));
        order.setWorking(node.path("status").asText("").equalsIgnoreCase("OPEN")
                || node.path("status").asText("").equalsIgnoreCase("PENDING"));
        order.setTimestamp(parseDate(node.path("created_time").asText("")));
        order.setSide(Side.getSide(node.path("side").asText("UNKNOWN")));
        order.setOrderType(parseOrderType(node));
        double price = parseOrderPrice(node);
        if (price > 0) {
            order.setPrice(price);
        }
        double size = parseDouble(node.path("filled_size"));
        if (size <= 0) {
            size = parseConfiguredSize(node);
        }
        if (size > 0) {
            order.setSize(size);
        }
        order.setOrderStatus(parseOrderStatus(node.path("status").asText("UNKNOWN")));
        order.setOrderId(stableOrderId(node.path("order_id").asText("")));
        return order;
    }

    private ENUM_ORDER_TYPE parseOrderType(JsonNode node) {
        JsonNode configuration = node.path("order_configuration");
        if (configuration.isObject()) {
            if (configuration.has("market_market_ioc")) {
                return ENUM_ORDER_TYPE.MARKET;
            }
            if (configuration.has("limit_limit_gtc")
                    || configuration.has("sor_limit_ioc")
                    || configuration.has("limit_limit_fok")) {
                return ENUM_ORDER_TYPE.LIMIT;
            }
            if (configuration.has("stop_limit_stop_limit_gtc")
                    || configuration.has("trigger_bracket_gtc")) {
                return ENUM_ORDER_TYPE.STOP;
            }
        }
        String rawType = node.path("order_type").asText(node.path("type").asText("LIMIT"));
        try {
            return ENUM_ORDER_TYPE.valueOf(rawType.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return ENUM_ORDER_TYPE.LIMIT;
        }
    }

    private double parseOrderPrice(JsonNode node) {
        double averageFilledPrice = parseDouble(node.path("average_filled_price"));
        if (averageFilledPrice > 0) {
            return averageFilledPrice;
        }

        JsonNode configuration = node.path("order_configuration");
        if (configuration.isObject()) {
            for (String fieldName : List.of("limit_limit_gtc", "limit_limit_fok", "sor_limit_ioc", "trigger_bracket_gtc", "stop_limit_stop_limit_gtc")) {
                JsonNode config = configuration.path(fieldName);
                double limitPrice = parseDouble(config.path("limit_price"));
                if (limitPrice > 0) {
                    return limitPrice;
                }
            }
        }
        return 0.0;
    }

    private double parseConfiguredSize(JsonNode node) {
        JsonNode configuration = node.path("order_configuration");
        if (!configuration.isObject()) {
            return 0.0;
        }
        for (JsonNode config : configuration) {
            double baseSize = parseDouble(config.path("base_size"));
            if (baseSize > 0) {
                return baseSize;
            }
        }
        return 0.0;
    }

    private static ENUM_ORDER_STATUS parseOrderStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.US);
        return switch (normalized) {
            case "OPEN" -> ENUM_ORDER_STATUS.OPEN;
            case "PENDING" -> ENUM_ORDER_STATUS.PENDING;
            case "FILLED" -> ENUM_ORDER_STATUS.FILLED;
            case "CANCELLED", "CANCELED" -> ENUM_ORDER_STATUS.CANCELLED;
            case "PARTIALLY_FILLED" -> ENUM_ORDER_STATUS.PARTIALLY_FILLED;
            case "REJECTED" -> ENUM_ORDER_STATUS.REJECTED;
            case "EXPIRED" -> ENUM_ORDER_STATUS.EXPIRED;
            case "NEW" -> ENUM_ORDER_STATUS.NEW;
            default -> ENUM_ORDER_STATUS.UNKNOWN;
        };
    }

    private static Date parseDate(String value) {
        try {
            return Date.from(java.time.Instant.parse(value));
        } catch (Exception ignored) {
            return new Date();
        }
    }

    private static double parseDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0.0;
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private static String formatDecimal(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static long stableOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            return -1L;
        }
        return Integer.toUnsignedLong(orderId.hashCode());
    }
}
