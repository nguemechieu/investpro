package org.investpro.ai;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Local deterministic position manager.
 * Provides fallback logic when OpenAI API is unavailable.
 * 
 * Uses simple, transparent rules:
 * - HOLD healthy positions
 * - TAKE_PARTIAL_PROFIT or TRAIL_STOP when profitable but momentum weakens
 * - REDUCE_SIZE when risk is elevated
 * - CLOSE_POSITION when deterministic risk says EXIT_REQUIRED
 * - ESCALATE_TO_MANUAL_REVIEW for uncertain cases
 */
@Slf4j
public class LocalAiPositionManagerService implements AiPositionManagerService {
    private static final Logger logger = log;


    private static final String MODEL_NAME = "local-fallback";
    private long totalProcessingTimeMs = 0;
    private long callCount = 0;

    @Override
    public AiPositionManagementResponse reviewOpenPosition(AiPositionManagementRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            if (!request.isValid()) {
                logger.warn("Invalid position request");
                return AiPositionManagementResponse.failedResponse("Invalid request", MODEL_NAME);
            }

            AiPositionManagementResponse response = determineAction(request);

            long processingTime = System.currentTimeMillis() - startTime;
            response.setProcessingTimeMs(processingTime);
            response.setCreatedAt(LocalDateTime.now());
            response.setModelName(MODEL_NAME);

            totalProcessingTimeMs += processingTime;
            callCount++;

            return response;

        } catch (Exception e) {
            logger.error("Position manager error: %s".formatted(e.getMessage()));
            return AiPositionManagementResponse.failedResponse(e.getMessage(), MODEL_NAME);
        }
    }

    /**
     * Determine recommended action using deterministic rules.
     */
    private AiPositionManagementResponse determineAction(AiPositionManagementRequest request) {
        List<String> confirmations = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        double pnlPercent = request.getUnrealizedPnlPercent();
        double portfolioHeat = request.getPortfolioHeat();
        double drawdown = request.getCurrentDrawdown();

        // =====================================================================
        // Rule 1: Check for critical exit conditions
        // =====================================================================

        if (drawdown > 5.0) {
            blockers.add("Account drawdown > 5%");
            concerns.add("High portfolio drawdown increases risk");
            return buildResponse(AiPositionAction.CLOSE_POSITION, 0.95, blockers, concerns,
                    confirmations, recommendations, "Critical: Account drawdown requires position closure");
        }

        if (portfolioHeat > 7.0) {
            blockers.add("Portfolio heat > 7%");
            concerns.add("Overall portfolio risk is too high");
            return buildResponse(AiPositionAction.CLOSE_POSITION, 0.9, blockers, concerns,
                    confirmations, recommendations, "Critical: Portfolio heat too high, close position");
        }

        if (request.getCurrentStopLoss() != null &&
                Math.abs(request.getCurrentPrice() - request.getCurrentStopLoss()) < 50) {
            blockers.add("Stop loss too close (< 50 bps)");
            concerns.add("Position is at critical stop distance");
            recommendations.add("Consider closing to avoid stop-out");
            return buildResponse(AiPositionAction.ESCALATE_TO_MANUAL_REVIEW, 0.7, blockers, concerns,
                    confirmations, recommendations, "Stop loss is too close, escalate for manual decision");
        }

        // =====================================================================
        // Rule 2: Large losses - prioritize capital preservation
        // =====================================================================

        if (pnlPercent < -5.0) {
            concerns.add("Position is in significant drawdown (> -5%)");
            concerns.add("Thesis may be invalidated");
            recommendations.add("Consider reducing position size to preserve capital");
            recommendations.add("Close if stop loss is tight");

            if (request.getCurrentStopLoss() == null) {
                recommendations.add("Set a stop loss immediately");
            }

            return buildResponse(AiPositionAction.REDUCE_SIZE, 0.75, blockers, concerns,
                    confirmations, recommendations,
                    "Large loss: reduce size to protect remaining capital");
        }

        // =====================================================================
        // Rule 3: Highly profitable - protect gains
        // =====================================================================

        if (pnlPercent > 10.0) {
            confirmations.add("Position is highly profitable (> 10%)");
            confirmations.add("Profit protection is key");
            recommendations.add("Implement trailing stop to protect gains");
            recommendations.add("Consider partial profit taking");

            if (request.getCurrentTakeProfit() != null) {
                confirmations.add("Take profit level is set");
                return buildResponse(AiPositionAction.TRAIL_STOP, 0.85, blockers, concerns,
                        confirmations, recommendations,
                        "Highly profitable: tighten trailing stop to protect gains");
            } else {
                recommendations.add("Set or adjust take profit target");
                return buildResponse(AiPositionAction.TRAIL_STOP, 0.8, blockers, concerns,
                        confirmations, recommendations,
                        "Highly profitable: implement trailing stop");
            }
        }

        // =====================================================================
        // Rule 4: Moderately profitable - consider profit taking or trailing
        // =====================================================================

        if (pnlPercent > 3.0) {
            confirmations.add("Position is in profit");

            if (request.getVolatility() > 60) {
                concerns.add("Volatility is elevated");
                recommendations.add("Consider taking partial profit to reduce volatility risk");

                double suggestedCloseQuantity = request.getQuantity() * 0.3; // Close 30%
                return buildResponse(AiPositionAction.TAKE_PARTIAL_PROFIT, 0.75, blockers, concerns,
                        confirmations, recommendations,
                        "Profit taking recommended to reduce risk in volatile market",
                        suggestedCloseQuantity, 0, 0);
            } else {
                confirmations.add("Volatility is reasonable");
                recommendations.add("Implement trailing stop to lock in gains");
                return buildResponse(AiPositionAction.TRAIL_STOP, 0.8, blockers, concerns,
                        confirmations, recommendations, "Profitable: trail stop to protect gains");
            }
        }

        // =====================================================================
        // Rule 5: Small profit - monitor closely
        // =====================================================================

        if (pnlPercent > 0.5) {
            confirmations.add("Position is slightly profitable");
            recommendations.add("Continue monitoring");

            if (request.getCurrentStopLoss() == null) {
                recommendations.add("Set a stop loss to protect small gains");
            }

            return buildResponse(AiPositionAction.HOLD, 0.7, blockers, concerns,
                    confirmations, recommendations, "Small profit: hold and monitor");
        }

        // =====================================================================
        // Rule 6: Break-even - monitor
        // =====================================================================

        if (Math.abs(pnlPercent) <= 0.5) {
            confirmations.add("Position is near break-even");
            recommendations.add("Continue monitoring for clear signal");

            if (request.getCurrentStopLoss() != null) {
                confirmations.add("Stop loss is set for protection");
            }

            return buildResponse(AiPositionAction.HOLD, 0.6, blockers, concerns,
                    confirmations, recommendations, "Break-even: hold and wait for signal");
        }

        // =====================================================================
        // Rule 7: Small loss - monitor
        // =====================================================================

        if (pnlPercent < 0 && pnlPercent >= -2.0) {
            concerns.add("Position is underwater");
            recommendations.add("Monitor for recovery or exit signal");

            if (request.getCurrentStopLoss() != null) {
                confirmations.add("Stop loss is set");
            } else {
                concerns.add("No stop loss set to limit downside");
                recommendations.add("Set a stop loss immediately");
            }

            return buildResponse(AiPositionAction.HOLD, 0.65, blockers, concerns,
                    confirmations, recommendations, "Small loss: monitor, ensure stop is set");
        }

        // =====================================================================
        // Fallback: No specific signal
        // =====================================================================

        confirmations.add("Position metrics are within normal ranges");
        recommendations.add("Continue monitoring position");
        recommendations.add("Follow original trade thesis");

        return buildResponse(AiPositionAction.HOLD, 0.75, blockers, concerns,
                confirmations, recommendations, "No specific action indicated, continue monitoring");
    }

    // =========================================================================
    // Response Building Helpers
    // =========================================================================

    private AiPositionManagementResponse buildResponse(
            AiPositionAction action,
            double confidence,
            List<String> blockers,
            List<String> concerns,
            List<String> confirmations,
            List<String> recommendations,
            String explanation) {

        return buildResponse(action, confidence, blockers, concerns, confirmations, recommendations,
                explanation, 0.0, 0.0, 0.0);
    }

    private AiPositionManagementResponse buildResponse(
            AiPositionAction action,
            double confidence,
            List<String> blockers,
            List<String> concerns,
            List<String> confirmations,
            List<String> recommendations,
            String explanation,
            double suggestedCloseQuantity,
            double suggestedStopLoss,
            double suggestedTakeProfit) {

        return AiPositionManagementResponse.builder()
                .action(action)
                .confidence(confidence)
                .suggestedCloseQuantity(suggestedCloseQuantity != 0.0 ? suggestedCloseQuantity : 0.0)
                .suggestedStopLoss(suggestedStopLoss)
                .suggestedTakeProfit(suggestedTakeProfit)
                .blockers(blockers)
                .concerns(concerns)
                .confirmations(confirmations)
                .recommendations(recommendations)
                .explanation(explanation)
                .hadErrors(false)
                .build();
    }

    @Override
    public String getModelName() {
        return MODEL_NAME;
    }

    @Override
    public boolean isAvailable() {
        return true; // Always available locally
    }

    @Override
    public long getAverageProcessingTimeMs() {
        if (callCount == 0)
            return 0;
        return totalProcessingTimeMs / callCount;
    }
}
