package org.investpro.exchange.ibkr;

import org.investpro.exchange.credentials.ExchangeCredentials;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IbkrGatewayCredentialsTest {
    @Test
    void socketEndpointDoesNotRequireApiKeyPasswordOrAccountId() throws Exception {
        try (ServerSocket endpoint = new ServerSocket(0)) {
            IbkrExchange exchange = exchange(endpoint.getLocalPort(), false);
            try {
                var result = exchange.AuthCheckResult("interactive_brokers");
                assertTrue(result.success(), result.message());
                assertTrue(result.message().contains("handshake"));
                assertTrue(exchange.checkAuthentication().isSuccess());
                assertTrue(exchange.isConnected());
            } finally {
                exchange.getConnectionManager().shutdown();
            }
        }
    }

    @Test
    void closedEndpointFailsEvenWhenConnectionSettingsExist() throws Exception {
        int port;
        try (ServerSocket endpoint = new ServerSocket(0)) {
            port = endpoint.getLocalPort();
        }
        IbkrExchange exchange = new IbkrExchange(new ExchangeCredentials("interactive_brokers", null, null, null, null, null, null, false), new StubIbkrTwsSession() {
            @Override public void connect(IbkrConnectionProfile profile, String account) {
                throw new IllegalStateException("IBKR endpoint unavailable");
            }
        });
        try {
            assertFalse(exchange.AuthCheckResult("interactive_brokers").success());
            assertFalse(exchange.checkAuthentication().isSuccess());
        } finally {
            exchange.getConnectionManager().shutdown();
        }
    }

    @Test
    void liveGatewayProfileIsIndependentOfBotPaperMode() {
        IbkrExchange exchange = exchange(4001, false);
        try {
            exchange.setBotTradingMode("PAPER");
            exchange.connect();
            assertEquals(IbkrConnectionManager.Mode.LIVE, exchange.getConnectionManager().getMode());
            assertEquals(4001, exchange.getConnectionManager().getPort());
            assertTrue(exchange.ibkrSessionState().socketConnected());
            assertEquals(4001, exchange.ibkrSessionState().port());
            exchange.disconnect();
            assertFalse(exchange.isConnected());
            assertFalse(exchange.ibkrSessionState().socketConnected());
            exchange.reconnect();
            assertTrue(exchange.isConnected());
            assertTrue(exchange.ibkrSessionState().socketConnected());
            assertEquals(4001, exchange.ibkrSessionState().port());
            assertEquals(7, exchange.ibkrSessionState().clientId());
            exchange.getConnectionManager().reconnect();
            assertEquals(4001, exchange.getConnectionManager().getPort());
            assertEquals(7, exchange.getConnectionManager().getClientId());
        } finally {
            exchange.getConnectionManager().shutdown();
        }
    }

    @Test
    void selectAccountDefaultsToFirstManagedAccountWhenSelectionIsBlank() throws Exception {
        IbkrTwsSession session = new IbkrTwsSession();
        setField(session, "accounts", List.of("DU1234567", "DU7654321"));
        setField(session, "selectedAccount", "");

        assertEquals("DU1234567", invokeSelectAccount(session));
    }

    @Test
    void selectAccountUsesExplicitMatchingManagedAccount() throws Exception {
        IbkrTwsSession session = new IbkrTwsSession();
        setField(session, "accounts", List.of("DU1234567", "DU7654321"));
        setField(session, "selectedAccount", "du7654321");

        assertEquals("DU7654321", invokeSelectAccount(session));
    }

    private static Object invokeSelectAccount(IbkrTwsSession session) throws Exception {
        Method method = IbkrTwsSession.class.getDeclaredMethod("selectAccount");
        method.setAccessible(true);
        return method.invoke(session);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static IbkrExchange exchange(int port, boolean paper) {
        return new IbkrExchange(new ExchangeCredentials("interactive_brokers", null, null, null, null,
                null, null, paper, Map.of("authMode", "gateway", "host", "127.0.0.1",
                "port", String.valueOf(port), "clientId", "7", "watchlist", "")), new StubIbkrTwsSession());
    }
}
