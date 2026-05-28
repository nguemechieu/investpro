package org.investpro.strategy;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.signal.Signal;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.investpro.utils.Side.HOLD;

/**
 * Filters and approves/rejects strategy signals before risk review.
 * 
 * Evaluates StrategySignal confidence and direction to determine if
 * the signal is actionable or should be filtered out.
 */
@Getter
@Setter
public class StrategyAgent implements Agent {

    private AgentContext context;
    private boolean running;

    @Override
    public String name() {
        return "StrategyAgent";
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
        if (!running || event == null || !AgentEvent.SIGNAL_CREATED.equals(event.type())) {
            return;
        }

        Object payload = event.payload();
        if (payload instanceof org.investpro.strategy.StrategySignal strategySignal) {
            handleStrategySignal(strategySignal, event);
            return;
        }

        if (!(payload instanceof Signal signal)) {
            return;
        }

        if (signal.getConfidence() >= 0.50 && !HOLD.equals(signal.getSide())) {
            context.getEventBus().publishAsync(AgentEvent.of(AgentEvent.STRATEGY_SIGNAL_APPROVED, name(), signal));
        } else {
            context.getEventBus().publishAsync(AgentEvent.of(AgentEvent.STRATEGY_SIGNAL_REJECTED, name(), signal));
        }
    }

    private void handleStrategySignal(org.investpro.strategy.StrategySignal signal, AgentEvent sourceEvent) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (sourceEvent.metadata() != null) {
            metadata.putAll(sourceEvent.metadata());
        }
        metadata.putIfAbsent("symbol", signal.getSymbol());
        metadata.putIfAbsent("timeframe", signal.getTimeframe());
        metadata.put("side", signal.getSide());
        metadata.put("confidence", signal.getConfidence());
        metadata.put("strategy_name", signal.getStrategyName());
        TradePair pair = parsePair(signal.getSymbol());
        if (pair != null) {
            metadata.put("tradePairObject", pair);
            metadata.put("tradePair", pair);
        }

        String type = signal.getConfidence() >= 0.50 && !HOLD.equals(signal.getSide())
                ? AgentEvent.STRATEGY_SIGNAL_APPROVED
                : AgentEvent.STRATEGY_SIGNAL_REJECTED;
        context.getEventBus().publishAsync(new AgentEvent(type, name(), signal, Instant.now(), metadata));
    }

    private TradePair parsePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String[] parts = symbol.replace('_', '/').replace('-', '/').split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception ignored) {
            return null;
        }
    }
}
