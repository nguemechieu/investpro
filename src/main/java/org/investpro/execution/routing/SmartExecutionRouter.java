package org.investpro.execution.routing;

import org.investpro.execution.OrderIntent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Venue selection contract for future Coinbase/Binance/OANDA/Solana/Stellar
 * routing. Submission remains delegated to existing OrderRouter implementations.
 */
public final class SmartExecutionRouter {
    private final VenueScorer venueScorer;

    public SmartExecutionRouter(VenueScorer venueScorer) {
        this.venueScorer = venueScorer;
    }

    public Optional<VenueScore> selectBestVenue(List<String> venues, OrderIntent intent) {
        if (venues == null || venues.isEmpty() || venueScorer == null) {
            return Optional.empty();
        }
        return venues.stream()
                .map(venue -> venueScorer.score(venue, intent))
                .filter(score -> score != null && !score.venueId().isBlank())
                .max(Comparator.comparing(VenueScore::compositeScore));
    }
}
