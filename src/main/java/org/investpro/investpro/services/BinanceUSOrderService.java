package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.investpro.investpro.CreateOrderRequest;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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
                tradePair.toString('/'), side, orderType, price, size, timestamp, stopLoss, takeProfit));

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
        String query = "symbol=%s&orderId=%s&timestamp=%d".formatted(tradePair.toString('/'), orderId, timestamp);
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
        return new ArrayList<>();
    }

    public List<Order> getPendingOrders() {
        return new ArrayList<>();
    }

    public List<Order> getOpenOrder(TradePair tradePair) {
        return new ArrayList<>();
    }

    public void cancelAllOrders() {

    }
}
