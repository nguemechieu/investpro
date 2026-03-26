package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.CreateOrderRequest;
import org.investpro.investpro.ENUM_ORDER_STATUS;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;
import org.investpro.investpro.model.Order;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.investpro.investpro.BinanceUtils.HmacSHA256;

public class BinanceUSOrderService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSOrderService.class);
    private static final String API_URL = "https://api.binance.us/api/v3";

    private final String apiKey;
    private final String apiSecret;
    private final HttpClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceUSOrderService(String apiKey, String apiSecret, HttpClient client) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.client = client;
    }

    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType,
                            double price, double size, Date timestamp,
                            double stopLoss, double takeProfit) throws IOException, InterruptedException {

        String body = objectMapper.writeValueAsString(new CreateOrderRequest(
                tradePair.toSymbol(), side, orderType, price, size, timestamp, stopLoss, takeProfit));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/order"))
                .header("Content-Type", "application/json")
                .header("X-MBX-APIKEY", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to create order: " + response.statusCode() + "\n" + response.body());
        }

        logger.info("✅ Order created: {}", response.body());
    }

    public void cancelOrder(String orderId, TradePair tradePair) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        long timestamp = System.currentTimeMillis();
        String query = "symbol=%s&orderId=%s&timestamp=%d".formatted(tradePair.toSymbol(), orderId, timestamp);
        String signature = HmacSHA256(apiSecret, query);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("%s/order?%s&signature=%s".formatted(API_URL, query, signature)))
                .header("X-MBX-APIKEY", apiKey)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to cancel order: " + response.statusCode() + "\n" + response.body());
        }

        logger.info("✅ Order cancelled: {}", orderId);
    }

    public List<Order> getOrders() {
        return signedOrderRequest("/openOrders", null);
    }

    public List<Order> getPendingOrders() {
        return signedOrderRequest("/openOrders", null);
    }

    public List<Order> getOpenOrder(TradePair tradePair) {
        return signedOrderRequest("/openOrders", tradePair);
    }

    public void cancelAllOrders() {
        logger.info("cancelAllOrders is only supported for the currently selected Binance US pair.");
    }

    private List<Order> signedOrderRequest(String endpoint, TradePair tradePair) {
        try {
            long timestamp = System.currentTimeMillis();
            StringBuilder query = new StringBuilder();
            if (tradePair != null) {
                query.append("symbol=").append(tradePair.toSymbol()).append("&");
            }
            query.append("timestamp=").append(timestamp);
            String signature = HmacSHA256(apiSecret, query.toString());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("%s%s?%s&signature=%s".formatted(API_URL, endpoint, query, signature)))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch Binance US orders from {}: {}", endpoint, response.body());
                return List.of();
            }

            JsonNode root = objectMapper.readTree(response.body());
            List<Order> orders = new ArrayList<>();
            if (!root.isArray()) {
                return orders;
            }

            for (JsonNode node : root) {
                orders.add(mapOrder(node));
            }
            return orders;
        } catch (Exception e) {
            logger.error("Failed to fetch Binance US orders from {}", endpoint, e);
            return List.of();
        }
    }

    private Order mapOrder(JsonNode node) {
        Order order = new Order();
        order.setSymbol(node.path("symbol").asText("N/A"));
        order.setOrderId(node.path("orderId").asLong(-1));
        order.setSide(Side.getSide(node.path("side").asText("UNKNOWN")));
        order.setOrderType(parseOrderType(node.path("type").asText("LIMIT")));

        double price = node.path("price").asDouble(0);
        if (price > 0) {
            order.setPrice(price);
        }

        double size = node.path("origQty").asDouble(0);
        if (size > 0) {
            order.setSize(size);
        }

        long time = node.path("time").asLong(0);
        if (time > 0) {
            order.setTime(time);
            order.setTimestamp(Date.from(Instant.ofEpochMilli(time)));
        }

        order.setOrderStatus(parseOrderStatus(node.path("status").asText("UNKNOWN")));
        order.setWorking(node.path("isWorking").asBoolean(false));
        return order;
    }

    private ENUM_ORDER_TYPE parseOrderType(String value) {
        try {
            return ENUM_ORDER_TYPE.valueOf(value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return ENUM_ORDER_TYPE.LIMIT;
        }
    }

    private ENUM_ORDER_STATUS parseOrderStatus(String value) {
        String normalized = value.toUpperCase(Locale.US);
        if ("CANCELED".equals(normalized)) {
            return ENUM_ORDER_STATUS.CANCELED;
        }
        try {
            return ENUM_ORDER_STATUS.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ENUM_ORDER_STATUS.UNKNOWN;
        }
    }
}
