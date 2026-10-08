package org.investpro.exchange.ibkr;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IbkrMarketDataExceptionTest {
    @Test void subscriptionFailuresIncludeTheBrokerReasonAndAnExactPackageLookup() {
        var error = new IbkrMarketDataException(162, "No market data permissions for NASDAQ STK");
        assertTrue(error.getMessage().contains("NASDAQ STK"));
        assertTrue(error.getMessage().contains("Level 1"));
        assertTrue(error.getMessage().contains("Launch Market Data Subscription Manager"));
        assertTrue(IbkrMarketDataException.isSubscriptionError(162, "No market data permissions for NASDAQ STK"));
        assertTrue(IbkrMarketDataException.isSubscriptionError(10089, "Additional subscription required"));
        assertTrue(IbkrMarketDataException.isSubscriptionError(354, "Not subscribed"));
    }

    @Test void resolutionAndOrdinaryHistoricalErrorsAreNotSubscriptionFailures() {
        assertFalse(IbkrMarketDataException.isSubscriptionError(200, "No security definition found"));
        assertFalse(IbkrMarketDataException.isSubscriptionError(162, "HMDS query returned no data"));
        assertFalse(IbkrMarketDataException.isSubscriptionError(162, "Historical data request pacing violation"));
    }
}
