package org.investpro.exchange.ibkr;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

public final class IbkrFeatureAvailabilityService {

    @Contract("null -> new")
    public @NonNull FeatureAvailability evaluate(IbkrSessionState state) {
        if (state == null || !state.connectionSuccessful()) {
            return new FeatureAvailability(false, false, false, false, false);
        }
        return new FeatureAvailability(
                state.accountSummaryAvailable(),
                state.managedAccountsReceived(),
                state.marketDataPermissionAvailable(),
                state.marketDepthPermissionAvailable(),
                state.tradingEnabled());
    }

    public record FeatureAvailability(
            boolean accountBalanceAvailable,
            boolean positionsAvailable,
            boolean topOfBookAvailable,
            boolean orderbookAvailable,
            boolean tradingEnabled) {
    }
}
