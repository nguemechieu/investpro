package org.investpro.risk;

import lombok.extern.slf4j.Slf4j;

import org.investpro.enums.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Risk framework classes
// Note: Uses org.investpro.risk.* classes for risk management context, profiles, and calculators

/**
 * Central risk management orchestrator.
 * Evaluates trades against all risk criteria and returns approval decision.
 * <p>
 * This is the PRIMARY GATEKEEPER for all trade decisions.
 * Integrates all risk dimensions into a cohesive framework.
 */
@Slf4j

public record RiskManagementSystem(double defaultMaxRiskPerTrade, double defaultMaxCumulativeRisk) {

    public RiskManagementSystem() {
        // 2% max per trade
        this(2.0, 10.0); // 10% max cumulative
    }

    /**
     * PRIMARY GATEKEEPER: Evaluate a complete trade context and return decision.
     * This is the entry point for all trade evaluations.
     *
     * @param context Complete trade risk context
     * @return RiskDecision with approval status and detailed reasoning
     */
    public RiskDecision evaluateTrade(TradeRiskContext context) {
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        // ===== CRITICAL BLOCKERS (prevent trade) =====

        // 0. Block trades outside the instrument trading session.
        TradingSessionStatus sessionStatus = context.getTradingSessionStatus();
        if (sessionStatus == null) {
            sessionStatus = context.getSymbol() != null && context.getSymbol().getTradingSession() != null
                    ? context.getSymbol().getTradingSessionStatus()
                    : null;
        }
        if (sessionStatus != null && !sessionStatus.isTradable()) {
            blockers.add("Trading session is not open: " + sessionStatus);
        }

        // 1. Block low probability setups
        if (context.getProbabilityLevel() == ProbabilityLevel.VERY_LOW) {
            blockers.add("Probability level is VERY_LOW (<30%). Trade should not be executed.");
        } else if (context.getProbabilityLevel() == ProbabilityLevel.LOW) {
            if (context.getRiskProfile() != RiskProfile.EXTREME) {
                blockers.add("Probability level is LOW (30-50%). Only EXTREME profile can trade this.");
            }
        }

        // 2. Block illiquid markets with market execution
        if (context.getLiquidityProfile() == LiquidityProfile.ILLIQUID &&
                context.getExecutionStrategy() == ExecutionStrategy.MARKET_ORDER) {
            blockers.add("ILLIQUID market with MARKET execution. Not viable. Use SCALED_ENTRY instead.");
        }

        // 3. Block impulsive/fearful psychology without capital protection
        if ((context.getPsychologyProfile() == PsychologyProfile.IMPULSIVE ||
                context.getPsychologyProfile() == PsychologyProfile.FEARFUL) &&
                context.getCapitalProtection() == CapitalProtection.NONE) {
            blockers.add("IMPULSIVE/FEARFUL trader with NO capital protection. Must activate stop-loss strategy.");
        }

        // 4. Block trades exceeding max leverage
        if (context.getRequestedLeverage() > context.getRiskProfile().getMaxLeverage()) {
            blockers.add(String.format(
                    "Requested leverage %.2fx exceeds profile max of %.2fx",
                    context.getRequestedLeverage(),
                    context.getRiskProfile().getMaxLeverage()));
        }

        // 5. Block trades exceeding max position size
        double maxPositionPercent = context.getRiskProfile().getMaxPositionSizePercent();
        double requestedRiskPercent = context.calculateTradeRiskPercent();
        if (requestedRiskPercent > maxPositionPercent) {
            blockers.add(String.format(
                    "Trade risk %.2f%% exceeds profile max of %.2f%%",
                    requestedRiskPercent,
                    maxPositionPercent));
        }

        // 6. Block trades exceeding portfolio heat limit
        double portfolioHeat = context.calculateTotalPortfolioRiskPercent();
        double maxHeat = context.getRiskProfile().getMaxPortfolioHeatPercent();
        if (portfolioHeat > maxHeat) {
            blockers.add(String.format(
                    "Portfolio heat %.2f%% exceeds limit of %.2f%%",
                    portfolioHeat,
                    maxHeat));
        }

        // 7. Block negative expected value trades (unless overridden by extreme
        // profile)
        double expectedValue = ExpectedValueCalculator.calculateExpectedValueFromContext(context);
        if (expectedValue < 0 && context.getRiskProfile() != RiskProfile.EXTREME) {
            blockers.add(String.format(
                    "Negative expected value: $%.2f. Probability of profit too low.",
                    expectedValue));
        }

        // ===== WARNINGS (allow but alert) =====

        // High volatility
        if (Objects.equals(context.getMarketBehavior().getDisplayName(),
                MarketBehavior.HIGH_VOLATILITY.getDisplayName())) {
            warnings.add("HIGH_VOLATILITY market detected. Position size will be reduced. Monitor closely.");
        }

        // Thin liquidity
        if (context.getLiquidityProfile() == LiquidityProfile.THIN) {
            warnings.add("THIN_LIQUIDITY. Use SCALED_ENTRY or LIMIT orders. Slippage will be elevated.");
        }

        // Cautious psychology
        if (context.getPsychologyProfile() == PsychologyProfile.CAUTIOUS) {
            warnings.add("CAUTIOUS psychology profile. Consider reducing position size further.");
        }

        // Low probability (but not blocked)
        if (context.getProbabilityLevel() == ProbabilityLevel.LOW) {
            warnings.add("LOW probability setup. Ensure expected value justifies the risk.");
        }

        // No capital protection
        if (context.getCapitalProtection() == CapitalProtection.NONE) {
            warnings.add("NO capital protection active. This exposes account to catastrophic loss.");
        }

        // ===== RECOMMENDATIONS =====

        // Market behavior based recommendation
        if (Objects.equals(context.getMarketBehavior().getDisplayName(), MarketBehavior.TRENDING_UP.getDisplayName())) {
            recommendations.add("Market is TRENDING_UP. Bias toward BUY entries and trend-following.");
        } else if (Objects.equals(context.getMarketBehavior().getDisplayName(),
                MarketBehavior.TRENDING_DOWN.getDisplayName())) {
            recommendations.add("Market is TRENDING_DOWN. Bias toward SELL entries and shorting.");
        } else if (Objects.equals(context.getMarketBehavior().getDisplayName(),
                MarketBehavior.RANGING.getDisplayName())) {
            recommendations.add("Market is RANGING. Use mean-reversion strategies, not breakout.");
        } else if (Objects.equals(context.getMarketBehavior().getDisplayName(),
                MarketBehavior.BREAKOUT.getDisplayName())) {
            recommendations.add("Market in BREAKOUT. Use momentum strategies, tight stops required.");
        }

        // Liquidity-based execution recommendation
        if (!SlippageModel.isExecutionStrategyViable(context.getExecutionStrategy(), context.getLiquidityProfile())) {
            ExecutionStrategy recommended = SlippageModel.getRecommendedStrategy(context.getLiquidityProfile());
            recommendations.add(String.format(
                    "Current execution strategy not ideal for %s. Consider %s instead.",
                    context.getLiquidityProfile().getDisplayName(),
                    recommended.getDisplayName()));
        }

        // Position size reduction recommendations
        if (context.getPsychologyProfile() == PsychologyProfile.FEARFUL) {
            recommendations.add("FEARFUL psychology: Consider reducing position size further to build confidence.");
        }

        // Expected value guidance
        if (expectedValue > 0 && expectedValue < 10) {
            recommendations.add(String.format(
                    "Expected value $%.2f is positive but modest. Ensure trade setup is high-confidence.",
                    expectedValue));
        }

        // Decision: Approved if no blockers
        boolean approved = blockers.isEmpty();
        String approvalReason = approved ? "✓ Setup meets all risk criteria" : "✗ Critical blocker(s) present";

        // Calculate final position sizing
        double riskMultiplier = calculateRiskMultiplier(context);
        double calculatedPositionSize = calculateFinalPositionSize(context, riskMultiplier);
        double accountBalance = context.getAccountBalance() > 0.0
                ? context.getAccountBalance()
                : context.getAccountEquity();
        double finalPositionSize = SmallAccountSizingPolicy.apply(
                context.getBroker(),
                accountBalance,
                calculatedPositionSize);
        boolean smallAccountModeActive = isOanda(context.getBroker())
                && accountBalance > 0.0
                && accountBalance < SmallAccountSizingPolicy.SMALL_ACCOUNT_BALANCE_THRESHOLD;
        if (smallAccountModeActive) {
            String message = "Small account mode enabled: OANDA balance below $100, order size forced to 1 unit.";
            warnings.add(message);
            recommendations.add(message);
        }
        log.info(
                "RiskManagementSystem: sizing broker={} symbol={} accountBalance={} smallAccountMode={} requestedUnits={} finalUnits={}",
                context.getBroker(),
                context.getSymbol(),
                accountBalance,
                smallAccountModeActive ? "active" : "inactive",
                calculatedPositionSize,
                finalPositionSize);
        double finalLeverage = calculateFinalLeverage(context, finalPositionSize);

        // Calculate other metrics
        double portfolioHeatFinal = PortfolioHeatCalculator.calculatePortfolioHeat(
                context.calculateTradeRisk(),
                context.getCurrentOpenRisk(),
                context.getAccountEquity());

        ExecutionStrategy recommendedExecution = SlippageModel.getRecommendedStrategy(context.getLiquidityProfile());
        double estimatedSlippage = SlippageModel.calculateTotalSlippage(
                recommendedExecution,
                context.getLiquidityProfile(),
                context.getVolatility());

        return RiskDecision.builder()
                .approved(approved)
                .approvalReason(approvalReason)
                .finalPositionSize(finalPositionSize)
                .finalLeverage(finalLeverage)
                .riskMultiplier(riskMultiplier)
                .expectedValue(expectedValue)
                .portfolioHeat(portfolioHeatFinal)
                .estimatedSlippage(estimatedSlippage)
                .recommendedExecutionStrategy(recommendedExecution)
                .blockers(blockers)
                .warnings(warnings)
                .recommendations(recommendations)
                .humanReadableSummary(buildHumanReadableSummary(context, approved, blockers, warnings, recommendations))
                .build();
    }

    /**
     * Calculate risk multiplier based on market conditions and trader profile.
     * Reduces or increases position size based on multiple factors.
     *
     * @param context Trade risk context
     * @return Risk multiplier (0.1 to 2.0 typical range)
     */
    private double calculateRiskMultiplier(TradeRiskContext context) {
        double multiplier = 1.0;

        // Market behavior adjustment
        multiplier *= context.getMarketBehavior().getRiskMultiplier();

        // Psychology adjustment
        multiplier *= context.getPsychologyProfile().getSuccessProbability();

        // Volatility adjustment
        if (context.getVolatility() > 0.7) {
            multiplier *= 0.7; // High volatility = smaller position
        } else if (context.getVolatility() < 0.3) {
            multiplier *= 1.1; // Low volatility = slightly larger
        }

        // Liquidity adjustment
        multiplier *= context.getLiquidityProfile().getFillProbability();

        // Capital protection adjustment
        multiplier *= Math.min(1.0, context.getCapitalProtection().getCapitalRetention());

        return multiplier;
    }

    /**
     * Calculate final position size after all adjustments.
     *
     * @param context        Trade risk context
     * @param riskMultiplier Pre-calculated risk multiplier
     * @return Final position size in units
     */
    private double calculateFinalPositionSize(TradeRiskContext context, double riskMultiplier) {
        double maxRisk = defaultMaxRiskPerTrade; // 2% default
        if (context.getMaxRiskPerTrade() > 0) {
            maxRisk = context.getMaxRiskPerTrade();
        }

        double baseSize = PositionSizingEngine.calculateFixedFractionSize(
                context.getAccountEquity(),
                maxRisk,
                context.getEntryPrice(),
                context.getStopLossPrice(),
                context.getRiskProfile());

        // Apply all adjustments
        double adjusted = PositionSizingEngine.applyPsychologyAdjustment(baseSize, context.getPsychologyProfile());
        adjusted = PositionSizingEngine.applyLiquidityAdjustment(adjusted, context.getLiquidityProfile());
        adjusted = PositionSizingEngine.applyVolatilityAdjustment(adjusted, context.getVolatility());

        // Apply risk multiplier from market conditions
        adjusted = adjusted * riskMultiplier;

        // Enforce max position size from profile
        double maxPositionAmount = context.getAccountEquity()
                * (context.getRiskProfile().getMaxPositionSizePercent() / 100.0);
        double maxPositionUnits = maxPositionAmount / context.getEntryPrice();

        return Math.min(adjusted, maxPositionUnits);
    }

    private static boolean isOanda(String exchangeName) {
        return exchangeName != null && exchangeName.trim().equalsIgnoreCase("OANDA");
    }

    /**
     * Calculate final leverage after all constraints applied.
     *
     * @param context           Trade risk context
     * @param finalPositionSize Final calculated position size
     * @return Final leverage (capped by profile)
     */
    private double calculateFinalLeverage(TradeRiskContext context, double finalPositionSize) {
        if (context.getAccountEquity() <= 0 || context.getEntryPrice() <= 0) {
            return 1.0;
        }

        double positionValue = finalPositionSize * context.getEntryPrice();
        double leverage = positionValue / context.getAccountEquity();

        return Math.min(leverage, context.getRiskProfile().getMaxLeverage());
    }

    /**
     * Build human-readable summary of decision.
     *
     * @param context         Trade context
     * @param approved        Decision result
     * @param blockers        Blocker list
     * @param warnings        Warning list
     * @param recommendations Recommendation list
     * @return Formatted summary string
     */
    private String buildHumanReadableSummary(
            TradeRiskContext context,
            boolean approved,
            List<String> blockers,
            List<String> warnings,
            List<String> recommendations) {

        double re = context.getAccountEquity();

        StringBuilder sb = new StringBuilder();

        sb.append(approved ? "✓ TRADE APPROVED\n" : "✗ TRADE BLOCKED\n");
        sb.append("\n");

        if (!blockers.isEmpty()) {
            sb.append("BLOCKERS:\n");
            for (String blocker : blockers) {
                sb.append("  • ").append(blocker).append("\n");
            }
            sb.append("\n");
        }

        if (!warnings.isEmpty()) {
            sb.append("WARNINGS:\n");
            for (String warning : warnings) {
                sb.append("  ⚠ ").append(warning).append("\n");
            }
            sb.append("\n");
        }

        if (!recommendations.isEmpty()) {
            sb.append("RECOMMENDATIONS:\n");
            for (String rec : recommendations) {
                sb.append("  → %s".formatted(re)).append(rec).append("\n");
            }
        }
        RiskReport report = generateRiskReport(context, RiskDecision.builder().build());

        log.info(report.toString());
        return sb.toString();
    }

    /**
     * Generate comprehensive risk report for backtesting or trade logging.
     *
     * @param context  Trade context
     * @param decision Risk decision from evaluateTrade()
     * @return Professional risk report
     */
    public RiskReport generateRiskReport(TradeRiskContext context, RiskDecision decision) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        double rewardRiskRatio = 0;
        if (context.calculateTradeRisk() > 0) {
            rewardRiskRatio = context.calculateTradeReward() / context.calculateTradeRisk();
        }

        return RiskReport.builder()
                .symbol(context.getSymbol().toString())
                .timestamp(timestamp)
                .riskProfileName(context.getRiskProfile().getDisplayName())
                .marketBehaviorName(context.getMarketBehavior().getDisplayName())
                .liquidityProfileName(context.getLiquidityProfile().getDisplayName())
                .psychologyProfileName(context.getPsychologyProfile().getDisplayName())
                .probabilityLevelName(context.getProbabilityLevel().getDisplayName())
                .capitalProtectionName(context.getCapitalProtection().getDisplayName())
                .executionStrategyName(decision.getRecommendedExecutionStrategy().getDisplayName())
                .systemDesignName(context.getSystemDesign().getDisplayName())
                .approved(decision.isApproved())
                .decisionStatus(decision.isApproved() ? "✓ APPROVED" : "✗ BLOCKED")
                .finalPositionSize(decision.getFinalPositionSize())
                .finalLeverage(decision.getFinalLeverage())
                .riskMultiplier(decision.getRiskMultiplier())
                .accountEquity(context.getAccountEquity())
                .tradeRiskAmount(context.calculateTradeRisk())
                .tradeRiskPercent(context.calculateTradeRiskPercent())
                .tradeRewardAmount(context.calculateTradeReward())
                .tradeRewardPercent(context.calculateTradeRewardPercent())
                .portfolioHeat(decision.getPortfolioHeat())
                .maxAllowedHeat(context.getRiskProfile().getMaxPortfolioHeatPercent())
                .recommendedExecutionStrategy(decision.getRecommendedExecutionStrategy().getDisplayName())
                .estimatedSlippage(decision.getEstimatedSlippage())
                .rewardRiskRatio(rewardRiskRatio)
                .expectedValue(decision.getExpectedValue())
                .blockers(formatFeedback(decision.getBlockers()))
                .warnings(formatFeedback(decision.getWarnings()))
                .recommendations(formatFeedback(decision.getRecommendations()))
                .build();
    }

    /**
     * Format feedback list as indented string.
     *
     * @param items List of feedback items
     * @return Formatted string
     */
    private String formatFeedback(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            sb.append("  • ").append(item).append("\n");
        }
        return sb.toString();
    }

}
