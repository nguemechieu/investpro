package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CreateOrderRequest;
import org.investpro.investpro.ENUM_ORDER_STATUS;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.models.Order;
import org.investpro.investpro.models.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@Getter
@Setter

public class OandaOrderService {

    private static final Logger logger = LoggerFactory.getLogger(OandaOrderService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String accountId;

    private final String apiSecret;
    private final HttpClient client;
    private final HttpRequest.Builder baseRequestBuilder;

    public OandaOrderService(String accountId, String apiSecret, HttpClient client) {
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.client = client;
        this.baseRequestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    public List<Order> getOrders(TradePair tradePair) throws IOException, InterruptedException {
        HttpRequest request = baseRequestBuilder.uri(URI.create(Oanda.API_URL + "/accounts/" + accountId + "/orders"))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("ORDER HTTP error: {}", response.body());

            return new ArrayList<>();
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        List<Order> orders = new ArrayList<>();
        if (root.has("orders")) {
            for (JsonNode node : root.get("orders")) {
                Order order = mapOrder(node, root.path("lastTransactionID").asText(""), tradePair);
                orders.add(order);
            }
        }
        return orders;
    }

    public List<Order> getOpenOrder(TradePair tradePair) throws IOException, InterruptedException {
        return getOrders(tradePair); // Simplified, you may filter by tradePair if needed
    }

    public List<Order> getPendingOrders() throws IOException, InterruptedException {
        HttpRequest request = baseRequestBuilder.uri(URI.create(Oanda.API_URL + "/accounts/" + accountId + "/pendingOrders"))
                .GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            logger.error("Error fetching pending orders: {}", response.body());
            return new ArrayList<>();
        }

        List<Order> orders = new ArrayList<>();
        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        for (JsonNode node : root.get("orders")) {
            orders.add(mapOrder(node, root.path("lastTransactionID").asText(""), null));
        }
        return orders;
    }

    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType,
                            double price, double size, Date timestamp,
                            double stopLoss, double takeProfit) throws IOException, InterruptedException {

        URI uri = URI.create(String.format("%s/accounts/%s/orders", Oanda.API_URL, accountId));
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('_'), side, orderType, price, size, timestamp, stopLoss, takeProfit);

        String body = OBJECT_MAPPER.writeValueAsString(orderRequest);
        HttpRequest request = baseRequestBuilder.uri(uri)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 201) {
            logger.error("Error creating order: {}", response.body());
            return;
        }
        logger.info("Order created: {}", response.body());
    }

    public void cancelOrder(String orderId) throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/accounts/%s/orders/%s", Oanda.API_URL, accountId, orderId));
        HttpRequest request = baseRequestBuilder.uri(uri)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.error("Error cancelling order: {}", response.body());
        }
        logger.info("Order cancelled: {}", orderId);
    }

    public void cancelAllOrders() throws IOException, InterruptedException {
        URI uri = URI.create(String.format("%s/accounts/%s/orders", Oanda.API_URL, accountId));
        HttpRequest request = baseRequestBuilder.uri(uri)
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            logger.error("Error cancelling all orders: {}", response.body());
        }

        logger.info("All orders cancelled for account: {}", accountId);
    }

    private Order mapOrder(JsonNode node, String lastTransactionId, TradePair tradePair) {
        Order order = new Order();
        order.setLastTransactionID(lastTransactionId);

        long orderId = node.path("id").asLong(-1);
        if (orderId > -1) {
            order.setOrderId(orderId);
            order.setId(orderId);
        }

        String instrument = node.path("instrument").asText("");
        if (instrument.isBlank() && tradePair != null) {
            instrument = tradePair.toString('_');
        }
        order.setSymbol(instrument.isBlank() ? "N/A" : instrument);

        String rawType = node.path("type").asText("LIMIT");
        order.setOrderType(parseOrderType(rawType));

        String rawState = node.path("state").asText("UNKNOWN");
        order.setOrderStatus(parseOrderStatus(rawState));

        if (node.hasNonNull("price")) {
            double price = node.path("price").asDouble();
            if (price > 0) {
                order.setPrice(price);
            }
        }

        double units = 0;
        if (node.hasNonNull("units")) {
            units = node.path("units").asDouble();
        } else if (node.hasNonNull("currentUnits")) {
            units = node.path("currentUnits").asDouble();
        }

        if (units != 0) {
            order.setSize(Math.abs(units));
            order.setSide(units > 0 ? Side.BUY : Side.SELL);
        } else {
            order.setSide(Side.UNKNOWN);
        }

        String timestamp = node.hasNonNull("createTime")
                ? node.path("createTime").asText()
                : node.path("time").asText("");
        if (!timestamp.isBlank()) {
            order.setTimestamp(Date.from(Instant.parse(timestamp)));
        }

        return order;
    }

    private ENUM_ORDER_TYPE parseOrderType(String rawType) {
        try {
            return ENUM_ORDER_TYPE.valueOf(rawType.toUpperCase(Locale.US));
        } catch (IllegalArgumentException ignored) {
            return ENUM_ORDER_TYPE.LIMIT;
        }
    }

    private ENUM_ORDER_STATUS parseOrderStatus(String rawStatus) {
        String normalized = rawStatus.toUpperCase(Locale.US);
        if ("CANCELLED".equals(normalized)) {
            return ENUM_ORDER_STATUS.CANCELLED;
        }
        if ("CANCELED".equals(normalized)) {
            return ENUM_ORDER_STATUS.CANCELED;
        }

        try {
            return ENUM_ORDER_STATUS.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return ENUM_ORDER_STATUS.UNKNOWN;
        }
    }

}
