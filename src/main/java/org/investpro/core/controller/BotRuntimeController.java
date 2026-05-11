package org.investpro.core.controller;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.bot.SmartBot;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * BotRuntimeController manages the SmartBot lifecycle and state transitions.
 *
 * This controller:
 * - delegates to SmartBot for runtime control
 * - provides a clearer API for SystemCore
 * - enforces state transitions (STOPPED -> STARTED -> RUNNING)
 * - handles errors gracefully
 *
 * This controller does NOT:
 * - create SmartBot (that's SystemCore's job)
 * - make trade decisions (that's Agent's job)
 * - execute trades (that's ExecutionService's job)
 * - manage UI (that's TradingWindow's job)
 */
@Slf4j
public class BotRuntimeController {

    private final SmartBot smartBot;

    public BotRuntimeController(@NotNull SmartBot smartBot) {
        this.smartBot = Objects.requireNonNull(smartBot, "smartBot must not be null");
    }

    /**
     * Start the bot runtime.
     *
     * @param exchange          the exchange to use
     * @param tradingService    the trading service
     * @param selectedTradePair the initial trading pair
     */
    public void start(
            @NotNull Object exchange,
            @NotNull Object tradingService,
            @Nullable TradePair selectedTradePair) {
        try {
            smartBot.start(
                    (org.investpro.exchange.Exchange) exchange,
                    (org.investpro.service.TradingService) tradingService,
                    selectedTradePair);
            log.info("BotRuntimeController: SmartBot started successfully");
        } catch (Exception e) {
            log.error("BotRuntimeController: Failed to start SmartBot", e);
            throw new RuntimeException("Failed to start SmartBot", e);
        }
    }

    /**
     * Stop the bot runtime.
     */
    public void stop() {
        try {
            smartBot.stop();
            log.info("BotRuntimeController: SmartBot stopped");
        } catch (Exception e) {
            log.error("BotRuntimeController: Error stopping SmartBot", e);
        }
    }

    /**
     * Restart the bot with a new configuration.
     */
    public void restart(
            @NotNull Object exchange,
            @NotNull Object tradingService,
            @Nullable TradePair selectedTradePair) {
        stop();
        start(exchange, tradingService, selectedTradePair);
    }

    /**
     * Enable or disable auto-trading.
     *
     * When disabled, agents can still analyze, but no orders will be placed
     * automatically.
     * User manual trades are still possible with explicit confirmation.
     *
     * @param enabled true to enable auto-trading
     */
    public void setAutoTradingEnabled(boolean enabled) {
        try {
            smartBot.setAutoTradingEnabled(enabled);
            log.info("BotRuntimeController: Auto-trading {}", enabled ? "ENABLED" : "DISABLED");
        } catch (Exception e) {
            log.error("BotRuntimeController: Error setting auto-trading", e);
            throw new RuntimeException("Failed to set auto-trading state", e);
        }
    }

    /**
     * Enable or disable AI reasoning.
     *
     * When disabled, trade decisions will not be reviewed by AI.
     *
     * @param enabled true to enable AI reasoning
     */
    public void setAiReasoningEnabled(boolean enabled) {
        try {
            smartBot.setAiReasoningEnabled(enabled);
            log.info("BotRuntimeController: AI reasoning {}", enabled ? "ENABLED" : "DISABLED");
        } catch (Exception e) {
            log.error("BotRuntimeController: Error setting AI reasoning", e);
            throw new RuntimeException("Failed to set AI reasoning state", e);
        }
    }

    /**
     * Update the selected trading pair.
     *
     * @param selectedTradePair the new trading pair
     */
    public void selectTradePair(@Nullable TradePair selectedTradePair) {
        try {
            smartBot.setSelectedTradePair(selectedTradePair);
            log.info("BotRuntimeController: Selected pair {}", selectedTradePair);
        } catch (Exception e) {
            log.error("BotRuntimeController: Error selecting trade pair", e);
            throw new RuntimeException("Failed to select trade pair", e);
        }
    }

    /**
     * Check if the bot runtime is started.
     *
     * @return true if bot is started
     */
    public boolean isStarted() {
        return smartBot.isStarted();
    }

    /**
     * Check if the bot runtime is running.
     *
     * @return true if bot is running
     */
    public boolean isRunning() {
        return smartBot.isRunning();
    }

    /**
     * Check if auto-trading is enabled.
     *
     * @return true if auto-trading is enabled
     */
    public boolean isAutoTradingEnabled() {
        return smartBot.isAutoTradingEnabled();
    }

    /**
     * Check if AI reasoning is enabled.
     *
     * @return true if AI reasoning is enabled
     */
    public boolean isAiReasoningEnabled() {
        return smartBot.isAiReasoningEnabled();
    }

    /**
     * Get the underlying SmartBot instance.
     *
     * Direct access is provided for advanced use cases where direct control is
     * needed.
     * For most operations, use the controller methods above.
     *
     * @return the SmartBot instance
     */
    public SmartBot getSmartBot() {
        return smartBot;
    }
}
