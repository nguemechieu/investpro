package org.investpro.ui;

import org.investpro.exchange.Exchange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TradingDeskSessionTest {
    @Test
    void emptyVisibleMarketDoesNotSelectARememberedHiddenSymbol() {
        assertNull(TradingDesk.selectMarketWatchSymbol(java.util.List.of(), "BTC/USD"));
        assertNull(TradingDesk.selectMarketWatchSymbol(java.util.List.of(), ""));
    }

    @Test
    void visibleMarketRestoresRememberedSymbolOrUsesFirstVisibleSymbol() throws Exception {
        var btc = org.investpro.models.trading.TradePair.fromSymbol("BTC/USD");
        var eth = org.investpro.models.trading.TradePair.fromSymbol("ETH/USD");
        var visible = java.util.List.of(btc, eth);
        assertSame(eth, TradingDesk.selectMarketWatchSymbol(visible, "ETH/USD"));
        assertSame(btc, TradingDesk.selectMarketWatchSymbol(visible, "SOL/USD"));
    }
    @Test
    void ibkrFactoryPreservesSelectedGatewayRatherThanForcingLivePort() throws Exception {
        var method = TradingDesk.class.getDeclaredMethod("credentialValueForPluginFactory", String.class,
                org.investpro.exchange.credentials.ExchangeCredentials.class);
        method.setAccessible(true);
        var credentials = new org.investpro.exchange.credentials.ExchangeCredentials("interactive_brokers",
                null, null, null, null, null, null, true, java.util.Map.of(
                "IBKR_ENVIRONMENT", "paper", "IBKR_SANDBOX", "true", "IBKR_PORT", "7497",
                "IBKR_PAPER_PORT", "4002", "IBKR_LIVE_PORT", "4001"));
        assertEquals(java.util.Optional.of("7497"), method.invoke(null, "IBKR_PORT", credentials));
        assertEquals(java.util.Optional.of("paper"), method.invoke(null, "IBKR_ENVIRONMENT", credentials));
        assertEquals(java.util.Optional.of("true"), method.invoke(null, "IBKR_SANDBOX", credentials));
    }

    @Test
    void authenticatedAccountDoesNotPromptWithoutAWebSocket() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.isAuthenticatedSessionConnected()).thenReturn(true);
        assertFalse(TradingDesk.shouldPromptForCredentials(exchange, false));
    }

    @Test
    void pendingValidationSuppressesPromptButPublicStreamDoesNotAuthenticate() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.isConnected()).thenReturn(true);
        assertFalse(TradingDesk.shouldPromptForCredentials(exchange, true));
        assertTrue(TradingDesk.shouldPromptForCredentials(exchange, false));
        assertTrue(TradingDesk.shouldPromptForCredentials(null, false));
    }
    @Test
    void connectionParametersPreserveExplicitModeBeforeAdapterConstruction() {
        var params = TradingDesk.connectionParamsForMode(java.util.Map.of("IBKR_HOST", "localhost"), "PAPER");
        assertEquals("localhost", params.get("IBKR_HOST"));
        assertEquals("PAPER", params.get("tradingMode"));
        assertEquals("LIVE", TradingDesk.connectionParamsForMode(params, "LIVE").get("tradingMode"));
        assertEquals("PAPER", params.get("tradingMode"));
    }
    @Test
    void authenticatedSessionIsReusableWithoutAWebSocket() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.isAuthenticatedSessionConnected()).thenReturn(true);
        assertTrue(TradingDesk.canReuseBrokerSession(exchange, true));
        verify(exchange, never()).disconnect();
        verify(exchange, never()).disconnectStream();
    }

    @Test
    void localPaperSessionIsReusableWithoutABrokerConnection() {
        Exchange exchange = mock(Exchange.class);
        when(exchange.isDeskPaperTrading()).thenReturn(true);
        assertTrue(TradingDesk.canReuseBrokerSession(exchange, true));
    }

    @Test
    void disconnectedOrUnvalidatedSessionCannotBeRestored() {
        Exchange exchange = mock(Exchange.class);
        assertFalse(TradingDesk.canReuseBrokerSession(exchange, true));
        when(exchange.isAuthenticatedSessionConnected()).thenReturn(true);
        assertTrue(TradingDesk.canReuseBrokerSession(exchange, false));
        assertFalse(TradingDesk.canReuseBrokerSession(null, true));
    }
}
