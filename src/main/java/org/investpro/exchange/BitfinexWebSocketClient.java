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

/**
 * Bitfinex WebSocket client for real-time trade streaming.
 * Extends ExchangeWebSocketClient with Bitfinex-specific message handling.
 */
public class BitfinexWebSocketClient extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(BitfinexWebSocketClient.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BitfinexWebSocketClient(URI uri, Draft draft) {
        super(uri, draft);
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            logger.warn("Received empty WebSocket message from Bitfinex");
            return;
        }

        try {
            JsonNode messageJson = OBJECT_MAPPER.readTree(message);
            
            // Bitfinex uses array-based messages
            if (messageJson.isArray()) {
                handleArrayMessage(messageJson);
            } else if (messageJson.isObject()) {
                // Handle info/subscribe responses
                if (messageJson.has("event")) {
                    String event = messageJson.get("event").asText();
                    if ("subscribed".equals(event)) {
                        logger.info("Bitfinex subscribed to: %s".formatted(messageJson.get("pair")));
                    }
                }
            }
        } catch (JsonProcessingException ex) {
            logger.error("Failed to parse Bitfinex WebSocket message: {}", ex.getMessage(), ex);
        }
    }

    private void handleArrayMessage(JsonNode messageJson) {
        try {
            // Bitfinex format: [channel_id, data...]
            if (messageJson.size() < 2) {
                return;
            }

            // Check if this is a trade message
            // Format: [channel_id, "te" or "tu", [id, timestamp_ms, amount, price]]
            if (messageJson.size() >= 3) {
                String messageType = messageJson.get(1).asText();
                
                if ("te".equals(messageType) || "tu".equals(messageType)) {
                    // Trade execution message
                    handleTradeMessage(messageJson.get(2));
                }
            }
        } catch (Exception ex) {
            logger.error("Error processing Bitfinex array message: " + ex.getMessage(), ex);
        }
    }

    private void handleTradeMessage(JsonNode tradeData) {
        try {
            if (getTradePair() == null || !tradeData.isArray() || tradeData.size() < 4) {
                logger.debug("Invalid trade data format or tradePair not set");
                return;
            }

            long tradeId = tradeData.get(0).asLong();
            long timestamp = tradeData.get(1).asLong();
            BigDecimal amount = new BigDecimal(tradeData.get(2).asText());
            BigDecimal price = new BigDecimal(tradeData.get(3).asText());
            
            // Positive amount = buy, negative = sell
            Side side = amount.signum() > 0 ? Side.BUY : Side.SELL;
            BigDecimal quantity = amount.abs();

            Trade newTrade = new Trade(
                    getTradePair(),
                    price.byteValueExact(),
                    quantity.byteValueExact(),
                    side,
                    tradeId,
                    Instant.ofEpochMilli(timestamp)
            );

            // Send to registered consumer
            if (liveTradeConsumers.containsKey(getTradePair())) {
                LiveTradesConsumer consumer = liveTradeConsumers.get(getTradePair());
                consumer.acceptTrades(newTrade);
                logger.debug("Processed Bitfinex trade: %d at %s".formatted(tradeId, price));
            }
        } catch (Exception ex) {
            logger.error("Error processing Bitfinex trade data: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        if (liveTradesConsumer == null) {
            logger.error("Attempted to stream trades with null consumer");
            return;
        }

        if (!isOpen()) {
            logger.warn("WebSocket not connected, cannot subscribe to Bitfinex trades");
            return;
        }

        try {
            setTradePair(tradePair);
            liveTradeConsumers.put(tradePair, liveTradesConsumer);
            
            // Bitfinex pair format: tBTCUSD (with "t" prefix)
            String pair = "t%s%s".formatted(tradePair.getBaseCurrency().getCode(), tradePair.getCounterCurrency().getCode());
            
            String subscribeMsg = OBJECT_MAPPER.createObjectNode()
                    .put("event", "subscribe")
                    .put("channel", "trades")
                    .put("symbol", pair)
                    .toPrettyString();
            
            send(subscribeMsg);
            logger.info("Subscribed to Bitfinex live trades for " + tradePair);
        } catch (Exception ex) {
            logger.error("Failed to subscribe to Bitfinex trades for " + tradePair + ": " + ex.getMessage(), ex);
        }
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        if (tradePair != null) {
            liveTradeConsumers.remove(tradePair);
            logger.info("Unsubscribed from Bitfinex live trades for " + tradePair);
        }
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return liveTradeConsumers.containsKey(tradePair);
    }
}
