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

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;


public class BinanceUsWebSocket extends ExchangeWebSocketClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);


    public BinanceUsWebSocket() {
        super(
                URI.create(
                        "wss://stream.binance.us:9443/ws/btcusdt@kline_1m"
                        // "wss://ws-api.binance.us:443/ws-api/v3"
                )

                , new Draft_6455());



    }

    @Override
    public void onOpen(org.java_websocket.WebSocket conn, ClientHandshake handshake) {

        logger.info("BinanceUsWebSocket: " + conn);

    }

    @Override
    public void onClose(org.java_websocket.WebSocket conn, int code, String reason, boolean remote) {

        logger.info("BinanceUsWebSocket: " + code + " " + reason);


    }

    @Override
    public void onMessage(org.java_websocket.WebSocket conn, String message) {
        JsonNode messageJson;
        try {
            messageJson = OBJECT_MAPPER.readTree(message);
        } catch (JsonProcessingException ex) {
            logger.error("ex: ", ex);
            throw new RuntimeException(ex);
        }

        if (messageJson.has("e") && messageJson.get("e").asText().equalsIgnoreCase("info")) {

            logger.info("BinanceUsWebSocket: " + messageJson);

        }

    }

    @Override
    public void onError(org.java_websocket.WebSocket conn, Exception ex) {
        logger.error("ex: ", ex);

    }

    @Override
    public void onStart() {

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

        if (messageJson.has("e") && messageJson.get("e").asText().equalsIgnoreCase("info")) {

            logger.info("BinanceUsWebSocket: " + messageJson);

        }

//
//            "e": "executionReport",        // Event type
//                    "E": 1499405658658,            // Event time
//                    "s": "ETHBTC",                 // Symbol
//                    "c": "mUvoqJxFIILMdfAW5iGSOW", // Client order ID
//                    "S": "BUY",                    // Side
//                    "o": "LIMIT",                  // Order type
//                    "f": "GTC",                    // Time in force
//                    "q": "1.00000000",             // Order quantity
//                    "p": "0.10264410",             // Order price
//                    "P": "0.00000000",             // Stop price
//                    "d": 4,                        // Trailing Delta; This is only visible if the order was a trailing stop order.
//                    "F": "0.00000000",             // Iceberg quantity
//                    "g": -1,                       // OrderListId
//                    "C": "",                       // Original client order ID; This is the ID of the order being canceled
//                    "x": "NEW",                    // Current execution type
//                    "X": "NEW",                    // Current order status
//                    "r": "NONE",                   // Order reject reason; will be an error code.
//                    "i": 4293153,                  // Order ID
//                    "l": "0.00000000",             // Last executed quantity
//                    "z": "0.00000000",             // Cumulative filled quantity
//                    "L": "0.00000000",             // Last executed price
//                    "n": "0",                      // Commission amount
//                    "N": null,                     // Commission asset
//                    "T": 1499405658657,            // Transaction time
//                    "t": -1,                       // Trade ID
//                    "I": 8641984,                  // Ignore
//                    "w": true,                     // Is the order on the book?
//                    "m": false,                    // Is this trade the maker side?
//                    "M": false,                    // Ignore
//                    "O": 1499405658657,            // Order creation time
//                    "Z": "0.00000000",             // Cumulative quote asset transacted quantity
//                    "Y": "0.00000000",             // Last quote asset transacted quantity (i.e. lastPrice * lastQty)
//                    "Q": "0.00000000",             //Quote Order Quantity
//                    "V": "selfTradePreventionMode",
//                    "D": "trailing_time",          // (Appears if the trailing stop order is active)
//                    "W": "workingTime"             // (Appears if the order is working on the order book)
//            "u":12332                      // tradeGroupId (Appear if the order has expired due to STP)
//            "v":122                        // preventedMatchId (Appear if the order has expired due to STP)
//            "U":2039                       // counterOrderId (Appear if the order has expired due to STP)
//            "A":"1.00000000"               // preventedQuantity(Appear if the order has expired due to STP )
//            "B":"2.00000000"               // lastPreventedQuantity(Appear if the order has expired due to STP)


        Side side = messageJson.has("S") ? Side.getSide(messageJson.get("S").asText()) : null;
        logger.info("BinanceUsWebSocket: " + messageJson + " " + side);
        switch (messageJson.asText()) {
            case "heartbeat" ->
                    sendText(OBJECT_MAPPER.createObjectNode().put("type", "heartbeat").put("on", "false").toPrettyString(),true);
            case "match" -> {


            }
            case "error" -> throw new IllegalArgumentException("Error on Binance websocket client: " +
                    messageJson.get("message").asText());
            default -> throw new IllegalStateException("Unhandled message type on Gdax websocket client: " +
                    messageJson.get("type").asText());
        }

    }

    private @NotNull TradePair parseTradePair(@NotNull JsonNode messageJson) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        String productId0 = messageJson.get("s").asText();
        String productId1;
        if (productId0.contains("USDT") || productId0.contains("USD")) {
            productId0 = productId0.split("USDT")[0];
            productId1 = productId0 + "-" + "USDT";

            return TradePair.parse(productId1, "-", new Pair<>(CryptoCurrency.class, Currency.class));
        } else if (productId0.contains("BTC")) {
            productId0 = productId0.split("BTC")[0];
            productId1 = productId0 + "-" + "BTC";
            return TradePair.parse(productId1, "-", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));
        } else throw new CurrencyNotFoundException(CurrencyType.CRYPTO, productId0);

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
        logger.info("sendText: {}", data);

    }



    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("onClose: code: {}, reason: {}, remote: {}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        logger.error("ex: ", ex);

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
        logger.info("onOpen: {}", serverHandshake);
    }

    @Override
    public boolean isSupportsStreamingTrades() {
        return false;
    }
}
