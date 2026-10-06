package org.investpro.exchange;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.Account;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeskExecutionRoutingTest {
    private Exchange exchange() {
        var credentials = new ExchangeCredentials("test", "", "", null, null, null, null, false);
        Exchange exchange = mock(Exchange.class, withSettings().useConstructor(credentials).defaultAnswer(CALLS_REAL_METHODS));
        doReturn(false).when(exchange).isPaperTrading();
        doReturn(true).when(exchange).supportsLiveTrading();
        return exchange;
    }

    @Test
    void publicConnectionUsesLocalPaperWithoutPrivateRequests() {
        Exchange exchange = exchange();
        doReturn(true).when(exchange).isConnected();
        assertTrue(exchange.isDeskPaperTrading());
        assertNotSame(exchange, exchange.deskOrderExecution());
        assertTrue(exchange.deskTradingAccount().join().isPaperTrading());
        verify(exchange, never()).fetchAccount();
    }

    @Test
    void authenticatedDeskUsesLiveEvenWhenBotUsesPaper() {
        Exchange exchange = exchange();
        exchange.setAuthenticatedSessionConnected(true);
        exchange.setBotTradingMode("PAPER");
        assertEquals("LIVE", exchange.getResolvedTradingMode());
        Account account = new Account();
        doReturn(CompletableFuture.completedFuture(account)).when(exchange).fetchAccount();
        assertSame(exchange, exchange.deskOrderExecution());
        assertSame(account, exchange.deskTradingAccount().join());
        assertNotSame(exchange, exchange.botOrderExecution());
    }

    @Test
    void losingAuthenticationRestoresDeskPaperAndBlocksLiveBot() {
        Exchange exchange = exchange();
        exchange.setAuthenticatedSessionConnected(true);
        exchange.setBotTradingMode("LIVE");
        assertSame(exchange, exchange.botOrderExecution());
        doReturn(true).when(exchange).isConnected();
        exchange.setAuthenticatedSessionConnected(false);
        assertNotSame(exchange, exchange.deskOrderExecution());
        assertFalse(exchange.canSubmitBotOrders());
        assertThrows(IllegalStateException.class, exchange::botOrderExecution);
    }
}
