package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExchangeExecutionRoutingTest {
    @Test
    void explicitPaperModeAppliesBeforeAdapterInitialization() {
        ExchangeCredentials credentials = new ExchangeCredentials("test", "", "", null, null, null, null,
                false, java.util.Map.of("tradingMode", "PAPER"));
        Exchange exchange = spy(new org.investpro.exchange.coinbase.Coinbase(credentials));
        assertEquals("PAPER", exchange.getResolvedTradingMode());
        assertNotSame(exchange, exchange.orderExecution());
        assertTrue(exchange.tradingAccount().join().isPaperTrading());
        verify(exchange, never()).fetchAccount();
    }
    private Exchange exchange() {
        ExchangeCredentials credentials = new ExchangeCredentials("test", "", "", null, null, null, null, false);
        Exchange exchange = mock(Exchange.class, withSettings().useConstructor(credentials).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(false).when(exchange).isPaperTrading();
        doReturn(false).when(exchange).isConnected();
        doReturn(true).when(exchange).supportsLiveTrading();
        return exchange;
    }

    @Test
    void authenticatedAccountCanBeConnectedWithoutWebsocket() {
        Exchange exchange = exchange();
        assertFalse(exchange.canSubmitLiveOrders());
        exchange.setAuthenticatedSessionConnected(true);
        assertTrue(exchange.canSubmitLiveOrders());
        exchange.setAuthenticatedSessionConnected(false);
        assertFalse(exchange.canSubmitLiveOrders());
    }

    @Test
    void paperRoutesAwayFromBrokerEvenWhenAuthenticated() {
        Exchange exchange = exchange();
        assertSame(exchange, exchange.orderExecution());
        exchange.setAuthenticatedSessionConnected(true);
        doReturn(true).when(exchange).isPaperTrading();
        assertNotSame(exchange, exchange.orderExecution());
        assertFalse(exchange.canSubmitLiveOrders());
        assertTrue(exchange.tradingAccount().join().isPaperTrading());
        verify(exchange, never()).fetchAccount();
        doReturn(false).when(exchange).isPaperTrading();
        assertSame(exchange, exchange.orderExecution());
    }
}
