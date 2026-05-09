package org.investpro.exchange.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Binance WebSocket client for real-time trade streaming.
 * Extends ExchangeWebSocketClient with Binance-specific message handling.
 */
@Getter
@Setter
@Slf4j
public class BinanceWebSocketClient extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(BinanceWebSocketClient.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final java.util.Map<String, Consumer<String>> streamHandlers = new java.util.concurrent.ConcurrentHashMap<>();
    private long subscriptionId = 1;
    protected final Map<TradePair, ExchangeStreamConsumer> liveTradeConsumers =
            Collections.synchronizedMap(new HashMap<>());

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

            // Skip subscription response messages
            if (messageJson.has("result") || messageJson.has("id")) {
                logger.debug("Binance subscription response received: {}", messageJson.get("id"));
                return;
            }

            // Call all registered stream handlers
            for (Consumer<String> handler : streamHandlers.values()) {
                try {
                    handler.accept(message);
                } catch (Exception e) {
                    logger.warn("Error in stream handler: {}", e.getMessage());
                }
            }

            // Also check if it's a trade event for backward compatibility
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
                    Instant.ofEpochMilli(timestamp));

            // Send to registered consumer
            if (liveTradeConsumers.containsKey(getTradePair())) {
                ExchangeStreamConsumer consumer = liveTradeConsumers.get(getTradePair());
                consumer.acceptTrades(newTrade);
                logger.debug("Processed Binance trade: %d at %s".formatted(tradeId, price));
            }
        } catch (Exception ex) {
            logger.error("Error processing Binance trade message: %s".formatted(ex.getMessage()), ex);
        }
    }

    private TradePair tradePair;
    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {

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
                    .put("id", 1L)
                    .put("params", data1.toString())

                    .toPrettyString();

            send(subscribeMsg);
            logger.info("Subscribed to Binance live trades for %s".formatted(tradePair));
        } catch (Exception ex) {
            logger.error("Failed to subscribe to Binance trades for %s: %s".formatted(tradePair, ex.getMessage()), ex);
        }
    }

    @Override
    public void stopStreamLiveTrades(@NotNull TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
        logger.info("Unsubscribed from Binance live trades for %s".formatted(tradePair));
    }

    @Override
    public boolean supportsStreamingTrades(@NotNull TradePair tradePair) {
        return liveTradeConsumers.containsKey(tradePair);
    }

    @Override
    public void unsubscribeStream(@NotNull String streamName) {
        if (streamName.isBlank()) {
            return;
        }

        try {
            streamHandlers.remove(streamName);
            String unsubscribeMsg = OBJECT_MAPPER.createObjectNode()
                    .put("method", "UNSUBSCRIBE")

                    .put("id", subscriptionId)
                    .set("params",
                            new ArrayNode(
                            OBJECT_MAPPER.getNodeFactory()).add(streamName)).asText();

            send(unsubscribeMsg);
            logger.debug("Unsubscribed from Binance stream: {}", streamName);
        } catch (Exception e) {
            logger.warn("Failed to unsubscribe from Binance stream {}: {}", streamName, e.getMessage());
        }
    }

    @Override
    public void subscribeStream(@NotNull String streamName, @NotNull Consumer<String> handler) {
        if (streamName.isBlank()) {
            return;
        }

        try {
            streamHandlers.put(streamName, handler);

            // Binance subscription format:
            // {"method":"SUBSCRIBE","params":["btcusdt@trade"],"id":1}
            String subscribeMsg = OBJECT_MAPPER.createObjectNode()
                    .put("method", "SUBSCRIBE")
                    .put("id", subscriptionId++)
                    .set("params", new com.fasterxml.jackson.databind.node.ArrayNode(
                            OBJECT_MAPPER.getNodeFactory()).add(streamName))
                    .toString();

            send(subscribeMsg);
            logger.debug("Subscribed to Binance stream: {}", streamName);
        } catch (Exception e) {
            logger.warn("Failed to subscribe to Binance stream {}: {}", streamName, e.getMessage());
        }
    }

    /**
     * Called by the neutral ExchangeWebSocketClient after socket opens.
     */
    @Override
    protected void onConnected() {
//        try {
//            sendSubscribe(null, HEARTBEATS_CHANNEL);
//        } catch (Exception exception) {
//            log.debug("Unable to subscribe Coinbase heartbeats", exception);
//        }
//
//        synchronized (liveTradeConsumers) {
//            for (TradePair pair : liveTradeConsumers.keySet()) {
//                try {
//                    sendSubscribe(pair, MARKET_TRADES_CHANNEL);
//                    pendingSubscriptions.remove(pair);
//                    log.info("Resubscribed Coinbase market trades for {}", pair);
//                } catch (Exception exception) {
//                    pendingSubscriptions.add(pair);
//                    log.warn("Unable to resubscribe Coinbase trades for {}", pair, exception);
//                }
//            }
//        }
//
//        synchronized (rawStreamHandlers) {
//            for (String streamKey : rawStreamHandlers.keySet()) {
//                CoinbaseWebSocketClient.CoinbaseStream stream = parseCoinbaseStream(streamKey);
//                if (stream == null) {
//                    continue;
//                }
//
//                try {
//                    sendSubscribe(stream.tradePair(), stream.channel());
//                    log.info("Resubscribed Coinbase raw stream {}", stream.key());
//                } catch (Exception exception) {
//                    log.warn("Unable to resubscribe Coinbase raw stream {}", stream.key(), exception);
//                }
//            }
//        }
    }
}
