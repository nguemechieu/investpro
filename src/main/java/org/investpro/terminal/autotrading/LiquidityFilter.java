package org.investpro.terminal.autotrading;

import java.util.Optional;

public final class LiquidityFilter implements TradabilityCheck {
    @Override
    public Optional<TradabilityResult> evaluate(TradabilityContext context) {
        return context.marketQuality().liquidityScore() < context.policy().minLiquidityScore()
                ? Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.LIQUIDITY_TOO_LOW,
                "Liquidity score is below configured minimum"))
                : Optional.empty();
    }
}
