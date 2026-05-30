package org.investpro.exchange.coinbase;

import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Adaptive limiter for Coinbase REST market-data calls.
 *
 * <p>The limiter combines three controls:</p>
 * <ul>
 *     <li>Global concurrency permits to avoid request bursts.</li>
 *     <li>Per-product cooldown after HTTP 429 responses.</li>
 *     <li>A short circuit-open window when repeated rate limits occur.</li>
 * </ul>
 */
public class CoinbaseRestRateLimiter {

	private static final int MAX_CONCURRENT_MARKET_REQUESTS = 2;
	private static final long MIN_SPACING_MS = 175L;
	private static final long PRODUCT_COOLDOWN_MS = 60_000L;
	private static final long CIRCUIT_OPEN_MS = 10_000L;
	private static final int RATE_LIMIT_STREAK_FOR_CIRCUIT = 2;
	private static final long MAX_BACKOFF_MS = 12_000L;

	private final Semaphore permits = new Semaphore(MAX_CONCURRENT_MARKET_REQUESTS, true);
	private final Map<String, Long> productCooldownUntilMs = new ConcurrentHashMap<>();
	private final AtomicInteger consecutiveRateLimits = new AtomicInteger(0);
	private final AtomicLong circuitOpenUntilMs = new AtomicLong(0L);

	private long nextAllowedAtMs = 0L;

	/**
	 * Acquire a request slot, respecting circuit and product cooldown state.
	 */
	public void acquirePermit(String route, String productId) {
		long now = System.currentTimeMillis();
		long circuitWait = circuitOpenUntilMs.get() - now;
		if (circuitWait > 0) {
			throw new RateLimitBlockedException("circuit-open", circuitWait);
		}

		if (isProductCoolingDown(productId)) {
			long waitMs = productCooldownUntilMs.getOrDefault(normalizeProduct(productId), now) - now;
			throw new RateLimitBlockedException("product-cooldown", Math.max(200L, waitMs));
		}

		boolean acquired;
		try {
			acquired = permits.tryAcquire(1500L, TimeUnit.MILLISECONDS);
		} catch (InterruptedException interruptedException) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Interrupted while acquiring Coinbase REST permit for route=" + route,
					interruptedException);
		}

		if (!acquired) {
			throw new RateLimitBlockedException("concurrency-limit", 500L);
		}

		paceRequests();
	}

	/**
	 * Release a previously acquired request slot.
	 */
	public void releasePermit() {
		permits.release();
	}

	/**
	 * Register a 429 rate-limit event for a product and potentially open a short circuit.
	 */
	public void onRateLimited(String productId) {
		long now = System.currentTimeMillis();
		productCooldownUntilMs.put(normalizeProduct(productId), now + PRODUCT_COOLDOWN_MS);
		int streak = consecutiveRateLimits.incrementAndGet();
		if (streak >= RATE_LIMIT_STREAK_FOR_CIRCUIT) {
			circuitOpenUntilMs.set(now + CIRCUIT_OPEN_MS);
		}
	}

	/**
	 * Register a successful response and soften limiter state.
	 */
	public void onSuccess(String productId) {
		consecutiveRateLimits.updateAndGet(current -> Math.max(0, current - 1));
		if (productId != null) {
			productCooldownUntilMs.remove(normalizeProduct(productId));
		}
	}

	/**
	 * Register a non-rate-limit failure (network/5xx/etc.) without tripping the circuit.
	 */
	public void onNonRateLimitFailure() {
		consecutiveRateLimits.updateAndGet(current -> Math.max(0, current - 1));
	}

	/**
	 * Exponential backoff with bounded jitter and optional Retry-After hint.
	 */
	public long backoffWithJitterMs(int attempt, Duration retryAfterHint) {
		int safeAttempt = Math.max(0, attempt);
		long base = Math.min(MAX_BACKOFF_MS, 1_000L * (1L << Math.min(safeAttempt, 5)));
		if (retryAfterHint != null && !retryAfterHint.isNegative() && !retryAfterHint.isZero()) {
			base = Math.max(base, retryAfterHint.toMillis());
		}
		long jitter = ThreadLocalRandom.current().nextLong(50L, 260L);
		return Math.min(MAX_BACKOFF_MS, base + jitter);
	}

	public boolean isProductCoolingDown(String productId) {
		String normalized = normalizeProduct(productId);
		long until = productCooldownUntilMs.getOrDefault(normalized, 0L);
		long now = System.currentTimeMillis();
		if (until <= now) {
			productCooldownUntilMs.remove(normalized, until);
			return false;
		}
		return true;
	}

	public boolean isCircuitOpen() {
		long now = System.currentTimeMillis();
		long until = circuitOpenUntilMs.get();
		if (until <= now) {
			circuitOpenUntilMs.compareAndSet(until, 0L);
			return false;
		}
		return true;
	}

	/**
	 * Extract Coinbase product id from known REST paths or query parameters.
	 */
	public static String extractProductId(URI uri) {
		if (uri == null) {
			return "GLOBAL";
		}

		String fromQuery = queryParam(uri, "product_id");
		if (fromQuery != null && !fromQuery.isBlank()) {
			return fromQuery;
		}

		String path = uri.getPath();
		if (path == null || path.isBlank()) {
			return "GLOBAL";
		}

		String[] segments = path.split("/");
		for (int index = 0; index < segments.length - 1; index++) {
			if ("products".equals(segments[index])) {
				String candidate = segments[index + 1];
				if (candidate != null && !candidate.isBlank()
						&& !"ticker".equals(candidate)
						&& !"candles".equals(candidate)) {
					return candidate;
				}
			}
		}

		return "GLOBAL";
	}

	private synchronized void paceRequests() {
		long now = System.currentTimeMillis();
		if (now < nextAllowedAtMs) {
			long sleepMs = nextAllowedAtMs - now;
			try {
				Thread.sleep(sleepMs);
			} catch (InterruptedException interruptedException) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("Interrupted while pacing Coinbase REST requests", interruptedException);
			}
			now = System.currentTimeMillis();
		}
		nextAllowedAtMs = now + MIN_SPACING_MS;
	}

	private static String queryParam(@NonNull URI uri, String key) {
		String query = uri.getRawQuery();
		if (query == null || query.isBlank()) {
			return null;
		}

		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq < 0) {
				continue;
			}
			String paramKey = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
			if (!Objects.equals(paramKey, key)) {
				continue;
			}
			return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
		}
		return null;
	}

	private static String normalizeProduct(String productId) {
		if (productId == null || productId.isBlank()) {
			return "GLOBAL";
		}
		return productId.trim().toUpperCase();
	}

	/**
	 * Non-fatal signal used by retry logic when limiter temporarily blocks a request.
	 */
	public static final class RateLimitBlockedException extends RuntimeException {
		private final String reason;
		private final long waitMs;

		public RateLimitBlockedException(String reason, long waitMs) {
			super("Coinbase REST limiter blocked request: " + reason + " (waitMs=" + waitMs + ")");
			this.reason = reason;
			this.waitMs = Math.max(0L, waitMs);
		}

		public String reason() {
			return reason;
		}

		public long waitMs() {
			return waitMs;
		}
	}
}
