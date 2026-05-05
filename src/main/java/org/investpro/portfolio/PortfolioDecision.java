package org.investpro.portfolio;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Decision from the Quant Portfolio Manager for a candidate trade.
 * Contains approval status and all supporting analysis.
 */
@Getter
@Builder
public class PortfolioDecision {
    
    private final boolean approved;
    
    @Builder.Default
    private final double approvedPositionSize = 0.0; // contracts/shares approved (may be reduced from request)
    
    @Builder.Default
    private final double approvedLeverage = 1.0; // leverage approved (may be reduced)
    
    @Builder.Default
    private final double approvedCapital = 0.0; // capital allocated for this trade
    
    @Builder.Default
    private final double portfolioHeatBefore = 0.0; // % risk before trade
    
    @Builder.Default
    private final double portfolioHeatAfter = 0.0; // % risk after trade (if approved)
    
    @Builder.Default
    private final double exposureBefore = 0.0; // % exposure before
    
    @Builder.Default
    private final double exposureAfter = 0.0; // % exposure after
    
    @Builder.Default
    private final double correlationRiskScore = 0.0; // 0-100, higher = more risky
    
    @Builder.Default
    private final double concentrationRiskScore = 0.0; // 0-100, higher = more risky
    
    @Builder.Default
    private final double drawdownRiskScore = 0.0; // 0-100, higher = more risky
    
    @Builder.Default
    private final double capitalUtilizationPercent = 0.0; // % of available capital this uses
    
    @Builder.Default
    private final List<String> blockers = new ArrayList<>(); // Why trade was rejected
    
    @Builder.Default
    private final List<String> warnings = new ArrayList<>(); // Non-blocking concerns
    
    @Builder.Default
    private final List<String> recommendations = new ArrayList<>(); // Suggestions for trader/AI
    
    @Builder.Default
    private final String decisionSummary = ""; // One-line summary of decision
    
    @Builder.Default
    private final Instant decidedAt = Instant.now();
    
    public boolean hasBlockers() {
        return !blockers.isEmpty();
    }
    
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }
    
    public boolean hasRecommendations() {
        return !recommendations.isEmpty();
    }
    
    public double getSizeReductionPercent() {
        // Returns what % the requested size was reduced (0 = no reduction, 100 = rejected)
        return 0.0; // This would need requested size to calculate - is provided in context
    }
    
    public double getHeatIncrease() {
        return portfolioHeatAfter - portfolioHeatBefore;
    }
    
    public boolean increasesDifferently() {
        return Math.abs(getHeatIncrease()) > 0.01;
    }
    
    @Override
    public String toString() {
        return String.format("PortfolioDecision{approved=%s, size=%.2f, heat: %.1f→%.1f%%, corr=%.1f, conc=%.1f, dd=%.1f}",
                approved, approvedPositionSize, portfolioHeatBefore, portfolioHeatAfter, 
                correlationRiskScore, concentrationRiskScore, drawdownRiskScore);
    }
}
