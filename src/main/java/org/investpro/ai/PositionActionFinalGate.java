package org.investpro.ai;

import org.investpro.enums.ExecutionStrategy;
import org.investpro.models.trading.Position;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;

/**
 * Final gate for open-position actions.
 */
@Slf4j
public class PositionActionFinalGate {

    /**
     * Make final decision on position action.
     */

    public static @Nullable PositionActionIntent makeDecision(
            Position position,
            AiPositionManagementResponse aiRecommendation) {

        if (position == null || aiRecommendation == null) {
            log.warn("PositionActionFinalGate: Missing required inputs");
            return null;
        }

        AiPositionAction recommendedAction = aiRecommendation.getAction();

        if (recommendedAction == AiPositionAction.ESCALATE_TO_MANUAL_REVIEW) {
            log.info("Final gate: AI escalated to manual review");
            return null;
        }

        if (recommendedAction == AiPositionAction.HOLD) {
            return approveAction(
                    position,
                    AiPositionAction.HOLD,
                    0.0,
                    0.0,
                    0.0,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.REDUCE_SIZE ||
                recommendedAction == AiPositionAction.TAKE_PARTIAL_PROFIT) {

            Double quantityToClose = aiRecommendation.getSuggestedCloseQuantity();
            if (quantityToClose == null || quantityToClose <= 0 || quantityToClose > position.getQuantity()) {
                quantityToClose = position.getQuantity() * 0.25;
            }

            return approveAction(
                    position,
                    recommendedAction,
                    quantityToClose,
                    null,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.MOVE_STOP_LOSS) {
            if (aiRecommendation.getSuggestedStopLoss() == null) {
                log.warn("Final gate: MOVE_STOP_LOSS recommended but no suggested stop provided");
                return null;
            }

            return approveAction(
                    position,
                    AiPositionAction.MOVE_STOP_LOSS,
                    null,
                    aiRecommendation.getSuggestedStopLoss(),
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.TRAIL_STOP) {
            if (position.getUnrealizedPnl() <= 0) {
                log.warn("Final gate: TRAIL_STOP recommended on non-profitable position");
                return approveAction(
                        position,
                        AiPositionAction.HOLD,
                        null,
                        null,
                        null,
                        "Trailing stop not appropriate on unprofitable position",
                        aiRecommendation.getConfidence());
            }

            Double trailingStop = aiRecommendation.getSuggestedStopLoss();
            if (trailingStop == null) {
                double trailingDistance = (position.getCurrentPrice() * 0.01);
                trailingStop = position.isBuy() ? position.getCurrentPrice() - trailingDistance
                        : position.getCurrentPrice() + trailingDistance;
            }

            return approveAction(
                    position,
                    AiPositionAction.TRAIL_STOP,
                    null,
                    trailingStop,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.MOVE_TAKE_PROFIT) {
            if (aiRecommendation.getSuggestedTakeProfit() == null) {
                log.warn("Final gate: MOVE_TAKE_PROFIT recommended but no suggested TP provided");
                return null;
            }

            return approveAction(
                    position,
                    AiPositionAction.MOVE_TAKE_PROFIT,
                    null,
                    null,
                    aiRecommendation.getSuggestedTakeProfit(),
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.CLOSE_POSITION) {
            if (aiRecommendation.getConfidence() < 0.7) {
                log.warn("Final gate: CLOSE_POSITION recommended with low confidence");
                return null;
            }

            return approveAction(
                    position,
                    AiPositionAction.CLOSE_POSITION,
                    null,
                    null,
                    null,
                    aiRecommendation.getExplanation(),
                    aiRecommendation.getConfidence());
        }

        if (recommendedAction == AiPositionAction.HEDGE) {
            log.info("Final gate: HEDGE requires manual approval");
            return null;
        }

        log.warn("Final gate: Unknown action");
        return null;
    }

    /**
     * Helper to create approved PositionActionIntent.
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
                .symbol(position.getTradePair())
                .action(action)
                .quantityToClose(quantityToClose)
                .newStopLoss(newStopLoss)
                .newTakeProfit(newTakeProfit)
                .executionStrategy(ExecutionStrategy.MARKET_ORDER)
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
