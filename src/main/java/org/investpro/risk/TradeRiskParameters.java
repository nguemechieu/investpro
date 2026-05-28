package org.investpro.risk;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * Container for calculated trade risk parameters
 */
@Setter
@Getter
@Data
public class TradeRiskParameters {
    private final double positionSize;
    private final double entryPrice;
    private final double stopLossPrice;
    private final double stopLossPercent;
    private final double takeProfitPrice;
    private final double takeProfitPercent;
    private final double riskAmount;
    private final String riskMethod;
    private final long calculatedAt;
    
    public static final TradeRiskParameters EMPTY = new TradeRiskParameters(
        0, 0, 0, 0, 0, 0, 0, "NONE");
    
    public TradeRiskParameters(
            double positionSize,
            double entryPrice,
            double stopLossPrice,
            double stopLossPercent,
            double takeProfitPrice,
            double takeProfitPercent,
            double riskAmount,
            String riskMethod) {
        this.positionSize = positionSize;
        this.entryPrice = entryPrice;
        this.stopLossPrice = stopLossPrice;
        this.stopLossPercent = stopLossPercent;
        this.takeProfitPrice = takeProfitPrice;
        this.takeProfitPercent = takeProfitPercent;
        this.riskAmount = riskAmount;
        this.riskMethod = riskMethod;
        this.calculatedAt = System.currentTimeMillis();
    }

    public boolean isValid() {
        return positionSize > 0 && stopLossPrice != entryPrice && takeProfitPrice != entryPrice;
    }
    
    @Override
    public String toString() {
        return String.format(
            "TradeRiskParameters{size=%.4f, entry=%.2f, SL=%.2f (%.2f%%), TP=%.2f (%.2f%%), risk=$%.2f, method=%s}",
            positionSize, entryPrice, stopLossPrice, stopLossPercent,
            takeProfitPrice, takeProfitPercent, riskAmount, riskMethod);
    }
}
