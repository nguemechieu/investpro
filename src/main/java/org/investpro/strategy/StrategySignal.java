package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import org.investpro.timeframe.Timeframe;
import org.investpro.trading.MarketBehavior;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalized signal returned by all trading strategies.
 * Enables comparison and evaluation of strategies.
 */
@Getter
@Builder
public class StrategySignal {
    private final String symbol;
    private final Timeframe timeframe;
    private final String strategyId;
    
    @Builder.Default
    private final SignalSide side = SignalSide.HOLD;
    
    @Builder.Default
    private final double confidence = 0.0; // 0.0 to 1.0
    
    private final Double entryPrice;
    private final Double stopLoss;
    private final Double takeProfit;
    
    @Builder.Default
    private final double riskRewardRatio = 0.0;
    
    @Builder.Default
    private final double expectedValue = 0.0; // EV in % or pips
    
    @Builder.Default
    private final List<String> reasons = new ArrayList<>();
    
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    
    @Builder.Default
    private final Instant createdAt = Instant.now();
    
    private final Instant validUntil; // Signal expiry
    
    private final MarketBehavior marketBehavior;
    
    @Builder.Default
    private final SignalMetadata metadata = SignalMetadata.builder().build();

    public enum SignalSide {
        BUY("BUY"),
        SELL("SELL"),
        HOLD("HOLD");

        private final String label;

        SignalSide(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public boolean isValid() {
        if (validUntil != null && Instant.now().isAfter(validUntil)) {
            return false;
        }
        return side != SignalSide.HOLD;
    }

    public boolean hasStopLoss() {
        return stopLoss != null;
    }

    public boolean hasTakeProfit() {
        return takeProfit != null;
    }

    public boolean isHighConfidence() {
        return confidence >= 0.7;
    }

    public boolean hasSeriousWarnings() {
        return !warnings.isEmpty();
    }

    @Getter
    @Builder
    public static class SignalMetadata {
        @Builder.Default
        private final String dataQuality = "UNKNOWN";
        
        @Builder.Default
        private final int barsUsed = 0;
        
        @Builder.Default
        private final boolean hasLookaheadBias = false;
        
        @Builder.Default
        private final String indicatorConfirmation = "";
    }
}
