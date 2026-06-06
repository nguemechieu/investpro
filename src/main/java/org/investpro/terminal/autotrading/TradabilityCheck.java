package org.investpro.terminal.autotrading;

import java.util.Optional;

@FunctionalInterface
public interface TradabilityCheck {
    Optional<TradabilityResult> evaluate(TradabilityContext context);
}
