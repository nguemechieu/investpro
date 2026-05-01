package org.investpro.core.agents.reasoning;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.risk.RiskDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Performs optional AI reasoning after risk approval.
 * AI never executes orders directly.
 */
@Getter
@Setter
public class ReasoningAgent implements Agent {

    private final OpenAIReasoningClient reasoningClient = new OpenAIReasoningClient();
    private AgentContext context;
    private boolean running;
    private  static final Logger logger= LoggerFactory.getLogger(ReasoningAgent.class.getName());
    public ReasoningAgent() {
        logger.debug("Constructing Agent");
    }


    @Override
    public String name() {
        return "ReasoningAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
        logger.debug("Starting ReasoningAgent");
    }

    @Override
    public void stop() {
        running = false;
        logger.debug("Stopping ReasoningAgent");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null || !AgentEvent.RISK_APPROVED.equals(event.type())) {
            logger.debug("Received Risk Approved Event from ReasoningAgent");
            return;
        }

        if (!(event.payload() instanceof RiskDecision riskDecision)) {
            logger.debug("Received Risk Decision Event from ReasoningAgent");
            return;
        }

        ReasoningDecision decision;
        if (!context.isAiReasoningEnabled()) {
            decision = new ReasoningDecision(true, 0.65, "AI reasoning disabled; passing risk-approved setup forward.", List.of("Risk approved.", "AI reasoning disabled."), riskDecision);
        } else {
            decision = reasoningClient.review(riskDecision);
        }
        logger.debug("Sending ReasoningDecision to ReasoningAgent, {decision}");

        String eventType = decision.isApproved() ? AgentEvent.REASONING_APPROVED : AgentEvent.REASONING_REJECTED;
        context.getEventBus().publishAsync(AgentEvent.reasoning(eventType, name(), decision));
    }
}
