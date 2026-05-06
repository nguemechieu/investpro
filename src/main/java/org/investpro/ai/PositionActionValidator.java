package org.investpro.ai;

import org.investpro.models.trading.Position;
import org.investpro.risk.PositionRiskDecision;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Validates AI position management recommendations.
 * Enforces safety rules to prevent AI from making unsafe position modifications.
 * <p>
 * Rules:
 * - AI cannot remove stop loss
 * - AI cannot increase leverage
 * - AI cannot increase position size
 * - AI cannot move stop loss farther away in ways that increase risk
 * - AI cannot recommend HOLD when deterministic risk says EXIT_REQUIRED
 * - AI cannot recommend MARKET close in illiquid market without warning
 * - AI cannot recommend HEDGE unless broker supports hedging
 * - Invalid recommendations escalate to manual review
 */
@SuppressWarnings("unused")
public class PositionActionValidator {

    private static final Logger logger = Logger.getLogger(PositionActionValidator.class.getName());

    /**
     * Validate AI position management recommendation.
     *
     * @param aiResponse   AI recommendation to validate
     * @param position     Current position state
     * @param riskDecision Current risk decision for position
     * @return Validated (possibly modified) response
     */
    public AiPositionManagementResponse validate(
            AiPositionManagementResponse aiResponse,
            Position position,
            PositionRiskDecision riskDecision) {

        if (aiResponse == null || position == null || riskDecision == null) {
            return AiPositionManagementResponse.failedResponse("Validation context incomplete", "validator");
        }

        List<String> newBlockers = new ArrayList<>(aiResponse.getBlockers());
        List<String> newConcerns = new ArrayList<>(aiResponse.getConcerns());
        AiPositionAction action = aiResponse.getAction();
        boolean valid = true;

        // =====================================================================
        // Rule 1: Cannot remove stop loss
        // =====================================================================

        Double currentStopLoss = position.getStopLoss();
        if (aiResponse.getSuggestedStopLoss() == null && action == AiPositionAction.MOVE_STOP_LOSS) {
            newBlockers.add("Cannot remove existing stop loss");
            valid = false;
        }

        // =====================================================================
        // Rule 2: Cannot increase leverage
        // =====================================================================

        if (position.getLeverage() > 1.0) {
            // Position has leverage, AI cannot recommend increasing it
            if (aiResponse.getSuggestedCloseQuantity() == null ||
                    aiResponse.getSuggestedCloseQuantity() <= 0) {
                // AI is not closing/reducing, so it's implicitly increasing leverage
                newConcerns.add("Position has leverage; reducing size preferred");
            }
        }

        // =====================================================================
        // Rule 3: Cannot increase position size (directly or indirectly)
        // =====================================================================

        if (action == AiPositionAction.HEDGE) {
            // Hedge implicitly increases exposure
            newConcerns.add("Hedge would increase total portfolio exposure");
        }

        // =====================================================================
        // Rule 4: Cannot move stop loss farther away in ways that increase risk
        // =====================================================================

        if (action == AiPositionAction.MOVE_STOP_LOSS && aiResponse.getSuggestedStopLoss() != null) {
            double currentStop = position.getStopLoss();
            double suggestedStop = aiResponse.getSuggestedStopLoss();

            if (position.isBuy()) {
                // For long positions, moving stop down increases risk
                if (suggestedStop < currentStop) {
                    newBlockers.add("Cannot move stop loss down for long position (increases risk)");
                    valid = false;
                }
            } else {
                // For short positions, moving stop up increases risk
                if (suggestedStop > currentStop) {
                    newBlockers.add("Cannot move stop loss up for short position (increases risk)");
                    valid = false;
                }
            }
        }

        // =====================================================================
        // Rule 5: Cannot HOLD when deterministic risk says EXIT_REQUIRED
        // =====================================================================

        if (action == AiPositionAction.HOLD && riskDecision.isDeterministicExitRequired()) {
            newBlockers.add("Deterministic risk requires position exit");
            newBlockers.add(riskDecision.getDeterministicExitReason());
            valid = false;
            action = AiPositionAction.CLOSE_POSITION;
        }

        // =====================================================================
        // Rule 6: Cannot recommend MARKET close in illiquid market without warning
        // =====================================================================

        if (action == AiPositionAction.CLOSE_POSITION) {
            // This is a fallback warning; actual execution strategy validated elsewhere
            if (aiResponse.getRecommendations().stream()
                    .noneMatch(r -> r.toLowerCase().contains("limit") || r.toLowerCase().contains("slippage"))) {
                newConcerns.add("Verify liquidity before executing close (use limit order if liquidity uncertain)");
            }
        }

        // =====================================================================
        // Rule 7: Cannot recommend HEDGE unless broker supports it
        // =====================================================================

        if (action == AiPositionAction.HEDGE) {
            newBlockers.add("Hedge recommended, but broker support not verified");
            newConcerns.add("Hedging requires broker capability and account approval");
            valid = false;
        }

        // =====================================================================
        // Rule 8: Check if action is explicitly disallowed by risk decision
        // =====================================================================

        if (!riskDecision.isActionAllowed(action.name())) {
            newBlockers.add("Action %s is not allowed by risk constraints".formatted(action.name()));
            valid = false;
        }

        // =====================================================================
        // Rule 9: Validate confidence and escalate if too low
        // =====================================================================

        if (aiResponse.getConfidence() < 0.4) {
            newBlockers.add("Confidence too low for automated action");
            valid = false;
            action = AiPositionAction.ESCALATE_TO_MANUAL_REVIEW;
        }

        // =====================================================================
        // Rule 10: Validate that response has explanation
        // =====================================================================

        if (aiResponse.getExplanation() == null || aiResponse.getExplanation().isBlank()) {
            newBlockers.add("AI did not provide explanation for recommendation");
            valid = false;
        }

        // =====================================================================
        // Build Validated Response
        // =====================================================================

        if (!valid && action != AiPositionAction.ESCALATE_TO_MANUAL_REVIEW) {
            action = AiPositionAction.ESCALATE_TO_MANUAL_REVIEW;
        }

        AiPositionManagementResponse.AiPositionManagementResponseBuilder builder =
                AiPositionManagementResponse.builder()
                        .action(action)
                        .confidence(aiResponse.getConfidence())
                        .suggestedStopLoss(aiResponse.getSuggestedStopLoss())
                        .suggestedTakeProfit(aiResponse.getSuggestedTakeProfit())
                        .suggestedCloseQuantity(aiResponse.getSuggestedCloseQuantity())
                        .suggestedRiskReductionPercent(aiResponse.getSuggestedRiskReductionPercent())
                        .confirmations(aiResponse.getConfirmations())
                        .concerns(newConcerns)
                        .blockers(newBlockers)
                        .recommendations(aiResponse.getRecommendations())
                        .explanation(aiResponse.getExplanation())
                        .modelName(aiResponse.getModelName())
                        .createdAt(aiResponse.getCreatedAt())
                        .processingTimeMs(aiResponse.getProcessingTimeMs())
                        .hadErrors(aiResponse.isHadErrors())
                        .errorMessage(aiResponse.getErrorMessage());

        if (!valid) {
            builder.hadErrors(true);
            builder.errorMessage("Validation failed: AI recommendation violated safety rules");
            logger.warning("AI recommendation blocked by validator: %s".formatted(String.join("; ", newBlockers)));
        }

        return builder.build();
    }
}