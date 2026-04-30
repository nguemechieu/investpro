package org.investpro.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.utils.Side;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for CoinbaseWebSocketClient
 * Tests WebSocket message handling, data reception, and consumer integration
 * 
 * @author NOEL NGUEMECHIEU
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Coinbase WebSocket Client Tests")
class CoinbaseWebSocketClientTest {

    private CoinbaseWebSocketClient webSocketClient;
    
    @Mock
    private LiveTradesConsumer mockConsumer;
    
    private TradePair testTradePair;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize WebSocket client with test URI
        webSocketClient = new CoinbaseWebSocketClient(
            URI.create("wss://advanced-trade-ws.coinbase.com"),
            new Draft_6455()
        );
        
        // Setup test trade pair (BTC-USD)
        testTradePair = new TradePair(
            new CryptoCurrency("Bitcoin", "Bitcoin", "BTC", 8, "BTC", "bitcoin"),
            new FiatCurrency("US Dollar", "USD", "USD", 2, "$", "usd")
        );
        
        webSocketClient.setTradePair(testTradePair);
        
        // Initialize ObjectMapper
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("Should handle empty message gracefully")
    void testEmptyMessage() {
        // When: onMessage is called with empty string
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage("");
            webSocketClient.onMessage((String) null);
            webSocketClient.onMessage("   ");
        });
    }

    @Test
    @DisplayName("Should handle malformed JSON gracefully")
    void testMalformedJsonMessage() {
        // When: onMessage is called with invalid JSON
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage("{ invalid json }");
            webSocketClient.onMessage("not json at all");
        });
    }

    @Test
    @DisplayName("Should establish connection on info event")
    void testConnectionEstablishment() {
        // Given: subscriptions channel message (Advanced Trade format)
        String infoMessage = "{\"channel\": \"subscriptions\", \"events\": []}";
        
        // When: message is processed
        webSocketClient.onMessage(infoMessage);
        
        // Then: connection should be established
        assertThat(webSocketClient.connectionEstablished.get())
            .as("Connection should be established after info event")
            .isTrue();
    }

    @Test
    @DisplayName("Should process match (trade) message and send data to consumer")
    void testTradeMessageProcessing() {
        // Given: consumer is registered
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        // And: match message with trade data
        String tradeMessage = createTradeMessage(
            "12345",           // trade_id
            "45500.50",        // price
            "0.5",             // size
            "buy",             // side
            "2024-04-28T10:30:00Z"  // timestamp
        );
        
        // When: message is processed
        webSocketClient.onMessage(tradeMessage);
        
        // Then: consumer should receive trade data
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(mockConsumer, times(1)).acceptTrades(tradeCaptor.capture());
        
        Trade capturedTrade = tradeCaptor.getValue();
        assertThat(capturedTrade)
            .as("Trade should be captured and passed to consumer")
            .isNotNull();
        assertThat(capturedTrade.getTradePair())
            .as("Trade pair should match registered pair")
            .isEqualTo(testTradePair);
    }

    @Test
    @DisplayName("Should handle heartbeat message")
    void testHeartbeatMessage() {
        // Given: heartbeat message
        String heartbeatMessage = "{\"type\": \"heartbeat\", \"on\": \"true\"}";
        
        // When: message is processed
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage(heartbeatMessage);
        });
    }

    @Test
    @DisplayName("Should handle error message")
    void testErrorMessage() {
        // Given: error message
        String errorMessage = "{\"type\": \"error\", \"message\": \"Invalid subscription\"}";
        
        // When: message is processed
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage(errorMessage);
        });
    }

    @Test
    @DisplayName("Should ignore message if consumer not registered for trade pair")
    void testMessageIgnoredWhenConsumerNotRegistered() {
        // Given: no consumer registered
        webSocketClient.liveTradeConsumers.clear();
        
        // And: trade message
        String tradeMessage = createTradeMessage(
            "12345",
            "45500.50",
            "0.5",
            "buy",
            "2024-04-28T10:30:00Z"
        );
        
        // When: message is processed
        webSocketClient.onMessage(tradeMessage);
        
        // Then: no consumer should be called
        verify(mockConsumer, never()).acceptTrades(any(Trade.class));
    }

    @Test
    @DisplayName("Should handle trade pair subscription")
    void testStreamLiveTrades() {
        // Given: WebSocket is open and consumer is ready
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        // When: streaming is set up
        webSocketClient.streamLiveTrades(testTradePair, mockConsumer);
        
        // Then: consumer should be stored
        assertThat(webSocketClient.liveTradeConsumers)
            .as("Consumer should be stored in map")
            .containsKey(testTradePair)
            .containsValue(mockConsumer);
    }

    @Test
    @DisplayName("Should extract correct price from trade message")
    void testPriceExtraction() {
        // Given: consumer registered
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        String expectedPrice = "45500.75";
        String tradeMessage = createTradeMessage(
            "12345",
            expectedPrice,
            "0.5",
            "buy",
            "2024-04-28T10:30:00Z"
        );
        
        // When: message is processed
        webSocketClient.onMessage(tradeMessage);
        
        // Then: trade should have correct price
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(mockConsumer, times(1)).acceptTrades(tradeCaptor.capture());
        
        Trade trade = tradeCaptor.getValue();
        assertThat(trade.getPrice())
            .as("Price should be correctly extracted from message")
            .isEqualTo(Double.parseDouble(expectedPrice));
    }

    @Test
    @DisplayName("Should extract correct quantity from trade message")
    void testQuantityExtraction() {
        // Given: consumer registered
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        String expectedQuantity = "2.5";
        String tradeMessage = createTradeMessage(
            "12345",
            "45500.50",
            expectedQuantity,
            "sell",
            "2024-04-28T10:30:00Z"
        );
        
        // When: message is processed
        webSocketClient.onMessage(tradeMessage);
        
        // Then: trade should have correct quantity
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(mockConsumer, times(1)).acceptTrades(tradeCaptor.capture());
        
        Trade trade = tradeCaptor.getValue();
        assertThat(trade.getAmount())
            .as("Quantity should be correctly extracted from message")
            .isEqualTo(Double.parseDouble(expectedQuantity));
    }

    @Test
    @DisplayName("Should extract correct side (buy/sell) from trade message")
    void testSideExtraction() {
        // Given: consumer registered
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        String tradeMessage = createTradeMessage(
            "12345",
            "45500.50",
            "0.5",
            "sell",
            "2024-04-28T10:30:00Z"
        );
        
        // When: message is processed
        webSocketClient.onMessage(tradeMessage);
        
        // Then: trade should have correct side
        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(mockConsumer, times(1)).acceptTrades(tradeCaptor.capture());
        
        Trade trade = tradeCaptor.getValue();
        assertThat(trade.getTransactionType())
            .as("Side should be SELL")
            .isEqualTo(Side.SELL);
    }

    @Test
    @DisplayName("Should handle multiple trade messages in sequence")
    void testMultipleTradeMessages() {
        // Given: consumer registered
        webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
        
        // When: multiple trade messages are processed
        for (int i = 0; i < 5; i++) {
            String tradeMessage = createTradeMessage(
                "trade-" + i,
                String.valueOf(45000 + i * 100),
                "0.5",
                i % 2 == 0 ? "buy" : "sell",
                "2024-04-28T10:30:00Z"
            );
            webSocketClient.onMessage(tradeMessage);
        }
        
        // Then: consumer should be called 5 times
        verify(mockConsumer, times(5)).acceptTrades(any(Trade.class));
    }

    @Test
    @DisplayName("Should maintain separate consumers for different trade pairs")
    void testMultipleTradePairConsumers() throws Exception {
        // Given: multiple trade pairs and consumers
        LiveTradesConsumer consumer1 = mock(LiveTradesConsumer.class);
        LiveTradesConsumer consumer2 = mock(LiveTradesConsumer.class);
        
        TradePair pair1 = testTradePair;
        TradePair pair2 = new TradePair(
            new CryptoCurrency("Ethereum", "Ethereum", "ETH", 8, "ETH", "ethereum"),
            new FiatCurrency("US Dollar", "USD", "USD", 2, "$", "usd")
        );
        
        webSocketClient.liveTradeConsumers.put(pair1, consumer1);
        webSocketClient.liveTradeConsumers.put(pair2, consumer2);
        
        // When: switching between pairs and processing messages
        webSocketClient.setTradePair(pair1);
        webSocketClient.onMessage(createTradeMessage("1", "45500", "0.5", "buy", "2024-04-28T10:30:00Z"));
        
        webSocketClient.setTradePair(pair2);
        webSocketClient.onMessage(createTradeMessage("2", "3000", "1.0", "sell", "2024-04-28T10:30:00Z"));
        
        // Then: each consumer should receive their own pair's data
        verify(consumer1, times(1)).acceptTrades(any(Trade.class));
        verify(consumer2, times(1)).acceptTrades(any(Trade.class));
    }

    @Test
    @DisplayName("Should handle message without type field")
    void testMessageWithoutTypeField() {
        // Given: message without type
        String messageWithoutType = "{\"side\": \"buy\", \"price\": \"45500\"}";
        
        // When: message is processed
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage(messageWithoutType);
        });
    }

    @Test
    @DisplayName("Should handle unhandled message types gracefully")
    void testUnhandledMessageType() {
        // Given: unknown message type
        String unknownTypeMessage = "{\"type\": \"unknown_type\", \"data\": \"some data\"}";
        
        // When: message is processed
        assertThatNoException().isThrownBy(() -> {
            webSocketClient.onMessage(unknownTypeMessage);
        });
    }

    @Test
    @DisplayName("Should reject null consumer registration")
    void testNullConsumerRejection() {
        // When: attempting to stream with null consumer
        webSocketClient.streamLiveTrades(testTradePair, null);
        
        // Then: should not throw but handle gracefully
        assertThat(webSocketClient.liveTradeConsumers)
            .doesNotContainKey(testTradePair);
    }

    /**
     * Helper method to create a properly formatted Coinbase trade (match) message
     */
    private String createTradeMessage(String tradeId, String price, String size, String side, String timestamp) {
        ObjectNode message = JsonNodeFactory.instance.objectNode();
        message.put("type", "match");
        message.put("trade_id", tradeId);
        message.put("price", price);
        message.put("size", size);
        message.put("side", side);
        message.put("time", timestamp);
        message.put("product_id", "BTC-USD");
        
        return message.toString();
    }
}
