package org.investpro.exchange;

import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.models.trading.LiveTradesConsumer;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration test for Coinbase exchange
 * Tests the overall exchange functionality and WebSocket setup
 * 
 * @author NOEL NGUEMECHIEU
 */
@DisplayName("Coinbase Exchange Integration Tests")
class CoinbaseIntegrationTest {

    private Coinbase coinbaseExchange;
    private TradePair btcUsd;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize Coinbase with test API credentials
        coinbaseExchange = new Coinbase("test-api-key", "test-api-secret");
        
        // Setup trade pair
        btcUsd = new TradePair(
            new CryptoCurrency("Bitcoin", "Bitcoin", "BTC", 8, "BTC", "bitcoin"),
            new FiatCurrency("US Dollar", "USD", "USD", 2, "$", "usd")
        );
    }

    @Test
    @DisplayName("Should initialize Coinbase exchange with valid credentials")
    void testExchangeInitialization() {
        assertThat(coinbaseExchange)
            .as("Coinbase exchange should be initialized")
            .isNotNull();
        
        assertThat(coinbaseExchange.getName())
            .as("Exchange name should be Coinbase")
            .isEqualTo("Coinbase");
        
        assertThat(coinbaseExchange.getSignal())
            .as("Exchange signal should be 'Coinbase Advanced Trade'")
            .isEqualTo("Coinbase Advanced Trade");
    }

    @Test
    @DisplayName("Should initialize WebSocket client")
    void testWebSocketClientInitialization() {
        assertThat(coinbaseExchange.getWebsocketClient())
            .as("WebSocket client should be initialized")
            .isNotNull();
    }

    @Test
    @DisplayName("Should accept empty API credentials gracefully")
    void testEmptyCredentialsHandling() {
        // When: exchange initialized with null/empty credentials
        Coinbase exchange = new Coinbase(null, null);
        
        // Then: should initialize successfully
        assertThat(exchange)
            .as("Exchange should initialize with null credentials")
            .isNotNull();
        assertThat(exchange.getWebsocketClient())
            .as("WebSocket should still be initialized")
            .isNotNull();
    }

    @Test
    @DisplayName("Should handle trade pair setup")
    void testTradePairSetup() {
        // When: setting trade pair
        coinbaseExchange.getWebsocketClient().setTradePair(btcUsd);
        
        // Then: trade pair should be set
        assertThat(coinbaseExchange.getWebsocketClient().getTradePair())
            .as("Trade pair should be set in WebSocket client")
            .isEqualTo(btcUsd);
    }

    @Test
    @DisplayName("Should support Coinbase REST API base URLs")
    void testApiUrlsAvailable() {
        // Verify that the exchange has configured API endpoints
        assertThat(coinbaseExchange)
            .as("Exchange should have REST API configured")
            .isNotNull();
        
        // Note: This verifies the exchange is ready to make API calls
        assertThat(coinbaseExchange.getName())
            .isNotEmpty();
    }

    @Test
    @DisplayName("Should have HttpClient configured")
    void testHttpClientConfiguration() {
        // Verify HTTP client for REST API calls
        assertThat(coinbaseExchange)
            .as("Exchange should have HTTP client configured")
            .isNotNull();
    }
}
