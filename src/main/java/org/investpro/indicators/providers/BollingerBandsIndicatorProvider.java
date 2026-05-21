package org.investpro.indicators.providers;

import org.investpro.indicators.BollingerBandsIndicator;
import org.investpro.indicators.ChartIndicator;
import org.investpro.spi.IndicatorProvider;
import org.investpro.spi.IndicatorProviderContext;

import java.util.Set;

public final class BollingerBandsIndicatorProvider implements IndicatorProvider {
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
    public ChartIndicator create(IndicatorProviderContext context) {
        return new BollingerBandsIndicator(
                context.intConfig("period", 20),
                context.doubleConfig("stdDevMultiplier", 2.0));
    }
}
