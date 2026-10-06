package org.investpro.exchange.binanceus;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One conservative IP-weight budget shared by every transport on an exchange instance. */
final class BinanceUsRequestBudget {
    private static final Logger LOG = LoggerFactory.getLogger(BinanceUsRequestBudget.class);
    private static final int BUDGET = 4800;
    private long minute = -1, cooldownUntil, nextRequest;
    private int used;

    synchronized void acquire(int weight) throws IOException, InterruptedException {
        for (;;) {
            long now = System.currentTimeMillis();
            if (now < cooldownUntil) throw new RateLimitedException(cooldownUntil, "Binance US global cooldown");
            if (now / 60000 != minute) { minute = now / 60000; used = 0; }
            long wait = Math.max(0, nextRequest - now);
            if (used + weight > BUDGET) wait = Math.max(wait, (minute + 1) * 60000 - now + 100);
            if (wait > 0) { wait(wait); continue; }
            used += weight;
            // Reservations remain spaced after cooldown/window rollover: no queued herd.
            nextRequest = now + Math.max(50, weight * 60000L / BUDGET);
            LOG.debug("binance.rest.request weight={} usedWeight={} remainingEstimate={}", weight, used, BUDGET - used);
            return;
        }
    }

    synchronized void observe(HttpResponse<String> response) throws RateLimitedException {
        long now = System.currentTimeMillis();
        if (now / 60000 != minute) { minute = now / 60000; used = 0; }
        response.headers().firstValue("X-MBX-USED-WEIGHT-1M").ifPresent(value -> {
            try { used = Math.max(used, Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        });
        response.headers().firstValue("X-MBX-USED-WEIGHT").ifPresent(value -> {
            try { used = Math.max(used, Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        });
        if (response.statusCode() == 429 || response.statusCode() == 418) {
            long delay = response.statusCode() == 418 ? 120000 : 65000;
            String retry = response.headers().firstValue("Retry-After").orElse("");
            try { delay = Math.max(1000, Long.parseLong(retry) * 1000); }
            catch (NumberFormatException ignored) {
                try { delay = Math.max(1000, ZonedDateTime.parse(retry, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now); }
                catch (RuntimeException ignoredDate) { }
            }
            var ban = java.util.regex.Pattern.compile("until\\s+(\\d+)").matcher(response.body());
            if (ban.find()) {
                try { delay = Math.max(delay, Long.parseLong(ban.group(1)) - now); }
                catch (NumberFormatException ignored) { }
            }
            cooldownUntil = Math.max(cooldownUntil, now + delay + ThreadLocalRandom.current().nextLong(250, 1001));
            nextRequest = cooldownUntil;
            notifyAll();
            LOG.debug("binance.rest.cooldown until={} http={}", cooldownUntil, response.statusCode());
            throw new RateLimitedException(cooldownUntil, "Binance US HTTP " + response.statusCode());
        }
    }

    synchronized long cooldownUntil() { return cooldownUntil; }

    synchronized void checkCooldown() throws RateLimitedException {
        if (System.currentTimeMillis() < cooldownUntil)
            throw new RateLimitedException(cooldownUntil, "Binance US global cooldown");
    }

    synchronized java.util.Map<String, Long> diagnostics() {
        int current = minute == System.currentTimeMillis() / 60000 ? used : 0;
        return java.util.Map.of("binance.rateLimit.usedWeight", (long) current,
                "binance.rateLimit.remainingEstimate", (long) Math.max(0, BUDGET - current),
                "binance.rateLimit.cooldown", Math.max(0, cooldownUntil - System.currentTimeMillis()));
    }

    synchronized void observeWebSocket(com.fasterxml.jackson.databind.JsonNode payload) {
        long now = System.currentTimeMillis();
        if (now / 60000 != minute) { minute = now / 60000; used = 0; }
        for (var rate : payload.path("rateLimits")) {
            if (rate.path("rateLimitType").asText().equals("REQUEST_WEIGHT") && rate.path("interval").asText().equals("MINUTE"))
                used = Math.max(used, rate.path("count").asInt());
        }
        if (payload.path("status").asInt() == 429 || payload.path("status").asInt() == 418) {
            cooldownUntil = Math.max(cooldownUntil, payload.path("error").path("data").path("retryAfter").asLong(now + 65000)
                    + ThreadLocalRandom.current().nextLong(250, 1001));
            nextRequest = cooldownUntil;
            notifyAll();
        }
    }

    static int weight(HttpRequest request) {
        String path = request.uri().getPath();
        String query = request.uri().getRawQuery();
        boolean symbol = query != null && query.contains("symbol=");
        if (!request.method().equals("GET")) return 1;
        return switch (path) {
            case "/api/v3/openOrders" -> symbol ? 3 : 40;
            case "/api/v3/account", "/api/v3/exchangeInfo" -> 20;
            case "/api/v3/allOrders" -> 10;
            case "/api/v3/myTrades" -> query != null && query.contains("orderId=") ? 5 : 20;
            case "/api/v3/depth" -> depthWeight(query);
            case "/api/v3/trades", "/api/v3/historicalTrades" -> 25;
            case "/api/v3/ticker/24hr" -> symbol ? 1 : 40;
            case "/api/v3/klines" -> 2;
            case "/api/v3/ticker/price", "/api/v3/ticker/bookTicker" -> symbol ? 1 : 2;
            default -> 4;
        };
    }

    private static int depthWeight(String query) {
        int limit = 100;
        if (query != null) for (String parameter : query.split("&")) if (parameter.startsWith("limit=")) {
            try { limit = Integer.parseInt(parameter.substring(6)); } catch (NumberFormatException ignored) { return 250; }
        }
        return limit <= 100 ? 5 : limit <= 500 ? 25 : limit <= 1000 ? 50 : 250;
    }

    static final class RateLimitedException extends IOException {
        private final long retryAt;
        RateLimitedException(long retryAt, String message) { super(message); this.retryAt = retryAt; }
        public long retryAt() { return retryAt; }
    }
}
