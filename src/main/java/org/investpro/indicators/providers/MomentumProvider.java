package org.investpro.indicators.providers;

import org.investpro.indicators.Indicator;
import org.investpro.indicators.Momentum;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class MomentumProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "MOMENTUM";
    }

    @Override
    public String displayName() {
        return "Momentum";
    }

    @Override
    public String version() {
        return "1.0";
    }

    @Override
    public boolean enabledByDefault() {
        return true;
    }

    @Override
    public String indicatorName() {
        return "MOMENTUM";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public Indicator create(IndicatorProviderContext context) {
        return new Momentum(context.intConfig("period", 10));
    }
}
