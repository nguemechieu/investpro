package org.investpro.terminal.autotrading;

import org.investpro.terminal.domain.InstrumentId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TradabilityResult(
        InstrumentId instrumentId,
        boolean tradable,
        AutoTradeSymbolState state,
        TradabilityFailureReason failureReason,
        String message,
        double spreadPercent,
        int liquidityScore,
        double volume24h,
        Instant checkedAt,
        Map<String, Object> metadata
) {
    public TradabilityResult {
        state = state == null ? (tradable ? AutoTradeSymbolState.ELIGIBLE : AutoTradeSymbolState.NOT_TRADEABLE) : state;
        failureReason = failureReason == null ? TradabilityFailureReason.UNKNOWN : failureReason;
        message = message == null ? "" : message.trim();
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static TradabilityResult pass(TradabilityContext context, Map<String, Object> metadata) {
        return new TradabilityResult(
                context.instrument().id(),
                true,
                AutoTradeSymbolState.ELIGIBLE,
                TradabilityFailureReason.NONE,
                "Symbol passed auto-trading universe checks",
                context.marketQuality().spreadPercent(),
                context.marketQuality().liquidityScore(),
                context.marketQuality().volume24h(),
                context.checkedAt(),
                metadata);
    }

    public static TradabilityResult fail(TradabilityContext context, TradabilityFailureReason reason, String message) {
        return new TradabilityResult(
                context.instrument() == null ? null : context.instrument().id(),
                false,
                stateFor(reason),
                reason,
                message,
                context.marketQuality().spreadPercent(),
                context.marketQuality().liquidityScore(),
                context.marketQuality().volume24h(),
                context.checkedAt(),
                context.metadata());
    }

    public static TradabilityResult fail(TradabilityContext context, TradabilityFailureReason reason) {
        return fail(context, reason, reason.name());
    }

    public static TradabilityResult merge(TradabilityContext context, List<TradabilityResult> results) {
        if (results != null) {
            for (TradabilityResult result : results) {
                if (result != null && !result.tradable()) {
                    return result;
                }
            }
        }
        return pass(context, context.metadata());
    }

    private static AutoTradeSymbolState stateFor(TradabilityFailureReason reason) {
        if (reason == TradabilityFailureReason.MARKET_DATA_MISSING
                || reason == TradabilityFailureReason.MARKET_DATA_STALE) {
            return AutoTradeSymbolState.WAITING_FOR_DATA;
        }
        if (reason == TradabilityFailureReason.RISK_REJECTED) {
            return AutoTradeSymbolState.RISK_REJECTED;
        }
        if (reason == TradabilityFailureReason.SYMBOL_DISABLED_BY_USER) {
            return AutoTradeSymbolState.DISABLED_BY_USER;
        }
        if (reason == TradabilityFailureReason.EXCHANGE_DISCONNECTED
                || reason == TradabilityFailureReason.RECONCILIATION_REQUIRED) {
            return AutoTradeSymbolState.PAUSED;
        }
        return AutoTradeSymbolState.NOT_TRADEABLE;
    }
}
