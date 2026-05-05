package org.investpro.portfolio;

import org.investpro.models.trading.Position;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.*;

/**
 * Quant Portfolio Manager - Central portfolio-level decision brain
 * 
 * Role: ADVISORY & RESTRICTIVE ONLY
 * - Does NOT execute trades directly
 * - Returns PortfolioDecision (approved/rejected with reasons)
 * - FinalRiskGate is the authority that places trades
 */
@Slf4j
@Getter
public class QuantPortfolioManager {
    private static QuantPortfolioManager INSTANCE;
    
    private final PortfolioHeatCalculator heatCalculator;
    private final ExposureManager exposureManager;
    private final CorrelationRiskAnalyzer correlationAnalyzer;
    private final ConcentrationRiskAnalyzer concentrationAnalyzer;
    private final CapitalAllocator capitalAllocator;
    private final StrategyCapitalAllocator strategyAllocator;
    
    private QuantPortfolioManager() {
        this.heatCalculator = new PortfolioHeatCalculator();
        this.exposureManager = new ExposureManager();
        this.correlationAnalyzer = new CorrelationRiskAnalyzer();
        this.concentrationAnalyzer = new ConcentrationRiskAnalyzer();
        this.capitalAllocator = new CapitalAllocator();
        this.strategyAllocator = new StrategyCapitalAllocator();
    }
    
    public static synchronized QuantPortfolioManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new QuantPortfolioManager();
        }
        return INSTANCE;
    }
    
    /**
     * Evaluate whether a proposed trade fits within portfolio constraints
     */
    @NotNull
    public PortfolioDecision evaluateNewTrade(@NotNull PortfolioContext context) {
        try {
            log.debug("Evaluating trade: {}", context.getCandidateSymbol());
            
            PortfolioRiskState riskBefore = evaluatePortfolioRisk(context);
            
            if (riskBefore.shouldStopTrading()) {
                return PortfolioDecision.builder()
                        .approved(false)
                        .approvedPositionSize(0)
                        .approvedLeverage(1.0)
                        .approvedCapital(0)
                        .portfolioHeatBefore(riskBefore.getPortfolioHeat())
                        .portfolioHeatAfter(riskBefore.getPortfolioHeat())
                        .correlationRiskScore(0)
                        .concentrationRiskScore(0)
                        .drawdownRiskScore(0)
                        .blockers(List.of("Portfolio in STOP_TRADING mode"))
                        .warnings(Collections.emptyList())
                        .recommendations(Collections.emptyList())
                        .build();
            }
            
            double correlationScore = correlationAnalyzer.analyzeCorrelationRisk(context, context.getCorrelationMatrix());
            double concentrationScore = concentrationAnalyzer.analyzeConcentrationRisk(context);
            double drawdownScore = riskBefore.getPortfolioHeat() * 5.0;
            
            List<String> blockers = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            
            if (concentrationScore > 80) {
                blockers.add("Concentration risk too high");
            } else if (concentrationScore > 60) {
                warnings.add("High concentration risk");
            }
            
            if (correlationScore > 85) {
                blockers.add("Correlation risk too high");
            } else if (correlationScore > 65) {
                warnings.add("Moderate correlation risk");
            }
            
            boolean approved = blockers.isEmpty();
            double allocatedSize = context.getRequestedPositionSize();
            double allocatedLeverage = Math.max(1.0, context.getRequestedLeverage());
            
            if (approved) {
                allocatedSize = capitalAllocator.allocateCapital(context, context.getRequestedPositionSize(), riskBefore.getPortfolioHeat(), concentrationScore);
                allocatedLeverage = capitalAllocator.allocateLeverage(context, allocatedLeverage, riskBefore.getPortfolioHeat());
            }
            
            double heatAfter = riskBefore.getPortfolioHeat() + (allocatedSize / context.getAccountEquity()) * 2.0;
            
            PortfolioDecision decision = PortfolioDecision.builder()
                    .approved(approved)
                    .approvedPositionSize(allocatedSize)
                    .approvedLeverage(allocatedLeverage)
                    .approvedCapital(allocatedSize)
                    .portfolioHeatBefore(riskBefore.getPortfolioHeat())
                    .portfolioHeatAfter(heatAfter)
                    .correlationRiskScore((int) correlationScore)
                    .concentrationRiskScore((int) concentrationScore)
                    .drawdownRiskScore((int) Math.min(100, drawdownScore))
                    .blockers(blockers)
                    .warnings(warnings)
                    .recommendations(Collections.emptyList())
                    .build();
            
            log.info("Trade {} evaluated: approved={}", context.getCandidateSymbol(), approved);
            return decision;
            
        } catch (Exception e) {
            log.error("Error evaluating trade: {}", e.getMessage(), e);
            return PortfolioDecision.builder()
                    .approved(false)
                    .approvedPositionSize(0)
                    .approvedLeverage(1.0)
                    .approvedCapital(0)
                    .portfolioHeatBefore(0)
                    .portfolioHeatAfter(0)
                    .correlationRiskScore(0)
                    .concentrationRiskScore(0)
                    .drawdownRiskScore(0)
                    .blockers(List.of("Error: " + e.getMessage()))
                    .warnings(Collections.emptyList())
                    .recommendations(Collections.emptyList())
                    .build();
        }
    }
    
    /**
     * Evaluate current portfolio risk state
     */
    @NotNull
    public PortfolioRiskState evaluatePortfolioRisk(@NotNull PortfolioContext context) {
        try {
            List<Position> positions = context.getOpenPositions() != null ?
                    context.getOpenPositions() : Collections.emptyList();
            
            double heat = heatCalculator.calculatePortfolioHeat(positions, context.getAccountEquity());
            PortfolioRiskState.RiskStatus riskStatus = heatCalculator.determineRiskStatus(heat, context.getCurrentDrawdownPercent());
            double marginUsage = (context.getUsedMargin() > 0 && context.getFreeMargin() > 0) ? 
                    (context.getUsedMargin() / (context.getUsedMargin() + context.getFreeMargin())) * 100 : 0;
            
            return PortfolioRiskState.builder()
                    .portfolioHeat(heat)
                    .marginUsagePercent(marginUsage)
                    .openPositionCount(positions.size())
                    .riskStatus(riskStatus)
                    .build();
            
        } catch (Exception e) {
            log.error("Error evaluating portfolio risk: {}", e.getMessage(), e);
            return PortfolioRiskState.builder()
                    .portfolioHeat(0)
                    .marginUsagePercent(0)
                    .openPositionCount(0)
                    .riskStatus(PortfolioRiskState.RiskStatus.NORMAL)
                    .build();
        }
    }
    
    /**
     * Allocate capital across strategies
     */
    @NotNull
    public PortfolioAllocationPlan allocateCapital(@NotNull PortfolioContext context) {
        try {
            double totalCapital = context.getAccountEquity();
            
            Map<String, Double> strategyAllocs = strategyAllocator.allocateCapitalToStrategies(context, totalCapital);
            Map<String, Double> assetClassAllocs = new HashMap<>();
            
            assetClassAllocs.put("CRYPTO", totalCapital * 0.30);
            assetClassAllocs.put("FOREX", totalCapital * 0.30);
            assetClassAllocs.put("STOCKS", totalCapital * 0.25);
            assetClassAllocs.put("COMMODITIES", totalCapital * 0.15);
            
            return PortfolioAllocationPlan.builder()
                    .strategyAllocation(strategyAllocs)
                    .assetClassAllocation(assetClassAllocs)
                    .conservativeAllocation(0.40)
                    .aggressiveAllocation(0.70)
                    .defensiveAllocation(0.25)
                    .build();
            
        } catch (Exception e) {
            log.error("Error allocating capital: {}", e.getMessage(), e);
            return PortfolioAllocationPlan.builder()
                    .strategyAllocation(Collections.emptyMap())
                    .assetClassAllocation(Collections.emptyMap())
                    .conservativeAllocation(0.40)
                    .aggressiveAllocation(0.70)
                    .defensiveAllocation(0.25)
                    .build();
        }
    }
    
    /**
     * Evaluate rebalancing needs
     */
    @NotNull
    public List<RebalanceDecision> evaluateRebalance(@NotNull PortfolioContext context) {
        try {
            List<RebalanceDecision> decisions = new ArrayList<>();
            PortfolioRiskState riskState = evaluatePortfolioRisk(context);
            
            if (riskState.shouldStopTrading()) {
                decisions.add(RebalanceDecision.builder()
                        .action(RebalanceDecision.RebalanceAction.STOP_TRADING_FOR_DAY)
                        .reason("Portfolio heat exceeds STOP_TRADING threshold")
                        .build());
            }
            
            if (riskState.isDefensive()) {
                decisions.add(RebalanceDecision.builder()
                        .action(RebalanceDecision.RebalanceAction.MOVE_TO_DEFENSIVE_MODE)
                        .reason("Portfolio in DEFENSIVE risk state")
                        .build());
            }
            
            log.info("Rebalance evaluation: {} recommendations", decisions.size());
            return decisions;
            
        } catch (Exception e) {
            log.error("Error evaluating rebalance: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Generate portfolio report
     */
    @NotNull
    public PortfolioReport generatePortfolioReport(@NotNull PortfolioContext context) {
        try {
            PortfolioRiskState riskState = evaluatePortfolioRisk(context);
            
            String healthStatus = "HEALTHY";
            if (riskState.isDefensive()) {
                healthStatus = "DEFENSIVE";
            }
            if (riskState.shouldStopTrading()) {
                healthStatus = "CRITICAL";
            }
            
            List<String> warnings = new ArrayList<>();
            if (riskState.getPortfolioHeat() > 8.0) {
                warnings.add("High portfolio heat: " + String.format("%.1f%%", riskState.getPortfolioHeat()));
            }
            if (riskState.getMarginUsagePercent() > 80.0) {
                warnings.add("High margin usage: " + String.format("%.1f%%", riskState.getMarginUsagePercent()));
            }
            if (context.getCurrentDrawdownPercent() < -5.0) {
                warnings.add("Significant drawdown: " + String.format("%.1f%%", context.getCurrentDrawdownPercent()));
            }
            
            return PortfolioReport.builder()
                    .accountId(context.getAccountId())
                    .totalEquity(context.getAccountEquity())
                    .marginUsagePercent(riskState.getMarginUsagePercent())
                    .dailyPnl(context.getDailyPnl())
                    .portfolioHeat(riskState.getPortfolioHeat())
                    .riskStatus(riskState.getRiskStatus())
                    .openPositionCount(riskState.getOpenPositionCount())
                    .warnings(warnings)
                    .overallHealthStatus(healthStatus)
                    .generatedAt(Instant.now())
                    .build();
            
        } catch (Exception e) {
            log.error("Error generating portfolio report: {}", e.getMessage(), e);
            return PortfolioReport.builder()
                    .accountId("ERROR")
                    .totalEquity(0)
                    .marginUsagePercent(0)
                    .dailyPnl(0)
                    .portfolioHeat(0)
                    .riskStatus(PortfolioRiskState.RiskStatus.NORMAL)
                    .openPositionCount(0)
                    .warnings(List.of("Error generating report"))
                    .overallHealthStatus("ERROR")
                    .generatedAt(Instant.now())
                    .build();
        }
    }
    
    public static void resetSingleton() {
        INSTANCE = null;
    }
}
