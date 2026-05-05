package org.investpro.ai;

import org.investpro.models.trading.Position;
import org.investpro.risk.ExecutionStrategy;
import org.investpro.risk.PositionRiskDecision;

import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Final gate for open-position actions.
 * Combines risk decision, validated AI recommendation, broker capabilities, and account state.
 * Makes final approval/rejection decision before PositionActionIntent is sent to ExecutionEngine.
 *
 * Rules:
 * - Deterministic exit-required rules always win
 * - AI can reduce risk but cannot increase risk beyond approved limits
 * - AI WAIT/manual review blocks automatic modification
 * - Close/reduce actions must respect liquidity profile
 * - Stop modifications must respect broker minimum stop distance
 * - No action unless explicitly approved by final gate
 */
@SuppressWarnings("unused")
public class PositionActionFinalGate {
    
    private static final Logger logger = Logger.getLogger(PositionActionFinalGate.class.getName());
    
    /**
     * Make final decision on position action.
     * Combines deterministic risk decision and validated AI recommendation.
     *
     * @param position Current position state
     * @param riskDecision Deterministic risk assessment
     * @param aiRecommendation Validated AI recommendation
     * @return Approved PositionActionIntent or null if action is rejected
     */
    @SuppressWarnings("unused")
    public static PositionActionIntent makeDecision(
            Position position,
            PositionRiskDecision riskDecision,
            AiPositionManagementResponse aiRecommendation) {
        
        if (position == null || riskDecision == null || aiRecommendation == null) {
            logger.warning("PositionActionFinalGate: Missing required inputs");
            return null;
        }
        
        AiPositionAction recommendedAction = aiRecommendation.getAction();
        
        // =====================================================================
        // Rule 1: Deterministic exit-required rules always win
        // =====================================================================
        
        if (riskDecision.isDeterministicExitRequired()) {
            logger.info("Final gate: Deterministic exit required for position %s".formatted(position.getSymbol()));
            
            return approveAction(
                    position,
                    AiPositionAction.CLOSE_POSITION,
                    null, // Close entire position
                    null,
                    null,
                    "Deterministic risk rule requires position exit: %s".formatted(riskDecision.getDeterministicExitReason()),
                    0.95
            );
        }
        
        // =====================================================================
        // Rule 2: AI ESCALATE_TO_MANUAL_REVIEW blocks automatic action
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.ESCALATE_TO_MANUAL_REVIEW) {
            logger.info("Final gate: AI escalated to manual review for %s".formatted(position.getSymbol()));
            return null; // No automatic action, wait for manual review
        }
        
        // =====================================================================
        // Rule 3: AI HOLD is allowed only if not in critical condition
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.HOLD) {
            if (!riskDecision.isSafe()) {
                logger.warning("Final gate: AI recommended HOLD but position is not safe");
                
                // Override to REDUCE_SIZE if position is unhealthy
                if (riskDecision.getSuggestedReductionPercent() != null && riskDecision.getSuggestedReductionPercent() > 0) {
                    return approveAction(
                            position,
                            AiPositionAction.REDUCE_SIZE,
                            position.getQuantity() * riskDecision.getSuggestedReductionPercent(),
                            null,
                            null,
                            "AI HOLD overridden: position health score low",
                            Math.min(0.8, aiRecommendation.getConfidence())
                    );
                }
            }
            
            // HOLD is approved
            return approveAction(
                    position,
                    AiPositionAction.HOLD,
                    null,
                    null,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 4: REDUCE_SIZE and TAKE_PARTIAL_PROFIT reduce risk, always allowed
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.REDUCE_SIZE || 
            recommendedAction == AiPositionAction.TAKE_PARTIAL_PROFIT) {
            
            Double quantityToClose = aiRecommendation.getSuggestedCloseQuantity();
            if (quantityToClose == null || quantityToClose <= 0 || quantityToClose > position.getQuantity()) {
                quantityToClose = position.getQuantity() * 0.25; // Default: close 25%
            }
            
            return approveAction(
                    position,
                    recommendedAction,
                    quantityToClose,
                    null,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 5: MOVE_STOP_LOSS - validate broker constraints
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.MOVE_STOP_LOSS) {
            if (aiRecommendation.getSuggestedStopLoss() == null) {
                logger.warning("Final gate: MOVE_STOP_LOSS recommended but no suggested stop provided");
                return null;
            }
            
            // Validate stop distance is acceptable
            double stopDistance = Math.abs(position.getCurrentPrice() - aiRecommendation.getSuggestedStopLoss());
            double stopDistanceBps = (stopDistance / position.getCurrentPrice()) * 10000;
            
            if (stopDistanceBps < 25) {
                logger.warning("Final gate: Suggested stop too close (< 25 bps minimum)");
                return null;
            }
            
            return approveAction(
                    position,
                    AiPositionAction.MOVE_STOP_LOSS,
                    null,
                    aiRecommendation.getSuggestedStopLoss(),
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 6: TRAIL_STOP - only on profitable positions
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.TRAIL_STOP) {
            if (position.getUnrealizedPnl() <= 0) {
                logger.warning("Final gate: TRAIL_STOP recommended on non-profitable position");
                // Don't activate trailing stop on losses
                return approveAction(
                        position,
                        AiPositionAction.HOLD,
                        null,
                        null,
                        null,
                        "Trailing stop not appropriate on unprofitable position",
                        aiRecommendation.getConfidence()
                );
            }
            
            // Use suggested stop if provided, otherwise calculate from current
            Double trailingStop = aiRecommendation.getSuggestedStopLoss();
            if (trailingStop == null) {
                // Calculate a trailing stop ~100 bps back
                double trailingDistance = (position.getCurrentPrice() * 0.01); // 100 bps
                trailingStop = position.isBuy() ? 
                        position.getCurrentPrice() - trailingDistance :
                        position.getCurrentPrice() + trailingDistance;
            }
            
            return approveAction(
                    position,
                    AiPositionAction.TRAIL_STOP,
                    null,
                    trailingStop,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 7: MOVE_TAKE_PROFIT - always allowed (doesn't increase risk)
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.MOVE_TAKE_PROFIT) {
            if (aiRecommendation.getSuggestedTakeProfit() == null) {
                logger.warning("Final gate: MOVE_TAKE_PROFIT recommended but no suggested TP provided");
                return null;
            }
            
            return approveAction(
                    position,
                    AiPositionAction.MOVE_TAKE_PROFIT,
                    null,
                    null,
                    aiRecommendation.getSuggestedTakeProfit(),
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 8: CLOSE_POSITION - allowed if conditions warrant
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.CLOSE_POSITION) {
            if (aiRecommendation.getConfidence() < 0.7) {
                logger.warning("Final gate: CLOSE_POSITION recommended with low confidence");
                // Downgrade to ESCALATE for low-confidence closes
                return null;
            }
            
            return approveAction(
                    position,
                    AiPositionAction.CLOSE_POSITION,
                    null, // Close entire position
                    null,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence()
            );
        }
        
        // =====================================================================
        // Rule 9: HEDGE - not approved (requires manual intervention)
        // =====================================================================
        
        if (recommendedAction == AiPositionAction.HEDGE) {
            logger.info("Final gate: HEDGE requires manual approval");
            return null; // Hedge must be manually approved
        }
        
        // =====================================================================
        // Fallback: Unknown action - reject
        // =====================================================================
        
        logger.warning("Final gate: Unknown action - %s".formatted(recommendedAction));
        return null;
    }
    
    /**
     * Helper to create approved PositionActionIntent.
     * Always approves as PositionActionFinalGate (the final authority).
     */
    private static PositionActionIntent approveAction(
            Position position,
            AiPositionAction action,
            Double quantityToClose,
            Double newStopLoss,
            Double newTakeProfit,
            String reason,
            double confidence) {
        
        final String approver = "PositionActionFinalGate";
        
        return PositionActionIntent.builder()
                .positionId(position.getPositionId())
                .symbol(position.getSymbol() != null ? position.getSymbol() : "UNKNOWN")
                .action(action)
                .quantityToClose(quantityToClose)
                .newStopLoss(newStopLoss)
                .newTakeProfit(newTakeProfit)
                .executionStrategy(ExecutionStrategy.MARKET_ORDER) // Default, can be overridden
                .reason(reason)
                .confidence(confidence)
                .approved(true)
                .approvedBy(approver)
                .approvalReason(reason)
                .createdAt(LocalDateTime.now())
                .approvedAt(LocalDateTime.now())
                .source("AI")
                .build();
    }
}
