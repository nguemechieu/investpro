package org.investpro.core.execution;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.reasoning.ReasoningDecision;
import org.investpro.risk.RiskDecision;
import org.investpro.signal.Signal;
import org.investpro.models.trading.Order;
import org.investpro.utils.Side;
/**
 * Final execution gate.
 * <p>
 * This is the only agent that is allowed to call exchange.createOrder(...).
 * Uses Strategy Signal to determine the action instead of just side direction.
 */
@Slf4j
@Getter
@Setter
public class ExecutionAgent implements Agent {
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
            context.getEventBus()
                    .publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Auto trading disabled."));
            return;
        }

        if (context.getExchange() == null || !Boolean.TRUE.equals(context.getExchange().isConnected())) {
            context.getEventBus().publishAsync(
                    AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Exchange is not connected."));
            return;
        }

        if (!context.getExchange().canSubmitOrders()) {
            context.getEventBus().publishAsync(AgentEvent.execution(
                    AgentEvent.ORDER_REJECTED,
                    name(),
                    "Exchange is connected but not ready for order submission."));
            return;
        }

        ReasoningDecision reasoningDecision = (ReasoningDecision) event.payload();
        RiskDecision riskDecision = reasoningDecision.getSourcePayload() instanceof RiskDecision rd ? rd : null;
        Signal signal = riskDecision != null && riskDecision.getSourcePayload() instanceof Signal s ? s : null;

        if (riskDecision == null || signal == null || signal.getTradePair() == null) {
            context.getEventBus().publishAsync(
                    AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), "Missing signal/risk payload."));
            return;
        }

        try {
            // Extract strategy signal direction instead of converting action string
            Side strategyDirection = signal.getSide() != null ? signal.getSide() : Side.HOLD;

            Order order = context.getExchange().createOrder(
                    0,
                    signal.getTradePair(),
                    "MARKET",
                    0.0,
                    riskDecision.getApprovedSize(),
                    strategyDirection,
                    0.0,
                    0.0,
                    0.0);

            context.getExchange().createOrder(order)
                    .thenAccept(response -> context.getEventBus()
                            .publishAsync(AgentEvent.execution(AgentEvent.ORDER_SUBMITTED, name(), response)))
                    .exceptionally(exception -> {
                        context.getEventBus()
                                .publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
                        return null;
                    });
        } catch (JsonProcessingException exception) {
            log.error("Order serialization failed", exception);
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
        } catch (Exception exception) {
            log.error("Order execution failed", exception);
            context.getEventBus().publishAsync(AgentEvent.execution(AgentEvent.ORDER_REJECTED, name(), exception));
        }
    }
}
