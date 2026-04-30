package org.investpro.core.agents.reasoning;

import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.risk.RiskDecision;

import java.util.List;

/**
 * Performs optional AI reasoning after risk approval.
 *
 * AI never executes orders directly.
 */
public class ReasoningAgent implements Agent {

    private final OpenAIReasoningClient reasoningClient = new OpenAIReasoningClient();
    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "ReasoningAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null || !AgentEvent.RISK_APPROVED.equals(event.type())) {
            return;
        }

        if (!(event.payload() instanceof RiskDecision riskDecision)) {
            return;
        }

        ReasoningDecision decision;
        if (!context.isAiReasoningEnabled()) {
            decision = new ReasoningDecision(true, 0.65, "AI reasoning disabled; passing risk-approved setup forward.", List.of("Risk approved.", "AI reasoning disabled."), riskDecision);
        } else {
            decision = reasoningClient.review(riskDecision);
        }

        String eventType = decision.isApproved() ? AgentEvent.REASONING_APPROVED : AgentEvent.REASONING_REJECTED;
        context.getEventBus().publishAsync(AgentEvent.reasoning(eventType, name(), decision));
    }
}
