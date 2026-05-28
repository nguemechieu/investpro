package org.investpro.core.bot;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.agents.AgentRuntime;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.infrastructure.BotTradingConfig;
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
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Create and manage AgentContext.</li>
 *     <li>Start/stop AgentRuntime.</li>
 *     <li>Start/stop AgentEventBus where supported by the current implementation.</li>
 *     <li>Control auto-trading flag.</li>
 *     <li>Control AI reasoning flag.</li>
 *     <li>Publish bot/agent events.</li>
 * </ul>
 *
 * <p>SmartBot does not:</p>
 * <ul>
 *     <li>Manage exchange streaming modes.</li>
 *     <li>Send Telegram/email directly.</li>
 *     <li>Place trades directly.</li>
 *     <li>Own application wiring.</li>
 *     <li>Own UI/application lifecycle.</li>
 * </ul>
 *
 * <p>SystemCore owns app-level responsibilities.
 * TradingDesk owns desktop UI-only streaming.</p>
 */
@Slf4j
@Getter
public class SmartBot {

    public static final String SOURCE = "SmartBot";

    private final AgentEventBus eventBus;
    private final AgentRuntime runtime;
    private final BotTradingConfig botTradingConfig;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile AgentContext context;

    public SmartBot() {
        this(AgentRuntime.createDefault(), new AgentEventBus(), loadBotTradingConfig());
    }

    public SmartBot(
            @NotNull AgentRuntime runtime,
            @NotNull AgentEventBus eventBus
    ) {
        this(runtime, eventBus, loadBotTradingConfig());
    }

    public SmartBot(
            @NotNull AgentRuntime runtime,
            @NotNull AgentEventBus eventBus,
            @NotNull BotTradingConfig botTradingConfig
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.botTradingConfig = Objects.requireNonNull(botTradingConfig, "botTradingConfig must not be null");
    }

    /**
     * Starts the bot runtime.
     *
     * <p>Auto-trading uses the persisted {@link BotTradingConfig} value.
     * SystemCore/UI can still explicitly enable or disable it after startup.</p>
     */
    public void start(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @Nullable TradePair selectedTradePair
    ) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradingService, "tradingService must not be null");

        if (!started.compareAndSet(false, true)) {
            log.warn("SmartBot is already started.");
            return;
        }

        AgentContext newContext = createContext(exchange, tradingService, selectedTradePair);
        context = newContext;

        try {
            safeStartEventBus();

            runtime.start(newContext);
            runtime.setAutoTradingEnabled(newContext.isAutoTradingEnabled());
            runtime.setAiReasoningEnabled(newContext.isAiReasoningEnabled());

            publishSystemEvent("SMART_BOT_STARTED", "SmartBot started.");

            log.info(
                    "SmartBot started. exchange={} pair={} autoTrading={} aiReasoning={} agents={}",
                    safeExchangeName(exchange),
                    tradePairText(selectedTradePair),
                    newContext.isAutoTradingEnabled(),
                    newContext.isAiReasoningEnabled(),
                    safeAgentCount()
            );

        } catch (Exception exception) {
            context = null;
            started.set(false);

            safeStopRuntime();
            safeStopEventBus();

            log.error("Failed to start SmartBot", exception);
            throw new IllegalStateException("Failed to start SmartBot", exception);
        }
    }

    /**
     * Stops bot runtime and event bus.
     */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }

        publishSystemEvent("SMART_BOT_STOPPING", "SmartBot stopping.");

        safeStopRuntime();
        safeStopEventBus();

        context = null;

        log.info("SmartBot stopped.");
    }

    /**
     * Restarts the bot runtime with a new exchange/context.
     */
    public void restart(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @Nullable TradePair selectedTradePair
    ) {
        stop();
        start(exchange, tradingService, selectedTradePair);
    }

    /**
     * Enables or disables autonomous execution.
     *
     * <p>When disabled, agents may still analyze, generate signals, and evaluate risk,
     * but execution must not send orders.</p>
     */
    public void setAutoTradingEnabled(boolean enabled) {
        AgentContext activeContext = requireContext();

        activeContext.setAutoTradingEnabled(enabled);
        runtime.setAutoTradingEnabled(enabled);

        publishSystemEvent(
                enabled ? "AUTO_TRADING_ENABLED" : "AUTO_TRADING_DISABLED",
                enabled ? "Auto trading enabled." : "Auto trading disabled."
        );

        log.info("SmartBot autoTrading={}", enabled);
    }

    /**
     * Enables or disables AI reasoning.
     */
    public void setAiReasoningEnabled(boolean enabled) {
        AgentContext activeContext = requireContext();

        activeContext.setAiReasoningEnabled(enabled);
        runtime.setAiReasoningEnabled(enabled);

        publishSystemEvent(
                enabled ? "AI_REASONING_ENABLED" : "AI_REASONING_DISABLED",
                enabled ? "AI reasoning enabled." : "AI reasoning disabled."
        );

        log.info("SmartBot aiReasoning={}", enabled);
    }

    /**
     * Updates the selected trade pair in the running bot context.
     */
    public void setSelectedTradePair(@Nullable TradePair selectedTradePair) {
        AgentContext activeContext = requireContext();

        activeContext.setSelectedTradePair(selectedTradePair);
        activeContext.setSelectedSymbol(tradePairText(selectedTradePair));

        publishSystemEvent(
                "SMART_BOT_SYMBOL_CHANGED",
                "Selected symbol changed to " + tradePairText(selectedTradePair)
        );

        log.info("SmartBot selected pair changed to {}", tradePairText(selectedTradePair));
    }

    /**
     * Updates the bot exchange reference while keeping the bot runtime alive.
     *
     * <p>For safety, auto-trading is temporarily disabled during the switch and then
     * restored to its previous state if the switch succeeds.</p>
     */
    public void updateExchange(@NotNull Exchange newExchange) {
        Objects.requireNonNull(newExchange, "newExchange must not be null");

        AgentContext activeContext = requireContext();

        Exchange oldExchange = activeContext.getExchange();
        boolean wasAutoTradingEnabled = activeContext.isAutoTradingEnabled();

        try {
            activeContext.setAutoTradingEnabled(false);
            runtime.setAutoTradingEnabled(false);

            activeContext.setExchange(newExchange);

            activeContext.setAutoTradingEnabled(wasAutoTradingEnabled);
            runtime.setAutoTradingEnabled(wasAutoTradingEnabled);

            String oldName = oldExchange == null ? "NONE" : safeDisplayName(oldExchange);
            String newName = safeDisplayName(newExchange);

            publishSystemEvent(
                    "SMART_BOT_EXCHANGE_CHANGED",
                    "Exchange changed from %s to %s.".formatted(oldName, newName)
            );

            log.info(
                    "SmartBot exchange updated. oldExchange={} newExchange={} autoTradingRestored={}",
                    oldName,
                    newName,
                    wasAutoTradingEnabled
            );

        } catch (Exception exception) {
            activeContext.setExchange(oldExchange);
            activeContext.setAutoTradingEnabled(wasAutoTradingEnabled);
            runtime.setAutoTradingEnabled(wasAutoTradingEnabled);

            publishErrorEvent("SmartBot", exception, "Failed to update bot exchange.");

            log.error("Failed to update SmartBot exchange", exception);
            throw new IllegalStateException("Failed to update SmartBot exchange: " + rootMessage(exception), exception);
        }
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

    public boolean isAutoTradingEnabled() {
        AgentContext activeContext = context;
        return activeContext != null && activeContext.isAutoTradingEnabled();
    }

    public boolean isAiReasoningEnabled() {
        AgentContext activeContext = context;
        return activeContext != null && activeContext.isAiReasoningEnabled();
    }



    /**
     * Publishes a custom event into the agent system.
     */
    public void publish(@Nullable AgentEvent event) {
        ensureStarted();

        if (event != null) {
            eventBus.publish(event);
        }
    }

    public void publishSystemEvent(String type, String message) {


        eventBus.publish(event(
                safeEventType(type),
                SOURCE,
                safe(message),
                Map.of()
        ));
    }



    private AgentContext createContext(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            @Nullable TradePair selectedTradePair
    ) {
        AgentContext newContext = new AgentContext();

        newContext.setExchange(exchange);
        newContext.setTradingService(tradingService);
        newContext.setEventBus(eventBus);
        newContext.setSelectedTradePair(selectedTradePair);
        newContext.setSelectedSymbol(tradePairText(selectedTradePair));
        newContext.setBotTradingConfig(botTradingConfig);

        /*
         * Persisted bot settings feed runtime risk limits.
         * Execution must still go through the normal risk gate.
         */
        newContext.setAutoTradingEnabled(botTradingConfig.isEnabled());
        newContext.setAiReasoningEnabled(true);
        newContext.setMaxRiskPerTrade(percentToRatio(
                botTradingConfig.getMaxPortfolioRiskPercent(),
                0.01
        ));

        /*
         * NOTE:
         * If BotTradingConfig later exposes getMaxDailyLossPercent(),
         * prefer that here. getMaxDailyLosses() sounds like a count, but the
         * existing code treats it as a percent-like value.
         */
        newContext.setMaxDailyLoss(percentToRatio(
                botTradingConfig.getMaxDailyLosses(),
                0.03
        ));

        return newContext;
    }

    private @NotNull AgentEvent event(
            String type,
            String source,
            Object payload,
            Map<String, Object> metadata
    ) {
        return new AgentEvent(
                safeEventType(type),
                safe(source).isBlank() ? SOURCE : safe(source),
                payload,
                Instant.now(),
                metadata == null ? Map.of() : metadata
        );
    }

    private AgentContext requireContext() {
        ensureStarted();
        AgentContext activeContext = context;

        if (activeContext == null) {
            throw new IllegalStateException("SmartBot context is not available.");
        }

        return activeContext;
    }

    private void ensureStarted() {
        if (!started.get()) {
            throw new IllegalStateException("SmartBot has not been started.");
        }
    }
    public void publishErrorEvent(String source, Throwable throwable, String message) {
        eventBus.publish(event(
                AgentEvent.ERROR,
                safe(source).isBlank() ? SOURCE : safe(source),
                throwable,
                Map.of(
                        "message", safe(message),
                        "error", rootMessage(throwable)
                )
        ));
    }

    /**
     * Starts the event bus if the implementation supports start().
     *
     * <p>This method assumes AgentEventBus has a start method. If your actual
     * AgentEventBus does not expose start(), delete this helper and remove the
     * safeStartEventBus() call from start().</p>
     */
    private void safeStartEventBus() {
        try {
            eventBus.start();
        } catch (UnsupportedOperationException exception) {
            log.debug("AgentEventBus start is not supported by this implementation.");
        } catch (Exception exception) {
            log.warn("Failed to start AgentEventBus cleanly", exception);
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
        } catch (UnsupportedOperationException exception) {
            log.debug("AgentEventBus stop is not supported by this implementation.");
        } catch (Exception exception) {
            log.warn("Failed to stop AgentEventBus cleanly", exception);
        }
    }

    private int safeAgentCount() {
        try {
            return runtime.getAgents() == null ? 0 : runtime.getAgents().size();
        } catch (Exception exception) {
            return 0;
        }
    }

    private String tradePairText(@Nullable TradePair tradePair) {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    private static BotTradingConfig loadBotTradingConfig() {
        BotTradingConfig config = new BotTradingConfig();
        config.loadFromPreferences();
        return config;
    }

    private static double percentToRatio(double percent, double fallback) {
        if (!Double.isFinite(percent) || percent <= 0) {
            return fallback;
        }

        return percent / 100.0;
    }

    private String safeExchangeName(@Nullable Exchange exchange) {
        try {
            return exchange == null ? "UNKNOWN_EXCHANGE" : safe(exchange.getName());
        } catch (Exception exception) {
            return "UNKNOWN_EXCHANGE";
        }
    }

    private String safeDisplayName(@Nullable Exchange exchange) {
        try {
            if (exchange == null) {
                return "UNKNOWN_EXCHANGE";
            }

            String displayName = safe(exchange.getDisplayName());

            if (!displayName.isBlank()) {
                return displayName;
            }

            return safeExchangeName(exchange);

        } catch (Exception exception) {
            return "UNKNOWN_EXCHANGE";
        }
    }

    private @NotNull String safeEventType(@Nullable String value) {
        String text = safe(value);
        return text.isBlank() ? "UNKNOWN_EVENT" : text;
    }

    private @NotNull String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private @NotNull String rootMessage(@Nullable Throwable throwable) {
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