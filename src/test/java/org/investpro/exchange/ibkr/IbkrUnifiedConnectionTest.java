package org.investpro.exchange.ibkr;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IbkrUnifiedConnectionTest {
    @Test void openingTheDeskOrRecheckingTheSameProfileDoesNotReconnect() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrExchange exchange = new IbkrExchange(credentials(), session);
        try {
            assertTrue(exchange.AuthCheckResult("ibkr").success());
            var state = exchange.getConnectionService().getSessionState();
            var profile = new IbkrConnectionProfile(state.mode(), state.host(), state.port(), state.clientId(),
                    state.paper(), false, "Desk profile", null);
            assertTrue(exchange.getConnectionService().connect(profile).connectionSuccessful());
            assertEquals(1, session.connections);
            assertEquals("DU123456", exchange.fetchAccount().join().getAccountId());
            session.disconnect();
            assertTrue(exchange.getConnectionService().connect(profile).connectionSuccessful());
            assertEquals(2, session.connections);
        } finally { exchange.getConnectionManager().shutdown(); }
    }
    private static ExchangeCredentials credentials() {
        return new ExchangeCredentials("interactive_brokers", null, null, null, null, null, null,
                false, Map.of("host", "192.0.2.5", "port", "7496", "clientId", "42", "watchlist", ""));
    }

    @Test void authenticationAndTradingShareOneSessionAndDoNotReconnectOnRepeatedChecks() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrExchange exchange = new IbkrExchange(credentials(), session);
        try {
            assertTrue(exchange.AuthCheckResult("ibkr").success());
            assertTrue(exchange.checkAuthentication().isSuccess());
            assertTrue(exchange.isConnected());
            assertEquals(1, session.connections);
            assertEquals("DU123456", exchange.fetchAccount().join().getAccountId());
        } finally { exchange.getConnectionManager().shutdown(); }
    }

    @Test void reconnectPreservesEndpointAndClientId() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrConnectionManager manager = new IbkrConnectionManager(session, "");
        try {
            manager.connect(new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, "192.0.2.5", 7496, 42, false, false, null, null));
            manager.reconnect();
            assertEquals(2, session.connections);
            assertEquals("192.0.2.5", session.profile.host());
            assertEquals(7496, session.profile.port());
            assertEquals(42, session.profile.clientId());
        } finally { manager.shutdown(); }
    }

    @Test void lostApiReadinessOverridesManagerFlag() {
        StubIbkrTwsSession session = new StubIbkrTwsSession();
        IbkrConnectionManager manager = new IbkrConnectionManager(session, "");
        try {
            manager.connect(new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, null, 7496, 1, false, false, null, null));
            session.disconnect();
            assertFalse(manager.isConnected());
            assertFalse(manager.snapshotHealth().connected());
        } finally { manager.shutdown(); }
    }

    @Test void handshakeFailureIsNotReportedAsAuthenticated() {
        IbkrTwsSession session = new StubIbkrTwsSession() {
            @Override public void connect(IbkrConnectionProfile profile, String account) {
                throw new IllegalStateException("IBKR 326: client ID already in use");
            }
        };
        IbkrExchange exchange = new IbkrExchange(credentials(), session);
        try {
            var result = exchange.AuthCheckResult("ibkr");
            assertFalse(result.success());
            assertTrue(result.message().contains("326"));
            assertFalse(exchange.isConnected());
        } finally { exchange.getConnectionManager().shutdown(); }
    }
}
