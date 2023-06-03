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

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public abstract class OandaWebSocket extends ExchangeWebSocketClient {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    Account connectionEstablished;


//    curl \
//            -H "Authorization: Bearer <AUTHENTICATION TOKEN>" \
//            "https://stream-fxtrade.oanda.com/v3/accounts/<ACCOUNT>/pricing/stream?instruments=EUR_USD%2CUSD_CAD"


    public OandaWebSocket(String accountId) {
        super(URI.create("ws://stream-fxtrade.oanda.com/v3/accounts/" + accountId + "/pricing/stream?instruments=EUR_USD%2CUSD_CAD"),

                new Draft_6455());


        this.connectionEstablished = new Account();
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

        //                    {"asks":[{"liquidity":10000000,"price":"1.11704"},{"liquidity":10000000,"price":"1.11706"}],"bids":                        [{"liquidity":10000000,"price":"1.11690"},{"liquidity":10000000,"price":"1.11688"}],"closeoutAsk":"1.11708","closeoutBid":"1.11686","instrument":"EUR_USD","status":"tradeable","time":"2016-09-20T15:05:47.960449532Z"}
//                    {"asks":[{"liquidity":1000000,"price":"1.32149"},{"liquidity":2000000,"price":"1.32150"},{"liquidity":5000000,"price":"1.32151"},{"liquidity":10000000,"price":"1.32153"}],"bids":[{"liquidity":1000000,"price":"1.32128"},{"liquidity":2000000,"price":"1.32127"},{"liquidity":5000000,"price":"1.32126"},{"liquidity":10000000,"price":"1.32124"}],"closeoutAsk":"1.32153","closeoutBid":"1.32124","instrument":"USD_CAD","status":"tradeable","time":"2016-09-20T15:05:48.157162748Z"}
//                    {"asks":[{"liquidity":1000000,"price":"1.32145"},{"liquidity":2000000,"price":"1.32146"},{"liquidity":5000000,"price":"1.32147"},{"liquidity":10000000,"price":"1.32149"}],"bids":[{"liquidity":1000000,"price":"1.32123"},{"liquidity":2000000,"price":"1.32122"},{"liquidity":5000000,"price":"1.32121"},{"liquidity":10000000,"price":"1.32119"}],"closeoutAsk":"1.32149","closeoutBid":"1.32119","instrument":"USD_CAD","status":"tradeable","time":"2016-09-20T15:05:48.272079801Z"}
//                    {"asks":[{"liquidity":1000000,"price":"1.32147"},{"liquidity":2000000,"price":"1.32148"},{"liquidity":5000000,"price":"1.32149"},{"liquidity":10000000,"price":"1.32151"}],"bids":[{"liquidity":1000000,"price":"1.32126"},{"liquidity":2000000,"price":"1.32125"},{"liquidity":5000000,"price":"1.32124"},{"liquidity":10000000,"price":"1.32122"}],"closeoutAsk":"1.32151","closeoutBid":"1.32122","instrument":"USD_CAD","status":"tradeable","time":"2016-09-20T15:05:48.540813660Z"}
//                    {"time":"2016-09-20T15:05:50.163791738Z","type":"HEARTBEAT"}

        if (messageJson.has("event") && messageJson.get("event").asText().equalsIgnoreCase("subscribe")) {
            connectionEstablished.setValue(true);
            logger.info(
                    "oanda websocket client: connection established with account: "
            );
        }

        TradePair tradePair = null;
        try {
            tradePair = parseTradePair(messageJson);
        } catch (CurrencyNotFoundException exception) {
            logger.error("oanda websocket client: could not initialize trade pair: " +
                    messageJson.get("asks").asText(), exception);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        Side side = messageJson.has("side") ? Side.getSide(messageJson.get("side").asText()) : null;

        switch (messageJson.get("type").asText()) {
            case "HEARTBEAT" ->
                    sendText(OBJECT_MAPPER.createObjectNode().put("type", "HEARTBEAT").put("on", true).toPrettyString(), false);
            case "match" -> {
                if (liveTradeConsumers.containsKey(tradePair)) {
                    assert tradePair != null;
                    Trade newTrade;
                    try {
                        newTrade = new Trade(tradePair,
                                messageJson.get("asks").get("price").asDouble(),
                                messageJson.get("liquidity").asDouble(),
                                side, Long.parseLong(UUID.randomUUID().toString()),
                                Date.from(Instant.from(ISO_INSTANT.parse(messageJson.get("time").asText()))).getTime());
                    } catch (IOException | InterruptedException | ParseException |
                             URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                    liveTradeConsumers.get(tradePair).acceptTrades(Collections.singletonList(newTrade));
                }
            }
            case "error" -> throw new IllegalArgumentException("Error on Oanda websocket client: " +
                    messageJson.get("message").asText());
            default -> throw new IllegalStateException("Unhandled message type on Gdax websocket client: " +
                    messageJson.get("type").asText());
        }
    }

    private @NotNull TradePair parseTradePair(@NotNull JsonNode messageJson) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        final String productId = messageJson.get("instrument").asText();
        final String[] products = productId.split("_");
        TradePair tradePair;
        if (products[0].equalsIgnoreCase("USD")) {
            tradePair = TradePair.parse(productId, "_", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
        } else {
            // products[0] == "ETH"
            if (products[1].equalsIgnoreCase("usd")) {
                tradePair = TradePair.parse(productId, "_", new Pair<>(CryptoCurrency.class, FiatCurrency.class));
            } else {
                // productId == "ETH-BTC"
                tradePair = TradePair.parse(productId, "_", new Pair<>(CryptoCurrency.class, CryptoCurrency.class));
            }
        }

        return tradePair;
    }


    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, UpdateInProgressCandleTask liveTradesConsumer) {

        sendText(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                .put("ask", tradePair.toString('_')).toPrettyString(), true);
        liveTradeConsumers.put(tradePair, UpdateInProgressCandleTask.wrap(liveTradesConsumer));

    }



    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        liveTradeConsumers.remove(tradePair);
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {

        return !liveTradeConsumers.containsKey(tradePair);
    }

    @Override
    public boolean isStreamingTradesSupported(TradePair tradePair) {
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
                URI.create("wss://stream-fxtrade.oanda.com/v3/accounts/001-001-2783446-002/pricing/stream?instruments=" + "EUR_USD&USD_CAD");
    }


    @Override
    public void request(long n) {
        logger.info("oanda websocket client: request: " + n);


    }

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
        logger.debug("sendText: {}", data);
        return null;
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
        return null;
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        return null;
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        return null;
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
        return null;
    }

    @Override
    public String getSubprotocol() {
        return null;
    }

    @Override
    public boolean isOutputClosed() {
        return false;
    }

    @Override
    public boolean isInputClosed() {
        return false;
    }

    @Override
    public void abort() {
        logger.debug("abort");

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("oanda websocket client: connection closed with code: " + code + ", reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {

        logger.error("onError: ", ex);

    }

    @Override
    public void onOpen(@NotNull ServerHandshake serverHandshake) {
        logger.info("oanda websocket client: connection established with account: " +
                serverHandshake.getHttpStatusMessage());
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return 0;
    }

    @Override
    public void setAsyncSendTimeout(long timeout) {
        logger.debug("setAsyncSendTimeout: {}", timeout);


    }

    @Override
    public Session connectToServer(Object endpoint, ClientEndpointConfig path) {
        return null;
    }

    @Override
    public Session connectToServer(Class<?> annotatedEndpointClass, URI path) {
        return null;
    }

    @Override
    public Session connectToServer(Endpoint endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path) {
        return null;
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpoint, ClientEndpointConfig clientEndpointConfiguration, URI path) {
        return null;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return 0;
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long timeout) {
        logger.debug("setDefaultMaxSessionIdleTimeout: {}", timeout);

    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return 0;
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int max) {

    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return 0;
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int max) {

    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return null;
    }

    @Override
    public double getPrice(TradePair tradePair) {

        return 0;
    }
}
