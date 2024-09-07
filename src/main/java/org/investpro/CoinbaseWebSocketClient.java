package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.util.Pair;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;


public class CoinbaseWebSocketClient extends ExchangeWebSocketClient {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    Set<TradePair> tradePairs;

    public CoinbaseWebSocketClient(TradePair tradePair, String apiKey, String apiSecret) {
        super(URI.create(
                "wss://advanced-trade-ws.coinbase.com"
        ), new Draft_6455(), apiKey, apiSecret);
        Objects.requireNonNull(tradePair);

        connectionEstablished = new SimpleBooleanProperty(false);
        webSocketInitializedLatch = new CountDownLatch(1);

    }



    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }
        @NotNull TradePair tradePair;
        try {
            tradePair = TradePair.of("BTC", "USD");
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);

            if (liveTradeConsumers.containsKey(tradePair)) {
                liveTradeConsumers.get(tradePair).acceptTrades(Collections.emptyList());
                liveTradeConsumers.remove(tradePair);
            }
        }


        try {
            @NotNull Pair<Currency, Currency> tradePairs = parseTradePair(messageJson);
            logger.info(STR."coinbase websocket client: received trade for trade pair: \{tradePairs}");
        } catch (CurrencyNotFoundException | SQLException | ClassNotFoundException exception) {
            logger.error(STR."coinbase websocket client: could not initialize trade pair: \{messageJson.get("product_id").asText()}", exception);
        }

        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "heartbeat":
                send(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString());
                break;
            case "match":
                if (liveTradeConsumers.containsKey(tradePair)) {
                    Trade newTrade = new Trade(tradePair,
                            DefaultMoney.of(new BigDecimal(messageJson.get("price").asText()),
                                    Objects.requireNonNull(tradePair).getCounterCurrency()),
                            DefaultMoney.of(new BigDecimal(messageJson.get("size").asText()),
                                    tradePair.getBaseCurrency()),
                            side, messageJson.at("trade_id").asLong(),
                            Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText())));
                    liveTradeConsumers.get(tradePair).acceptTrades(Collections.singletonList(newTrade));
                }
                break;
            case "error":
                throw new IllegalArgumentException(STR."Error on Coinbase websocket client: \{messageJson.get("message").asText()}");
            default:
                throw new IllegalStateException(STR."Unhandled message type on Gdax websocket client: \{messageJson.get("type").asText()}");
        }
    }

    private @NotNull TradePair parseTradePair(@NotNull JsonNode messageJson) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        final String productId = messageJson.get("product_id").asText();
        final String[] products = productId.split("-");
        TradePair tradePair;
        if (products[0].equalsIgnoreCase("BTC")) {
            try {
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
            } catch (SQLException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        } else {
            // products[0] == "ETH"
            if (products[1].equalsIgnoreCase("usd")) {
                try {
                    tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
                } catch (SQLException | ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            } else {
                // productId == "ETH-BTC"
                tradePair = TradePair.parse(productId, "-", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));

                if (products[1].equalsIgnoreCase("BTC")) {
                    tradePairs.add(tradePair);
                } else {
                    throw new IllegalArgumentException(STR."Invalid product ID: \{productId}");
                }
            }

        }

        return tradePair;
    }

    @Override
    public CountDownLatch getInitializationLatch() {
        return webSocketInitializedLatch;

    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                .put("product_id", tradePair.toString('-')).toPrettyString());
        liveTradeConsumers.put(tradePair, liveTradesConsumer);
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {

        return tradePairs.contains(tradePair);
    }

    @Override
    public void onError(Exception exception) {

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connectionEstablished.setValue(false);
        logger.info("WebSocket connection closed: {}", reason);
        webSocketInitializedLatch.countDown();

        if (!remote) {
            logger.info("WebSocket connection was closed by server, trying to reconnect");
            try {
                connectBlocking();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }


    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        logger.info("WebSocket connection opened");
        webSocketInitializedLatch.countDown();
        //send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
        //     .put("product_id", tradePair.toString('-')).toPrettyString());
    }

    @Override
    public boolean connectBlocking() throws InterruptedException {
        return false;
    }
}
