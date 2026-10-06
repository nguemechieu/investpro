package org.investpro.exchange.binanceus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.*;

/** Guarded account snapshot. Events received during reconciliation are replayed over REST. */
final class BinanceUsAccountState {
    enum Health { LIVE, STALE, SYNCING, DISCONNECTED }
    private final Map<String, JsonNode> orders = new LinkedHashMap<>();
    private final Map<String, JsonNode> open = new LinkedHashMap<>();
    private final Map<String, JsonNode> fills = new LinkedHashMap<>();
    private final List<JsonNode> duringSync = new ArrayList<>();
    private JsonNode account;
    private Health health = Health.DISCONNECTED;
    private Instant lastSuccessfulSync;
    private Instant lastEvent;
    private boolean syncing, subscribed;
    private long accountUpdateTime;
    private final Map<String, Long> balanceVersions = new HashMap<>();
    private final Map<String, Long> absoluteBalanceVersions = new HashMap<>();
    private final Set<String> balanceDeltas = new HashSet<>();

    synchronized void beginSync() { syncing = true; duringSync.clear(); health = Health.SYNCING; }
    synchronized void completeSync(JsonNode snapshot, JsonNode balances) {
        Set<String> authoritativeIds = new HashSet<>();
        open.clear();
        for (JsonNode order : snapshot) {
            String key = order.path("symbol").asText() + ":" + order.path("orderId").asText();
            authoritativeIds.add(key);
            JsonNode known = orders.get(key);
            if (known != null && (terminal(known.path("status").asText()) ||
                    known.path("updateTime").asLong() > order.path("updateTime").asLong() ||
                    known.path("executedQty").asDouble() > order.path("executedQty").asDouble())) order = known;
            orders.put(key, order.deepCopy());
            if (!terminal(order.path("status").asText())) open.put(key, order.deepCopy());
        }
        // An absent order is no longer open, but REST has not told us its terminal reason.
        for (var entry : orders.entrySet()) if (!authoritativeIds.contains(entry.getKey()) && !terminal(entry.getValue().path("status").asText())) {
            ObjectNode missing = entry.getValue().deepCopy(); missing.put("status", "UNKNOWN"); entry.setValue(missing);
        }
        account = balances.deepCopy();
        accountUpdateTime = balances.path("updateTime").asLong();
        balanceVersions.clear();
        absoluteBalanceVersions.clear();
        for (JsonNode balance : account.path("balances")) balanceVersions.put(balance.path("asset").asText(), accountUpdateTime);
        absoluteBalanceVersions.putAll(balanceVersions);
        syncing = false;
        for (JsonNode event : duringSync) apply(event);
        duringSync.clear();
        lastSuccessfulSync = Instant.now();
        health = subscribed ? Health.LIVE : Health.STALE;
    }
    synchronized void failedSync() {
        syncing = false;
        for (JsonNode event : duringSync) apply(event);
        duringSync.clear();
        health = Health.STALE;
    }
    synchronized void subscription(boolean active) {
        subscribed = active;
        health = syncing ? Health.SYNCING : active ? Health.STALE : Health.DISCONNECTED;
    }
    synchronized boolean event(JsonNode event) {
        if (!event.path("e").asText().equals("historicalFill")) lastEvent = Instant.now();
        if (syncing) { duringSync.add(event.deepCopy()); return false; }
        return apply(event);
    }
    private boolean apply(JsonNode event) {
        switch (event.path("e").asText()) {
            case "restOrder" -> { return updateOrder(event.path("order")); }
            case "historicalFill" -> {
                String key = event.path("s").asText() + ":" + event.path("i").asText() + ":" + event.path("t").asText();
                return fills.putIfAbsent(key, event.deepCopy()) == null;
            }
            case "executionReport" -> {
                ObjectNode order = event.deepCopy();
                order.set("orderId", event.path("i")); order.set("symbol", event.path("s"));
                order.set("status", event.path("X")); order.set("side", event.path("S"));
                order.set("type", event.path("o")); order.set("price", event.path("p"));
                order.set("origQty", event.path("q")); order.set("executedQty", event.path("z"));
                order.set("time", event.path("O")); order.set("updateTime", event.path("T"));
                order.set("timeInForce", event.path("f")); order.set("clientOrderId", event.path("c"));
                boolean changed = updateOrder(order);
                if (event.path("x").asText().equals("TRADE")) {
                    String key = event.path("s").asText() + ":" + event.path("i").asText() + ":" + event.path("t").asText();
                    changed |= fills.putIfAbsent(key, event.deepCopy()) == null;
                }
                return changed;
            }
            case "outboundAccountPosition" -> {
                if (account == null) return false;
                long updateTime = event.path("u").asLong(event.path("E").asLong());
                ObjectNode next = account.deepCopy();
                Map<String, JsonNode> balances = new LinkedHashMap<>();
                for (JsonNode balance : account.path("balances")) balances.put(balance.path("asset").asText(), balance);
                for (JsonNode balance : event.path("B")) {
                    String asset = balance.path("a").asText();
                    if (updateTime < balanceVersions.getOrDefault(asset, accountUpdateTime)) continue;
                    balanceVersions.put(asset, updateTime);
                    absoluteBalanceVersions.put(asset, updateTime);
                    ObjectNode mapped = balance.deepCopy(); mapped.set("asset", balance.path("a"));
                    mapped.set("free", balance.path("f")); mapped.set("locked", balance.path("l"));
                    balances.put(balance.path("a").asText(), mapped);
                }
                var array = next.putArray("balances"); balances.values().forEach(array::add);
                if (account.equals(next)) return false;
                account = next;
                return true;
            }
            case "balanceUpdate", "externalLockUpdate" -> {
                if (account == null) return false;
                String asset = event.path("a").asText();
                long time = event.path("T").asLong(event.path("E").asLong());
                String key = event.path("e").asText() + ":" + asset + ":" + time + ":" + event.path("E").asLong() + ":" + event.path("d").asText();
                if (!balanceDeltas.add(key) || time <= absoluteBalanceVersions.getOrDefault(asset, accountUpdateTime)) return false;
                ObjectNode next = account.deepCopy();
                var balances = next.putArray("balances");
                boolean found = false;
                for (JsonNode balance : account.path("balances")) {
                    if (!asset.equals(balance.path("asset").asText())) { balances.add(balance); continue; }
                    found = true;
                    ObjectNode updated = balance.deepCopy();
                    java.math.BigDecimal delta = new java.math.BigDecimal(event.path("d").asText("0"));
                    updated.put("free", new java.math.BigDecimal(balance.path("free").asText("0")).add(delta).toPlainString());
                    if (event.path("e").asText().equals("externalLockUpdate"))
                        updated.put("locked", new java.math.BigDecimal(balance.path("locked").asText("0")).subtract(delta).toPlainString());
                    balances.add(updated);
                }
                if (!found) {
                    ObjectNode added = balances.addObject(); added.put("asset", asset); added.put("free", event.path("d").asText("0")); added.put("locked", "0");
                }
                balanceVersions.merge(asset, time, Math::max); account = next;
                return true;
            }
            default -> { return false; }
        }
    }
    synchronized void updateRestOrder(JsonNode order) {
        if (syncing) {
            ObjectNode event = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            event.put("e", "restOrder"); event.set("order", order.deepCopy()); duringSync.add(event);
        } else updateOrder(order);
    }
    synchronized boolean updateOrder(JsonNode order) {
        if (!order.hasNonNull("orderId") || !order.hasNonNull("status")) return false;
        ObjectNode normalized = order.deepCopy();
        if (!normalized.has("time")) normalized.set("time", order.path("transactTime"));
        if (!normalized.has("updateTime")) normalized.set("updateTime", order.path("transactTime"));
        order = normalized;
        String key = order.path("symbol").asText() + ":" + order.path("orderId").asText();
        JsonNode previous = orders.get(key);
        long timestamp = order.path("updateTime").asLong(order.path("time").asLong());
        if (previous != null) {
            long previousTime = previous.path("updateTime").asLong(previous.path("time").asLong());
            if (order.path("executedQty").asDouble() < previous.path("executedQty").asDouble()) return false;
            if (timestamp < previousTime || (timestamp == previousTime &&
                    previous.path("status").equals(order.path("status")) &&
                    previous.path("executedQty").equals(order.path("executedQty")))) return false;
            if (terminal(previous.path("status").asText()) && !terminal(order.path("status").asText())) return false;
        }
        orders.put(key, order.deepCopy());
        if (terminal(order.path("status").asText())) open.remove(key); else open.put(key, order.deepCopy());
        return true;
    }
    private static boolean terminal(String status) {
        return Set.of("FILLED", "CANCELED", "CANCELLED", "REJECTED", "EXPIRED", "EXPIRED_IN_MATCH").contains(status);
    }
    synchronized List<JsonNode> openOrders(String symbol) {
        return open.values().stream().filter(order -> symbol == null || symbol.equals(order.path("symbol").asText()))
                .<JsonNode>map(JsonNode::deepCopy).toList();
    }
    synchronized JsonNode account() { return account == null ? null : account.deepCopy(); }
    synchronized List<JsonNode> fills() { return fills.values().stream().<JsonNode>map(JsonNode::deepCopy).toList(); }
    synchronized JsonNode order(String symbol, String id) { JsonNode order = orders.get(symbol + ":" + id); return order == null ? null : order.deepCopy(); }
    synchronized JsonNode orderById(String id) {
        return orders.values().stream().filter(order -> id.equals(order.path("orderId").asText()))
                .findFirst().<JsonNode>map(JsonNode::deepCopy).orElse(null);
    }
    synchronized Health health() { return health; }
    synchronized Instant lastSuccessfulSync() { return lastSuccessfulSync; }
    synchronized Instant lastEvent() { return lastEvent; }
    synchronized Set<String> trackedSymbols() {
        return orders.values().stream().map(order -> order.path("symbol").asText()).collect(java.util.stream.Collectors.toSet());
    }
}
