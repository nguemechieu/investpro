package org.investpro.indicators.providers;

import org.investpro.indicators.Indicator;
import org.investpro.indicators.WilliamsR;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class WilliamsRProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "WILLIAMS_R";
    }

    @Override
    public String displayName() {
        return "Williams %R";
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
        return "WILLIAMS_R";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("high", "low", "close");
    }

    @Override
    public Indicator create(IndicatorProviderContext context) {
        return new WilliamsR(context.intConfig("period", 14));
    }
}
