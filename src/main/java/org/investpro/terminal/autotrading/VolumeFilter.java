package org.investpro.terminal.autotrading;

import java.util.Optional;

public final class VolumeFilter implements TradabilityCheck {
    @Override
    public Optional<TradabilityResult> evaluate(TradabilityContext context) {
        return context.marketQuality().volume24h() < context.policy().minVolume24h()
                ? Optional.of(TradabilityResult.fail(context, TradabilityFailureReason.VOLUME_TOO_LOW,
                "Recent volume is below configured minimum"))
                : Optional.empty();
    }
}
