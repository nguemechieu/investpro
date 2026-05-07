package org.investpro.execution;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SymbolTradeLockManager {

    public static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final ConcurrentHashMap<String, LockEntry> locks = new ConcurrentHashMap<>();
    private final Duration ttl;

    public SymbolTradeLockManager() {
        this(DEFAULT_TTL);
    }

    public SymbolTradeLockManager(Duration ttl) {
        this.ttl = ttl == null || ttl.isNegative() || ttl.isZero() ? DEFAULT_TTL : ttl;
    }

    public boolean tryLock(String exchangeName, String symbol, String reason) {
        String key = key(exchangeName, symbol);
        long now = System.currentTimeMillis();
        long expiresAt = now + ttl.toMillis();
        LockEntry candidate = new LockEntry(safeReason(reason), expiresAt);

        LockEntry result = locks.compute(key, (ignored, existing) -> {
            if (existing == null || existing.isExpired(now)) {
                return candidate;
            }
            return existing;
        });

        return result == candidate;
    }

    public void unlock(String exchangeName, String symbol) {
        locks.remove(key(exchangeName, symbol));
    }

    public boolean isLocked(String exchangeName, String symbol) {
        String key = key(exchangeName, symbol);
        long now = System.currentTimeMillis();
        LockEntry entry = locks.get(key);
        if (entry == null) {
            return false;
        }
        if (entry.isExpired(now)) {
            locks.remove(key, entry);
            return false;
        }
        return true;
    }

    public String getLockReason(String exchangeName, String symbol) {
        String key = key(exchangeName, symbol);
        long now = System.currentTimeMillis();
        LockEntry entry = locks.get(key);
        if (entry == null) {
            return "";
        }
        if (entry.isExpired(now)) {
            locks.remove(key, entry);
            return "";
        }
        return entry.reason();
    }

    private static String key(String exchangeName, String symbol) {
        return normalize(exchangeName) + "::" + normalize(symbol);
    }

    private static String normalize(String value) {
        return Objects.toString(value, "")
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static String safeReason(String reason) {
        return reason == null || reason.isBlank() ? "Order transition in progress" : reason.trim();
    }

    private record LockEntry(String reason, long expiresAtMillis) {
        boolean isExpired(long nowMillis) {
            return nowMillis >= expiresAtMillis;
        }
    }
}
