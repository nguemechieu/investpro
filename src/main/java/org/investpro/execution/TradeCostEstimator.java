package org.investpro.execution;

import org.investpro.decision.TradeCostEstimate;
import org.investpro.models.trading.Ticker;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;

/**
 * Estimates expected direct and indirect trade costs.
 */
public class TradeCostEstimator {

    private static final double DEFAULT_COMMISSION_RATE = 0.0020;
    private static final double DEFAULT_SLIPPAGE_RATE = 0.0005;
    private static final double DEFAULT_SWAP_RATE = 0.0001;
    private static final double DEFAULT_MARKET_IMPACT_RATE = 0.0001;

    @NotNull
    public TradeCostEstimate estimate(@NotNull Ticker ticker, double positionSize) {
        BigDecimal spread = BigDecimal.valueOf(Math.max(0.0, ticker.getAskPrice() - ticker.getBidPrice()));
        BigDecimal referencePrice = BigDecimal.valueOf(Math.max(0.0, ticker.getLastPrice()));
        BigDecimal size = BigDecimal.valueOf(Math.max(0.0, positionSize));
        BigDecimal notional = size.multiply(referencePrice);

        BigDecimal spreadCost = spread.multiply(size).multiply(BigDecimal.valueOf(2.0));
        BigDecimal commission = notional.multiply(BigDecimal.valueOf(DEFAULT_COMMISSION_RATE));
        BigDecimal slippage = notional.multiply(BigDecimal.valueOf(DEFAULT_SLIPPAGE_RATE));
        BigDecimal swap = notional.multiply(BigDecimal.valueOf(DEFAULT_SWAP_RATE));
        BigDecimal marketImpact = notional.multiply(BigDecimal.valueOf(DEFAULT_MARKET_IMPACT_RATE));
        BigDecimal total = spreadCost.add(commission).add(slippage).add(swap).add(marketImpact);

        String breakdown = String.format(
                "Spread: %.6f, Commission: %.6f, Slippage: %.6f, Swap: %.6f, Impact: %.6f = Total: %.6f",
                spreadCost, commission, slippage, swap, marketImpact, total);

        BigDecimal expectedProfit = notional.multiply(BigDecimal.valueOf(0.005));
        boolean acceptable = total.compareTo(expectedProfit.multiply(BigDecimal.valueOf(0.30))) <= 0;
        return new TradeCostEstimate(
                spreadCost,
                commission,
                slippage,
                swap,
                marketImpact,
                total,
                breakdown,
                acceptable,
                acceptable ? null : "Cost exceeds 30% of expected profit");
    }
}
