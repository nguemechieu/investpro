package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.CreateOrderRequest;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.SIGNAL;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.model.*;
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
import java.util.concurrent.ExecutionException;

public class CoinbaseOrderService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseOrderService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;

    public CoinbaseOrderService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public List<Order> getOrders() throws IOException, InterruptedException, ExecutionException {
        String url = Coinbase.API_URL + "/orders";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        List<Order> orders = new ArrayList<>();
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("orders")) {
            for (JsonNode orderx : node.get("orders")) {
                Order order = objectMapper.treeToValue(orderx, Order.class);
                orders.add(order);
            }
        }
        return orders;
    }

    public List<Order> getOpenOrder(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        String url = Coinbase.API_URL + "/orders?product_id=" + tradePair.toString('-');
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        List<Order> orders = new ArrayList<>();
        JsonNode node = objectMapper.readTree(response.body());
        if (node.has("orders")) {
            for (JsonNode orderx : node.get("orders")) {
                Order order = objectMapper.treeToValue(orderx, Order.class);
                orders.add(order);
            }
        }
        return orders;
    }

    public List<Order> getPendingOrders() {
        return List.of();
    }

    public void createOrder(TradePair tradePair, Side side, ENUM_ORDER_TYPE orderType, double price, double size,
                            Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
        String url = Coinbase.API_URL + "/orders";
        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('/'), side, orderType, price, size, timestamp, stopLoss, takeProfit);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(orderRequest)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error creating order: " + response.body());
        }
        logger.info("Order created: {}", response.body());
    }

    public void cancelOrder(String orderId) throws IOException, InterruptedException {
        String url = Coinbase.API_URL + "/orders/" + orderId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiKey)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error cancelling order: " + response.body());
        }
        logger.info("Order cancelled: {}", orderId);
    }

    public void cancelAllOrders() {
        // Coinbase does not currently provide a single API endpoint to cancel all orders.
        // This method would require iterating over open orders and cancelling them one-by-one.
        logger.warn("cancelAllOrders() not implemented. Coinbase API does not support it directly.");
    }
}
