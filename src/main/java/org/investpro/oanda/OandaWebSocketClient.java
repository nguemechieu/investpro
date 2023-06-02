package org.investpro.oanda;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.investpro.*;
import org.java_websocket.drafts.Draft_6455;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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


        @NotNull TradePair tradePair =
                new TradePair(Currency.of("BTC")
                , Currency.of("USD"));

    }

    private TradePair parseTradePair(JsonNode messageJson) {
        if (messageJson.has("pair")) {
            return new TradePair(Currency.of(messageJson.get("pair").asText()), Currency.of("USD"));
        } else {
            return new TradePair(Currency.of("BTC"), Currency.of("USD"));
        }
    }


}



