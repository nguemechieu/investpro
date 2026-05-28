package org.investpro.portfolio.intelligence;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portfolio intelligence facade. This first implementation accepts normalized
 * exposure rows and avoids coupling to any single portfolio persistence model.
 */
public final class PortfolioIntelligenceService {
    private static final PortfolioIntelligenceService INSTANCE = new PortfolioIntelligenceService();
    private static final MathContext MC = MathContext.DECIMAL64;

    private PortfolioIntelligenceService() {
    }

    public static PortfolioIntelligenceService getInstance() {
        return INSTANCE;
    }

    public PortfolioIntelligenceSnapshot snapshot() {
        return PortfolioIntelligenceSnapshot.empty();
    }

    public PortfolioIntelligenceSnapshot analyze(List<ExposureRow> exposures) {
        if (exposures == null || exposures.isEmpty()) {
            return PortfolioIntelligenceSnapshot.empty();
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal blockchain = BigDecimal.ZERO;
        BigDecimal derivatives = BigDecimal.ZERO;
        Map<String, BigDecimal> bySymbol = new LinkedHashMap<>();
        Map<String, BigDecimal> byAsset = new LinkedHashMap<>();

        for (ExposureRow row : exposures) {
            if (row == null) {
                continue;
            }
            BigDecimal exposure = row.marketValue();
            gross = gross.add(exposure.abs(), MC);
            net = net.add(exposure, MC);
            bySymbol.merge(row.symbol(), exposure, BigDecimal::add);
            byAsset.merge(row.assetClass(), exposure.abs(), BigDecimal::add);
            if (row.blockchain()) {
                blockchain = blockchain.add(exposure.abs(), MC);
            }
            if (row.derivative()) {
                derivatives = derivatives.add(exposure.abs(), MC);
            }
        }

        BigDecimal maxSymbol = BigDecimal.ZERO;
        for (BigDecimal value : bySymbol.values()) {
            maxSymbol = maxSymbol.max(value.abs());
        }
        BigDecimal concentration = gross.signum() == 0 ? BigDecimal.ZERO : maxSymbol.divide(gross, MC);

        return new PortfolioIntelligenceSnapshot(
                gross,
                net,
                gross,
                blockchain,
                derivatives,
                BigDecimal.ZERO,
                concentration,
                BigDecimal.ZERO,
                bySymbol,
                byAsset,
                Instant.now());
    }

    public record ExposureRow(
            String symbol,
            String assetClass,
            BigDecimal marketValue,
            boolean blockchain,
            boolean derivative) {

        public ExposureRow {
            symbol = symbol == null ? "" : symbol.trim();
            assetClass = assetClass == null ? "UNKNOWN" : assetClass.trim();
            marketValue = marketValue == null ? BigDecimal.ZERO : marketValue;
        }
    }
}
