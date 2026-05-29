package org.investpro.indicators.providers;

import org.investpro.indicators.BollingerBands;
import org.investpro.indicators.Indicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class BollingerBandsProvider implements IndicatorProvider {
    @Override
    public String id() {
        return "BOLLINGER_BANDS";
    }

    @Override
    public String displayName() {
        return "Bollinger Bands";
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
        return "Bollinger Bands";
    }

    @Override
    public Set<String> supportedInputs() {
        return Set.of("close");
    }

    @Override
    public Indicator create(IndicatorProviderContext context) {
        return new BollingerBands(
                context.intConfig("period", 20),
                context.doubleConfig("stdDevMultiplier", 2.0));
    }
}
