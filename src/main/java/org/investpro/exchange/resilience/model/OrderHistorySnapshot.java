package org.investpro.exchange.resilience.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Stale-safe snapshot of order history for a given account.
 *
 * <p>When the order-history endpoint returns HTTP 504 or trips its circuit breaker,
 * the runtime serves this snapshot instead of propagating the failure. Consumers
 * must check {@link #stale} before relying on order data for decision-making.
 *
 * @param orders    The cached list of orders (never {@code null}, may be empty).
 * @param fetchedAt Timestamp when this snapshot was successfully fetched from the exchange.
 * @param stale     {@code true} when the underlying endpoint has failed and this data
 *                  is being served from cache.
 * @param staleSince When the snapshot became stale ({@code null} if fresh).
 */
public record OrderHistorySnapshot(
        @NotNull List<Object> orders,
        @NotNull Instant fetchedAt,
        boolean stale,
        @Nullable Instant staleSince
) {

    public OrderHistorySnapshot {
        Objects.requireNonNull(orders, "orders must not be null");
        Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");
    }

    /** Creates a fresh snapshot from a successful fetch. */
    public static OrderHistorySnapshot fresh(@NotNull List<Object> orders) {
        return new OrderHistorySnapshot(
                List.copyOf(orders),
                Instant.now(),
                false,
                null
        );
    }

    /** Returns this snapshot marked as stale with the current time as staleSince. */
    public OrderHistorySnapshot markStale() {
        if (stale) return this;
        return new OrderHistorySnapshot(orders, fetchedAt, true, Instant.now());
    }

    /** Returns how long this snapshot has been stale, or {@link Duration#ZERO} if fresh. */
    public Duration staleAge() {
        if (staleSince == null) return Duration.ZERO;
        return Duration.between(staleSince, Instant.now());
    }

    /** Returns how old this snapshot is regardless of freshness. */
    public Duration age() {
        return Duration.between(fetchedAt, Instant.now());
    }

    /** Returns {@code true} if the snapshot is fresh enough (within the given max age). */
    public boolean isFreshEnough(Duration maxAge) {
        return !stale && age().compareTo(maxAge) <= 0;
    }
}
