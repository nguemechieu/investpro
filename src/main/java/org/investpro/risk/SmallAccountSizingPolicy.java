package org.investpro.risk;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;

public final class SmallAccountSizingPolicy {

    public static final double SMALL_ACCOUNT_BALANCE_THRESHOLD = 100.0;
    public static final double OANDA_MICRO_UNIT_SIZE = 1.0;

    private SmallAccountSizingPolicy() {
    }

    public static double apply(
            String exchangeName,
            double accountBalance,
            TradePair pair,
            Side side,
            double requestedUnits) {
        if (isOanda(exchangeName)
                && accountBalance > 0.0
                && accountBalance < SMALL_ACCOUNT_BALANCE_THRESHOLD) {
            return OANDA_MICRO_UNIT_SIZE;
        }

        return Math.max(0.0, requestedUnits);
    }

    private static boolean isOanda(String exchangeName) {
        return exchangeName != null && exchangeName.trim().equalsIgnoreCase("OANDA");
    }
}
