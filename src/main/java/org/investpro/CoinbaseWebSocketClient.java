package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.util.Pair;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;


public class CoinbaseWebSocketClient extends ExchangeWebSocketClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    private final boolean supportsStreamingTrades;

    Account connectionEstablished = new Account();

    public CoinbaseWebSocketClient() {

        super(URI.create(
                "wss://ws-feed.pro.coinbase.com"
        ), new Draft_6455());


        this.supportsStreamingTrades = true;

    }

    public CoinbaseWebSocketClient(String apiKey, String apiSecret) {
        super(URI.create(
                "wss://ws-feed.pro.coinbase.com"
        ), new Draft_6455(), apiKey, apiSecret);

        this.supportsStreamingTrades = true;
    }

    @Override
    public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {
        logger.info("Coinbase websocket client connected");

    }

    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Coinbase websocket client disconnected");

    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);

            logger.info("Received message from coinbase : " + messageJson.toString());
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);
        }

        TradePair tradePair = null;
        try {
            logger.info("onMessage: {}", messageJson);
            tradePair = parseTradePair(messageJson);
        } catch (CurrencyNotFoundException | SQLException exception) {
            logger.error("coinbase websocket client: could not initialize trade pair: " +
                    messageJson.get("product_id").asText(), exception);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        logger.error("Coinbase websocket client error: ", ex);

    }

    @Override
    public void onStart() {
        logger.info("Coinbase websocket client started");

    }

    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);

            logger.info("Received message from coinbase : " + messageJson.toString());
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);
        }

        TradePair tradePair = null;
        try {
            logger.info("onMessage: {}", messageJson);
            tradePair = parseTradePair(messageJson);
        } catch (CurrencyNotFoundException | SQLException exception) {
            logger.error("coinbase websocket client: could not initialize trade pair: " +
                    messageJson.get("product_id").asText(), exception);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "heartbeat" ->
                    sendText(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString(),true);
            case "match" -> {
                if (liveTradeConsumers.containsKey(tradePair)) {
                    assert tradePair != null;
                    Trade newTrade = new Trade(tradePair,
                            DefaultMoney.of(new BigDecimal(messageJson.get("price").asText()),
                                    tradePair.getCounterCurrency()),
                            DefaultMoney.of(new BigDecimal(messageJson.get("size").asText()),
                                    tradePair.getBaseCurrency()),
                            side, messageJson.at("trade_id").asLong(),
                            Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText())));
                    liveTradeConsumers.get(tradePair).acceptTrades(Collections.singletonList(newTrade));
                }
            }
            case "error" -> throw new IllegalArgumentException("Error on Coinbase websocket client: " +
                    messageJson.get("message").asText());
            default -> throw new IllegalStateException("Unhandled message type on Gdax websocket client: " +
                    messageJson.get("type").asText());
        }
    }

    private @NotNull TradePair parseTradePair(@NotNull JsonNode messageJson) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        final String productId = messageJson.get("product_id").asText();
        final String[] products = productId.split("-");
        TradePair tradePair;
        if (products[0].equalsIgnoreCase("BTC")) {
            tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, Currency.class));
        } else {
            // products[0] == "ETH"
            if (products[1].equalsIgnoreCase("usd")) {
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, Currency.class));
            } else {
                // productId == "ETH-BTC"
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));
            }
        }

        return tradePair;
    }


    @Override
    public void onClose(int code, String reason, boolean remote) {
        connectionEstablished.setValue(false);
    }

    @Override
    public void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        liveTradeConsumers.put(tradePair, liveTradesConsumer);

    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);

    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return false;
    }

    @Override
    public boolean isStreamingTradesEnabled(TradePair tradePair) {
        return false;
    }

    @Override
    public void sendText(CharSequence data, boolean last) {
    }


    @Override
    public void onError(Exception ex) {
        logger.error("Coinbase websocket client error: ", ex);

    }

    @Override
    public boolean connectBlocking() {
        return false;
    }

    @Override
    public @NotNull URI getURI() {
        return null;
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        connectionEstablished.setValue(true);
    }

@Override
    public boolean isSupportsStreamingTrades() {
        return supportsStreamingTrades;
    }
}
