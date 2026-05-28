package org.investpro.reasoning;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.risk.RiskDecision;
import java.util.List;

/**
 * Performs optional AI reasoning after risk approval.
 * AI never executes orders directly.
 */
@Getter
@Setter
@Slf4j
public class ReasoningAgent implements Agent {

    private final OpenAIReasoningClient reasoningClient = new OpenAIReasoningClient();
    private AgentContext context;
    private boolean running;
    public ReasoningAgent() {
        log.debug("Constructing Agent");
    }


    @Override
    public String name() {
        return "ReasoningAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
        log.debug("Starting ReasoningAgent");
    }

    @Override
    public void stop() {
        running = false;
        log.debug("Stopping ReasoningAgent");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null || !AgentEvent.RISK_APPROVED.equals(event.type())) {
            log.debug("Received Risk Approved Event from ReasoningAgent");
            return;
        }

        if (!(event.payload() instanceof RiskDecision riskDecision)) {
            log.debug("Received Risk Decision Event from ReasoningAgent");
            return;
        }

        ReasoningDecision decision;
        if (!context.isAiReasoningEnabled()) {
            decision = new ReasoningDecision(true, 0.65, "AI reasoning disabled; passing risk-approved setup forward.", List.of("Risk approved.", "AI reasoning disabled."), riskDecision);
        } else {
            decision = reasoningClient.review(riskDecision);
        }
        log.debug("Sending ReasoningDecision to ReasoningAgent, {decision}");

        String eventType = decision.isApproved() ? AgentEvent.REASONING_APPROVED : AgentEvent.REASONING_REJECTED;
        context.getEventBus().publishAsync(AgentEvent.reasoning(eventType, name(), decision));
    }
}
