package org.investpro.market;

import org.investpro.decision.MarketRegime;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public record MarketRegimeAnalysis(
        @NotNull MarketRegime regime,
        double confidence,
        @NotNull List<String> reasons,
        @NotNull List<String> warnings) {
}
