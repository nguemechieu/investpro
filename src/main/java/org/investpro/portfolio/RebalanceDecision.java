package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A rebalancing action for portfolio adjustment.
 * Can target positions, strategies, or portfolio-wide settings.
 */
@Getter
@Builder
public class RebalanceDecision {
    
    public enum RebalanceAction {
        HOLD("No action needed"),
        REDUCE_POSITION("Reduce specific position size"),
        CLOSE_POSITION("Close specific position"),
        TAKE_PARTIAL_PROFIT("Take profits on winning position"),
        REDUCE_STRATEGY_ALLOCATION("Reduce capital allocated to strategy"),
        DISABLE_STRATEGY_TEMPORARILY("Pause strategy (temp or permanent)"),
        MOVE_TO_DEFENSIVE_MODE("Shift portfolio to lower risk"),
        STOP_TRADING_FOR_DAY("Do not open new positions");
        
        public final String description;
        
        RebalanceAction(String description) {
            this.description = description;
        }
    }
    
    private final RebalanceAction action;
    
    @Nullable
    private final String targetSymbol; // null if portfolio-wide
    
    @Nullable
    private final String targetStrategyId; // null if not strategy-specific
    
    @Builder.Default
    private final double targetExposurePercent = 0.0; // Target exposure level (if REDUCE_POSITION)
    
    @Builder.Default
    private final double reductionPercent = 0.0; // % to reduce by (if applicable)
    
    @Builder.Default
    private final double profitTargetPercent = 0.0; // % profit to take (if TAKE_PARTIAL_PROFIT)
    
    @Builder.Default
    private final String reason = ""; // Why this rebalance is recommended
    
    @Builder.Default
    private final List<String> blockers = new ArrayList<>();
    
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();
    
    @Builder.Default
    private final Instant recommendedAt = Instant.now();
    
    @Builder.Default
    private final boolean urgent = false;
    
    public boolean isPortfolioWide() {
        return targetSymbol == null;
    }
    
    public boolean isSymbolSpecific() {
        return targetSymbol != null && targetStrategyId == null;
    }
    
    public boolean isStrategySpecific() {
        return targetStrategyId != null;
    }
    
    public boolean isHighPriority() {
        return urgent || action == RebalanceAction.STOP_TRADING_FOR_DAY 
               || action == RebalanceAction.MOVE_TO_DEFENSIVE_MODE
               || action == RebalanceAction.CLOSE_POSITION;
    }
    
    @Override
    public String toString() {
        String target = targetSymbol != null ? targetSymbol : 
                       targetStrategyId != null ? ("strategy:" + targetStrategyId) : 
                       "portfolio";
        return String.format("RebalanceDecision{%s on %s, urgent=%s, reason=%s}",
                action, target, urgent, reason);
    }
}
