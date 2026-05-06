package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import org.investpro.models.trading.TradePair;
import org.investpro.timeframe.Timeframe;
import org.investpro.trading.MarketBehavior;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.investpro.utils.Side.HOLD;

/**
 * Normalized signal returned by all trading strategies.
 * Enables comparison and evaluation of strategies.
 */
@Getter
@Builder
public class StrategySignal {
    private final TradePair symbol;
    private final Timeframe timeframe;
    private final String strategyId;
    
    @Builder.Default
    private final Side side = HOLD;
    
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



    public boolean isValid() {
        if (validUntil != null && Instant.now().isAfter(validUntil)) {
            return false;
        }
        return side != HOLD;
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
