package org.investpro.core.agents.signal;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.investpro.utils.Side.*;

/**
 * Creates simple deterministic signals from live market events.
 */
@Getter
@Setter
public class SignalAgent implements Agent {

    private static final Logger logger = LoggerFactory.getLogger(SignalAgent.class);

    private AgentContext context;
    private volatile boolean running;
    private final Map<TradePair, Double> lastPrices = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "SignalAgent";
    }

    @Override
    public void start(AgentContext context) {
        this.context = context;
        this.running = true;
    }

    Signal signal;
    @Override
    public void stop() {
        running = false;
        lastPrices.clear();
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null || !AgentEvent.MARKET_TICK.equals(event.type())) {
            return;
        }

        if (!(event.payload() instanceof Ticker ticker)) {
            return;
        }

        TradePair pair = context == null ? null : context.getSelectedTradePair();
        if (pair == null) {
            return;
        }

        double price = extractPrice(ticker);
        if (price <= 0) {
            return;
        }

        double previous = lastPrices.put(pair, price);
        if ( previous <= 0) {
            return;
        }


        double change = (price - previous) / previous;
        double min = Math.min(0.95, 0.55 + Math.abs(change * 100));
        if (change > 0.0005) {
            signal = new Signal(pair, BUY, min, "momentum", List.of("Price momentum is positive."));
        } else if (change < -0.0005) {
            signal = new Signal(pair, SELL, min, "momentum", List.of("Price momentum is negative."));
        } else {
            signal = new Signal(pair, HOLD, 0.50, "momentum", List.of("Price movement is neutral."));
        }

        logger.debug("Created signal {}", signal);
        context.getEventBus().publishAsync(AgentEvent.signal(name(), signal));
    }

    private double extractPrice(Ticker ticker) {
        try {
            double bid = ticker.getBidPrice();
            double ask = ticker.getAskPrice();
            if (bid > 0 && ask > 0) {
                return (bid + ask) / 2.0;
            }
            return Math.max(bid, ask);
        } catch (Exception exception) {
            return 0.0;
        }
    }
}
