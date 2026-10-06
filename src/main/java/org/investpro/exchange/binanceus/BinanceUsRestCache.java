package org.investpro.exchange.binanceus;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ConcurrentHashMap;

/** Coalesces identical public snapshot reads; signed requests and historical queries bypass it. */
final class BinanceUsRestCache {
    private final java.util.function.LongSupplier now;
    BinanceUsRestCache() { this(System::currentTimeMillis); }
    BinanceUsRestCache(java.util.function.LongSupplier now) { this.now = now; }
    @FunctionalInterface interface Sender {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }
    private record Snapshot(HttpResponse<String> response, long expiresAt) { }
    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();
    private final java.util.Set<String> stale = ConcurrentHashMap.newKeySet();

    HttpResponse<String> read(HttpRequest request, Sender sender) throws IOException, InterruptedException {
        long ttl = request.method().equals("GET") ? switch (request.uri().getPath()) {
            case "/api/v3/exchangeInfo" -> 600000;
            case "/api/v3/depth", "/api/v3/trades" -> 2000;
            case "/api/v3/ticker/price", "/api/v3/ticker/bookTicker", "/api/v3/ticker/24hr" -> 1000;
            default -> 0;
        } : 0;
        if (ttl == 0) return sender.send(request);
        String key = request.uri().toString();
        synchronized (locks.computeIfAbsent(key, _ -> new Object())) {
            Snapshot cached = snapshots.get(key);
            if (cached != null && now.getAsLong() < cached.expiresAt()) return cached.response();
            HttpResponse<String> response;
            try { response = sender.send(request); }
            catch (IOException failure) {
                // Instrument metadata remains usable during an outage; never serve stale trading quotes here.
                if (cached == null || !request.uri().getPath().equals("/api/v3/exchangeInfo")) throw failure;
                if (stale.add(key)) org.slf4j.LoggerFactory.getLogger(BinanceUsRestCache.class)
                        .warn("Binance US exchangeInfo refresh failed; metadata snapshot is STALE: {}", failure.getMessage());
                return cached.response();
            }
            if (response.statusCode() == 200) snapshots.put(key, new Snapshot(response, now.getAsLong() + ttl));
            if (response.statusCode() == 200) stale.remove(key);
            return response;
        }
    }
    int staleCount() { return stale.size(); }
}
