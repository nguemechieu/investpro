package org.investpro.exchange.oanda;

import java.time.Duration;
import java.util.concurrent.Semaphore;

public final class OandaRateLimiter {

    private final Semaphore concurrency;
    private final long minDelayMillis;

    private long nextAllowedAtMillis = 0L;

    public OandaRateLimiter(int maxConcurrentRequests, Duration minDelayBetweenRequests) {
        this.concurrency = new Semaphore(maxConcurrentRequests);
        this.minDelayMillis = minDelayBetweenRequests.toMillis();
    }

    public <T> T execute(CheckedSupplier<T> supplier) throws Exception {
        concurrency.acquire();

        try {
            waitForTurn();
            return supplier.get();
        } finally {
            concurrency.release();
        }
    }

    private synchronized void waitForTurn() throws InterruptedException {
        long now = System.currentTimeMillis();

        if (now < nextAllowedAtMillis) {
            Thread.sleep(nextAllowedAtMillis - now);
        }

        nextAllowedAtMillis = System.currentTimeMillis() + minDelayMillis;
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}