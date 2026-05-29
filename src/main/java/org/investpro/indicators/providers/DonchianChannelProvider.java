package org.investpro.indicators.providers;

import org.investpro.indicators.DonchianChannel;
import org.investpro.indicators.Indicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class DonchianChannelProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "DONCHIAN_CHANNEL";
    }

    @Override
    public String displayName() {
        return "Donchian Channel";
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
        return "DONCHIAN_CHANNEL";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("high", "low");
    }

    @Override
    public Indicator create(IndicatorProviderContext context) {
        return new DonchianChannel(context.intConfig("period", 20));
    }
}
