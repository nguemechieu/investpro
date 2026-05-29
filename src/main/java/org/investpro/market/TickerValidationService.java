package org.investpro.market;

import org.investpro.models.trading.Ticker;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates market ticker sanity before trade review.
 */
public class TickerValidationService {

    private static final Duration STALE_TICKER_MAX_AGE = Duration.ofSeconds(30);

    @NotNull
    public TickerValidationResult validate(@NotNull Ticker ticker) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double last = ticker.getLastPrice();

        if (bid <= 0) {
            errors.add("Bid price must be greater than 0");
        }
        if (ask <= 0) {
            errors.add("Ask price must be greater than 0");
        }
        if (last <= 0) {
            errors.add("Last price must be greater than 0");
        }

        if (bid > 0 && ask > 0 && bid >= ask) {
            errors.add("Bid must be lower than ask");
        }

        if (bid > 0 && ask > 0 && (ask - bid) <= 0.0) {
            errors.add("Spread must be greater than 0");
        }

        Instant updateTime = ticker.getInstant();
        if (updateTime != null) {
            Duration age = Duration.between(updateTime, Instant.now());
            if (age.compareTo(STALE_TICKER_MAX_AGE) > 0) {
                warnings.add("Ticker may be stale: age=" + age.toSeconds() + "s");
            }
        }

        return new TickerValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }
}
