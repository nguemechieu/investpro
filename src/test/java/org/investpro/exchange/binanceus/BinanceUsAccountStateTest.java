package org.investpro.exchange.binanceus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BinanceUsAccountStateTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    static JsonNode json(String text) throws Exception { return JSON.readTree(text); }
    static JsonNode event(String status, long time, String execution) throws Exception {
        return json("""
            {"e":"executionReport","s":"BTCUSDT","i":7,"X":"%s","x":"%s","S":"BUY",
             "o":"LIMIT","p":"100","q":"2","z":"1","O":1,"T":%d,"f":"GTC",
             "t":12,"l":"1","L":"100","n":"0.1"}
            """.formatted(status, execution, time));
    }
    static JsonNode account() throws Exception { return json("{\"balances\":[{\"asset\":\"USDT\",\"free\":\"100\",\"locked\":\"0\"}],\"updateTime\":1}"); }
    private BinanceUsAccountState initialized() throws Exception {
        var state = new BinanceUsAccountState(); state.subscription(true); state.beginSync();
        state.completeSync(json("[]"), account()); return state;
    }
    @Test void newPartialAndFilledLifecycleIsIdempotent() throws Exception {
        var state = initialized(); assertTrue(state.event(event("NEW", 2, "NEW")));
        assertEquals(1, state.openOrders(null).size()); assertFalse(state.event(event("NEW", 2, "NEW")));
        assertTrue(state.event(event("PARTIALLY_FILLED", 3, "TRADE")));
        assertEquals("PARTIALLY_FILLED", state.openOrders(null).getFirst().path("status").asText());
        assertTrue(state.event(event("FILLED", 4, "TRADE"))); assertTrue(state.openOrders(null).isEmpty());
        assertEquals("FILLED", state.order("BTCUSDT", "7").path("status").asText());
        assertFalse(state.event(event("FILLED", 4, "TRADE"))); assertEquals(1, state.fills().size());
        assertFalse(state.event(event("NEW", 5, "NEW")));
    }
    @Test void terminalEventsRemoveOpenOrders() throws Exception {
        for (String status : java.util.List.of("CANCELED", "REJECTED", "EXPIRED", "EXPIRED_IN_MATCH")) {
            var state = initialized(); state.event(event("NEW", 2, "NEW")); state.event(event(status, 3, status));
            assertTrue(state.openOrders(null).isEmpty()); assertNotNull(state.order("BTCUSDT", "7"));
        }
    }
    @Test void reconnectSnapshotReplaysEventsAndUnchangedOrdersSurvive() throws Exception {
        var state = initialized(); state.event(event("NEW", 2, "NEW")); JsonNode initial = state.openOrders(null).getFirst();
        state.beginSync(); state.completeSync(JSON.createArrayNode().add(initial), account());
        assertEquals(1, state.openOrders(null).size());
        state.subscription(false); state.subscription(true); state.beginSync(); state.event(event("FILLED", 3, "TRADE"));
        state.completeSync(JSON.createArrayNode().add(initial), account()); assertTrue(state.openOrders(null).isEmpty());
        assertEquals(BinanceUsAccountState.Health.LIVE, state.health());
    }
    @Test void failedReconciliationRetainsSnapshotAndReplaysBufferedEvents() throws Exception {
        var state = initialized(); var lastSync = state.lastSuccessfulSync();
        state.event(event("NEW", 2, "NEW")); state.beginSync(); state.failedSync();
        assertEquals(1, state.openOrders(null).size()); assertEquals(lastSync, state.lastSuccessfulSync());
        assertEquals(BinanceUsAccountState.Health.STALE, state.health());
        state.beginSync(); state.event(event("FILLED", 3, "TRADE")); state.failedSync(); assertTrue(state.openOrders(null).isEmpty());
    }
    @Test void balanceUpdatesAreIdempotentAndReturnedDataCannotMutateCache() throws Exception {
        var state = initialized();
        JsonNode update = json("{\"e\":\"outboundAccountPosition\",\"u\":2,\"B\":[{\"a\":\"USDT\",\"f\":\"99\",\"l\":\"1\"}]}");
        assertTrue(state.event(update)); assertFalse(state.event(update));
        assertEquals("99", state.account().path("balances").get(0).path("free").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode) state.account()).removeAll(); assertFalse(state.account().isEmpty());
    }

    @Test void depositsAndExternalLocksDoNotApplyDuplicateDeltas() throws Exception {
        var state = initialized();
        JsonNode deposit = json("{\"e\":\"balanceUpdate\",\"a\":\"USDT\",\"d\":\"10\",\"T\":2,\"E\":2}");
        assertTrue(state.event(deposit)); assertFalse(state.event(deposit));
        assertEquals("110", state.account().path("balances").get(0).path("free").asText());
        JsonNode lock = json("{\"e\":\"externalLockUpdate\",\"a\":\"USDT\",\"d\":\"-5\",\"T\":3,\"E\":3}");
        assertTrue(state.event(lock)); assertFalse(state.event(lock));
        assertEquals("105", state.account().path("balances").get(0).path("free").asText());
        assertEquals("5", state.account().path("balances").get(0).path("locked").asText());
    }

    @Test void restAcknowledgementsDuringReconciliationAreNotOverwritten() throws Exception {
        var state = initialized(); state.event(event("NEW", 2, "NEW"));
        JsonNode snapshot = state.openOrders(null).getFirst(); state.beginSync();
        var cancel = (com.fasterxml.jackson.databind.node.ObjectNode) snapshot.deepCopy();
        cancel.put("status", "CANCELED"); cancel.put("updateTime", 3);
        state.updateRestOrder(cancel);
        state.completeSync(JSON.createArrayNode().add(snapshot), account());
        assertTrue(state.openOrders(null).isEmpty());
    }

    @Test void olderRestSnapshotCannotResurrectTerminalOrders() throws Exception {
        var state = initialized(); state.event(event("NEW", 2, "NEW"));
        JsonNode old = state.openOrders(null).getFirst(); state.event(event("FILLED", 3, "TRADE"));
        state.beginSync(); state.completeSync(JSON.createArrayNode().add(old), account());
        assertTrue(state.openOrders(null).isEmpty());
        assertEquals("FILLED", state.order("BTCUSDT", "7").path("status").asText());
    }
}
