package org.investpro.exchange.migration_bridge;

import org.investpro.exchange.Exchange;
import org.investpro.exchange.models.ExchangeCapability;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeAdapterMigrationBridgeTest {
    @Test
    void preservesCompleteProfileAndUsesPublicIdentity() {
        Exchange exchange = exchange();
        ExchangeCapability profile = ExchangeCapability.builder()
                .exchangeName("internal-class-name")
                .apiBaseUrl("https://example.invalid")
                .authenticationType("JWT")
                .supportsMarketOrders(true).supportsLimitOrders(true)
                .supportsFutures(true).supportsPerpetuals(true)
                .supportsBracketOrders(false).supportsStopLossTakeProfit(false)
                .supportsAccountInfo(true).supportsHistoricalCandles(true)
                .supportsTickerStreaming(true).supportsPollingFallback(true)
                .supportsWebSocket(false).supportsFullOrderBook(false)
                .requiresAuthenticationForTrading(true)
                .supportedOrderType("MARKET").supportedTimeframe("1h")
                .build();
        when(exchange.getCapability()).thenReturn(profile);
        // Contract booleans must not be used to invent full-depth or WebSocket support.
        when(exchange.supportsTickerStreaming()).thenReturn(true);
        when(exchange.supportsOrderBook()).thenReturn(true);
        var detected = ExchangeAdapterMigrationBridge.wrap(exchange).getCapability();
        assertEquals(profile.toBuilder().exchangeName("VENUE")
                .exchangeId("venue").displayName("Venue Display").build(), detected);
        assertFalse(detected.isSupportsWebSocket());
        assertFalse(detected.isSupportsFullOrderBook());
        verify(exchange, never()).checkAuthentication();
    }

    @Test
    void explicitOverrideRemainsUnchanged() {
        ExchangeCapability override = ExchangeCapability.builder().exchangeName("Override").build();
        assertSame(override, ExchangeAdapterMigrationBridge.wrap(exchange(), override).getCapability());
    }

    @Test
    void missingProfilesFailInsteadOfAdvertisingGuessedCapabilities() {
        assertThrows(NullPointerException.class, () -> ExchangeAdapterMigrationBridge.wrap(exchange()));
        assertThrows(NullPointerException.class, () -> ExchangeAdapterMigrationBridge.wrap(null));
        assertThrows(NullPointerException.class, () -> ExchangeAdapterMigrationBridge.wrap(exchange(), null));
    }

    private Exchange exchange() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.getName()).thenReturn("VENUE");
        when(exchange.getExchangeId()).thenReturn("venue");
        when(exchange.getDisplayName()).thenReturn("Venue Display");
        return exchange;
    }
}
