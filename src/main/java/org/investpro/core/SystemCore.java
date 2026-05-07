package org.investpro.core;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.LocalAiReasoningService;
import org.investpro.ai.OpenAiReasoningService;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.agents.AgentRegistry;
import org.investpro.core.agents.SystemCoreDependencies;
import org.investpro.core.agents.execution.ExecutionEngine;
import org.investpro.core.agents.execution.TradeExecutionCoordinator;
import org.investpro.core.agents.modules.DefaultTradingAgentModule;
import org.investpro.core.bot.SmartBot;
import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.exchange.infrastructure.ExchangeStreamSubscription;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.repository.RepositoryFactory;
import org.investpro.service.TradingService;

import org.investpro.strategy.StrategyEngine;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SystemCore.class);

    public enum StreamingMode {
        EVERYTHING("All market + account data"),
        MARKET_DATA("Market data only: ticker, trades, candles, order book"),
        ACCOUNT_DATA("Account data only: account, orders, fills, positions, balances"),
        TICKER_ONLY("Ticker updates only"),
        TRADES_ONLY("Market trades only"),
        ORDER_BOOK_ONLY("Order book depth only"),
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

    private TelegramNotifier telegramNotifier;
    private EmailNotifier emailNotifier;

    private String telegramToken;
    private String fromEmail = "nnoelmartial@yahoo.fr";
    private String toEmail = "nguemechieu@live.com";

    private TradingService tradingService;
    private TradePair selectedTradePair;

    private final AtomicBoolean streaming = new AtomicBoolean(false);
    private StreamingMode currentStreamingMode = StreamingMode.EVERYTHING;
    private ExchangeStreamSubscription activeSubscription;
    private ExchangeStreamConsumer streamConsumer;

    public SystemCore(@NotNull Exchange exchange, Properties config) {
        this.exchange = Objects.requireNonNull(exchange, "exchange cannot be null");
        this.config = config == null ? new Properties() : config;

        this.telegramToken = this.config.getProperty("telegram_token", "").trim();
        this.fromEmail = this.config.getProperty("from_email", "").trim();
        this.toEmail = this.config.getProperty("to_email", "").trim();

        configureNotifiers();

        this.executionEngine = new ExecutionEngine(exchange, null, RepositoryFactory.getDatabase());
        this.riskManagementSystem = new RiskManagementSystem();
        this.aiReasoningService = createAiReasoningService(this.config);

        this.tradeExecutionCoordinator = new TradeExecutionCoordinator(
                riskManagementSystem,
                aiReasoningService,
                executionEngine);

        this.strategyEngine = new StrategyEngine(tradeExecutionCoordinator);

        // Create agent registry (agents will be registered when start() is called)
        this.agentRegistry = new AgentRegistry();

        // Create SmartBot with the agent registry
        this.smartBot = new SmartBot(
                org.investpro.core.agents.AgentRuntime.createDefault(),
                new AgentEventBus(),
                agentRegistry);

        log.info(
                "SystemCore initialized. exchange={} aiService={} telegramEnabled={} agents={}",
                exchange.getName(),
                aiReasoningService.getClass().getSimpleName(),
                !telegramToken.isBlank(),
                agentRegistry.size());
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

            SystemCoreDependencies dependencies = new SystemCoreDependencies(
                    exchange,
                    tradingService,
                    strategyEngine,
                    riskManagementSystem,
                    aiReasoningService,
                    executionEngine,
                    tradeExecutionCoordinator);

            DefaultTradingAgentModule defaultModule = new DefaultTradingAgentModule();
            defaultModule.configure(agentRegistry, dependencies);

            log.debug("Default trading agents registered. count={}", agentRegistry.size());

        } catch (Exception e) {
            log.error("Failed to register default agents", e);
            throw new RuntimeException("Failed to initialize agent registry", e);
        }
    }

    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------

    public void start(
            @NotNull TradingService tradingService,
            TradePair selectedTradePair) {
        this.tradingService = Objects.requireNonNull(tradingService, "tradingService cannot be null");
        this.selectedTradePair = selectedTradePair;

        // Register default agents now that tradingService is available
        registerDefaultAgents();

        smartBot.start(exchange, tradingService, selectedTradePair);

        notifyAllChannels("SmartBot", "🤖 SmartBot started.");
    }

    public void stop() {
        stopStreaming();

        smartBot.stop();

        notifyAllChannels("SmartBot stopped", "🤖 SmartBot stopped.");
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

    // ---------------------------------------------------------------------
    // Bot flags
    // ---------------------------------------------------------------------

    public void setAutoTradingEnabled(boolean enabled) {
        smartBot.setAutoTradingEnabled(enabled);

        notifyAllChannels(
                "Auto trading",
                enabled ? "🟢 Auto trading enabled." : "🔴 Auto trading disabled.");
    }

    public void setAiReasoningEnabled(boolean enabled) {
        smartBot.setAiReasoningEnabled(enabled);

        notifyAllChannels(
                "AI reasoning",
                enabled ? "🧠 AI reasoning enabled." : "🧠 AI reasoning disabled.");
    }

    // ---------------------------------------------------------------------
    // Streaming
    // ---------------------------------------------------------------------

    public void switchStreamingMode(StreamingMode mode) {
        ensureBotStarted();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        publishSystemEvent(
                "SYSTEM_CORE_STREAMING_MODE_CHANGED",
                "Streaming mode changed to %s".formatted(safeMode.name()));

        if (streaming.get() && selectedTradePair != null) {
            startStreaming(selectedTradePair, safeMode);
        } else {
            log.info("SystemCore streaming mode set to {}", safeMode);
        }
    }

    public void startStreaming(TradePair tradePair) {
        startStreaming(tradePair, currentStreamingMode);
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
                    "⚠️ Streaming was not started because no symbol is selected.");
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
            notifyAllChannels("Streaming started", "📡 %s".formatted(message));

            log.info(
                    "SystemCore streaming started. exchange={} pair={} mode={} transport={}",
                    exchange.getName(),
                    tradePair,
                    safeMode,
                    exchange.getStreamTransport());

        } catch (Exception exception) {
            streaming.set(false);
            publishErrorEvent("SystemCore", exception, "Failed to start exchange streaming.");
            notifyAllChannels(
                    "Streaming failed",
                    "❌ Failed to start streaming: %s".formatted(rootMessage(exception)));
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
            notifyAllChannels("Streaming stopped", "🛑 Streaming stopped.");
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
            case TICKER_ONLY -> subscription.setTicker(true);
            case TRADES_ONLY -> subscription.setTrades(true);
            case ORDER_BOOK_ONLY -> subscription.setOrderBook(true);
            case CUSTOM -> subscription.setTicker(true);
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

                notifyAllChannels("Stream connected", "✅ Stream connected: " + exchangeName);
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
                        "⚠️ Stream disconnected: %s | Reason: %s".formatted(exchangeName, reason));
            }

            @Override
            public void onError(String exchangeName, Throwable throwable) {
                publishErrorEvent(exchangeName, throwable, "Exchange stream error.");

                notifyAllChannels(
                        "Stream error",
                        "❌ Stream error on %s: %s".formatted(exchangeName, rootMessage(throwable)));
            }

            @Override
            public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
                eventBus.publish(event(
                        AgentEvent.MARKET_TICK,
                        exchangeName,
                        ticker,
                        Map.of("tradePair", tradePairText(tradePair))));
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
                eventBus.publish(event(
                        AgentEvent.ORDER_BOOK_UPDATE,
                        exchangeName,
                        orderBook,
                        Map.of("tradePair", tradePairText(tradePair))));
            }

            @Override
            public void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
                eventBus.publish(event(
                        AgentEvent.MARKET_CANDLE,
                        exchangeName,
                        candleData,
                        Map.of("tradePair", tradePairText(tradePair))));
            }

            @Override
            public void onAccount(String exchangeName, Account account) {
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
            public void onOpenOrder(String exchangeName, OpenOrder order) {
                eventBus.publish(event(
                        AgentEvent.ORDER_UPDATE,
                        exchangeName,
                        order,
                        Map.of()));
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
            public void onOrderAccepted(String exchangeName, String orderId) {
                eventBus.publish(event(
                        "ORDER_ACCEPTED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order accepted", "✅ Order accepted: " + orderId);
            }

            @Override
            public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
                eventBus.publish(event(
                        AgentEvent.ORDER_REJECTED,
                        exchangeName,
                        reason,
                        Map.of("clientOrderId", safe(clientOrderId))));

                notifyAllChannels(
                        "Order rejected",
                        "❌ Order rejected: %s | Reason: %s".formatted(clientOrderId, reason));
            }

            @Override
            public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
                eventBus.publish(event(
                        AgentEvent.ORDER_FILLED,
                        exchangeName,
                        fill,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order filled", "💰 Order filled: " + orderId);
            }

            @Override
            public void onOrderCancelled(String exchangeName, String orderId) {
                eventBus.publish(event(
                        "ORDER_CANCELLED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))));

                notifyAllChannels("Order cancelled", "🟡 Order cancelled: " + orderId);
            }

            @Override
            public void onRawMessage(String exchangeName, String channel, String rawJson) {
                eventBus.publish(event(
                        "RAW_STREAM_MESSAGE",
                        exchangeName,
                        rawJson,
                        Map.of("channel", safe(channel))));
            }
        };
    }

    // ---------------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------------

    public void configureTelegram(String telegramToken) {
        this.telegramToken = safe(telegramToken);

        if (!this.telegramToken.isBlank()) {
            this.telegramNotifier = new TelegramNotifier(this.telegramToken);
        }
    }

    public void configureEmail(String fromEmail, String toEmail) {
        this.fromEmail = safe(fromEmail);
        this.toEmail = safe(toEmail);

        if (!this.fromEmail.isBlank() && !this.toEmail.isBlank()) {
            this.emailNotifier = new EmailNotifier(this.fromEmail, this.toEmail);
        }
    }

    private void configureNotifiers() {
        if (!telegramToken.isBlank()) {
            this.telegramNotifier = new TelegramNotifier(telegramToken);
        }

        if (!fromEmail.isBlank() && !toEmail.isBlank()) {
            this.emailNotifier = new EmailNotifier(fromEmail, toEmail);
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

    // ---------------------------------------------------------------------
    // AI
    // ---------------------------------------------------------------------

    private AiReasoningService createAiReasoningService(Properties config) {
        String provider = config.getProperty("ai.provider", "openai").trim().toLowerCase();
        String apiKey = firstNonBlank(
                config.getProperty("openai.api_key"),
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

    private String firstNonBlank(String first, String second) {
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
}
