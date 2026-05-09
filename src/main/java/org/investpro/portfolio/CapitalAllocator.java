package org.investpro.portfolio;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Allocates position size and leverage based on risk state, capital, and
 * strategy.
 */
@Slf4j
@Getter
@Setter
public class CapitalAllocator {
    /**
     * Allocate capital for a candidate trade based on portfolio state.
     */
    public double allocateCapital(
            @NotNull PortfolioContext context,
            double requestedCapital,
            double portfolioHeat,
            double concentrationRiskScore) {

        double allocatedCapital = requestedCapital;

        // Reduce during drawdown
        allocatedCapital *= getDrawdownReductionFactor(context.getCurrentDrawdownPercent());

        // Reduce during high heat
        allocatedCapital *= getHeatReductionFactor(portfolioHeat);

        // Reduce during high concentration risk
        allocatedCapital *= getConcentrationReductionFactor(concentrationRiskScore);

        // Reduce based on available capital
        if (allocatedCapital > context.getAvailableCash()) {
            allocatedCapital = context.getAvailableCash();
            log.debug("Reduced allocation to available cash: {}", allocatedCapital);
        }

        // Apply risk profile limits
        allocatedCapital *= getRiskProfileFactor(context.getRiskProfile());

        // Reduce during high market volatility
        allocatedCapital *= getVolatilityReductionFactor(context.getMarketVolatilityLevel());

        log.info("Allocated capital: {} (from requested {})", allocatedCapital, requestedCapital);
        return Math.max(0, allocatedCapital);
    }

    /**
     * Allocate leverage based on risk state.
     */
    public double allocateLeverage(
            @NotNull PortfolioContext context,
            double requestedLeverage,
            double portfolioHeat) {

        double allocatedLeverage = requestedLeverage;

        // Never allocate more leverage than account profile allows
        double maxLeverage = getMaxLeverageForProfile(context.getRiskProfile());
        allocatedLeverage = Math.min(allocatedLeverage, maxLeverage);

        // Reduce during drawdown
        allocatedLeverage *= getLeverageDrawdownFactor(context.getCurrentDrawdownPercent());

        // Reduce during high heat
        allocatedLeverage *= getLeverageHeatFactor(portfolioHeat);

        // Floor at 1x (no leverage)
        allocatedLeverage = Math.max(1.0, allocatedLeverage);

        log.info("Allocated leverage: {}x (from requested {}x)", allocatedLeverage, requestedLeverage);
        return allocatedLeverage;
    }

    /**
     * Determine if we should reject a trade entirely.
     */
    public boolean shouldRejectTrade(
            @NotNull PortfolioContext context,
            double portfolioHeatAfter,
            double concentrationScore) {

        // Reject if margin insufficient
        if (context.getAvailableCash() <= 0) {
            log.warn("Rejecting trade: insufficient cash");
            return true;
        }

        // Reject if adding trade would exceed heat limit
        PortfolioRiskState.RiskStatus riskStatus = getRiskStatusFromHeat(portfolioHeatAfter);
        if (riskStatus == PortfolioRiskState.RiskStatus.STOP_TRADING) {
            log.warn("Rejecting trade: heat limit exceeded");
            return true;
        }

        // Reject if concentration risk too high
        if (concentrationScore > 90 && portfolioHeatAfter > 8) {
            log.warn("Rejecting trade: concentration risk too high ({})", concentrationScore);
            return true;
        }

        // Conservative profile rejects more trades
        if (context.isConservativeProfile() && portfolioHeatAfter > 5) {
            log.warn("Rejecting trade: conservative profile, heat too high");
            return true;
        }

        return false;
    }

    // Helper methods

    private double getDrawdownReductionFactor(double drawdownPercent) {
        if (drawdownPercent > 15.0) {
            return 0.3; // Only 30% of capital allocated
        } else if (drawdownPercent > 10.0) {
            return 0.5; // Only 50%
        } else if (drawdownPercent > 5.0) {
            return 0.7; // 70%
        } else {
            return 1.0; // Full allocation
        }
    }

    private double getHeatReductionFactor(double portfolioHeat) {
        if (portfolioHeat > 8.0) {
            return 0.2; // Only 20% allocation
        } else if (portfolioHeat > 6.0) {
            return 0.5; // 50%
        } else if (portfolioHeat > 4.0) {
            return 0.8; // 80%
        } else {
            return 1.0; // Full
        }
    }

    private double getConcentrationReductionFactor(double concentrationScore) {
        if (concentrationScore > 90) {
            return 0.0; // Reject
        } else if (concentrationScore > 75) {
            return 0.2;
        } else if (concentrationScore > 60) {
            return 0.5;
        } else if (concentrationScore > 45) {
            return 0.8;
        } else {
            return 1.0;
        }
    }

    private double getRiskProfileFactor(@NotNull String riskProfile) {
        return switch (riskProfile) {
            case "CONSERVATIVE" -> 0.5; // Half allocation for conservative
            case "AGGRESSIVE" -> 1.2; // Extra 20% for aggressive
            default -> 1.0; // Balanced
        };
    }

    private double getVolatilityReductionFactor(double volatilityLevel) {
        // 0-30: low, 30-60: normal, 60-100: high
        if (volatilityLevel > 75) {
            return 0.4;
        } else if (volatilityLevel > 60) {
            return 0.6;
        } else if (volatilityLevel > 40) {
            return 0.9;
        } else {
            return 1.0;
        }
    }

    private double getLeverageDrawdownFactor(double drawdownPercent) {
        if (drawdownPercent > 10.0) {
            return 0.5; // Max 0.5x leverage
        } else if (drawdownPercent > 5.0) {
            return 0.8; // Max 0.8x leverage
        } else {
            return 1.0; // Full leverage allowed
        }
    }

    private double getLeverageHeatFactor(double portfolioHeat) {
        if (portfolioHeat > 8.0) {
            return 0.2; // Minimal leverage
        } else if (portfolioHeat > 6.0) {
            return 0.5;
        } else {
            return 1.0;
        }
    }

    private double getMaxLeverageForProfile(@NotNull String riskProfile) {
        return switch (riskProfile) {
            case "CONSERVATIVE" -> 1.0; // No leverage
            case "BALANCED" -> 2.0; // Up to 2x
            case "AGGRESSIVE" -> 5.0; // Up to 5x
            default -> 2.0;
        };
    }

    private PortfolioRiskState.RiskStatus getRiskStatusFromHeat(double heat) {
        if (heat > 20.0) {
            return PortfolioRiskState.RiskStatus.STOP_TRADING;
        } else if (heat > 15.0) {
            return PortfolioRiskState.RiskStatus.DANGER;
        } else if (heat > 10.0) {
            return PortfolioRiskState.RiskStatus.DEFENSIVE;
        } else if (heat > 7.0) {
            return PortfolioRiskState.RiskStatus.WATCH;
        } else {
            return PortfolioRiskState.RiskStatus.NORMAL;
        }
    }
}
