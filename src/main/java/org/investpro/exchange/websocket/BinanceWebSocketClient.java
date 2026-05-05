package org.investpro.exchange.websocket;

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
 * Binance WebSocket client for real-time trade streaming.
 * Extends ExchangeWebSocketClient with Binance-specific message handling.
 */
public class BinanceWebSocketClient extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);
    
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public BinanceWebSocketClient(URI uri, Draft draft) {
        super(uri, draft);
    }

    @Override
    public void onMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            logger.warn("Received empty WebSocket message from Binance");
            return;
        }

        try {
            JsonNode messageJson = OBJECT_MAPPER.readTree(message);
            
            // Check if it's a trade event
            if (messageJson.has("e") && messageJson.get("e").asText().equals("trade")) {
                handleTradeMessage(messageJson);
            } else if (messageJson.has("s")) {
                // This might be an aggregated trade or kline
                logger.debug("Received Binance message: " + messageJson.get("e"));
            }
        } catch (JsonProcessingException ex) {
            logger.error("Failed to parse Binance WebSocket message: " + ex.getMessage(), ex);
        }
    }

    private void handleTradeMessage(JsonNode messageJson) {
        try {
            if (getTradePair() == null) {
                logger.warn("Trade pair not set, ignoring trade message");
                return;
            }

            long tradeId = messageJson.get("t").asLong();
            BigDecimal price = new BigDecimal(messageJson.get("p").asText());
            BigDecimal quantity = new BigDecimal(messageJson.get("q").asText());
            long timestamp = messageJson.get("T").asLong();
            
            // Determine if it's a buy or sell (Binance uses "m" for "Buyer is Maker")
            boolean isBuyerMaker = messageJson.get("m").asBoolean();
            Side side = isBuyerMaker ? Side.SELL : Side.BUY;

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
                logger.debug("Processed Binance trade: %d at %s".formatted(tradeId, price));
            }
        } catch (Exception ex) {
            logger.error("Error processing Binance trade message: %s".formatted(ex.getMessage()), ex);
        }
    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {
        if (liveTradesConsumer == null) {
            logger.error("Attempted to stream trades with null consumer");
            return;
        }

        if (!isOpen()) {
            logger.warn("WebSocket not connected, cannot subscribe to Binance trades");
            return;
        }

        try {
            setTradePair(tradePair);
            liveTradeConsumers.put(tradePair, liveTradesConsumer);
            
            // Binance uses lowercase symbols without separators for streaming
            String symbol = (tradePair.getBaseCurrency().getCode() +
                           tradePair.getCounterCurrency().getCode()).toLowerCase();

            JsonNode data1 = OBJECT_MAPPER.createArrayNode().add("%s@trade".formatted(symbol)).get(0);
            String subscribeMsg = OBJECT_MAPPER.createObjectNode()
                    .put("method", "SUBSCRIBE")
                    .put("symbol", symbol)
                    .put( "id", 1L)
                    .put("params", data1.toString())

                    .toPrettyString();
            
            send(subscribeMsg);
            logger.info("Subscribed to Binance live trades for %s".formatted(tradePair));
        } catch (Exception ex) {
            logger.error("Failed to subscribe to Binance trades for %s: %s".formatted(tradePair, ex.getMessage()), ex);
        }
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        if (tradePair != null) {
            liveTradeConsumers.remove(tradePair);
            logger.info("Unsubscribed from Binance live trades for %s".formatted(tradePair));
        }
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return liveTradeConsumers.containsKey(tradePair);
    }
}
