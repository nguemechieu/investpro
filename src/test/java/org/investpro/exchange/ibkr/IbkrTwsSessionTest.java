package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.investpro.models.trading.Ticker;

class IbkrTwsSessionTest {
    public static class Socket {
        final java.util.List<String> requests = new java.util.ArrayList<>();
        int quoteId;
        public boolean isConnected() { return true; }
        public void eDisconnect() { }
        public void reqMarketDataType(int type) { requests.add("type:" + type); }
        public void reqMktData(int id, Object contract, String ticks, boolean snapshot, boolean regulatory, java.util.List<?> options) {
            quoteId = id;
            requests.add("quote");
        }
    }
    public static class Contract {
        public void conid(int id) { }
        public void symbol(String symbol) { }
        public void secType(String type) { }
        public void currency(String currency) { }
        public void exchange(String exchange) { }
        public void primaryExch(String exchange) { }
    }

    @Test void delayedSubscriptionSurvivesCallerTimeoutAndPermissionNotice() throws Exception {
        IbkrTwsSession session = session();
        Socket socket = new Socket();
        field(session, "client", socket);
        callback(session, "nextValidId", 100);
        callback(session, "managedAccounts", "U123456");
        var contract = mock(IbkrResolvedContract.class);
        when(contract.conId()).thenReturn(265598L);
        when(contract.exchange()).thenReturn("SMART");
        try (var sdk = mockStatic(IbkrApiRuntime.class)) {
            sdk.when(() -> IbkrApiRuntime.type("com.ib.client.Contract")).thenReturn(Contract.class);
            var first = session.ticker(contract);
            assertEquals(java.util.List.of("type:3", "quote"), socket.requests);
            first.completeExceptionally(new java.util.concurrent.TimeoutException());
            var second = session.ticker(contract);
            callback(session, "error", socket.quoteId, 0L, 10089, "Additional subscription required. Delayed market data is available.", "");
            assertFalse(second.isDone());
            callback(session, "tickPrice", socket.quoteId, 66, 200.0, null);
            callback(session, "tickPrice", socket.quoteId, 67, 201.0, null);
            assertEquals(Ticker.QuoteType.DELAYED, second.join().getQuoteType());
            assertEquals(200, second.join().getBidPrice());
            assertTrue(session.state().connectionSuccessful());

            // Live entitlement callbacks must not relabel cached delayed prices as live.
            callback(session, "marketDataType", socket.quoteId, 1);
            var live = session.ticker(contract);
            assertFalse(live.isDone());
            callback(session, "tickPrice", socket.quoteId, 1, 202.0, null);
            callback(session, "tickPrice", socket.quoteId, 2, 203.0, null);
            assertEquals(Ticker.QuoteType.LIVE, live.join().getQuoteType());
            assertEquals(202, live.join().getBidPrice());
            assertEquals(2, socket.requests.size());
        }
    }

    @Test void unavailableDelayedDataStillFailsTheQuoteRequest() throws Exception {
        IbkrTwsSession session = session();
        var waiter = new java.util.concurrent.CompletableFuture<Ticker>();
        var waiters = new java.util.concurrent.ConcurrentHashMap<Integer, java.util.concurrent.CompletableFuture<Ticker>>();
        waiters.put(42, waiter);
        field(session, "quoteWaiters", waiters);
        callback(session, "error", 42, 10089, "Additional subscription required; no delayed data.", "");
        assertTrue(waiter.isCompletedExceptionally());
    }

    @Test void historicalSubscriptionFailureProvidesGuidanceWithoutDisconnecting() throws Exception {
        IbkrTwsSession session = session();
        callback(session, "nextValidId", 100);
        callback(session, "managedAccounts", "U123456");
        var waiter = new java.util.concurrent.CompletableFuture<java.util.List<org.investpro.data.CandleData>>();
        var requests = new java.util.concurrent.ConcurrentHashMap<Integer,
                java.util.concurrent.CompletableFuture<java.util.List<org.investpro.data.CandleData>>>();
        requests.put(42, waiter);
        field(session, "histories", requests);
        callback(session, "error", 42, 162, "No market data permissions for NASDAQ STK");
        var error = assertThrows(java.util.concurrent.CompletionException.class, waiter::join);
        assertInstanceOf(IbkrMarketDataException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("Subscription Manager"));
        assertTrue(session.state().connectionSuccessful());
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
