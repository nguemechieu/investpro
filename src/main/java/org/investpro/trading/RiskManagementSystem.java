package org.investpro.trading;

/**
 * RiskManagementSystem - Comprehensive risk management framework integrating all risk factors.
 * Combines risk profile, market behavior, execution strategy, liquidity, psychology, 
 * probability, capital protection, and system design into a unified system.
 */
public class RiskManagementSystem {
    
    private RiskProfile riskProfile;
    private MarketBehavior marketBehavior;
    private ExecutionStrategy executionStrategy;
    private LiquidityProfile liquidityProfile;
    private PsychologyProfile psychologyProfile;
    private ProbabilityLevel probabilityLevel;
    private CapitalProtection capitalProtection;
    private SystemDesign systemDesign;
    
    private double accountSize;
    private double maxRiskPerTrade;            // % of account to risk per trade
    private double maxCumulativeRisk;          // % of account at risk across all positions
    private double winRate;                    // Expected win rate
    private double avgWinSize;                 // Average winning trade size
    private double avgLossSize;                // Average losing trade size
    
    public RiskManagementSystem() {
        // Default safe configuration
        this.riskProfile = RiskProfile.CONSERVATIVE;
        this.marketBehavior = MarketBehavior.TRENDING_UP;
        this.executionStrategy = ExecutionStrategy.LIMIT_ORDER;
        this.liquidityProfile = LiquidityProfile.NORMAL_LIQUIDITY;
        this.psychologyProfile = PsychologyProfile.DISCIPLINED;
        this.probabilityLevel = ProbabilityLevel.HIGH;
        this.capitalProtection = CapitalProtection.STRICT_STOPS;
        this.systemDesign = SystemDesign.HYBRID_SYSTEM;
        
        this.maxRiskPerTrade = 0.02;            // 2% risk per trade
        this.maxCumulativeRisk = 0.10;          // 10% cumulative risk
        this.winRate = 0.55;                    // 55% win rate
        this.avgWinSize = 0.02;                 // 2% average win
        this.avgLossSize = 0.015;               // 1.5% average loss
    }
    
    /**
     * Calculate maximum position size based on current risk settings.
     */
    public double calculateMaxPositionSize(double currentPrice, double stopLossPrice) {
        if (currentPrice <= 0 || stopLossPrice <= 0) {
            return 0;
        }
        
        double riskPerShare = Math.abs(currentPrice - stopLossPrice);
        if (riskPerShare <= 0) {
            return 0;
        }
        
        double maxRiskAmount = accountSize * maxRiskPerTrade;
        return maxRiskAmount / riskPerShare;
    }
    
    /**
     * Calculate expected value of a trade setup.
     * Positive EV means statistically profitable trade.
     */
    public double calculateExpectedValue(double winSize, double lossSize) {
        return (winRate * winSize) - ((1 - winRate) * lossSize);
    }
    
    /**
     * Check if current setup meets minimum requirements.
     */
    public boolean isSetupValid() {
        // Must have adequate probability
        if (probabilityLevel == ProbabilityLevel.VERY_LOW) {
            return false;
        }
        
        // Disciplined psychology or must have strict capital protection
        if (psychologyProfile == PsychologyProfile.IMPULSIVE || 
            psychologyProfile == PsychologyProfile.FEARFUL) {
            return capitalProtection == CapitalProtection.STRICT_STOPS;
        }
        
        // Must have reasonable liquidity for execution
        if (liquidityProfile == LiquidityProfile.ILLIQUID && 
            executionStrategy == ExecutionStrategy.MARKET_ORDER) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Calculate portfolio heat - total % of account at risk across all open positions.
     */
    public double calculatePortfolioHeat(double totalOpenPositionRisk) {
        return totalOpenPositionRisk / accountSize;
    }
    
    /**
     * Check if safe to enter new trade based on portfolio heat.
     */
    public boolean canEnterNewTrade(double currentPortfolioHeat) {
        return (currentPortfolioHeat + maxRiskPerTrade) <= maxCumulativeRisk;
    }
    
    /**
     * Get risk-adjusted position size multiplier based on market conditions.
     */
    public double getRiskMultiplier() {
        double multiplier = 1.0;
        
        // Market behavior adjustments
        switch (marketBehavior) {
            case TRENDING_UP, TRENDING_DOWN -> multiplier *= 1.0;
            case RANGING -> multiplier *= 0.8;
            case HIGH_VOLATILITY -> multiplier *= 0.6;
            case LOW_VOLATILITY -> multiplier *= 0.9;
            case BREAKOUT -> multiplier *= 1.1;
            case REVERSAL -> multiplier *= 0.5;
        }
        
        // Liquidity adjustments
        switch (liquidityProfile) {
            case DEEP_LIQUIDITY -> multiplier *= 1.0;
            case NORMAL_LIQUIDITY -> multiplier *= 0.9;
            case THIN_LIQUIDITY -> multiplier *= 0.6;
            case ILLIQUID -> multiplier *= 0.3;
        }
        
        // Psychology adjustments
        if (!psychologyProfile.isEmotionallyControlled()) {
            multiplier *= 0.5;  // Halve position size for uncontrolled psychology
        }
        
        return Math.max(multiplier, 0.1);  // Never drop below 10% of intended size
    }
    
    /**
     * Calculate required win rate for profitability given win/loss sizes.
     */
    public double calculateBreakevenWinRate(double avgWin, double avgLoss) {
        if (avgWin + avgLoss <= 0) return 0.5;
        return avgLoss / (avgWin + avgLoss);
    }
    
    /**
     * Generate risk assessment report.
     */
    public String generateRiskReport() {
        StringBuilder report = new StringBuilder();
        report.append("═════════════════════════════════════════════════════════\n");
        report.append("                 RISK MANAGEMENT REPORT\n");
        report.append("═════════════════════════════════════════════════════════\n\n");
        
        report.append("CONFIGURATION:\n");
        report.append("  Risk Profile: ").append(riskProfile.getDisplayName()).append("\n");
        report.append("  Market Behavior: ").append(marketBehavior.getDisplayName()).append("\n");
        report.append("  Execution Strategy: ").append(executionStrategy.getDisplayName()).append("\n");
        report.append("  Liquidity: ").append(liquidityProfile.getDisplayName()).append("\n");
        report.append("  Psychology: ").append(psychologyProfile.getDisplayName()).append("\n");
        report.append("  System Design: ").append(systemDesign.getDisplayName()).append("\n\n");
        
        report.append("PARAMETERS:\n");
        report.append("  Account Size: $").append(String.format("%.2f", accountSize)).append("\n");
        report.append("  Risk per Trade: ").append(String.format("%.2f%%", maxRiskPerTrade * 100)).append("\n");
        report.append("  Max Cumulative Risk: ").append(String.format("%.2f%%", maxCumulativeRisk * 100)).append("\n");
        report.append("  Expected Win Rate: ").append(String.format("%.2f%%", winRate * 100)).append("\n");
        report.append("  Risk Multiplier: ").append(String.format("%.2f", getRiskMultiplier())).append("\n\n");
        
        report.append("ANALYSIS:\n");
        report.append("  Setup Valid: ").append(isSetupValid() ? "YES ✓" : "NO ✗").append("\n");
        report.append("  Expected Value: ").append(String.format("%.4f", calculateExpectedValue(avgWinSize, avgLossSize))).append("\n");
        report.append("  Breakeven Win Rate: ").append(String.format("%.2f%%", calculateBreakevenWinRate(avgWinSize, avgLossSize) * 100)).append("\n");
        
        report.append("═════════════════════════════════════════════════════════\n");
        
        return report.toString();
    }
    
    // Getters and Setters
    public RiskProfile getRiskProfile() { return riskProfile; }
    public void setRiskProfile(RiskProfile riskProfile) { this.riskProfile = riskProfile; }
    
    public MarketBehavior getMarketBehavior() { return marketBehavior; }
    public void setMarketBehavior(MarketBehavior marketBehavior) { this.marketBehavior = marketBehavior; }
    
    public ExecutionStrategy getExecutionStrategy() { return executionStrategy; }
    public void setExecutionStrategy(ExecutionStrategy executionStrategy) { this.executionStrategy = executionStrategy; }
    
    public LiquidityProfile getLiquidityProfile() { return liquidityProfile; }
    public void setLiquidityProfile(LiquidityProfile liquidityProfile) { this.liquidityProfile = liquidityProfile; }
    
    public PsychologyProfile getPsychologyProfile() { return psychologyProfile; }
    public void setPsychologyProfile(PsychologyProfile psychologyProfile) { this.psychologyProfile = psychologyProfile; }
    
    public ProbabilityLevel getProbabilityLevel() { return probabilityLevel; }
    public void setProbabilityLevel(ProbabilityLevel probabilityLevel) { this.probabilityLevel = probabilityLevel; }
    
    public CapitalProtection getCapitalProtection() { return capitalProtection; }
    public void setCapitalProtection(CapitalProtection capitalProtection) { this.capitalProtection = capitalProtection; }
    
    public SystemDesign getSystemDesign() { return systemDesign; }
    public void setSystemDesign(SystemDesign systemDesign) { this.systemDesign = systemDesign; }
    
    public double getAccountSize() { return accountSize; }
    public void setAccountSize(double accountSize) { this.accountSize = accountSize; }
    
    public double getMaxRiskPerTrade() { return maxRiskPerTrade; }
    public void setMaxRiskPerTrade(double maxRiskPerTrade) { this.maxRiskPerTrade = maxRiskPerTrade; }
    
    public double getWinRate() { return winRate; }
    public void setWinRate(double winRate) { this.winRate = winRate; }
}
