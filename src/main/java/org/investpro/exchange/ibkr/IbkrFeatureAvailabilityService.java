package org.investpro.exchange.ibkr;

public final class IbkrFeatureAvailabilityService {

    public FeatureAvailability evaluate(IbkrSessionState state) {
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
