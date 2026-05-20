package org.investpro.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.LocalAiReasoningService;
import org.investpro.ai.OpenAiReasoningService;
import org.investpro.core.agents.*;
import org.investpro.core.agents.execution.ExecutionEngine;
import org.investpro.core.agents.execution.SymbolExecutionFilter;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.core.agents.modules.DefaultTradingAgentModule;
import org.investpro.core.agents.symbol.SymbolAgent;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.bot.SmartBot;
import org.investpro.decision.BotTradeDecisionEngine;
import org.investpro.decision.SignalToDecisionFilter;
import org.investpro.models.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.consumers.UiExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.BotTradingConfig;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.monitoring.SystemHealthSnapshot;
import org.investpro.monitoring.SystemMonitorService;
import org.investpro.monitoring.SystemEventRecorder;
import org.investpro.monitoring.SignalMonitorService;
import org.investpro.repository.HistoricalDataRepository;
import org.investpro.repository.HistoricalDataRepositoryImpl;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.repository.RepositoryFactory;
import org.investpro.service.TradingService;
import org.investpro.event.EventBusManager;
import org.investpro.event.EventPersistenceListener;
import org.investpro.persistence.repository.EventLogRepositoryImpl;

import org.investpro.research.LiveTradingMetricsTracker;
import org.investpro.strategy.*;
import org.investpro.strategy.lab.StrategyLabService;
import org.investpro.ui.panels.SettingsPanel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * SystemCore is the application composition root.
 * <p>
 * Responsibilities:
 * - wire exchange, bot, strategy, risk, AI, execution
 * - own app-level streaming
 * - own notifications
 * - start/stop app-level services
 * <p>
 * SmartBot remains focused on bot runtime only.
 */
@Slf4j
@Getter
@Setter
public class SystemCore {

    private HistoricalDataRepository historicalDataRepository;
    private SystemHealthSnapshot health;

    public StrategyAssignment strategyAssignment;

    public StrategyLabService labService;

    public enum StreamingMode {
        EVERYTHING("All market + account data"),
        MARKET_DATA("Market data only: ticker, trades, candles, order book"),
        ACCOUNT_DATA("Account data only: account, orders, fills, positions, balances"),
        TICKER_ONLY("Ticker updates only"),
        TRADES_ONLY("Market trades only"),
        ORDER_BOOK_ONLY("Order book depth only"),
        SAFE_DEFAULT("Safe default streaming based on exchange capabilities"),
        CUSTOM("Custom subscription with explicit flags");

        public final String description;

        StreamingMode(String description) {
            this.description = description;
        }
    }

    private final Exchange exchange;
    private final Properties config;

    private final SmartBot smartBot;
    private final AgentRegistry agentRegistry;
    private final StrategyEngine strategyEngine;
    private final ExecutionEngine executionEngine;
    private final RiskManagementSystem riskManagementSystem;
    private final AiReasoningService aiReasoningService;
    private final TradeExecutionCoordinator tradeExecutionCoordinator;
    private final BotTradeDecisionEngine botTradeDecisionEngine;
    private final SignalToDecisionFilter signalToDecisionFilter;
    private SystemMonitorService systemMonitorService;
    private SignalMonitorService signalMonitorService;
    /**
     * -- GETTER - -
     * Get the system event recorder for recording important system events.
     * Used internally by stream consumers and other components to track
     * the last known occurrence of key events for diagnostic purposes.
     *
     */
    private SystemEventRecorder systemEventRecorder;

    // Small account sizing config
    private boolean smallAccountModeEnabled;
    private double smallAccountBalanceThreshold;
    private double smallAccountOandaUnits;

    private TelegramNotifier telegramNotifier;
    private EmailNotifier emailNotifier;
    private TelegramCommandHandler telegramCommandHandler;
    private TelegramEventListener telegramEventListener;
    private SymbolAgentUpdater symbolAgentUpdater;

    private String telegramToken;
    private String fromEmail;
    private String toEmail;

    private TradingService tradingService;
    private TradePair selectedTradePair;

    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private StreamingMode currentStreamingMode = StreamingMode.SAFE_DEFAULT;
    private ExchangeStreamSubscription activeSubscription;
    private ExchangeStreamConsumer streamConsumer;
    private SymbolAgentManager symbolAgentManager;
    private SystemCoreDependencies systemCoreDependencies;
    private SymbolExecutionFilter symbolExecutionFilter;
    private EventBusManager eventBusManager;
    private final java.util.Map<TradePair, org.investpro.core.agents.symbol.SymbolAgent> symbolAgents =
            new java.util.concurrent.ConcurrentHashMap<>();

    private final LiveTradingMetricsTracker liveMetricsTracker = new LiveTradingMetricsTracker();

    public SystemCore(@NotNull Exchange exchange, Properties config, String open_ai_api_key)
            throws SQLException, ClassNotFoundException {

        this.exchange = Objects.requireNonNull(exchange, "exchange cannot be null");

        this.config = config == null ? new Properties() : config;

        // Load persisted configuration from disk
        loadPropertiesFromFile();

        // Store OpenAI API key if provided
        if (open_ai_api_key != null && !open_ai_api_key.isBlank()) {
            this.config.setProperty("openai.api_key", open_ai_api_key);
            savePropertiesToFile();
        }

        this.telegramToken = this.config.getProperty("telegram_token", "").trim();

        // Build AgentRuntime with all trading agents before creating SmartBot
        AgentRuntime agentRuntime = new AgentRuntime();
        this.agentRegistry = new AgentRegistry(); // temp registry; agents imported into runtime below

        BotTradingConfig botTradingConfig = new BotTradingConfig();
        botTradingConfig.loadFromPreferences();

        // Create Smart Bot — agents live inside the runtime, not in SmartBot
        this.smartBot = new SmartBot(agentRuntime, new AgentEventBus(), botTradingConfig);

        this.fromEmail = this.config.getProperty("from_email", "").trim();
        this.toEmail = this.config.getProperty("to_email", "").trim();

        // Load small-account sizing config
        this.smallAccountModeEnabled = Boolean.parseBoolean(
                this.config.getProperty("risk.small_account.enabled", "true"));
        this.smallAccountBalanceThreshold = Double.parseDouble(
                this.config.getProperty("risk.small_account.threshold", "100.0"));
        this.smallAccountOandaUnits = Double.parseDouble(
                this.config.getProperty("risk.small_account.oanda_units", "1.0"));

        configureNotifiers();

        // Initialize symbol manager
        this.symbolAgentManager = new SymbolAgentManager();
        this.historicalDataRepository = HistoricalDataRepositoryImpl.getInstance();
        this.symbolExecutionFilter = new SymbolExecutionFilter();
        this.executionEngine = new ExecutionEngine(exchange, symbolExecutionFilter, RepositoryFactory.getDatabase(),
                this);
        this.riskManagementSystem = new RiskManagementSystem();
        this.aiReasoningService = createAiReasoningService(this.config);

        // Initialize TradeExecutionCoordinator (it internally manages safety
        // components)
        this.tradeExecutionCoordinator = new TradeExecutionCoordinator(
                riskManagementSystem,
                aiReasoningService,
                executionEngine);

        // Initialize StrategyEngine with TradeExecutionCoordinator
        this.strategyEngine = new StrategyEngine(tradeExecutionCoordinator);

        // Initialize BotTradeDecisionEngine for institutional-grade trade decisions
        Account currentAccount;
        try {
            currentAccount = exchange.fetchAccount().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        this.botTradeDecisionEngine = new BotTradeDecisionEngine(currentAccount);

        // Initialize SignalToDecisionFilter to intercept and validate all signals
        this.signalToDecisionFilter = new SignalToDecisionFilter(
                botTradeDecisionEngine,

                tradeExecutionCoordinator);

        this.systemCoreDependencies = new SystemCoreDependencies(exchange, tradingService, strategyEngine,
                riskManagementSystem, aiReasoningService, executionEngine, tradeExecutionCoordinator);

        this.labService = StrategyLabService.getInstance();
        // Wire symbol agent state updates for real-time UI
        this.symbolAgentUpdater = new SymbolAgentUpdater(smartBot.getEventBus(), getSymbolAgentManager());

        if (!telegramToken.isBlank()) {
            this.telegramNotifier = new TelegramNotifier(telegramToken);
            // Initialize command handler for Telegram commands
            this.telegramCommandHandler = new TelegramCommandHandler(this, telegramNotifier);
            this.telegramNotifier.setCommandHandler(telegramCommandHandler);
            // Create event listener - will be wired to SmartBot in start()
            this.telegramEventListener = new TelegramEventListener(smartBot.getEventBus(), telegramNotifier);

            // Initialize ChatGPT integration if OpenAI API key is configured
            // Check three sources: config property, environment variable, or parameter
            String openaiApiKey = Objects.requireNonNull(config).getProperty("openai.api_key", "").trim();
            if (openaiApiKey.isBlank()) {
                openaiApiKey = System.getenv("OPENAI_API_KEY") != null ? System.getenv("OPENAI_API_KEY").trim() : "";
            }

            if (!openaiApiKey.isBlank()) {
                telegramNotifier.initializeChatGPT(openaiApiKey);
                log.info("\u2705 ChatGPT integration initialized for Telegram bot");
            } else {
                log.info("\u2139\uFE0F OpenAI API key not configured - ChatGPT features disabled");
            }

            log.info("\u2705 Telegram notifier configured");
        }

        // Initialize the system event recorder and monitor service after all components
        // are ready
        this.systemEventRecorder = new SystemEventRecorder();
        this.systemMonitorService = new SystemMonitorService(this);
        this.signalMonitorService = new SignalMonitorService();
        this.health = this.systemMonitorService.checkNow();

        log.debug(this.toString());

    }

    /**
     * Load configuration properties from disk (~/.investpro/config.Properties).
     * Called early in the lifecycle to restore persisted settings.
     */
    private void loadPropertiesFromFile() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".investpro");
            Path configFile = configDir.resolve("config.properties");

            if (Files.exists(configFile)) {
                try (FileInputStream fis = new FileInputStream(configFile.toFile())) {
                    this.config.load(fis);
                    String apiKeyStatus = this.config.getProperty("openai.api_key") != null ? "\u2705 (loaded)"
                            : "\u274C (not found)";
                    log.info("\u2705 Loaded configuration from {}", configFile.toAbsolutePath());
                    log.info("   OpenAI API key status: {}", apiKeyStatus);
                }
            } else {
                log.info("\u2139\uFE0F Configuration file not found at {}; using defaults", configFile.toAbsolutePath());
                log.info("   To persist settings, use /setapikey command or set OPENAI_API_KEY environment variable");
            }
        } catch (IOException e) {
            log.warn("\u26A0\uFE0F Failed to load properties from file: {}", e.getMessage());
        }
    }

    /**
     * Save configuration properties to disk (~/.investpro/config.properties).
     * Called after properties are modified to ensure persistence across restarts.
     */
    private void savePropertiesToFile() {
        try {
            Path configDir = Paths.get(System.getProperty("user.home"), ".investpro");
            Path configFile = configDir.resolve("config.properties");

            // Create directory if it doesn't exist
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                log.info("\u2705 Created configuration directory: {}", configDir.toAbsolutePath());
            }

            try (FileOutputStream fos = new FileOutputStream(configFile.toFile())) {
                this.config.store(fos, "InvestPro Configuration - DO NOT EDIT MANUALLY");
                log.info("\u2705 Saved configuration to {}", configFile.toAbsolutePath());
                log.info("   OpenAI API key: {}",
                        this.config.getProperty("openai.api_key") != null &&
                                !this.config.getProperty("openai.api_key").isBlank() ? "\u2705 Set" : "\u274C Not set");
            }
        } catch (IOException e) {
            log.warn("\u26A0\uFE0F Failed to save properties to file: {}", e.getMessage());
        }
    }

    /**
     * Set the OpenAI API key and persist it to disk.
     * This method allows the Telegram bot to configure the API key at runtime.
     */
    public void setOpenAiApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isBlank()) {
            this.config.setProperty("openai.api_key", apiKey);
            savePropertiesToFile();

            // Reinitialize ChatGPT with the new key
            if (this.telegramNotifier != null) {
                this.telegramNotifier.initializeChatGPT(apiKey);
                log.info("\u2705 ChatGPT reinitialized with new API key");
            } else {
                log.warn("\u26A0\uFE0F TelegramNotifier not initialized; unable to reinitialize ChatGPT");
            }

            log.info("\u2705 OpenAI API key configured and persisted");
        } else {
            log.warn("\u26A0\uFE0F Invalid API key provided (empty or null)");
        }
    }

    /**
     * Register the default set of trading agents.
     * Should only be called after tradingService is initialized.
     */
    private void registerDefaultAgents() {
        try {
            if (tradingService == null) {
                throw new IllegalStateException("tradingService must be initialized before registering agents");
            }

            // Initialize strategies before registering agents
            try {
                StrategyBootstrapper.initialize();
                log.info("StrategyBootstrapper initialized");
            } catch (Exception e) {
                log.warn("Failed to initialize StrategyBootstrapper", e);
            }

            DefaultTradingAgentModule defaultModule = new DefaultTradingAgentModule();
            defaultModule.configure(agentRegistry, systemCoreDependencies);

            log.debug("Default trading agents registered. count={}", agentRegistry.size());

        } catch (Exception e) {
            log.error("Failed to register default agents", e);
            throw new RuntimeException("Failed to initialize agent registry", e);
        }
    }

    public void applySystemSettings(SettingsPanel.SystemSafetySettings settings) {
        if (settings == null) {
            return;
        }

        System.setProperty(
                "tradeadviser.strategy.requireBacktestBeforeLive",
                String.valueOf(settings.requireBacktestBeforeLive()));
        System.setProperty(
                "tradeadviser.strategy.requirePaperTradingBeforeLive",
                String.valueOf(settings.requirePaperTradingBeforeLive()));
        System.setProperty(
                "tradeadviser.strategy.autoAssignBest",
                String.valueOf(settings.autoAssignBestStrategy()));
        System.setProperty(
                "tradeadviser.strategy.minScore",
                String.valueOf(settings.minStrategyScore()));
        System.setProperty(
                "tradeadviser.strategy.topCandidates",
                String.valueOf(settings.topStrategiesToPaperTrade()));

        System.setProperty(
                "tradeadviser.execution.smallAccountMode",
                String.valueOf(settings.smallAccountModeEnabled()));
        System.setProperty(
                "tradeadviser.execution.smallAccountThreshold",
                String.valueOf(settings.smallAccountThreshold()));
        System.setProperty(
                "tradeadviser.execution.smallAccountTradeUnits",
                String.valueOf(settings.smallAccountUnits()));
        System.setProperty(
                "tradeadviser.execution.preventOpenCloseSameCycle",
                String.valueOf(settings.preventOpenCloseSameCycle()));
        System.setProperty(
                "tradeadviser.execution.preventInstantReverse",
                String.valueOf(settings.preventInstantReverse()));
        System.setProperty(
                "tradeadviser.execution.symbolCooldownSeconds",
                String.valueOf(settings.symbolCooldownSeconds()));

        log.info(
                "Applied system safety settings: requireBacktest={}, requirePaper={}, autoAssignBest={}, minScore={}, smallAccountMode={}, preventReverse={}",
                settings.requireBacktestBeforeLive(),
                settings.requirePaperTradingBeforeLive(),
                settings.autoAssignBestStrategy(),
                settings.minStrategyScore(),
                settings.smallAccountModeEnabled(),
                settings.preventInstantReverse());
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public void start(
            @NotNull TradingService tradingService,
            TradePair selectedTradePair) {
        this.tradingService = Objects.requireNonNull(tradingService, "tradingService cannot be null");
        this.selectedTradePair = selectedTradePair;

        // Initialize and start EventBusManager for event-driven architecture
        this.eventBusManager = EventBusManager.getInstance();
        this.eventBusManager.start();
        log.info("✅ EventBusManager started - Event-driven architecture active");

        // Wire EventPersistenceListener to persist all events to database
       // wireEventPersistenceListener();

        // Register default agents now that tradingService is available
        registerDefaultAgents();

        smartBot.start(exchange, tradingService, selectedTradePair);

        if (signalMonitorService != null) {
            signalMonitorService.start(smartBot.getEventBus());
            log.info("Signal monitor wired to SmartBot event bus");
        }

        // Wire Symbol Agent Updater to SmartBot's event bus
        if (symbolAgentUpdater != null) {
            symbolAgentUpdater.start();
            log.info("\u2705 Symbol agent updater wired to SmartBot - Real-time UI updates enabled");
        }

        // Wire Telegram event listener to SmartBot's event bus
        if (telegramEventListener != null) {
            telegramEventListener.start();
            log.info("\u2705 Telegram event listener wired to SmartBot");
        }

        // Auto-detect and set Telegram chat ID
        if (telegramNotifier != null && !telegramNotifier.hasTargetChat()) {
            telegramNotifier.detectAndUseLatestChatId()
                    .ifPresentOrElse(
                            chatId -> log.info("\uD83D\uDCF1 Telegram chat auto-detected: {}", chatId),
                            () -> log.warn(
                                    "\u26A0\uFE0F No Telegram chat detected. Send /start to the bot or add it to a group."));
        }

        // Start Telegram polling if bot token is configured
        startTelegramPolling();

        notifyAllChannels("SmartBot", "\uD83E\uDD16 SmartBot started.");
    }


    /**
     * Initialize per-symbol agents for all symbols in the market watch list.
     * Safe to call multiple times — existing agents are kept, new ones added.
     */
    public void initializeSymbolAgents(java.util.Collection<TradePair> symbols) {
        if (symbols == null || symbols.isEmpty() || smartBot == null) return;
        for (TradePair symbol : symbols) {
            symbolAgents.computeIfAbsent(symbol, s -> {
                SymbolAgent agent =
                        new SymbolAgent(s, symbolAgentManager);

                log.debug("SymbolAgent registered in runtime for {}", s.toString('/'));
                return agent;
            });
        }
        log.info("✅ {} symbol agents active", symbolAgents.size());
    }

    public void stop() {
        stopStreaming();

        // Stop per-symbol agents
        symbolAgents.values().forEach(agent -> {
            try { agent.stop(); } catch (Exception ignored) {}
        });
        symbolAgents.clear();

        // Stop live metrics tracking
        liveMetricsTracker.stopTracking();

        // Stop symbol agent updater
        if (symbolAgentUpdater != null) {
            symbolAgentUpdater.stop();
            log.info("\u2705 Symbol agent updater stopped");
        }

        // Stop Telegram event listener
        if (telegramEventListener != null) {
            telegramEventListener.stop();
            log.info("\u2705 Telegram event listener stopped");
        }

        // Stop Telegram polling
        stopTelegramPolling();

        if (signalMonitorService != null) {
            signalMonitorService.stop();
        }

        // Shutdown EventBusManager gracefully
        if (eventBusManager != null) {
            eventBusManager.shutdown();
            log.info("✅ EventBusManager shutdown complete - Event-driven architecture stopped");
        }

        smartBot.stop();

        notifyAllChannels("SmartBot stopped", "\uD83E\uDD16 SmartBot stopped.");
    }

    public void connect() {
        log.info("SystemCore: Connecting exchange={}", exchange.getName());
        exchange.connect();
    }

    public void disconnect() {
        log.info("SystemCore: Disconnecting exchange={}", exchange.getName());

        stopStreaming();

        try {
            exchange.disconnect();
        } catch (Exception exception) {
            log.warn("SystemCore: Exchange disconnect failed: {}", exception.getMessage(), exception);
        }
    }

    public boolean isReady() {
        return exchange != null
                && smartBot != null
                && executionEngine != null
                && riskManagementSystem != null
                && aiReasoningService != null
                && tradeExecutionCoordinator != null
                && strategyEngine != null;
    }


    /**
     * Record a completed trade into the live metrics tracker.
     * Called by TradingService after every executed or closed trade.
     */
    public void recordCompletedTrade(Trade trade) {
        if (trade != null) {
            liveMetricsTracker.recordTrade(trade);
        }
    }

    /**
     * Update the live metrics tracker with the latest account balance.
     * Called periodically to keep drawdown and equity curve accurate.
     */
    public void updatePerformanceBalance(double balance) {
        liveMetricsTracker.updateBalance(balance);
    }

    // ---------------------------------------------------------------------
    // Bot flags
    // ---------------------------------------------------------------------


    public void setAutoTradingEnabled(boolean enabled) {
        // Safety gate: prevent auto trading if system health has critical blockers
        if (enabled && !canTradeNow()) {
            SystemHealthSnapshot health = getSystemHealth();
            log.warn("Cannot enable auto trading: system has critical issues. Blockers: {}", health.getBlockers());
            notifyAllChannels(
                    "Auto Trading Blocked",
                    "\uD83D\uDEAB Cannot enable auto trading due to system health issues:\n\n" +
                            String.join("\n", health.getBlockers()) + "\n\n" +
                            "Please resolve these issues and try again.");
            return;
        }

        smartBot.setAutoTradingEnabled(enabled);
        setAiReasoningEnabled(true);

        notifyAllChannels(
                "Auto trading",
                enabled ? "\uD83D\uDFE2 Auto trading enabled." : "\uD83D\uDD34 Auto trading disabled.");
    }

    public void setAiReasoningEnabled(boolean enabled) {
        smartBot.setAiReasoningEnabled(enabled);

        notifyAllChannels(
                "AI reasoning",
                enabled ? "\uD83E\uDDE0 AI reasoning enabled." : "\uD83E\uDDE0 AI reasoning disabled.");
    }

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

    public void switchStreamingMode(StreamingMode mode) {
        // Set the preferred streaming mode - bot may not be started yet
        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        publishSystemEvent(
                "SYSTEM_CORE_STREAMING_MODE_CHANGED",
                "Streaming mode changed to %s".formatted(safeMode.name()));

        // Only start streaming if bot is already running
        if (smartBot.isStarted() && streaming.get() && selectedTradePair != null) {
            startStreaming(selectedTradePair, safeMode);
        } else {
            log.info("SystemCore streaming mode set to {} (waiting for bot to start)", safeMode);
        }
    }

    public void startStreaming(Collection<TradePair> tradePairs, StreamingMode mode) {
        ensureBotStarted();

        Set<TradePair> selectedPairs = new LinkedHashSet<>();
        if (tradePairs != null) {
            for (TradePair pair : tradePairs) {
                if (pair != null) {
                    selectedPairs.add(pair);

                }
            }
        }

        if (selectedPairs.isEmpty()) {
            log.warn("Cannot start streaming: no symbols selected.");
            notifyAllChannels(
                    "Streaming not started",
                    "Streaming was not started because no symbols are selected.");
            return;
        }

        stopStreaming();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        this.selectedTradePair = selectedPairs.iterator().next();

        activeSubscription = buildSubscription(selectedPairs, safeMode);
        streamConsumer = createAgentStreamConsumer();

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            String message = "Streaming started using %s mode=%s for %d symbols".formatted(
                    exchange.getStreamTransport(),
                    safeMode.name(),
                    selectedPairs.size());

            publishSystemEvent("SYSTEM_CORE_STREAMING_STARTED", message);
            notifyAllChannels("Streaming started", message);

            log.info(
                    "SystemCore streaming started. exchange={} symbols={} mode={}",
                    exchange.getName(),
                    selectedPairs.size(),
                    safeMode);

        } catch (Exception exception) {
            streaming.set(false);
            publishErrorEvent("SystemCore", exception, "Failed to start exchange streaming.");
            notifyAllChannels(
                    "Streaming failed",
                    "Failed to start streaming: %s".formatted(rootMessage(exception)));
            log.error("Failed to start streaming", exception);
        }
    }

    public void startStreaming(TradePair tradePair, StreamingMode mode) {
        ensureBotStarted();

        if (tradePair == null) {
            log.warn("Cannot start streaming: tradePair is null.");
            notifyAllChannels(
                    "Streaming not started",
                    "\u26A0\uFE0F Streaming was not started because no symbol is selected.");
            return;
        }

        stopStreaming();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;
        this.selectedTradePair = tradePair;

        activeSubscription = buildSubscription(tradePair, safeMode);
        streamConsumer = createAgentStreamConsumer();

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            String message = "Streaming started using %s mode=%s for %s".formatted(
                    exchange.getStreamTransport(),
                    safeMode.name(),
                    tradePairText(tradePair));

            publishSystemEvent("SYSTEM_CORE_STREAMING_STARTED", message);
            notifyAllChannels("Streaming started", "\uD83D\uDCE1 %s".formatted(message));

            log.info(
                    "SystemCore streaming started. exchange={} pair={} mode={} transport={}",
                    exchange.getName(),
                    tradePair,
                    safeMode,
                    exchange.getStreamTransport());

            // tradePairs=exchange.getTradePairSymbol();
            if (mode == StreamingMode.EVERYTHING) {
                streamEverything(tradePair);
            } else if (mode == StreamingMode.MARKET_DATA) {

                streamMarketData(tradePair);
            } else if (mode == StreamingMode.ACCOUNT_DATA) {
                streamAccountData();
            } else if (mode == StreamingMode.TICKER_ONLY) {
                streamTicker(tradePair);
            } else if (mode == StreamingMode.CUSTOM) {
                streamCustom(tradePair, true, true, true, true, true, true, true, true, true);
            } else if (mode == StreamingMode.TRADES_ONLY) {
                streamTrades(tradePair);
            } else if (mode == StreamingMode.SAFE_DEFAULT) {

                streamMarketData(tradePair);
            } else if (mode == StreamingMode.ORDER_BOOK_ONLY) {

                streamOrderBookOnly(tradePair);
            }

        } catch (Exception exception) {
            streaming.set(false);
            publishErrorEvent("SystemCore", exception, "Failed to start exchange streaming.");
            notifyAllChannels(
                    "Streaming failed",
                    "\u274C Failed to start streaming: %s".formatted(rootMessage(exception)));
            log.error("Failed to start streaming", exception);
        }
    }

    public void stopStreaming() {
        if (activeSubscription == null) {
            streaming.set(false);
            return;
        }

        try {
            exchange.stopStreaming(activeSubscription);
            publishSystemEvent("SYSTEM_CORE_STREAMING_STOPPED", "Streaming stopped.");
            notifyAllChannels("Streaming stopped", "\uD83D\uDED1 Streaming stopped.");
        } catch (Exception exception) {
            log.warn("Failed to stop streaming cleanly", exception);
            publishErrorEvent("SystemCore", exception, "Failed to stop streaming cleanly.");
        } finally {
            activeSubscription = null;
            streamConsumer = null;
            streaming.set(false);

        }
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public void streamEverything(TradePair tradePair) {
        startStreaming(tradePair, StreamingMode.EVERYTHING);
    }

    public void streamMarketData(TradePair tradePair) {
        startStreaming(tradePair, StreamingMode.MARKET_DATA);
    }

    public void streamAccountData() {
        ensureBotStarted();

        if (selectedTradePair == null) {
            throw new IllegalStateException("No selected trade pair available for account-data stream.");
        }

        startStreaming(selectedTradePair, StreamingMode.ACCOUNT_DATA);
    }

    public void streamTicker(TradePair... tradePairs) {
        startSimpleStream(StreamingMode.TICKER_ONLY, tradePairs);
    }

    public void streamTrades(TradePair... tradePairs) {
        startSimpleStream(StreamingMode.TRADES_ONLY, tradePairs);
    }

    public void streamOrderBookOnly(TradePair... tradePairs) {
        startSimpleStream(StreamingMode.ORDER_BOOK_ONLY, tradePairs);
    }

    private void startSimpleStream(StreamingMode mode, TradePair... tradePairs) {
        ensureBotStarted();

        if (tradePairs == null || tradePairs.length == 0) {
            throw new IllegalArgumentException("At least one trade pair is required.");
        }

        Set<TradePair> pairs = new LinkedHashSet<>();
        for (TradePair pair : tradePairs) {
            if (pair != null) {
                pairs.add(pair);
            }
        }

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("At least one non-null trade pair is required.");
        }

        startStreaming(pairs, mode);
    }

    public void streamCustom(
            TradePair tradePair,
            boolean ticker,
            boolean trades,
            boolean candles,
            boolean orderBook,
            boolean account,
            boolean orders,
            boolean fills,
            boolean positions,
            boolean balances) {
        ensureBotStarted();

        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        stopStreaming();

        this.selectedTradePair = tradePair;

        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        subscription.setTradePairs(Set.of(tradePair));
        subscription.setTicker(ticker);
        subscription.setTrades(trades);
        subscription.setCandles(candles);
        subscription.setOrderBook(orderBook);
        subscription.setAccount(account);
        subscription.setOrders(orders);
        subscription.setFills(fills);
        subscription.setPositions(positions);
        subscription.setBalances(balances);

        streamConsumer = createAgentStreamConsumer();
        activeSubscription = subscription;
        currentStreamingMode = StreamingMode.CUSTOM;

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            publishSystemEvent(
                    "SYSTEM_CORE_CUSTOM_STREAMING_STARTED",
                    "Custom streaming started for %s".formatted(tradePairText(tradePair)));

        } catch (Exception exception) {
            streaming.set(false);
            activeSubscription = null;
            streamConsumer = null;
            publishErrorEvent("SystemCore", exception, "Failed to start custom streaming.");
            throw new IllegalStateException("Failed to start custom streaming", exception);
        }
    }

    public StreamingMode[] getAvailableStreamingModes() {
        return StreamingMode.values();
    }

    public String getStreamingModeDescription(StreamingMode mode) {
        return mode == null ? "" : mode.description;
    }

    public String getSubscriptionSummary() {
        if (activeSubscription == null) {
            return "No active subscription.";
        }

        StringBuilder summary = new StringBuilder();

        summary.append("mode=").append(currentStreamingMode);
        summary.append("description=").append(getStreamingModeDescription(currentStreamingMode));
        summary.append(", streaming=").append(streaming.get());
        summary.append(", pairs=").append(activeSubscription.getTradePairs());
        summary.append(", flags=[");

        if (activeSubscription.isTicker()) {
            summary.append("ticker ");
        }

        if (activeSubscription.isTrades()) {
            summary.append("trades ");
        }

        if (activeSubscription.isCandles()) {
            summary.append("candles ");
        }

        if (activeSubscription.isOrderBook()) {
            summary.append("orderBook ");
        }

        if (activeSubscription.isAccount()) {
            summary.append("account ");
        }

        if (activeSubscription.isOrders()) {
            summary.append("orders ");
        }

        if (activeSubscription.isFills()) {
            summary.append("fills ");
        }

        if (activeSubscription.isPositions()) {
            summary.append("positions ");
        }

        if (activeSubscription.isBalances()) {
            summary.append("balances ");
        }

        summary.append("]");

        return summary.toString();
    }

    /**
     * Check if this is an OANDA exchange
     */
    private boolean isOandaExchange() {
        if (exchange == null)
            return false;
        String exchangeId = exchange.getExchangeId();
        String exchangeName = exchange.getName();
        return (exchangeId != null && exchangeId.equalsIgnoreCase("oanda")) ||
                (exchangeName != null && exchangeName.equalsIgnoreCase("oanda"));
    }

    private ExchangeStreamSubscription buildSubscription(
            TradePair tradePair,
            StreamingMode mode) {
        return buildSubscription(tradePair == null ? Set.of() : Set.of(tradePair), mode);
    }

    private ExchangeStreamSubscription buildSubscription(
            Set<TradePair> tradePairs,
            StreamingMode mode) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        Set<TradePair> pairs = tradePairs == null ? Set.of() : tradePairs;

        subscription.setTradePairs(pairs);

        switch (mode) {
            case EVERYTHING -> {
                subscription.setTicker(true);
                subscription.setTrades(true);
                subscription.setCandles(true);
                subscription.setOrderBook(true);
                subscription.setAccount(true);
                subscription.setOrders(true);
                subscription.setFills(true);
                subscription.setPositions(true);
                subscription.setBalances(true);
            }
            case MARKET_DATA -> {
                subscription.setTicker(true);
                subscription.setTrades(true);
                subscription.setCandles(true);
                subscription.setOrderBook(true);
            }
            case ACCOUNT_DATA -> {
                subscription.setAccount(true);
                subscription.setOrders(true);
                subscription.setFills(true);
                subscription.setPositions(true);
                subscription.setBalances(true);
            }
            case TICKER_ONLY, CUSTOM -> subscription.setTicker(true);
            case TRADES_ONLY -> subscription.setTrades(true);
            case ORDER_BOOK_ONLY -> subscription.setOrderBook(true);
            case SAFE_DEFAULT -> {
                // Safe defaults based on exchange capabilities
                subscription.setTicker(true);
                subscription.setCandles(true);
                subscription.setAccount(true);
                subscription.setPositions(true);
                subscription.setBalances(true);

                // Exchange-specific adjustments
                if (isOandaExchange()) {
                    // OANDA: disable heavy polling streams
                    subscription.setOrderBook(false);
                    subscription.setTrades(false);
                    subscription.setFills(false);
                    subscription.setOrders(true); // Allow orders with adapter-side caching
                } else {
                    // Coinbase and others: enable more streams
                    subscription.setTrades(true);
                    subscription.setOrderBook(true);
                    subscription.setOrders(true);
                }
            }
            default -> {
                subscription.setTicker(true);
                subscription.setTrades(true);
            }
        }

        return subscription;
    }

    // ---------------------------------------------------------------------
    // Stream consumer -> bot event bus
    // ---------------------------------------------------------------------

    private @NotNull ExchangeStreamConsumer createAgentStreamConsumer() {
        AgentEventBus eventBus = smartBot.getEventBus();

        return new ExchangeStreamConsumer() {

            @Override
            public void onConnected(String exchangeName) {
                eventBus.publish(event(
                        "STREAM_CONNECTED",
                        exchangeName,
                        exchangeName,
                        Map.of()));

                notifyAllChannels("Stream connected", "\u2705 Stream connected: " + exchangeName);
            }

            @Override
            public void onDisconnected(String exchangeName, String reason) {
                eventBus.publish(event(
                        "STREAM_DISCONNECTED",
                        exchangeName,
                        reason,
                        Map.of()));

                notifyAllChannels(
                        "Stream disconnected",
                        "\u26A0\uFE0F Stream disconnected: %s | Reason: %s".formatted(exchangeName, reason));
            }

            @Override
            public void onStatus(@Nullable String exchangeName, @Nullable String message) {

            }

            @Override
            public void onError(String exchangeName, Throwable throwable) {
                systemEventRecorder.recordExecutionError(rootMessage(throwable));
                publishErrorEvent(exchangeName, throwable, "Exchange stream error.");

                notifyAllChannels(
                        "Stream error",
                        "\u274C Stream error on %s: %s".formatted(exchangeName, rootMessage(throwable)));
            }

            @Override
            public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
                systemEventRecorder.recordMarketTick();
                Map<String, Object> tickMeta = new java.util.LinkedHashMap<>();
                tickMeta.put("tradePair", tradePairText(tradePair));
                tickMeta.put("tradePairObject", tradePair);
                eventBus.publish(event(AgentEvent.MARKET_TICK, exchangeName, ticker, tickMeta));
            }

            @Override
            public void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
                eventBus.publish(event(
                        AgentEvent.MARKET_TRADE,
                        exchangeName,
                        trade,
                        Map.of("tradePair", tradePairText(tradePair))));
            }

            @Override
            public void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
                systemEventRecorder.recordMarketTick(); // Track as market data event
                eventBus.publish(event(
                        AgentEvent.ORDER_BOOK_UPDATE,
                        exchangeName,
                        orderBook,
                        Map.of("tradePair", tradePairText(tradePair))));
            }

            @Override
            public void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
                systemEventRecorder.recordMarketTick(); // Track as market data event
                eventBus.publish(event(
                        AgentEvent.MARKET_CANDLE,
                        exchangeName,
                        candleData,
                        Map.of(
                                "tradePair", tradePairText(tradePair),
                                "tradePairObject", tradePair,
                                "symbol", tradePairText(tradePair),
                                "timeframe", "1h",
                                "current", candleData == null ? 0.0 : candleData.closePrice(),
                                "volume", candleData == null ? 0.0 : candleData.volume())));
            }

            @Override
            public void onAccount(String exchangeName, Account account) {
                try {
                    double balance = account != null ? account.balancesView().values().stream()
                            .mapToDouble(Double::doubleValue).sum() : 0;
                    systemEventRecorder.recordAccountUpdate(balance);
                } catch (Exception e) {
                    log.debug("Could not record account balance", e);
                }
                eventBus.publish(event(
                        AgentEvent.ACCOUNT_UPDATE,
                        exchangeName,
                        account,
                        Map.of()));
            }

            @Override
            public void onBalanceChanged(String exchangeName, Account account) {
                eventBus.publish(event(
                        "BALANCE_UPDATE",
                        exchangeName,
                        account,
                        Map.of()));
            }

            @Override
            public boolean containsKey(TradePair tradePair) {
                return ExchangeStreamConsumer.super.containsKey(tradePair);
            }

            @Override
            public ExchangeStreamConsumer get(TradePair tradePair) {
                return ExchangeStreamConsumer.super.get(tradePair);
            }

            @Override
            public void put(@NotNull TradePair tradePair, ExchangeStreamConsumer liveTradesConsumer) {
                ExchangeStreamConsumer.super.put(tradePair, liveTradesConsumer);
            }

            @Override
            public void remove(TradePair tradePair) {
                ExchangeStreamConsumer.super.remove(tradePair);
            }

            @Override
            public void acceptTrades(Trade newTrade) {
                ExchangeStreamConsumer.super.acceptTrades(newTrade);
            }

            @Override
            public void onOpenOrder(String exchangeName, OpenOrder order) {
                eventBus.publish(event(
                        AgentEvent.ORDER_UPDATE,
                        exchangeName,
                        order,
                        Map.of()));
            }

            @Override
            public void onBalance(@Nullable String exchangeName, @Nullable Account account) {

            }

            @Override
            public void onOrder(@Nullable String exchangeName, @Nullable OpenOrder order) {

            }

            @Override
            public void onOpenOrders(String exchangeName, List<OpenOrder> orders) {
                ExchangeStreamConsumer.super.onOpenOrders(exchangeName, orders);
            }

            @Override
            public void onPosition(String exchangeName, Position position) {
                eventBus.publish(event(
                        AgentEvent.POSITION_UPDATE,
                        exchangeName,
                        position,
                        Map.of()));
            }

            @Override
            public void onOrders(String exchangeName, List<OpenOrder> orders) {

            }

            @Override
            public void onFill(String exchangeName, TradePair tradePair, Trade fill) {

            }

            @Override
            public void onPositions(String exchangeName, List<Position> positions) {
                ExchangeStreamConsumer.super.onPositions(exchangeName, positions);
            }

            @Override
            public void onOrderAccepted(String exchangeName, String orderId) {
                systemEventRecorder.recordOrderAccepted();
                eventBus.publish(event(
                        "ORDER_ACCEPTED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order accepted", "\u2705 Order accepted: " + orderId);
            }

            @Override
            public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
                systemEventRecorder.recordOrderRejected(reason);
                eventBus.publish(event(
                        AgentEvent.ORDER_REJECTED,
                        exchangeName,
                        reason,
                        Map.of("clientOrderId", safe(clientOrderId))));

                notifyAllChannels(
                        "Order rejected",
                        "\u274C Order rejected: %s | Reason: %s".formatted(clientOrderId, reason));
            }

            @Override
            public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
                eventBus.publish(event(
                        AgentEvent.ORDER_FILLED,
                        exchangeName,
                        fill,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order filled", "\uD83D\uDCB0 Order filled: " + orderId);
            }

            @Override
            public void onOrderCancelled(String exchangeName, String orderId) {
                eventBus.publish(event(
                        "ORDER_CANCELLED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order cancelled", "\uD83D\uDFE1 Order cancelled: " + orderId);
            }

            @Override
            public void onRawMessage(String exchangeName, String channel, String rawJson) {
                eventBus.publish(event(
                        "RAW_STREAM_MESSAGE",
                        exchangeName,
                        rawJson,
                        Map.of("channel", safe(channel))));
            }

            @Override
            public boolean hasReceivedEvents() {
                return false;
            }

            @Override
            public boolean hasErrors() {
                return false;
            }

            @Override
            public UiExchangeStreamConsumer onOrdersUpdate(@Nullable Consumer<List<OpenOrder>> setAll) {
                return null;
            }
        };
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    private void configureNotifiers() {

        if (!fromEmail.isBlank() && !toEmail.isBlank()) {
            this.emailNotifier = new EmailNotifier(fromEmail, toEmail);
            log.info("\u2705 Email notifier configured");
        }
    }

    private void notifyAllChannels(String title, String message) {
        notifyTelegram(message);
        notifyEmail(title, message);
    }

    private void notifyTelegram(String message) {
        if (telegramNotifier == null || message == null || message.isBlank()) {
            return;
        }

        try {
            telegramNotifier.send(message);
        } catch (Exception exception) {
            log.warn("Telegram notification failed: {}", exception.getMessage(), exception);
        }
    }

    private void notifyEmail(String title, String message) {
        if (emailNotifier == null || message == null || message.isBlank()) {
            return;
        }

        try {
            emailNotifier.send(NotificationMessage.info(
                    safe(title).isBlank() ? "InvestPro SystemCore" : title,
                    message));
        } catch (Exception exception) {
            log.warn("Email notification failed: {}", exception.getMessage(), exception);
        }
    }

    // ============================================================================
    // TELEGRAM COMMAND SUPPORT METHODS
    // ============================================================================

    /**
     * Get the name of the active strategy.
     * <p>
     * Uses the most recent cached StrategySignal from StrategyEngine.
     */
    public String getActiveStrategyName() {
        if (strategyEngine == null) {
            return "Not available yet";
        }

        StrategySignal latestSignal = getMostRecentStrategySignal();

        if (latestSignal == null) {
            return "No active strategy signal yet";
        }

        String strategyId = safe(latestSignal.getStrategyId());

        if (strategyId.isBlank()) {
            return "Unknown strategy";
        }

        return strategyId;
    }

    /**
     * Get risk management metrics.
     * <p>
     * Uses reflection fallbacks so this compiles even while RiskManagementSystem
     * is still evolving. If RiskManagementSystem later exposes formal getters,
     * replace reflection with direct calls.
     */
    public String getRiskMetrics() {
        if (riskManagementSystem == null) {
            return "Not available yet";
        }

        Object lastDecision = invokeNoArg(riskManagementSystem,
                "getLastRiskDecision",
                "getLastDecision",
                "lastDecision");

        if (lastDecision == null) {
            return """
                    Risk Management
                    Status: initialized
                    Last decision: not available yet
                    Small-account mode: configured in risk/execution layer if enabled
                    """;
        }

        Boolean approved = asBoolean(invokeNoArg(lastDecision, "isApproved", "getApproved"));
        Double finalPositionSize = asDouble(invokeNoArg(lastDecision, "getFinalPositionSize"));
        Double riskMultiplier = asDouble(invokeNoArg(lastDecision, "getRiskMultiplier"));
        Double portfolioHeat = asDouble(invokeNoArg(lastDecision, "getPortfolioHeat"));
        String reason = asString(invokeNoArg(
                lastDecision,
                "getApprovalReason",
                "getHumanReadableSummary",
                "getReason"));

        return """
                Risk Management
                Last decision approved: %s
                Final position size: %.4f
                Risk multiplier: %.4f
                Portfolio heat: %.4f
                Reason: %s
                """.formatted(
                approved == null ? "unknown" : approved,
                finalPositionSize == null ? 0.0 : finalPositionSize,
                riskMultiplier == null ? 0.0 : riskMultiplier,
                portfolioHeat == null ? 0.0 : portfolioHeat,
                reason.isBlank() ? "No reason available" : reason);
    }



    private StrategySignal getMostRecentStrategySignal() {
        if (strategyEngine == null
                || strategyEngine.getLastSignalCache() == null
                || strategyEngine.getLastSignalTimestampCache() == null
                || strategyEngine.getLastSignalCache().isEmpty()) {
            return null;
        }

        return strategyEngine.getLastSignalCache()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .max(Comparator.comparingLong(entry -> strategyEngine.getLastSignalTimestampCache()
                        .getOrDefault(entry.getKey(), 0L)))
                .map(Map.Entry::getValue)
                .orElse(null);
    }


    private Object invokeNoArg(Object target, String... methodNames) {
        if (target == null || methodNames == null) {
            return null;
        }

        for (String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) {
                continue;
            }

            try {
                Method method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
                // Try next method name.
            }
        }

        return null;
    }

    private Double asDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    private Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        String text = String.valueOf(value).trim();

        if (text.equalsIgnoreCase("true")) {
            return true;
        }

        if (text.equalsIgnoreCase("false")) {
            return false;
        }

        return null;
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * Get a trade pair by symbol
     * Returns the matching pair or null if not found/selected
     */
    public TradePair getTradePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }

        // Only return selectedTradePair if symbol matches
        if (selectedTradePair != null) {
            String selectedSymbol = selectedTradePair.getSymbol();
            if (selectedSymbol != null && selectedSymbol.equalsIgnoreCase(symbol.trim())) {
                return selectedTradePair;
            }
        }

        // TODO: search tradingService for symbol if available
        // For now, return null for non-matching symbols
        return null;
    }

    /**
     * Check if auto trading is enabled
     */
    public boolean isAutoTradingEnabled() {
        return smartBot != null && smartBot.isAutoTradingEnabled();
    }

    /**
     * Check if trading is allowed on the connected exchange.
     * Returns true only if the exchange is connected and supports either live or
     * paper trading.
     */
    public boolean canSubmitOrders() {
        return exchange != null && exchange.canSubmitOrders();
    }

    /**
     * Check if AI reasoning is enabled
     */
    public boolean isAiReasoningEnabled() {
        return smartBot != null && smartBot.isAiReasoningEnabled();
    }

    /**
     * Start Telegram message polling
     */
    public void startTelegramPolling() {
        if (telegramNotifier != null) {
            telegramNotifier.startPolling();
            log.info("Telegram message polling started");
        }
    }

    /**
     * Stop Telegram message polling
     */
    public void stopTelegramPolling() {
        if (telegramNotifier != null) {
            telegramNotifier.stopPolling();
            log.info("Telegram message polling stopped");
        }
    }

    /**
     * Check if Telegram polling is active
     */
    public boolean isTelegramPollingActive() {
        return telegramNotifier != null && telegramNotifier.isPollingEnabled();
    }

    // =====================================================================
    // AI
    // =====================================================================

    private AiReasoningService createAiReasoningService(Properties config) {
        String provider = config.getProperty("ai.provider", "openai").trim().toLowerCase();
        String apiKey = firstNonBlank(
                config.getProperty("OPENAI_API_KEY"),
                System.getenv("OPENAI_API_KEY"));

        boolean openAiEnabled = "openai".equals(provider) && !apiKey.isBlank();

        if (openAiEnabled) {
            try {
                log.info("SystemCore: Using OpenAiReasoningService.");
                return new OpenAiReasoningService(apiKey);
            } catch (Exception exception) {
                log.warn(
                        "SystemCore: Failed to initialize OpenAiReasoningService. Falling back to LocalAiReasoningService. Reason={}",
                        exception.getMessage());
            }
        }

        log.info("SystemCore: Using LocalAiReasoningService.");
        return new LocalAiReasoningService();
    }

    // ---------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------

    private @NotNull AgentEvent event(
            String type,
            String source,
            Object payload,
            Map<String, Object> metadata) {
        return new AgentEvent(
                safeEventType(type),
                safe(source).isBlank() ? "SystemCore" : safe(source),
                payload,
                Instant.now(),
                metadata == null ? Map.of() : metadata);
    }

    private void publishSystemEvent(String type, String message) {
        smartBot.getEventBus().publish(event(
                safeEventType(type),
                "SystemCore",
                message,
                Map.of()));
    }

    private void publishErrorEvent(String source, Throwable throwable, String message) {
        smartBot.getEventBus().publish(event(
                AgentEvent.ERROR,
                source,
                throwable,
                Map.of(
                        "message", safe(message),
                        "error", rootMessage(throwable))));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void ensureBotStarted() {
        if (!smartBot.isStarted()) {
            throw new IllegalStateException("SmartBot has not been started.");
        }
    }

    private String tradePairText(TradePair tradePair) {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    private @NotNull String safeEventType(String value) {
        String text = safe(value);
        return text.isBlank() ? "UNKNOWN_EVENT" : text;
    }

    private @NotNull String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private @NotNull String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }

        if (second != null && !second.isBlank()) {
            return second.trim();
        }

        return "";
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

    /**
     * Get system diagnostics for troubleshooting and status reporting
     */
    public String getSystemDiagnostics() {

        assert exchange != null;
        return "\uD83D\uDCCA SystemCore Diagnostics\n\n" +

        // Exchange info
                "*Exchange*\n" +
                "Name: " + exchange.getName() + "\n" +
                "ID: " + exchange.getExchangeId() + "\n" +
                exchange.getName() + " Safe Mode: " + (isOandaExchange() ? "\uD83D\uDD34 ENABLED" : "\uD83D\uDFE2 N/A") + "\n\n" +

                // Streaming info
                "*Streaming*\n" +
                "Mode: " + currentStreamingMode.name() + "\n" +
                "Active: " + (streaming.get() ? "\uD83D\uDFE2 YES" : "\uD83D\uDD34 NO") + "\n" +
                "Summary: " + getSubscriptionSummary() + "\n\n" +

                // Bot state
                "*Bot State*\n" +
                "Auto Trading: " + (isAutoTradingEnabled() ? "\uD83D\uDFE2 ENABLED" : "\uD83D\uDD34 DISABLED") + "\n" +
                "AI Reasoning: " + (isAiReasoningEnabled() ? "\uD83D\uDFE2 ENABLED" : "\uD83D\uDD34 DISABLED") + "\n" +
                "Selected Pair: " + (selectedTradePair != null ? selectedTradePair : "None") + "\n\n" +

                // Component readiness
                "*Component Status*\n" +
                "Strategy Engine: " + (strategyEngine != null ? "\u2705 Ready" : "\u274C N/A") + "\n" +
                "Risk System: " + (riskManagementSystem != null ? "\u2705 Ready" : "\u274C N/A") + "\n" +
                "Execution Coordinator: " + (tradeExecutionCoordinator != null ? "\u2705 Ready" : "\u274C N/A") +
                "\n" +
                "  \u251C\u2500 Position Transition Policy: \u2705 Internal\n" +
                "  \u2514\u2500 Symbol Lock Manager: \u2705 Internal\n\n" +

                // Small account config
                "*Small Account Mode*\n" +
                "Enabled: " + (smallAccountModeEnabled ? "\uD83D\uDFE2 YES" : "\uD83D\uDD34 NO") + "\n" +
                "Threshold: $" + String.format("%.2f", smallAccountBalanceThreshold) + "\n" +
                exchange.getName() + " Units: " + String.format("%.2f", smallAccountOandaUnits) + "\n\n" +

                // Telegram status
                "*Telegram*\n" +
                "Token Configured: " + (!telegramToken.isBlank() ? "\u2705 YES" : "\u274C NO") + "\n" +
                "Polling Active: " + (isTelegramPollingActive() ? "\uD83D\uDFE2 YES" : "\uD83D\uDD34 NO") + "\n";
    }

    /**
     * Get current system health snapshot from the monitoring service.
     *
     * @return SystemHealthSnapshot with status of all 9 subsystems and overall
     *         trading capability
     */
    public SystemHealthSnapshot getSystemHealth() {
        return systemMonitorService.checkNow();
    }

    /**
     * Determine if the system is healthy enough to trade now.
     * Returns true only if overall status is HEALTHY or DEGRADED without critical
     * blockers.
     *
     * @return true if system is in trading-capable state, false if critical issues
     *         present
     */
    public boolean canTradeNow() {
        return systemMonitorService.checkNow().isCanTrade();
    }

    // =====================================================================
    // Pre-Trade Validation APIs (for PreTradeValidationEngine)
    // =====================================================================




    /**
     * Get the trading account associated with this system.
     *
     * @return the Account object if available, null if not yet initialized
     */
    @Nullable
    public Account getAccount() {
        // Try to get from exchange (if exchange has account info)
        if (exchange != null) {
            try {
                Object account = null;
                try {
                    // Use reflection to try getAccount method
                    java.lang.reflect.Method method = exchange.getClass().getMethod("getAccount");
                    account = method.invoke(exchange);
                } catch (NoSuchMethodException ignored) {
                    // Exchange doesn't have getAccount
                }

                if (account instanceof Account) {
                    return (Account) account;
                }
            } catch (Exception e) {
                log.debug("Failed to retrieve account from exchange", e);
            }
        }


        return new Account();
    }



}
