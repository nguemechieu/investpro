package org.investpro.terminal.autotrading;

import java.util.Optional;

public final class SpreadFilter implements TradabilityCheck {
    @Override
    public Optional<TradabilityResult> evaluate(TradabilityContext context) {
        return context.marketQuality().spreadPercent() > context.policy().maxSpreadPercent()
                ? Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.SPREAD_TOO_WIDE,
                "Spread exceeds configured max"))
                : Optional.empty();
    }
}
