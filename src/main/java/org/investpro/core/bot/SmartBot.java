package org.investpro.core.bot;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.agents.AgentRegistry;
import org.investpro.core.agents.AgentRuntime;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SmartBot is the focused bot runtime controller.
 * <p>
 * Responsibilities:
 * - create and manage AgentContext
 * - start/stop AgentRuntime
 * - start/stop AgentEventBus
 * - control auto-trading flag
 * - control AI reasoning flag
 * - publish bot/agent events
 * <p>
 * SmartBot does NOT:
 * - manage exchange streaming modes
 * - send Telegram/email directly
 * - place trades directly
 * - own application wiring
 * - own UI/app lifecycle
 * <p>
 * SystemCore owns app-level responsibilities.
 * TradingWindow owns desktop UI-only streaming.
 */
@Slf4j
@Getter
@Setter
public class SmartBot {
    private final AgentEventBus eventBus;
    private final AgentRuntime runtime;
    private final AgentRegistry agentRegistry;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private AgentContext context;

    public SmartBot() {
        this(AgentRuntime.createDefault(), new AgentEventBus(), new AgentRegistry());
    }

    public SmartBot(
            @NotNull AgentRuntime runtime,
            @NotNull AgentEventBus eventBus,
            @NotNull AgentRegistry agentRegistry) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.agentRegistry = Objects.requireNonNull(agentRegistry, "agentRegistry must not be null");

    }

    /**
     * Start the bot runtime.
     * <p>
     * Auto-trading starts OFF by default.
     * SystemCore must explicitly enable auto-trading after startup.
     * <p>
     * Starts registered agents after event bus and runtime are ready.
     */
    public void start(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @Nullable TradePair selectedTradePair) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradingService, "tradingService must not be null");

        if (!started.compareAndSet(false, true)) {
            log.warn("SmartBot is already started.");
            return;
        }

        AgentContext newContext = new AgentContext();
        newContext.setExchange(exchange);
        newContext.setTradingService(tradingService);
        newContext.setEventBus(eventBus);
        newContext.setSelectedTradePair(selectedTradePair);
        newContext.setSelectedSymbol(tradePairText(selectedTradePair));

        /*
         * Safe defaults.
         *
         * Auto-trading starts OFF.
         * AI reasoning can be enabled, but AI still cannot execute directly.
         */
        newContext.setAutoTradingEnabled(false);
        newContext.setAiReasoningEnabled(true);
        newContext.setMaxRiskPerTrade(0.01);
        newContext.setMaxDailyLoss(0.03);

        this.context = newContext;

        try {
            eventBus.start();
            runtime.start(context);

            // Start all registered agents
            agentRegistry.startAll(context);

            publishSystemEvent("SMART_BOT_STARTED", "SmartBot started.");

            log.info(
                    "SmartBot started. exchange={} pair={} agents={}",
                    safeExchangeName(exchange),
                    selectedTradePair,
                    agentRegistry.size());

        } catch (Exception exception) {
            safeStopAgents();
            safeStopRuntime();
            safeStopEventBus();

            context = null;
            started.set(false);

            log.error("Failed to start SmartBot", exception);
            throw new IllegalStateException("Failed to start SmartBot", exception);
        }
    }

    /**
     * Stop bot runtime and event bus.
     * <p>
     * Stops all registered agents first, then stops runtime and event bus.
     */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }

        safeStopAgents();
        safeStopRuntime();
        safeStopEventBus();

        context = null;

        log.info("SmartBot stopped.");
    }

    /**
     * Restart the bot runtime with a new exchange/context.
     */
    public void restart(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @Nullable TradePair selectedTradePair) {
        stop();
        start(exchange, tradingService, selectedTradePair);
    }

    /**
     * Enable or disable autonomous execution.
     * <p>
     * When disabled:
     * - agents can still analyze
     * - signals can still be produced
     * - risk can still evaluate
     * - execution must not send orders
     */
    public void setAutoTradingEnabled(boolean enabled) {
        ensureStarted();

        context.setAutoTradingEnabled(enabled);
        runtime.setAutoTradingEnabled(enabled);

        publishSystemEvent(
                enabled ? "AUTO_TRADING_ENABLED" : "AUTO_TRADING_DISABLED",
                enabled ? "Auto trading enabled." : "Auto trading disabled.");

        log.info("SmartBot auto trading enabled={}", enabled);
    }

    /**
     * Enable or disable AI reasoning.
     */
    public void setAiReasoningEnabled(boolean enabled) {
        ensureStarted();

        context.setAiReasoningEnabled(enabled);
        runtime.setAiReasoningEnabled(enabled);

        publishSystemEvent(
                enabled ? "AI_REASONING_ENABLED" : "AI_REASONING_DISABLED",
                enabled ? "AI reasoning enabled." : "AI reasoning disabled.");

        log.info("SmartBot AI reasoning enabled={}", enabled);
    }

    /**
     * Update the selected trade pair in the running bot context.
     */
    public void setSelectedTradePair(@Nullable TradePair selectedTradePair) {
        ensureStarted();

        context.setSelectedTradePair(selectedTradePair);
        context.setSelectedSymbol(tradePairText(selectedTradePair));

        publishSystemEvent(
                "SMART_BOT_SYMBOL_CHANGED",
                "Selected symbol changed to " + tradePairText(selectedTradePair));
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isRunning() {
        return started.get();
    }

    public boolean isStopped() {
        return !started.get();
    }

    /**
     * Update the bot's exchange connection without stopping trading.
     * <p>
     * This allows switching between exchanges while keeping the bot running
     * and continuing to trade across different markets.
     * <p>
     * 
     * @param newExchange the new exchange to use for trading
     */
    public void updateExchange(@NotNull Exchange newExchange) {
        Objects.requireNonNull(newExchange, "newExchange must not be null");

        if (!started.get()) {
            log.warn("Cannot update exchange - SmartBot is not started");
            return;
        }

        if (context == null) {
            log.warn("Cannot update exchange - bot context is null");
            return;
        }

        Exchange oldExchange = context.getExchange();
        try {
            context.setExchange(newExchange);
            log.info("SmartBot exchange updated from {} to {}",
                    oldExchange != null ? oldExchange.getDisplayName() : "NONE",
                    newExchange.getDisplayName());

            publishSystemEvent(
                    "SMART_BOT_EXCHANGE_CHANGED",
                    "Exchange changed to " + newExchange.getDisplayName() +
                            " - bot continues trading");
        } catch (Exception e) {
            log.error("Error updating bot exchange", e);
            // Restore old exchange on failure
            if (oldExchange != null) {
                context.setExchange(oldExchange);
            }
            throw new RuntimeException("Failed to update bot exchange: " + e.getMessage(), e);
        }
    }

    public boolean isAutoTradingEnabled() {
        return context != null && context.isAutoTradingEnabled();
    }

    public boolean isAiReasoningEnabled() {
        return context != null && context.isAiReasoningEnabled();
    }

    /**
     * Publish a market event into the agent system.
     */
    public void publishMarketEvent(String type, Object payload) {
        ensureStarted();

        eventBus.publish(AgentEvent.market(
                safeEventType(type),
                "SmartBot",
                payload));
    }

    /**
     * Publish a custom event into the agent system.
     */
    public void publish(AgentEvent event) {
        ensureStarted();

        if (event != null) {
            eventBus.publish(event);
        }
    }

    public void publishSystemEvent(String type, String message) {
        if (!eventBusIsReady()) {
            log.debug("Skipping system event because event bus is not ready. type={}", type);
            return;
        }

        eventBus.publish(event(
                safeEventType(type),
                "SmartBot",
                message,
                Map.of()));
    }

    public void publishErrorEvent(String source, Throwable throwable, String message) {
        if (!eventBusIsReady()) {
            log.debug("Skipping error event because event bus is not ready. source={}", source);
            return;
        }

        eventBus.publish(event(
                AgentEvent.ERROR,
                safe(source).isBlank() ? "SmartBot" : safe(source),
                throwable,
                Map.of(
                        "message", safe(message),
                        "error", rootMessage(throwable))));
    }

    private @NotNull AgentEvent event(
            String type,
            String source,
            Object payload,
            Map<String, Object> metadata) {
        return new AgentEvent(
                safeEventType(type),
                safe(source).isBlank() ? "SmartBot" : safe(source),
                payload,
                Instant.now(),
                metadata == null ? Map.of() : metadata);
    }

    private void ensureStarted() {
        if (!started.get() || context == null) {
            throw new IllegalStateException("SmartBot has not been started.");
        }
    }

    private boolean eventBusIsReady() {
        return eventBus != null;
    }

    private void safeStopAgents() {
        try {
            agentRegistry.stopAll();
        } catch (Exception exception) {
            log.warn("Failed to stop agents cleanly", exception);
        }
    }

    private void safeStopRuntime() {
        try {
            runtime.stop();
        } catch (Exception exception) {
            log.warn("Failed to stop AgentRuntime cleanly", exception);
        }
    }

    private void safeStopEventBus() {
        try {
            eventBus.stop();
        } catch (Exception exception) {
            log.warn("Failed to stop AgentEventBus cleanly", exception);
        }
    }

    private String tradePairText(TradePair tradePair) {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    private String safeExchangeName(Exchange exchange) {
        try {
            return exchange == null ? "UNKNOWN_EXCHANGE" : safe(exchange.getName());
        } catch (Exception exception) {
            return "UNKNOWN_EXCHANGE";
        }
    }

    private @NotNull String safeEventType(String value) {
        String text = safe(value);
        return text.isBlank() ? "UNKNOWN_EVENT" : text;
    }

    private @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private @NotNull String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        String message = current.getMessage();

        return message == null || message.isBlank()
                ? current.getClass().getSimpleName()
                : message;
    }
}