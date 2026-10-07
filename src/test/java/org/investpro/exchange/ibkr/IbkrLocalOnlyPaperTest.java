package org.investpro.exchange.ibkr;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IbkrLocalOnlyPaperTest {
    @Test
    void nativeApiRejectsPaperAccountOrdersAndCancellationEvenWithLiveProfile() throws Exception {
        IbkrTwsSession session = new IbkrTwsSession();
        var accounts = IbkrTwsSession.class.getDeclaredField("accounts");
        accounts.setAccessible(true);
        accounts.set(session, java.util.List.of("DU123456"));
        var profile = IbkrTwsSession.class.getDeclaredField("profile");
        profile.setAccessible(true);
        profile.set(session, new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, null, 7496,
                1, false, false, null, null));
        var submission = assertThrows(java.util.concurrent.CompletionException.class,
                () -> session.submit(null, Side.BUY, 1, "MKT", 0, 0).join());
        assertTrue(submission.getCause().getMessage().contains("paper orders are disabled"));
        var cancellation = assertThrows(java.util.concurrent.CompletionException.class,
                () -> session.cancel("123").join());
        assertTrue(cancellation.getCause().getMessage().contains("paper cancellation is disabled"));
    }

    @Test
    void botPaperPlanUsesLocalExecutionEvenWithLiveExchangeSelected() throws Exception {
        IbkrExchange exchange = new IbkrExchange(new ExchangeCredentials("interactive_brokers", null,
                null, null, null, null, "U123456", false, Map.of("watchlist", "")), new StubIbkrTwsSession());
        try {
            exchange.setBotTradingMode("PAPER");
            var plan = org.investpro.strategy.execution.ExecutionPlan.builder()
                    .symbol("EUR/USD").side("BUY").orderType("LIMIT").units(1).entryPrice(1.1)
                    .riskApproved(true).build();
            String id = exchange.getExecutionAdapter().execute(plan,
                    mock(org.investpro.strategy.execution.ExecutionRouter.class),
                    mock(org.investpro.risk.RiskEngine.class)).join();
            assertTrue(id.startsWith("paper-"));
            assertEquals(1, exchange.botOrderExecution().fetchAllOpenOrders().join().size());
            assertFalse(exchange.isConnected());
        } finally { exchange.getConnectionManager().shutdown(); }
    }

    @Test
    void legacyPaperSettingsExecuteLocallyWithoutGatewayHandshake() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrExchange exchange = new IbkrExchange(new ExchangeCredentials("interactive_brokers", null,
                null, null, null, null, "DU123456", true,
                Map.of("port", "4002", "watchlist", "")), session);
        try {
            exchange.connect();
            assertTrue(exchange.AuthCheckResult("ibkr").success());
            TradePair pair = mock(TradePair.class);
            when(pair.toString('/')).thenReturn("AAPL/USD");
            String id = exchange.createLimitOrder(pair, Side.BUY, 1, 100).join();
            assertTrue(id.startsWith("paper-"));
            assertEquals(1, exchange.fetchAllOpenOrders().join().size());
            exchange.cancelOrder(id).join();
            assertTrue(exchange.fetchAllOpenOrders().join().isEmpty());
            assertTrue(exchange.fetchAccount().join().isPaperTrading());
            assertEquals(0, session.connections);
            assertFalse(exchange.isConnected());
        } finally { exchange.getConnectionManager().shutdown(); }
    }

    @Test
    void remotePaperProfileIsRejectedBeforeOpeningSocket() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrConnectionManager manager = new IbkrConnectionManager(session, "");
        try {
            assertThrows(IllegalArgumentException.class, () -> manager.connect(IbkrConnectionProfile.twsPaper()));
            assertEquals(0, session.connections);
        } finally { manager.shutdown(); }
    }
}
