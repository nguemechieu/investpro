package org.investpro.core.agents.modules;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.strategy.StrategyEngine;
import org.investpro.strategy.StrategyDecisionService;
import org.investpro.strategy.StrategyDecisionResult;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Agent responsible for generating trading signals using the strategy framework.
 * <p>
 * Processes:
 * - Market data from MarketDataAgent (candles, ticks, volatility)
 * - Executes registered strategies via StrategyEngine
 * - Validates signals through StrategyDecisionService
 * <p>
 * Publishes:
 * - Trading signals (BUY/SELL/HOLD)
 * - Signal confidence scores
 * - Strategy recommendations and reasoning
 */
@Slf4j
public class SignalAgent implements org.investpro.core.agents.Agent {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SignalAgent.class);

    private volatile boolean running = false;
    private AgentContext context;
    private StrategyEngine strategyEngine;
    private StrategyDecisionService decisionService;

    public SignalAgent() {
        this.strategyEngine = StrategyEngine.getInstance();
        this.decisionService = new StrategyDecisionService();
    }

    @Override
    public String name() {
        return "SignalAgent";
    }

    @Override
    public void start(AgentContext context) {
        if (running) {
            log.warn("SignalAgent is already started");
            return;
        }

        this.context = context;
        this.running = true;

        log.info("SignalAgent started with {} registered strategies", 
                strategyEngine.getAvailableStrategies().size());
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }

        this.running = false;
        this.context = null;

        log.info("SignalAgent stopped");
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running || event == null) {
            return;
        }

        try {
            // Signal agent processes market and analysis events
            if (AgentEvent.MARKET_CANDLE.equals(event.type())) {
                handleCandleEvent(event);
            } else if (AgentEvent.MARKET_TICK.equals(event.type())) {
                handleTickEvent(event);
            }

        } catch (Exception e) {
            log.error("Error processing signal event", e);
        }
    }

    /**
     * Handle candle close events - generate strategy signals for new candles.
     */
    private void handleCandleEvent(@NotNull AgentEvent event) {
        Map<String, Object> data = event.data();
        if (data == null) {
            log.debug("Candle event has no data");
            return;
        }

        TradePair symbol = (TradePair) data.get("symbol");
        Timeframe timeframe = (Timeframe) data.get("timeframe");
        @SuppressWarnings("unchecked")
        List<CandleData> candles = (List<CandleData>) data.get("candles");
        Double bid = (Double) data.get("bid");
        Double ask = (Double) data.get("ask");
        Double current = (Double) data.get("current");
        Double volatility = (Double) data.get("volatility");
        Double volume = (Double) data.get("volume");

        if (symbol == null || timeframe == null || candles == null || candles.isEmpty()) {
            log.debug("Incomplete candle data for signal generation");
            return;
        }

        generateSignalFromStrategy(symbol, timeframe, candles, bid, ask, current, volatility, volume);
    }

    /**
     * Handle tick events - can generate higher-frequency signals if needed.
     */
    private void handleTickEvent(@NotNull AgentEvent event) {
        // Tick events contain real-time price updates
        // Can be used for quick stop-loss or take-profit adjustments
        log.trace("Received tick event: {}", event.type());
    }

    /**
     * Generate trading signal using assigned strategy.
     */
    private void generateSignalFromStrategy(
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles,
            Double bid,
            Double ask,
            Double current,
            Double volatility,
            Double volume) {

        try {
            // Use StrategyDecisionService to get a validated signal
            StrategyDecisionResult result = decisionService.generateDecision(
                    symbol, timeframe, candles, bid, ask, current, volatility, volume, null);

            if (!result.isSuccess()) {
                log.debug("Strategy decision rejected for {}/{}: {}",
                        symbol, timeframe, result.getRejectionReason());
                return;
            }

            if (!result.hasActionableSignal()) {
                log.trace("No actionable signal (HOLD) for {}/{}", symbol, timeframe);
                return;
            }

            // Signal is actionable - publish it to trading engine
            var signal = result.getSignal();
            publishSignal(symbol, timeframe, signal);

            log.info("Strategy signal generated: {} {} at {} (confidence: {}, strategy: {})",
                    signal.getSignalDirection(),
                    symbol,
                    timeframe,
                    String.format("%.2f", signal.getConfidence()),
                    signal.getStrategyId());

        } catch (Exception e) {
            log.error("Error generating signal for {}/{}", symbol, timeframe, e);
        }
    }

    /**
     * Publish signal to the trading event stream.
     */
    private void publishSignal(
            @NotNull TradePair symbol,
            @NotNull Timeframe timeframe,
            @NotNull org.investpro.strategy.StrategySignal signal) {

        if (context == null) {
            log.warn("Agent context not available for publishing signal");
            return;
        }

        try {
            // Create a signal event and broadcast to other agents
            AgentEvent signalEvent = new AgentEvent(
                    "strategy_signal",
                    Map.of(
                            "symbol", symbol,
                            "timeframe", timeframe,
                            "signal", signal,
                            "timestamp", System.currentTimeMillis()
                    )
            );

            context.publish(signalEvent);
            log.debug("Signal published to event stream: {}", signal.getSignalDirection());

        } catch (Exception e) {
            log.error("Failed to publish signal event", e);
        }
    }
}
