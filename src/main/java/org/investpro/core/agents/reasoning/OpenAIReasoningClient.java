package org.investpro.core.agents.reasoning;

import org.investpro.core.agents.risk.RiskDecision;

import java.util.List;

/**
 * Placeholder client for future OpenAI integration.
 *
 * This class is intentionally deterministic and does not require an API key.
 */
public class OpenAIReasoningClient {

    public ReasoningDecision review(RiskDecision riskDecision) {
        if (riskDecision == null) {
            return new ReasoningDecision(false, 0.0, "No risk decision was available.", List.of("Missing risk decision."), null);
        }

        if (!riskDecision.isApproved()) {
            return new ReasoningDecision(false, 0.0, "Risk rejected the setup.", riskDecision.getReasons(), riskDecision);
        }

        return new ReasoningDecision(
                true,
                0.70,
                "Local reasoning approved this risk-approved setup. External AI integration can be connected later.",
                List.of("Risk decision approved.", "No external AI key required for local fallback."),
                riskDecision
        );
    }
}
