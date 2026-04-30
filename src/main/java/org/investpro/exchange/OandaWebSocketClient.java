package org.investpro.exchange;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.java_websocket.drafts.Draft;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;

/**
 * Oanda-specific WebSocket client for real-time trade streaming.
 * Extends ExchangeWebSocketClient with Oanda-specific message handling.
 * Uses Oanda REST v3 API format for trades.
 */
public class OandaWebSocketClient extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(OandaWebSocketClient.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public OandaWebSocketClient(URI uri, Draft draft) {
        super(uri, draft);
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            logger.warn("Received empty WebSocket message from Oanda");
            return;
        }

        try {
            JsonNode messageJson = OBJECT_MAPPER.readTree(message);
            
            // Check if it's a trade event (Oanda uses "TRADE" type)
            if (messageJson.has("type") && messageJson.get("type").asText().equalsIgnoreCase("TRADE")) {
                handleTradeMessage(messageJson);
            } else if (messageJson.has("type")) {
                logger.debug("Received Oanda message type: " + messageJson.get("type").asText());
            }
        } catch (JsonProcessingException ex) {
            logger.error("Failed to parse Oanda WebSocket message: " + ex.getMessage(), ex);
        }
    }

    /**
     * Handle Oanda TRADE message format.
     * Example: {"type": "TRADE", "id": 123, "instrument": "EUR_USD", 
     *           "price": "1.0750", "openTime": "2024-01-01T12:00:00Z", 
     *           "side": "BUY", "units": "100000"}
     */
    private void handleTradeMessage(JsonNode messageJson) {
        try {
            if (getTradePair() == null) {
                logger.warn("Trade pair not set, ignoring trade message");
                return;
            }

            long tradeId = messageJson.get("id").asLong();
            BigDecimal price = new BigDecimal(messageJson.get("price").asText());
            BigDecimal quantity = new BigDecimal(messageJson.get("units").asText());
            String sideStr = messageJson.get("side").asText().toUpperCase();
            Side side = sideStr.equals("BUY") ? Side.BUY : Side.SELL;
            
            // Parse timestamp (ISO 8601 format)
            Instant timestamp = Instant.parse(messageJson.get("openTime").asText());

            Trade newTrade = new Trade(
                    getTradePair(),
                    price.doubleValue(),
                    quantity.doubleValue(),
                    side,
                    tradeId,
                    timestamp
            );

            // Send to registered consumer
            if (liveTradeConsumers.containsKey(getTradePair())) {
                LiveTradesConsumer consumer = liveTradeConsumers.get(getTradePair());
                consumer.acceptTrades(newTrade);
                logger.debug("Processed Oanda trade: %d at %s".formatted(tradeId, price));
            }
        } catch (Exception ex) {
            logger.error("Error processing Oanda trade message: {}", ex.getMessage(), ex);
        }
    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        if (liveTradesConsumer == null) {
            logger.error("Attempted to stream trades with null consumer");
            return;
        }
        
        if (!isOpen()) {
            logger.warn("WebSocket not connected, cannot subscribe to trades");
            return;
        }
        
        try {

            // Oanda subscription message format for streaming prices
            String subscribeMsg = OBJECT_MAPPER.createObjectNode()
                    .put("type", "SUBSCRIBE")
                    .put("instruments", tradePair.toString('_'))
                    .toPrettyString();
            send(subscribeMsg);
            liveTradeConsumers.put(tradePair, liveTradesConsumer);
            logger.info("Subscribed to live trades for %s".formatted(tradePair));
        } catch (Exception ex) {
            logger.error("Failed to subscribe to trades for %s: %s".formatted(tradePair, ex.getMessage()), ex);
        }
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        if (tradePair != null) {
            liveTradeConsumers.remove(tradePair);
            logger.info("Unsubscribed from live trades for %s".formatted(tradePair));
        }
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return liveTradeConsumers.containsKey(tradePair);
    }
}
