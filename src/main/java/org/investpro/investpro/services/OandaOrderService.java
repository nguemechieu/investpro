package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CreateOrderRequest;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.Order;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                Order order = OBJECT_MAPPER.readValue(node.toString(), Order.class);
                order.setLastTransactionID(root.get("lastTransactionID").asText());
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
            orders.add(OBJECT_MAPPER.treeToValue(node, Order.class));
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

}
