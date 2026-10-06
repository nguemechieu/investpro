package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class IbkrTwsSessionTest {
    public static class Socket {
        public boolean isConnected() { return true; }
        public void eDisconnect() { }
    }
    private static IbkrTwsSession session() throws Exception {
        IbkrTwsSession session = new IbkrTwsSession();
        field(session, "profile", IbkrConnectionProfile.twsPaper());
        field(session, "client", new Socket());
        return session;
    }
    private static void field(Object object, String name, Object value) throws Exception {
        Field field = IbkrTwsSession.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
    private static void callback(IbkrTwsSession session, String name, Object... args) throws Exception {
        Method callback = IbkrTwsSession.class.getDeclaredMethod("callback", String.class, Object[].class);
        callback.setAccessible(true);
        callback.invoke(session, name, args);
    }
    @Test void openSocketWithoutHandshakeIsNotReady() throws Exception {
        assertFalse(session().state().connectionSuccessful());
    }
    @Test void bothManagedAccountsAndNextValidOrderIdAreRequired() throws Exception {
        IbkrTwsSession session = session();
        callback(session, "managedAccounts", "DU123456");
        assertFalse(session.state().apiReady());
        callback(session, "nextValidId", 100);
        assertTrue(session.state().connectionSuccessful());
    }
    @Test void connectivityLossAndRestorationFollowBrokerCallbacks() throws Exception {
        IbkrTwsSession session = session();
        callback(session, "nextValidId", 100);
        callback(session, "managedAccounts", "DU123456");
        callback(session, "error", -1, 0L, 1100, "Connectivity lost", "");
        assertFalse(session.state().connectionSuccessful());
        callback(session, "error", -1, 0L, 1102, "Connectivity restored", "");
        assertTrue(session.state().connectionSuccessful());
    }
    @Test void duplicateClientIdFailsReadinessAndPreservesReason() throws Exception {
        IbkrTwsSession session = session();
        callback(session, "error", -1, 0L, 326, "Client ID already in use", "");
        assertFalse(session.state().connectionSuccessful());
        assertTrue(session.state().message().contains("326"));
    }

    @Test void submissionCompletesOnlyWhenBrokerAcknowledgesIt() throws Exception {
        IbkrTwsSession session = session();
        var result = new java.util.concurrent.CompletableFuture<String>();
        var pending = new java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CompletableFuture<String>>();
        pending.put(100, result);
        field(session, "pendingOrders", pending);
        assertFalse(result.isDone());
        callback(session, "orderStatus", 100, "Submitted");
        assertEquals("100", result.join());
    }

    @Test void brokerRejectionDoesNotBecomeLocalExecutionSuccess() throws Exception {
        IbkrTwsSession session = session();
        var result = new java.util.concurrent.CompletableFuture<String>();
        var pending = new java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CompletableFuture<String>>();
        pending.put(100, result);
        field(session, "pendingOrders", pending);
        callback(session, "error", 100, 0L, 201, "Order rejected", "");
        assertTrue(result.isCompletedExceptionally());
    }
}
