package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Isolates exchange worker threads so that failures in one functional area
 * cannot block or starve other areas.
 *
 * <p>Worker pools:
 * <ul>
 *   <li><b>pricing</b>: market data fetches (critical)</li>
 *   <li><b>execution</b>: order submission and management (critical, highest priority)</li>
 *   <li><b>account</b>: balance and account queries (critical)</li>
 *   <li><b>positions</b>: position queries (critical)</li>
 *   <li><b>history</b>: order history, fills, transactions (non-critical)</li>
 *   <li><b>analytics</b>: statistics and reporting (non-critical, lowest priority)</li>
 *   <li><b>reconciliation</b>: background reconciliation (non-critical)</li>
 * </ul>
 *
 * <p>Each pool has its own thread group. A thread pool exhaustion or uncaught exception
 * in the history pool has zero impact on the pricing or execution pools.
 */
@Slf4j
public final class ExchangeWorkerIsolator {

    private final String exchangeName;
    private final Map<WorkerPool, ExecutorService> pools;

    public ExchangeWorkerIsolator(@NotNull String exchangeName) {
        this.exchangeName = exchangeName;
        this.pools = new ConcurrentHashMap<>();

        pools.put(WorkerPool.EXECUTION, buildPool(WorkerPool.EXECUTION, 2));
        pools.put(WorkerPool.PRICING, buildPool(WorkerPool.PRICING, 2));
        pools.put(WorkerPool.ACCOUNT, buildPool(WorkerPool.ACCOUNT, 1));
        pools.put(WorkerPool.POSITIONS, buildPool(WorkerPool.POSITIONS, 1));
        pools.put(WorkerPool.HISTORY, buildPool(WorkerPool.HISTORY, 2));
        pools.put(WorkerPool.ANALYTICS, buildPool(WorkerPool.ANALYTICS, 1));
        pools.put(WorkerPool.RECONCILIATION, buildPool(WorkerPool.RECONCILIATION, 1));
    }

    /**
     * Submits a callable to the worker pool appropriate for the given endpoint.
     *
     * <p>Failures within the callable are contained to the pool’s thread
     * and reported via the returned future only.
     *
     * @param endpoint the target endpoint (determines pool selection)
     * @param callable the work to perform
     * @param <T>      return type
     * @return a CompletableFuture that resolves to the callable result
     */
    public <T> @NotNull CompletableFuture<T> submit(
            @NotNull EndpointType endpoint,
            @NotNull Callable<T> callable
    ) {
        WorkerPool pool = poolFor(endpoint);
        ExecutorService executor = pools.get(pool);

        CompletableFuture<T> future = new CompletableFuture<>();
        executor.submit(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                log.debug("Worker failure in pool {} for {}: {}", pool, exchangeName, e.getMessage());
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    /**
     * Submits a CompletableFuture supplier to the appropriate pool.
     *
     * @param endpoint the target endpoint
     * @param supplier supplier of a future
     * @param <T>      return type
     * @return the future produced by the supplier
     */
    public <T> @NotNull CompletableFuture<T> submitAsync(
            @NotNull EndpointType endpoint,
            @NotNull java.util.function.Supplier<CompletableFuture<T>> supplier
    ) {
        return submit(endpoint, () -> null)
                .thenCompose(ignored -> supplier.get());
    }

    /** Shuts down all worker pools gracefully. */
    public void shutdown() {
        pools.forEach((pool, executor) -> {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            }
        });
    }

    /** Returns the number of active tasks in the given pool (approximate). */
    public int activeCount(@NotNull WorkerPool pool) {
        ExecutorService executor = pools.get(pool);
        if (executor instanceof ThreadPoolExecutor tpe) {
            return tpe.getActiveCount();
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private @NotNull ExecutorService buildPool(@NotNull WorkerPool pool, int threads) {
        return new ThreadPoolExecutor(
                threads, threads,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(500),
                r -> {
                    Thread t = new Thread(r, "%s-worker-%s".formatted(exchangeName, pool.name().toLowerCase()));
                    t.setDaemon(true);
                    t.setUncaughtExceptionHandler((thread, ex) ->
                            log.error("Uncaught exception in {} pool for {}: {}", pool, exchangeName, ex.getMessage(), ex));
                    return t;
                },
                new ThreadPoolExecutor.DiscardOldestPolicy() // drop oldest non-critical task if full
        );
    }

    private @NotNull WorkerPool poolFor(@NotNull EndpointType endpoint) {
        return switch (endpoint) {
            case EXECUTION -> WorkerPool.EXECUTION;
            case PRICING -> WorkerPool.PRICING;
            case ACCOUNT, BALANCES -> WorkerPool.ACCOUNT;
            case POSITIONS -> WorkerPool.POSITIONS;
            case ORDER_HISTORY, TRADE_HISTORY, TRANSACTIONS -> WorkerPool.HISTORY;
            case ANALYTICS -> WorkerPool.ANALYTICS;
        };
    }

    /** Named worker pools, one per functional area. */
    public enum WorkerPool {
        EXECUTION,
        PRICING,
        ACCOUNT,
        POSITIONS,
        HISTORY,
        ANALYTICS,
        RECONCILIATION
    }
}
