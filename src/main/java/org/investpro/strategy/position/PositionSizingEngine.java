package org.investpro.strategy.position;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.lifecycle.StrategyLearningProfile;

/**
 * Calculates position sizes using 7 different sizing methods.
 * All calculations apply an AI-derived size multiplier and enforce
 * a maximum position size cap as a percentage of account equity.
 *
 * <p>
 * <strong>CRITICAL:</strong> This engine computes advisory position sizes only.
 * It NEVER places orders or directly interacts with any exchange.
 * The RiskEngine reviews and may further reduce the resulting size.
 * </p>
 */
@Slf4j
public class PositionSizingEngine {

    private static volatile PositionSizingEngine instance;

    private PositionSizingEngine() {
        log.info("PositionSizingEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton PositionSizingEngine
     */
    public static PositionSizingEngine getInstance() {
        PositionSizingEngine local = instance;
        if (local == null) {
            synchronized (PositionSizingEngine.class) {
                local = instance;
                if (local == null) {
                    local = new PositionSizingEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Calculates a position size given the sizing request.
     *
     * @param request the sizing parameters
     * @return a PositionSizeResult containing the computed size and metadata
     * @throws IllegalArgumentException if request is invalid
     */
    public PositionSizeResult calculateSize(PositionSizeRequest request) {
        validateRequest(request);

        double rawUnits = switch (request.getMethod()) {
            case RISK_PERCENT, FIXED_RISK_PERCENT -> calcRiskPercent(request);
            case FIXED_LOT, FIXED_UNITS -> calcFixedLot(request);
            case KELLY_CRITERION -> calcKelly(request);
            case ATR_BASED -> calcAtrBased(request);
            case VOLATILITY_ADJUSTED, VOLATILITY_BASED -> calcVolatilityAdjusted(request);
            case EQUAL_WEIGHT, RISK_PARITY -> calcEqualWeight(request);
            case MAX_LOSS, DRAWDOWN_SCALING -> calcMaxLoss(request);
        };

        // Apply AI multiplier (confidence-derived) — clamped to [0.25, 1.50]
        double aiMultiplier = clamp(request.getAiSizeMultiplier(), 0.25, 1.50);
        double adjustedUnits = rawUnits * aiMultiplier;

        // Enforce maximum position size cap
        double entryPrice = Math.max(request.getEntryPrice(), 1.0);
        double maxUnits = (request.getEquity() * request.getMaxPositionSizePercent()) / entryPrice;
        boolean capped = adjustedUnits > maxUnits;
        double finalUnits = Math.min(adjustedUnits, maxUnits);
        finalUnits = Math.max(finalUnits, 0.0);

        double pipValue = request.getPipValue() > 0 ? request.getPipValue() : 1.0;
        double slDistance = request.getStopLossDistance() > 0 ? request.getStopLossDistance() : 0.0001;
        double riskAmount = finalUnits * slDistance * pipValue;
        double riskPercent = request.getEquity() > 0 ? riskAmount / request.getEquity() : 0.0;
        double notionalValue = finalUnits * entryPrice;

        boolean valid = finalUnits > 0
                && riskPercent <= request.getMaxPositionSizePercent();
        String validationReason = !valid
                ? (finalUnits <= 0 ? "Computed size is zero or negative"
                        : "Risk " + String.format("%.2f%%", riskPercent * 100)
                                + " exceeds maximum "
                                + String.format("%.2f%%", request.getMaxPositionSizePercent() * 100))
                : "OK";

        log.debug("Position size: assignment={} method={} raw={} ai={} final={}",
                request.getAssignmentId(), request.getMethod(),
                String.format("%.4f", rawUnits),
                String.format("%.4f", adjustedUnits),
                String.format("%.4f", finalUnits));

        return PositionSizeResult.builder()
                .requestId(request.getRequestId())
                .assignmentId(request.getAssignmentId())
                .strategyId(request.getStrategyId())
                .symbol(request.getSymbol())
                .method(request.getMethod())
                .positionUnits(finalUnits)
                .notionalValue(notionalValue)
                .riskAmount(riskAmount)
                .riskPercent(riskPercent)
                .rawUnits(rawUnits)
                .aiMultiplierApplied(aiMultiplier)
                .cappedByMax(capped)
                .maxAllowedUnits(maxUnits)
                .valid(valid)
                .validationReason(validationReason)
                .build();
    }

    // =========================================================================
    // Sizing method implementations
    // =========================================================================

    /** Fixed percentage of equity at risk per trade. */
    private double calcRiskPercent(PositionSizeRequest req) {
        double riskAmount = req.getEquity() * req.getRiskPerTradePercent();
        double pipRisk = req.getStopLossDistance() * Math.max(req.getPipValue(), 1.0);
        return pipRisk > 0 ? riskAmount / pipRisk : 0.0;
    }

    /** Fixed lot size regardless of equity. */
    private double calcFixedLot(PositionSizeRequest req) {
        return req.getLotSize() > 0 ? req.getLotSize() : 1.0;
    }

    /** Kelly Criterion — optimises for geometric growth. */
    private double calcKelly(PositionSizeRequest req) {
        StrategyLearningProfile profile = req.getLearningProfile();
        double winRate = profile != null ? profile.getAvgWinConfidence() : 0.50;
        double equity = req.getEquity();
        double avgWin = equity * 0.02; // fallback 2%
        double avgLoss = equity * 0.01; // fallback 1%
        double b = avgLoss > 0 ? avgWin / avgLoss : 2.0;
        double kellFraction = (b * winRate - (1.0 - winRate)) / b;
        kellFraction = clamp(kellFraction, 0.0, 0.25); // cap full Kelly at 25%
        kellFraction *= 0.5; // half-Kelly for safety
        double riskAmount = equity * kellFraction;
        double pipRisk = req.getStopLossDistance() * Math.max(req.getPipValue(), 1.0);
        return pipRisk > 0 ? riskAmount / pipRisk : 0.0;
    }

    /** ATR-based sizing: adjusts for market volatility. */
    private double calcAtrBased(PositionSizeRequest req) {
        double atr = req.getAtr() > 0 ? req.getAtr() : req.getStopLossDistance();
        double riskAmount = req.getEquity() * req.getRiskPerTradePercent();
        double atrRisk = atr * Math.max(req.getPipValue(), 1.0);
        return atrRisk > 0 ? riskAmount / atrRisk : 0.0;
    }

    /** Volatility-adjusted: inversely proportional to current volatility. */
    private double calcVolatilityAdjusted(PositionSizeRequest req) {
        double vol = req.getVolatility() > 0 ? req.getVolatility() : 0.01;
        double baseSize = calcRiskPercent(req);
        double volAdjustment = 0.01 / vol; // normalised to 1% vol
        return baseSize * clamp(volAdjustment, 0.25, 2.0);
    }

    /** Equal weight: divide equity equally across N positions. */
    private double calcEqualWeight(PositionSizeRequest req) {
        int positions = req.getMaxOpenPositions() > 0 ? req.getMaxOpenPositions() : 5;
        double allocation = req.getEquity() / positions;
        return req.getEntryPrice() > 0 ? allocation / req.getEntryPrice() : 0.0;
    }

    /** Maximum loss: size based on absolute maximum dollar loss allowed. */
    private double calcMaxLoss(PositionSizeRequest req) {
        double maxLoss = req.getMaxDollarLoss() > 0 ? req.getMaxDollarLoss()
                : req.getEquity() * req.getRiskPerTradePercent();
        double pipRisk = req.getStopLossDistance() * Math.max(req.getPipValue(), 1.0);
        return pipRisk > 0 ? maxLoss / pipRisk : 0.0;
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private void validateRequest(PositionSizeRequest request) {
        if (request == null)
            throw new IllegalArgumentException("PositionSizeRequest must not be null");
        if (request.getEquity() <= 0)
            throw new IllegalArgumentException("Equity must be positive");
        if (request.getMethod() == null)
            throw new IllegalArgumentException("Sizing method must not be null");
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
