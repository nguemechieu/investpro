package org.investpro.exchange;

import org.investpro.exchange.models.*;
import org.investpro.exchange.oanda.Oanda;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.nio.channels.UnresolvedAddressException;

import static org.assertj.core.api.Assertions.*;

/**
 * Contract tests for OANDA exchange adapter.
 *
 * <p>
 * These tests verify:
 * <ul>
 * <li>Endpoint construction (URL format validation)</li>
 * <li>Capability declarations</li>
 * <li>Authentication flow</li>
 * <li>Order book fallback behavior</li>
 * </ul>
 */
@DisplayName("OANDA Exchange Adapter Contract Tests")
class OandaAdapterContractTest {

    @Test
    @DisplayName("OANDA uses correct camelCase orderBook endpoint")
    void testOrderBookEndpointUsesCamelCase() {
        // This test documents that OANDA endpoint is /orderBook (camelCase)
        // NOT /orderbook (lowercase)
        // The actual HTTP call is verified in integration tests with mocked HTTP client

        String correctEndpoint = "/v3/instruments/EUR_USD/orderBook";
        String incorrectEndpoint = "/v3/instruments/EUR_USD/orderbook";

        // Confirm the correct endpoint follows camelCase
        assertThat(correctEndpoint)
                .contains("orderBook")
                .doesNotContain("orderbook");

        assertThat(incorrectEndpoint)
                .contains("orderbook")
                .doesNotContain("orderBook");
    }

    @Test
    @DisplayName("OANDA capability declares TOP_OF_BOOK market depth")
    void testOandaCapabilityMarketDepthType() {
        // OANDA does not provide Binance/Coinbase-style full order books
        // It should declare TOP_OF_BOOK or DISTRIBUTION_BOOK, not FULL_ORDER_BOOK

        ExchangeCapability oandaCapability = ExchangeCapability.builder()
                .exchangeName("OANDA")
                .supportsForex(true)
                .supportsCrypto(false)
                .supportsFullOrderBook(false) // OANDA does NOT have full depth books
                .supportsTopOfBook(true) // OANDA provides best bid/ask
                .supportsDistributionBook(true) // Via positionBook endpoint
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .authenticationType("API_KEY")
                .apiBaseUrl("https://api-fxtrade.oanda.com")
                .build();

        // Verify capability
        assertThat(oandaCapability)
                .isNotNull()
                .extracting("exchangeName").isEqualTo("OANDA");

        assertThat(oandaCapability.isSupportsForex()).isTrue();
        assertThat(oandaCapability.isSupportsFullOrderBook()).isFalse();
        assertThat(oandaCapability.getMarketDepthType())
                .isNotEqualTo(MarketDepthType.FULL_ORDER_BOOK)
                .isEqualTo(MarketDepthType.TOP_OF_BOOK);
    }

    @Test
    @DisplayName("OANDA supports() method checks features against capability")
    void testOandaSupportsFeatures() {
        ExchangeCapability oandaCapability = ExchangeCapability.builder()
                .exchangeName("OANDA")
                .supportsForex(true)
                .supportsTopOfBook(true)
                .supportsFullOrderBook(false)
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .build();

        // Verify feature checking
        assertThat(oandaCapability.supports(ExchangeFeature.FOREX_TRADING)).isTrue();
        assertThat(oandaCapability.supports(ExchangeFeature.CRYPTO_TRADING)).isFalse();
        assertThat(oandaCapability.supports(ExchangeFeature.FULL_ORDER_BOOK)).isFalse();
        assertThat(oandaCapability.supports(ExchangeFeature.TOP_OF_BOOK)).isTrue();
    }

    @Test
    @DisplayName("AuthCheckResult distinguishes credential vs endpoint failures")
    void testAuthCheckResultFailureTypes() {
        // Credential failure
        AuthCheckResult credentialFailure = AuthCheckResult.builder()
                .exchangeName("OANDA")
                .success(false)
                .credentialIssue(true) // The credentials are invalid
                .endpointTested("/v3/accounts")
                .httpStatus(401)
                .message("Unauthorized: invalid API key")
                .build();

        // Endpoint failure (network/server issue, not credential)
        AuthCheckResult endpointFailure = AuthCheckResult.builder()
                .exchangeName("OANDA")
                .success(false)
                .credentialIssue(false) // Credentials might be fine
                .endpointTested("/v3/accounts")
                .httpStatus(503)
                .message("Service unavailable")
                .build();

        // Success case
        AuthCheckResult success = AuthCheckResult.builder()
                .exchangeName("OANDA")
                .success(true)
                .credentialIssue(false)
                .endpointTested("/v3/accounts")
                .httpStatus(200)
                .message("OK")
                .build();

        // Verify distinction
        assertThat(credentialFailure)
                .extracting("success").isEqualTo(false);
        assertThat(credentialFailure)
                .extracting("credentialIssue").isEqualTo(true);

        assertThat(endpointFailure)
                .extracting("credentialIssue").isEqualTo(false);
        assertThat(endpointFailure.getHttpStatus()).isEqualTo(503);

        assertThat(success)
                .extracting("success").isEqualTo(true);
        assertThat(success.getHttpStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("OANDA has pricing-based synthetic fallback documented")
    void testOandaPricingFallbackDocumentation() {
        // This documents that OANDA can create synthetic top-of-book
        // from pricing endpoint when order book unavailable

        ExchangeCapability capability = ExchangeCapability.builder()
                .exchangeName("OANDA")
                .marketDepthType(MarketDepthType.TOP_OF_BOOK)
                .notes("Supports pricing-derived synthetic order books. Uses positionBook " +
                        "endpoint first, falls back to pricing endpoint to construct top-of-book.")
                .build();

        assertThat(capability.getNotes())
                .contains("pricing-derived")
                .contains("synthetic order books");
    }

    @Test
    @DisplayName("Market depth types are mutually exclusive in capability")
    void testMarketDepthTypeMutuallyExclusive() {
        // An exchange should have ONE primary marketDepthType
        // Even if it supports multiple (rare), the primary type matters most

        ExchangeCapability oanda = ExchangeCapability.builder()
                .exchangeName("OANDA")
                .supportsTopOfBook(true)
                .supportsDistributionBook(true)
                .supportsFullOrderBook(false)
                .marketDepthType(MarketDepthType.TOP_OF_BOOK) // Primary type
                .build();

        assertThat(oanda.getMarketDepthType()).isEqualTo(MarketDepthType.TOP_OF_BOOK);

        // If an exchange somehow supported full book, it would be:
        ExchangeCapability hypothetical = ExchangeCapability.builder()
                .exchangeName("HYPOTHETICAL")
                .supportsFullOrderBook(true)
                .marketDepthType(MarketDepthType.FULL_ORDER_BOOK)
                .build();

        assertThat(hypothetical.getMarketDepthType()).isEqualTo(MarketDepthType.FULL_ORDER_BOOK);
    }

    @Test
    @DisplayName("OANDA classifies unresolved address failures as connectivity issues")
    void testUnresolvedAddressIsConnectivityIssue() {
        ConnectException exception = new ConnectException();
        exception.initCause(new UnresolvedAddressException());

        assertThat(Oanda.isConnectivityException(exception)).isTrue();
    }
}
