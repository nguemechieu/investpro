package org.investpro.core.agents.risk;

import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.signal.Signal;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies account/risk controls before AI reasoning and execution.
 */
public class RiskAgent implements Agent {

    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "RiskAgent";
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
        if (!running || event == null || !AgentEvent.STRATEGY_SIGNAL_APPROVED.equals(event.type())) {
            return;
        }

        if (!(event.payload() instanceof Signal signal)) {
            return;
        }

        List<String> reasons = new ArrayList<>();

        if (!context.isAutoTradingEnabled()) {
            reasons.add("Auto trading is disabled; signal is display-only.");
            publishRejected(signal, reasons);
            return;
        }

        if (context.getExchange() == null || !Boolean.TRUE.equals(context.getExchange().isConnected())) {
            reasons.add("Exchange is not connected.");
            publishRejected(signal, reasons);
            return;
        }

        if (signal.getConfidence() < 0.60) {
            reasons.add("Signal confidence is below risk threshold.");
            publishRejected(signal, reasons);
            return;
        }

        double approvedSize = Math.max(0.0, context.getMaxRiskPerTrade());
        reasons.add("Risk approved with conservative sizing.");

        RiskDecision decision = new RiskDecision(true, signal.getSide(), approvedSize, context.getMaxRiskPerTrade(), reasons, signal);
        context.getEventBus().publishAsync(AgentEvent.risk(AgentEvent.RISK_APPROVED, name(), decision));
    }

    private void publishRejected(Signal signal, List<String> reasons) {
        RiskDecision decision = new RiskDecision(false, signal.getSide(), 0.0, 0.0, reasons, signal);
        context.getEventBus().publishAsync(AgentEvent.risk(AgentEvent.RISK_REJECTED, name(), decision));
    }
}
