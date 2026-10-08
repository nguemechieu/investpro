package org.investpro.exchange.ibkr;

import java.util.Locale;

/** Preserves the broker's reason while providing actionable subscription guidance. */
public final class IbkrMarketDataException extends IllegalStateException {
    public IbkrMarketDataException(int code, String reason) {
        super("IBKR " + code + ": " + reason + "\n\n"
                + "IBKR requires a market-data subscription for this instrument. "
                + "For quotes and chart data, check the exchange's API-enabled Level 1 / top-of-book subscription. "
                + "Full market depth may require a separate Level 2 subscription. "
                + "In TWS, add this contract to a watchlist, right-click it and select "
                + "Launch Market Data Subscription Manager to identify the exact package. "
                + "Manage subscriptions in IBKR Client Portal. Reconnecting InvestPro does not grant these permissions.");
    }

    public static boolean isSubscriptionError(int code, String reason) {
        String message = reason == null ? "" : reason.toLowerCase(Locale.ROOT);
        return code == 354 || code == 10089
                || (code == 162 && (message.contains("no market data permissions")
                    || message.contains("not subscribed") || message.contains("subscription")));
    }
}
