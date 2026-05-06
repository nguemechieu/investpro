package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;

/**
 * Agent responsible for collecting and publishing market data events.
 * <p>
 * Processes:
 * - Ticker updates
 * - Candle data
 * - Order book changes
 * - Market trade events
 * <p>
 * Publishes:
 * - Market analysis data
 * - Data quality events
 */
@Slf4j
public class MarketDataAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(MarketDataAgent.class);

    private volatile boolean running = false;
    private AgentContext context;

    @Override
    public String name() {
        return "MarketDataAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("MarketDataAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("MarketDataAgent started");
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("MarketDataAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Market data agent processes market-type events
            if (AgentEvent.MARKET_TICK.equals(event.type()) ||
                    AgentEvent.MARKET_TRADE.equals(event.type()) ||
                    AgentEvent.MARKET_CANDLE.equals(event.type()) ||
                    AgentEvent.ORDER_BOOK_UPDATE.equals(event.type())) {
                handleMarketEvent(event);
            }

        } catch (Exception e) {
            log.error("Error processing market event", e);
        }
    }

    private void handleMarketEvent(AgentEvent event) {
        // Log and process market data
        log.debug("MarketDataAgent processing market event: {}", event.type());
    }
}
