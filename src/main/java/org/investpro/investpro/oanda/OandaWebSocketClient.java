package org.investpro.investpro.oanda;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.investpro.investpro.*;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public abstract class OandaWebSocketClient extends ExchangeWebSocketClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    public OandaWebSocketClient(Set<String> tradePairs) {
        super(URI.create("wss://api-fxtrade.oanda.com"), new Draft_6455());
        Objects.requireNonNull(tradePairs);
    }

    @Override
    public void onMessage(String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException ex) {
            Log.error("ex: " + ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("info")) {
            connectionEstablished.setValue(true);
        }


        @NotNull String tradePair = null;
        assert parseTradePair(messageJson) != null;
        tradePair = parseTradePair(messageJson);

        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "heartbeat" ->
                    send(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString());
            case "match" -> {
                if (liveTradeConsumers.containsKey(tradePair)) {

                    assert tradePair != null;
                    Trade newTrade = new Trade(tradePair,
                            DefaultMoney.of(new BigDecimal(messageJson.get("price").asText()),
                                    tradePair),
                            DefaultMoney.of(new BigDecimal(messageJson.get("size").asText()),
                                    tradePair),
                            side, messageJson.at("id").asLong(),
                            Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText())));
                    liveTradeConsumers.get(tradePair).acceptTrades(Collections.singletonList(newTrade));
                }
            }
            case "error" -> throw new IllegalArgumentException("Error on Oanda websocket client: " +
                    messageJson.get("message").asText());
            default -> throw new IllegalStateException("Unhandled message type on Gdax websocket client: " +
                    messageJson.get("type").asText());
        }
    }

    private String parseTradePair(JsonNode messageJson) {
        return null;
    }

    @Override
    public void streamLiveTrades(String tradePair, LiveTradesConsumer liveTradesConsumer) {
        send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                .put("instrument", tradePair).toPrettyString());
        liveTradeConsumers.put(tradePair, liveTradesConsumer);
    }


}
