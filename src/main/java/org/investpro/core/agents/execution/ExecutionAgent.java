package org.investpro.core.agents.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.reasoning.ReasoningDecision;
import org.investpro.core.agents.risk.RiskDecision;
import org.investpro.core.agents.signal.Signal;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Final execution gate.
 *
 * This is the only agent that is allowed to call exchange.createOrder(...).
 */
public class ExecutionAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionAgent.class);

    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "ExecutionAgent";
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
        if (!running || event == null || !AgentEvent.REASONING_APPROVED.equals(event.type())) {
            return;
        }

        if (!context.isAutoTradingEnabled()) {
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Auto trading disabled."));
            return;
        }

        if (context.getExchange() == null || !Boolean.TRUE.equals(context.getExchange().isConnected())) {
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Exchange is not connected."));
            return;
        }

        ReasoningDecision reasoningDecision = (ReasoningDecision) event.payload();
        RiskDecision riskDecision = reasoningDecision.getSourcePayload() instanceof RiskDecision rd ? rd : null;
        Signal signal = riskDecision != null && riskDecision.getSourcePayload() instanceof Signal s ? s : null;

        if (riskDecision == null || signal == null || signal.getTradePair() == null) {
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Missing signal/risk payload."));
            return;
        }

        try {
            Side side = "SELL".equalsIgnoreCase(riskDecision.getAction()) ? Side.SELL : Side.BUY;
            Order order = context.getExchange().createOrder(
                    0,
                    signal.getTradePair(),
                    "MARKET",
                    0.0,
                    riskDecision.getApprovedSize(),
                    side,
                    0.0,
                    0.0,
                    0.0
            );

            context.getExchange().createOrder(order)
                    .thenAccept(response -> context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_SUBMITTED, name(), response)))
                    .exceptionally(exception -> {
                        context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
                        return null;
                    });
        } catch (JsonProcessingException exception) {
            logger.error("Order serialization failed", exception);
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
        } catch (Exception exception) {
            logger.error("Order execution failed", exception);
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
        }
    }
}
