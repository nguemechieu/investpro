package org.investpro.ui.theme;

import org.investpro.models.market.ProductVenue;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MarketVenueSelectionTest {
    private MarketConfiguration selection(String exchange, String market, String venue, String mode) {
        return new MarketConfiguration("", market, venue, exchange, "", "", "", "", "", null, null, mode);
    }

    @Test
    void destinationsAreGroupedByExchangeAndNeverContainExecutionModes() {
        assertEquals(List.of(ProductVenue.COINBASE_ADVANCED, ProductVenue.COINBASE_DERIVATIVES,
                ProductVenue.COINBASE_INTERNATIONAL), MarketConfiguration.availableVenues("coinbase"));
        assertEquals(List.of(ProductVenue.BINANCE_US_SPOT), MarketConfiguration.availableVenues("binance_us"));
        assertEquals(List.of(ProductVenue.BINANCE_SPOT), MarketConfiguration.availableVenues("binance"));
        assertTrue(MarketConfiguration.availableVenues("interactive_brokers").stream()
                .allMatch(venue -> venue.name().startsWith("IBKR_")));
    }

    @Test
    void legacyPaperRouteBecomesActualVenueWithoutChangingPaperMode() {
        var old = selection("coinbase", "Futures", "Paper Trading", "PAPER");
        assertEquals(ProductVenue.COINBASE_DERIVATIVES, old.normalizedVenue());
        assertEquals("PAPER", old.tradingMode());
        assertEquals(ProductVenue.COINBASE_INTERNATIONAL,
                selection("coinbase", "Perpetuals", "Paper Trading", "PAPER").normalizedVenue());
    }

    @Test
    void venueDisplayNamesRoundTripThroughSavedConfiguration() {
        for (ProductVenue venue : MarketConfiguration.availableVenues("interactive_brokers")) {
            assertEquals(venue, selection("interactive_brokers", "Spot", venue.displayName(), "PAPER").normalizedVenue());
        }
    }
}
