package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.util.Pair;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.CompletableFuture;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;


public abstract class BinanceWebSocket extends ExchangeWebSocketClient {


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    private final CompletableFuture<Boolean> connectionEstablished = new CompletableFuture<>();


    public BinanceWebSocket() {
        super(URI.create(
                "wss://stream.binance.us:9443/ws"
        ), new Draft_6455());

        logger.info("Coinbase websocket client initialized");
    }


    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
            if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("ping")) {
                sendText(OBJECT_MAPPER.createObjectNode().put("type", "pong").toPrettyString(), true);
                logger.info(
                        "Coinbase websocket client: Pong received"
                );
            }

        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {

            if (messageJson.has("product_id") && messageJson.get("product_id").asText().equalsIgnoreCase("BTCUSD")) {
                connectionEstablished.complete(true);
            }
        }


        TradePair tradePair = null;
        try {
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
                    sendText(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString(), true);
            case "match" -> {
                if (liveTradeConsumers.containsKey(tradePair)) {
                    assert tradePair != null;
                    Trade newTrade;
                    newTrade = new Trade(tradePair,
                            messageJson.get("p").asDouble(),

                            messageJson.get("q").asDouble(),

                            side, messageJson.at("E").asLong(),
                            Date.from(Instant.from(ISO_INSTANT.parse(messageJson.get("t").asText()))).getTime());

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
        final String[] products = productId.split("/");
        TradePair tradePair;
        if (products[0].equalsIgnoreCase("BTC")) {
            tradePair = TradePair.parse(productId, "/", new Pair<>(CryptoCurrency.class, Currency.class));
        } else {
            // products[0] == "ETH"
            if (products[1].equalsIgnoreCase("usd")) {
                tradePair = TradePair.parse(productId, "/", new Pair<>(CryptoCurrency.class, Currency.class));
            } else {
                // productId == "ETH-BTC"
                tradePair = TradePair.parse(productId, "/", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));
            }
        }

        return tradePair;
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
    @NotNull
    public URI getURI() {
        return
                URI.create(

                        "wss://stream.binance.us:9443/ws"
                );
    }


    @Override
    public void sendText(CharSequence data, boolean last) {
    }



    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.warn("BinanceUs websocket client: onClose not implemented");
    }

    @Override
    public void onError(Exception ex) {
        logger.error("BinanceUs websocket client: onError", ex);

    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        connectionEstablished.complete(true);
    }







}


