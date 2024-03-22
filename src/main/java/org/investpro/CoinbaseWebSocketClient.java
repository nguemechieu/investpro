package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CoinbaseWebSocketClient extends WebSocketClient {


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    protected BooleanProperty connectionEstablished;
    private LiveTrade liveTradeConsumers;
    private TradePair tradePair;


    public CoinbaseWebSocketClient(URI uri, Draft draft) {
        super(uri, draft);


        connectionEstablished = new SimpleBooleanProperty(false);
        this.connect();


    }



    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
            logger.info(STR."message :\{messageJson}");
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);
        }



        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "heartbeat":
                send(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString());
                break;
            case "match":
                if (liveTradeConsumers.containsKey(getTradePair())) {

                    LiveTrade newTrade = new LiveTrade(getTradePair(),
                            DefaultMoney.of(new BigDecimal(messageJson.get("price").asText()),
                                    getTradePair().getCounterCurrency()),
                            DefaultMoney.of(new BigDecimal(messageJson.get("size").asText()),
                                    getTradePair().getBaseCurrency()),
                            side, messageJson.at("trade_id").asLong(),
                            Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText())));
                    liveTradeConsumers.acceptTrades(Collections.singletonList(newTrade));
                }
                break;
            case "error":
                throw new IllegalArgumentException(STR."Error on Coinbase websocket client: \{messageJson.get("message").asText()}");
            default:
                throw new IllegalStateException(STR."Unhandled message type on Gdax websocket client: \{messageJson.get("type").asText()}");
        }
    }



    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                .put("product_id", tradePair.toString('-')).toPrettyString());
        liveTradeConsumers.put(tradePair, liveTradesConsumer);
    }


    public void stopStreamLiveTrades(TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
    }
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return liveTradeConsumers.containsKey(tradePair);

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

        logger.info("Connection closed on Coinbase websocket client: ", reason);

    }

    @Override
    public void onError(Exception ex) {

        logger.error("Error on Coinbase websocket client: ", ex);

    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        connectionEstablished.setValue(true);
        logger.info("Connection established on Coinbase websocket client: ");
    }

    public void setLiveTradeConsumers(LiveTrade liveTradeConsumers) {
        this.liveTradeConsumers = liveTradeConsumers;
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }
}
