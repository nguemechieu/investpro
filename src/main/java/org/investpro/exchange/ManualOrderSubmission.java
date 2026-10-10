package org.investpro.exchange;

import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Explicit desk orders, separate from strategy/bot execution and its approval state. */
public final class ManualOrderSubmission {
    public enum Type { MARKET, LIMIT, STOP, TRAILING_STOP, BRACKET }
    private static final ExecutorService WORKERS = org.investpro.core.concurrent.AppExecutors.TRADING;
    private ManualOrderSubmission() {}

    public static CompletableFuture<String> submit(Exchange venue, boolean paper, Type type,
            TradePair pair, Side side, double quantity, double price, double stopLoss,
            double takeProfit, boolean trailingPercent) {
        return submit(venue, paper, type, pair, side, quantity, price, stopLoss, takeProfit, trailingPercent, WORKERS);
    }
    static CompletableFuture<String> submit(Exchange venue, boolean paper, Type type,
            TradePair pair, Side side, double quantity, double price, double stopLoss,
            double takeProfit, boolean trailingPercent, Executor executor) {
        try {
            if (venue == null || pair == null || type == null || (side != Side.BUY && side != Side.SELL))
                throw new IllegalArgumentException("Exchange, symbol, order type and BUY/SELL side are required");
            positive(quantity, "Quantity");
            if (type != Type.MARKET) positive(price, "Price or trailing distance");
            if (type == Type.TRAILING_STOP && trailingPercent && price >= 100)
                throw new IllegalArgumentException("Trailing percentage must be below 100");
            if (type == Type.BRACKET) {
                positive(stopLoss, "Stop loss"); positive(takeProfit, "Take profit");
                if (side == Side.BUY ? !(stopLoss < price && price < takeProfit) : !(takeProfit < price && price < stopLoss))
                    throw new IllegalArgumentException("Bracket stop loss and take profit must surround entry in the correct direction");
            }
            checkRouting(venue, paper);
            if (!paper && ((type == Type.STOP && !venue.supportsStopOrders())
                    || (type == Type.BRACKET && !venue.supportsBracketOrders())
                    || (type == Type.TRAILING_STOP && !venue.supportsTrailingStopOrders())))
                throw new UnsupportedOperationException("This exchange adapter does not support " + type);
            var execution = paper ? venue.localPaperOrderExecution() : venue;
            return CompletableFuture.supplyAsync(() -> {
                checkRouting(venue, paper);
                Supplier<CompletableFuture<String>> place = () -> switch (type) {
                    case MARKET -> execution.createMarketOrder(pair, side, quantity);
                    case LIMIT -> execution.createLimitOrder(pair, side, quantity, price);
                    case STOP -> execution.createStopOrder(pair, side, quantity, price);
                    case TRAILING_STOP -> execution.createTrailingStopOrder(pair, side, quantity, price, trailingPercent);
                    case BRACKET -> execution.createBracketOrder(pair, side, quantity, price, stopLoss, takeProfit);
                };
                // Explicit user orders have passed desk permission/quantity validation. Keep
                // IBKR's broker handshake, license and contract checks; bot approvals stay separate.
                return !paper && venue instanceof IbkrExchange ibkr
                        ? ibkr.executeRiskApproved(true, place) : place.get();
            }, executor).thenCompose(future -> future);
        } catch (RuntimeException exception) { return CompletableFuture.failedFuture(exception); }
    }
    private static void checkRouting(Exchange venue, boolean paper) {
        if (venue.isDeskPaperTrading() != paper)
            throw new IllegalStateException("Trading authentication or execution mode changed; review and resubmit the order");
        if (!paper && (!venue.isAuthenticatedSessionConnected() || !venue.canSubmitLiveOrders()))
            throw new IllegalStateException("Live manual orders require an authenticated trading session");
    }
    private static void positive(double value, String label) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(label + " must be positive and finite");
    }
}
