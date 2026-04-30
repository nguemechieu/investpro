package org.investpro.exchange;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.java_websocket.drafts.Draft_6455;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Detailed test suite for Coinbase WebSocket message parsing and data flow
 * Tests various message formats, edge cases, and data validation
 * 
 * @author NOEL NGUEMECHIEU
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Coinbase WebSocket Message Parsing Tests")
class CoinbaseWebSocketMessageParsingTest {

    private CoinbaseWebSocketClient webSocketClient;
    
    @Mock
    private LiveTradesConsumer mockConsumer;
    
    private TradePair testTradePair;

    @BeforeEach
    void setUp() throws SQLException, ClassNotFoundException {
        webSocketClient = new CoinbaseWebSocketClient(
            URI.create("wss://advanced-trade-ws.coinbase.com"),
            new Draft_6455()
        );
        
        testTradePair = new TradePair(
            new CryptoCurrency("Bitcoin", "Bitcoin", "BTC", 8, "BTC", "bitcoin"),
            new FiatCurrency("US Dollar", "USD", "USD", 2, "$", "usd")
        );
        
        webSocketClient.setTradePair(testTradePair);
    }

    @Nested
    @DisplayName("Trade Message Validation")
    class TradeMessageValidation {

        @Test
        @DisplayName("Should correctly parse buy trade message")
        void testBuyTradeMessage() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: buy trade message
            String buyMessage = createTradeMessage(
                "BUY-001",
                "50000.25",
                "1.5",
                "buy",
                "2024-04-28T14:30:45Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(buyMessage);
            
            // Then: trade should be captured
            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(mockConsumer).acceptTrades(captor.capture());
            
            Trade trade = captor.getValue();
            assertThat(trade)
                .as("Trade should be captured")
                .isNotNull();
        }

        @Test
        @DisplayName("Should correctly parse sell trade message")
        void testSellTradeMessage() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: sell trade message
            String sellMessage = createTradeMessage(
                "SELL-001",
                "49500.75",
                "2.0",
                "sell",
                "2024-04-28T14:35:30Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(sellMessage);
            
            // Then: trade should be captured
            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(mockConsumer).acceptTrades(captor.capture());
            
            Trade trade = captor.getValue();
            assertThat(trade)
                .as("Sell trade should be captured")
                .isNotNull();
        }

        @Test
        @DisplayName("Should handle very small trade quantities")
        void testSmallQuantityTrade() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: very small quantity
            String message = createTradeMessage(
                "SMALL-001",
                "50000.00",
                "0.00001",
                "buy",
                "2024-04-28T14:30:00Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(message);
            
            // Then: should handle small quantities
            verify(mockConsumer).acceptTrades(any(Trade.class));
        }

        @Test
        @DisplayName("Should handle very large trade quantities")
        void testLargeQuantityTrade() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: very large quantity
            String message = createTradeMessage(
                "LARGE-001",
                "50000.00",
                "1000.0",
                "sell",
                "2024-04-28T14:30:00Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(message);
            
            // Then: should handle large quantities
            verify(mockConsumer).acceptTrades(any(Trade.class));
        }

        @Test
        @DisplayName("Should handle high precision prices")
        void testHighPrecisionPrice() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: high precision price
            String message = createTradeMessage(
                "PRECISION-001",
                "50000.123456789",
                "0.5",
                "buy",
                "2024-04-28T14:30:00Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(message);
            
            // Then: should capture high precision
            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(mockConsumer).acceptTrades(captor.capture());
            
            Trade trade = captor.getValue();
            assertThat(trade.getPrice())
                .as("Should handle high precision prices")
                .isNotNull();
        }
    }

    @Nested
    @DisplayName("Connection State Management")
    class ConnectionStateManagement {

        @Test
        @DisplayName("Should be not connected initially")
        void testInitialConnectionState() {
            assertThat(webSocketClient.connectionEstablished.get())
                .as("Should not be connected initially")
                .isFalse();
        }

        @Test
        @DisplayName("Should transition to connected after info event")
        void testConnectionStateTransition() {
            // Given: not connected
            assertThat(webSocketClient.connectionEstablished.get()).isFalse();
            
            // When: info event received (subscriptions channel)
            String infoEvent = "{\"channel\": \"subscriptions\", \"events\": []}";
            webSocketClient.onMessage(infoEvent);
            
            // Then: should be connected
            assertThat(webSocketClient.connectionEstablished.get())
                .as("Should be connected after info event")
                .isTrue();
        }

        @Test
        @DisplayName("Should maintain connection state across multiple messages")
        void testConnectionStatePersistence() {
            // Given: info event establishes connection (subscriptions channel)
            webSocketClient.onMessage("{\"channel\": \"subscriptions\", \"events\": []}");
            assertThat(webSocketClient.connectionEstablished.get()).isTrue();
            
            // When: other messages are processed
            webSocketClient.onMessage("{\"type\": \"heartbeat\"}");
            webSocketClient.onMessage("{\"type\": \"unknown\"}");
            
            // Then: connection should still be established
            assertThat(webSocketClient.connectionEstablished.get())
                .as("Connection state should persist")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Consumer Data Reception")
    class ConsumerDataReception {

        @Test
        @DisplayName("Should pass all trade fields to consumer")
        void testAllTradeFieldsPassedToConsumer() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // And: complete trade message
            String tradeMessage = createTradeMessage(
                "COMPLETE-001",
                "52500.50",
                "3.25",
                "buy",
                "2024-04-28T15:45:30.123456Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(tradeMessage);
            
            // Then: consumer receives complete trade data
            ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
            verify(mockConsumer).acceptTrades(captor.capture());
            
            Trade trade = captor.getValue();
            assertThat(trade)
                .as("All trade fields should be present")
                .isNotNull()
                .satisfies(t -> {
                    assertThat(t.getTradePair()).isEqualTo(testTradePair);
                    assertThat(t.getPrice()).isNotNull();
                    assertThat(t.getAmount()).isPositive();
                    assertThat(t.getTransactionType()).isNotNull();
                });
        }

        @Test
        @DisplayName("Should not call consumer if consumer is null")
        void testNullConsumerHandling() {
            // Given: no consumer registered but trying to set one as null
            webSocketClient.liveTradeConsumers.clear();
            
            // And: trade message
            String tradeMessage = createTradeMessage(
                "NULL-CONSUMER",
                "50000.00",
                "1.0",
                "buy",
                "2024-04-28T15:00:00Z"
            );
            
            // When: message is processed
            webSocketClient.onMessage(tradeMessage);
            
            // Then: no exceptions and consumer not called
            verify(mockConsumer, never()).acceptTrades(any());
        }

        @Test
        @DisplayName("Should batch multiple trades to same consumer")
        void testMultipleTradesToSameConsumer() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // When: processing multiple trades rapidly
            for (int i = 0; i < 10; i++) {
                String message = createTradeMessage(
                    "BATCH-" + i,
                    String.valueOf(50000 + i),
                    "0.1",
                    i % 2 == 0 ? "buy" : "sell",
                    "2024-04-28T15:00:00Z"
                );
                webSocketClient.onMessage(message);
            }
            
            // Then: consumer should be called for each trade
            verify(mockConsumer, times(10)).acceptTrades(any(Trade.class));
        }
    }

    @Nested
    @DisplayName("Error Handling and Edge Cases")
    class ErrorHandling {

        @Test
        @DisplayName("Should handle message with missing optional fields")
        void testMessageWithMissingOptionalFields() {
            // Given: message with minimal fields
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            message.put("type", "match");
            message.put("price", "50000.00");
            message.put("size", "1.0");
            
            // When: message is processed
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            assertThatNoException().isThrownBy(() -> {
                webSocketClient.onMessage(message.toString());
            });
        }

        @Test
        @DisplayName("Should handle invalid price format gracefully")
        void testInvalidPriceFormat() {
            // Given: message with invalid price
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            message.put("type", "match");
            message.put("price", "invalid-price");
            message.put("size", "1.0");
            message.put("side", "buy");
            
            // When: message is processed
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // Then: should not crash
            assertThatNoException().isThrownBy(() -> {
                webSocketClient.onMessage(message.toString());
            });
        }

        @Test
        @DisplayName("Should handle invalid quantity format gracefully")
        void testInvalidQuantityFormat() {
            // Given: message with invalid quantity
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            message.put("type", "match");
            message.put("price", "50000.00");
            message.put("size", "invalid-quantity");
            message.put("side", "buy");
            
            // When: message is processed
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // Then: should not crash
            assertThatNoException().isThrownBy(() -> {
                webSocketClient.onMessage(message.toString());
            });
        }

        @Test
        @DisplayName("Should handle unknown side value")
        void testUnknownSideValue() {
            // Given: message with unknown side
            ObjectNode message = JsonNodeFactory.instance.objectNode();
            message.put("type", "match");
            message.put("price", "50000.00");
            message.put("size", "1.0");
            message.put("side", "unknown_side");
            message.put("trade_id", "UNKNOWN-001");
            message.put("time", "2024-04-28T15:00:00Z");
            
            // When: message is processed
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            // Then: should still process message
            assertThatNoException().isThrownBy(() -> {
                webSocketClient.onMessage(message.toString());
            });
        }

        @Test
        @DisplayName("Should handle duplicate trade IDs")
        void testDuplicateTradeIds() {
            // Given: consumer registered
            webSocketClient.liveTradeConsumers.put(testTradePair, mockConsumer);
            
            String message = createTradeMessage(
                "DUPLICATE-001",
                "50000.00",
                "1.0",
                "buy",
                "2024-04-28T15:00:00Z"
            );
            
            // When: processing same trade ID twice
            webSocketClient.onMessage(message);
            webSocketClient.onMessage(message);
            
            // Then: both should be processed (no deduplication at WebSocket level)
            verify(mockConsumer, times(2)).acceptTrades(any(Trade.class));
        }
    }

    /**
     * Helper method to create a properly formatted Coinbase trade message
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
        message.put("user_id", "test-user");
        message.put("profile_id", "test-profile");
        message.put("order_id", "test-order");
        message.put("liquidity", "T");
        message.put("fee_rate", "0.005");
        message.put("best_bid", "49999.00");
        message.put("best_ask", "50001.00");
        
        return message.toString();
    }
}
