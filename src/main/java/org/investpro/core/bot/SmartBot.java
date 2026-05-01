package org.investpro.core.bot;

import lombok.Getter;
import lombok.Setter;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.agents.AgentRuntime;
import org.investpro.core.EmailNotifier;
import org.investpro.core.NotificationMessage;
import org.investpro.core.TelegramNotifier;
import org.investpro.data.Account;
import org.investpro.data.CandleData;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.ExchangeStreamConsumer;
import org.investpro.exchange.ExchangeStreamSubscription;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.service.TradingService;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SmartBot is the high-level AI/agent controller for InvestPro.
 *
 * Responsibilities:
 * - start/stop the agent runtime
 * - connect exchange stream events to the agent event bus
 * - control auto-trading and AI reasoning flags
 * - send optional Telegram/email notifications
 *
 * Safety rule:
 * SmartBot does NOT directly place trades.
 * ExecutionAgent is the only component allowed to call exchange.createOrder(...).
 */
@Getter
@Setter
public class SmartBot {




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
    private  StreamingMode streamingMode;
    private static final Logger logger = LoggerFactory.getLogger(SmartBot.class);

    private final AgentEventBus eventBus;
    private final AgentRuntime runtime;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean streaming = new AtomicBoolean(false);

    private AgentContext context;

    private StreamingMode currentStreamingMode = StreamingMode.EVERYTHING;
    private ExchangeStreamSubscription activeSubscription;
    private ExchangeStreamConsumer streamConsumer;

    private TelegramNotifier telegramNotifier;
    private EmailNotifier emailNotifier;

    private String telegramToken = "";
    private String fromEmail = "";
    private String toEmail = "";

    public SmartBot() {
        this(new AgentRuntime(), new AgentEventBus());
    }

    public SmartBot(
            @NotNull AgentRuntime runtime,
            @NotNull AgentEventBus eventBus
    ) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");


    }

    /**
     * Start the SmartBot with notifications disabled unless already configured.
     */
    public void start(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            TradePair selectedTradePair
    ) {
        start(exchange, tradingService, selectedTradePair, telegramToken);
    }

    /**
     * Start the SmartBot with selected exchange, trading service, pair,
     * and optional Telegram bot token.
     */
    public void start(
            @NotNull Exchange exchange,
            @NotNull TradingService tradingService,
            TradePair selectedTradePair,
            String telegramToken
    ) {
        Objects.requireNonNull(exchange, "exchange must not be null");
        Objects.requireNonNull(tradingService, "tradingService must not be null");

        if (!started.compareAndSet(false, true)) {
            logger.warn("SmartBot is already started.");
            return;
        }

        this.telegramToken = safe(telegramToken);
        configureNotifiers();

        this.context = new AgentContext();
        this.context.setExchange(exchange);
        this.context.setTradingService(tradingService);
        this.context.setEventBus(eventBus);
        this.context.setSelectedTradePair(selectedTradePair);
        this.context.setSelectedSymbol(tradePairText(selectedTradePair));

        /*
         * Safe defaults.
         * Auto-trading starts OFF.
         * AI reasoning starts ON, but AI still cannot execute directly.
         */
        this.context.setAutoTradingEnabled(false);
        this.context.setAiReasoningEnabled(true);
        this.context.setMaxRiskPerTrade(0.01);
        this.context.setMaxDailyLoss(0.03);

        try {
            eventBus.start();
            runtime.start(context);

            publishSystemEvent("SMART_BOT_STARTED", "SmartBot started.");
            notifyAllChannels("SmartBot", "🤖 SmartBot started.");

            logger.info(
                    "SmartBot started exchange={} pair={}",
                    exchange.getName(),
                    selectedTradePair
            );
        } catch (Exception exception) {
            started.set(false);
            context = null;

            logger.error("Failed to start SmartBot", exception);
            notifyAllChannels(
                    "SmartBot failed",
                    "❌ SmartBot failed to start: %s".formatted(rootMessage(exception))
            );

            throw new IllegalStateException("Failed to start SmartBot", exception);
        }
    }

    /**
     * Change streaming mode.
     *
     * If streaming is already active, the bot restarts streaming with the new mode.
     */
    public void switchStreamingMode(StreamingMode mode) {
        ensureStarted();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        TradePair selectedPair = context.getSelectedTradePair();

        publishSystemEvent(
                "SMART_BOT_STREAMING_MODE_CHANGED",
                "Streaming mode changed to %s".formatted(safeMode.name())
        );

        if (streaming.get() && selectedPair != null) {
            startStreaming(selectedPair, safeMode);
        } else {
            logger.info("SmartBot streaming mode set to {}", safeMode);
        }
    }

    public void startStreaming(TradePair tradePair) {
        startStreaming(tradePair, currentStreamingMode);
    }

    public void startStreaming(Collection<TradePair> tradePairs, StreamingMode mode) {
        ensureStarted();

        Set<TradePair> selectedPairs = new LinkedHashSet<>();
        if (tradePairs != null) {
            for (TradePair pair : tradePairs) {
                if (pair != null) {
                    selectedPairs.add(pair);
                }
            }
        }

        if (selectedPairs.isEmpty()) {
            logger.warn("Cannot start SmartBot streaming: no symbols selected.");
            notifyAllChannels("Streaming not started", "SmartBot streaming was not started because no symbols are selected.");
            return;
        }

        stopStreaming();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        TradePair firstPair = selectedPairs.iterator().next();
        context.setSelectedTradePair(firstPair);
        context.setSelectedSymbol(tradePairText(firstPair));

        activeSubscription = buildSubscription(selectedPairs, safeMode);
        streamConsumer = createAgentStreamConsumer();

        Exchange exchange = context.getExchange();

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            String message = "Streaming started using %s mode=%s for %d symbols".formatted(
                    exchange.getStreamTransport(),
                    safeMode.name(),
                    selectedPairs.size()
            );

            publishSystemEvent("SMART_BOT_STREAMING_STARTED", message);
            notifyAllChannels("Streaming started", message);
            logger.info("SmartBot streaming started exchange={} symbols={} mode={}", exchange.getName(), selectedPairs.size(), safeMode);
        } catch (Exception exception) {
            streaming.set(false);
            publishErrorEvent("SmartBot", exception, "Failed to start exchange streaming.");
            notifyAllChannels("Streaming failed", "Failed to start streaming: %s".formatted(rootMessage(exception)));
            logger.error("Failed to start SmartBot streaming", exception);
        }
    }

    /**
     * Start exchange streaming and route stream events into the agent bus.
     */
    public void startStreaming(TradePair tradePair, StreamingMode mode) {
        ensureStarted();

        if (tradePair == null) {
            logger.warn("Cannot start SmartBot streaming: tradePair is null.");
            notifyAllChannels(
                    "Streaming not started",
                    "⚠️ SmartBot streaming was not started because no symbol is selected."
            );
            return;
        }

        stopStreaming();

        StreamingMode safeMode = mode == null ? StreamingMode.EVERYTHING : mode;
        this.currentStreamingMode = safeMode;

        context.setSelectedTradePair(tradePair);
        context.setSelectedSymbol(tradePairText(tradePair));

        activeSubscription = buildSubscription(tradePair, safeMode);
        streamConsumer = createAgentStreamConsumer();

        Exchange exchange = context.getExchange();

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            String message = "Streaming started using %s mode=%s for %s".formatted(
                    exchange.getStreamTransport(),
                    safeMode.name(),
                    tradePairText(tradePair)
            );

            publishSystemEvent("SMART_BOT_STREAMING_STARTED", message);
            notifyAllChannels("Streaming started", "📡 %s".formatted(message));

            logger.info(
                    "SmartBot streaming started exchange={} pair={} mode={} transport={}",
                    exchange.getName(),
                    tradePair,
                    safeMode,
                    exchange.getStreamTransport()
            );
        } catch (Exception exception) {
            streaming.set(false);

            publishErrorEvent("SmartBot", exception, "Failed to start exchange streaming.");
            notifyAllChannels(
                    "Streaming failed",
                    "❌ Failed to start streaming: %s".formatted(rootMessage(exception))
            );

            logger.error("Failed to start SmartBot streaming", exception);
        }
    }

    /**
     * Stop current exchange streaming.
     */
    public void stopStreaming() {
        if (context == null || context.getExchange() == null || activeSubscription == null) {
            streaming.set(false);
            return;
        }

        try {
            context.getExchange().stopStreaming(activeSubscription);
            publishSystemEvent("SMART_BOT_STREAMING_STOPPED", "Streaming stopped.");
            notifyAllChannels("Streaming stopped", "🛑 SmartBot streaming stopped.");
        } catch (Exception exception) {
            logger.warn("Failed to stop SmartBot streaming cleanly", exception);
            publishErrorEvent("SmartBot", exception, "Failed to stop streaming cleanly.");
        } finally {
            activeSubscription = null;
            streamConsumer = null;
            streaming.set(false);
        }
    }

    /**
     * Stop all streams and agents.
     */
    public void stop() {
        if (!started.compareAndSet(true, false)) {
            return;
        }

        stopStreaming();

        try {
            runtime.stop();
        } catch (Exception exception) {
            logger.warn("Failed to stop AgentRuntime cleanly", exception);
        }

        try {
            eventBus.stop();
        } catch (Exception exception) {
            logger.warn("Failed to stop AgentEventBus cleanly", exception);
        }

        context = null;
        notifyAllChannels("SmartBot stopped", "🤖 SmartBot stopped.");
        logger.info("SmartBot stopped.");
    }

    /**
     * Enable or disable autonomous execution.
     *
     * When disabled:
     * - agents can still analyze
     * - signals can still be produced
     * - risk can still evaluate
     * - ExecutionAgent should not send orders
     */
    public void setAutoTradingEnabled(boolean enabled) {
        ensureStarted();

        context.setAutoTradingEnabled(enabled);
        runtime.setAutoTradingEnabled(enabled);

        String type = enabled ? "AUTO_TRADING_ENABLED" : "AUTO_TRADING_DISABLED";
        String message = enabled ? "Auto trading enabled." : "Auto trading disabled.";

        publishSystemEvent(type, message);
        notifyAllChannels(
                "Auto trading",
                enabled ? "🟢 %s".formatted(message) : "🔴 %s".formatted(message)
        );

        logger.info("SmartBot auto trading enabled={}", enabled);
    }

    /**
     * Enable or disable AI reasoning.
     *
     * When disabled:
     * ReasoningAgent should use deterministic local reasoning.
     */
    public void setAiReasoningEnabled(boolean enabled) {
        ensureStarted();

        context.setAiReasoningEnabled(enabled);
        runtime.setAiReasoningEnabled(enabled);

        String type = enabled ? "AI_REASONING_ENABLED" : "AI_REASONING_DISABLED";
        String message = enabled ? "AI reasoning enabled." : "AI reasoning disabled.";

        publishSystemEvent(type, message);
        notifyAllChannels("AI reasoning", "🧠 %s".formatted(message));

        logger.info("SmartBot AI reasoning enabled={}", enabled);
    }

    public boolean isStarted() {
        return started.get();
    }

    public boolean isStreaming() {
        return streaming.get();
    }

    public boolean isAutoTradingEnabled() {
        return context != null && context.isAutoTradingEnabled();
    }

    public boolean isAiReasoningEnabled() {
        return context != null && context.isAiReasoningEnabled();
    }

    /**
     * Publish a manual market event into the agent system.
     * Useful for testing without a live stream.
     */
    public void publishMarketEvent(String type, Object payload) {
        ensureStarted();

        eventBus.publish(AgentEvent.market(
                safeEventType(type),
                "SmartBot",
                payload
        ));
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

    private ExchangeStreamSubscription buildSubscription(
            TradePair tradePair,
            StreamingMode mode
    ) {
        return buildSubscription(tradePair == null ? Set.of() : Set.of(tradePair), mode);
    }

    private ExchangeStreamSubscription buildSubscription(
            Set<TradePair> tradePairs,
            StreamingMode mode
    ) {
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();
        Set<TradePair> pairs = tradePairs == null ? Set.of() : tradePairs;

        switch (mode) {
            case EVERYTHING -> {
                subscription.setTradePairs(pairs);
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
                subscription.setTradePairs(pairs);
                subscription.setTicker(true);
                subscription.setTrades(true);
                subscription.setCandles(true);
                subscription.setOrderBook(true);
            }
            case ACCOUNT_DATA -> {
                subscription.setTradePairs(pairs);
                subscription.setAccount(true);
                subscription.setOrders(true);
                subscription.setFills(true);
                subscription.setPositions(true);
                subscription.setBalances(true);
            }
            case TICKER_ONLY -> {
                subscription.setTradePairs(pairs);
                subscription.setTicker(true);
            }
            case TRADES_ONLY -> {
                subscription.setTradePairs(pairs);
                subscription.setTrades(true);
            }
            case ORDER_BOOK_ONLY -> {
                subscription.setTradePairs(pairs);
                subscription.setOrderBook(true);
            }
            case CUSTOM -> {
                /*
                 * CUSTOM is intentionally conservative here.
                 * Use streamCustom(...) when exact flags are needed.
                 */
                subscription.setTradePairs(pairs);
                subscription.setTicker(true);
            }
            default -> {
                subscription.setTradePairs(pairs);
                subscription.setTicker(true);
                subscription.setTrades(true);
            }
        }

        return subscription;
    }

    private @NotNull ExchangeStreamConsumer createAgentStreamConsumer() {
        return new ExchangeStreamConsumer() {

            @Override
            public void onConnected(String exchangeName) {
                eventBus.publish(event(
                        "STREAM_CONNECTED",
                        exchangeName,
                        exchangeName,
                        Map.of()
                ));

                notifyAllChannels("Stream connected", "✅ Stream connected: " + exchangeName);
            }

            @Override
            public void onDisconnected(String exchangeName, String reason) {
                eventBus.publish(event(
                        "STREAM_DISCONNECTED",
                        exchangeName,
                        reason,
                        Map.of()
                ));

                notifyAllChannels(
                        "Stream disconnected",
                        "⚠️ Stream disconnected: %s | Reason: %s".formatted(exchangeName, reason)
                );
            }

            @Override
            public void onError(String exchangeName, Throwable throwable) {
                publishErrorEvent(exchangeName, throwable, "Exchange stream error.");

                notifyAllChannels(
                        "Stream error",
                        "❌ Stream error on %s: %s".formatted(exchangeName, rootMessage(throwable))
                );
            }

            @Override
            public void onTicker(String exchangeName, TradePair tradePair, Ticker ticker) {
                eventBus.publish(event(
                        AgentEvent.MARKET_TICK,
                        exchangeName,
                        ticker,
                        Map.of("tradePair", tradePairText(tradePair))
                ));
            }

            @Override
            public void onTrade(String exchangeName, TradePair tradePair, Trade trade) {
                eventBus.publish(event(
                        AgentEvent.MARKET_TRADE,
                        exchangeName,
                        trade,
                        Map.of("tradePair", tradePairText(tradePair))
                ));
            }

            @Override
            public void onOrderBook(String exchangeName, TradePair tradePair, OrderBook orderBook) {
                eventBus.publish(event(
                        AgentEvent.ORDER_BOOK_UPDATE,
                        exchangeName,
                        orderBook,
                        Map.of("tradePair", tradePairText(tradePair))
                ));
            }

            @Override
            public void onCandle(String exchangeName, TradePair tradePair, CandleData candleData) {
                eventBus.publish(event(
                        AgentEvent.MARKET_CANDLE,
                        exchangeName,
                        candleData,
                        Map.of("tradePair", tradePairText(tradePair))
                ));
            }

            @Override
            public void onAccount(String exchangeName, Account account) {
                eventBus.publish(event(
                        AgentEvent.ACCOUNT_UPDATE,
                        exchangeName,
                        account,
                        Map.of()
                ));
            }

            @Override
            public void onBalanceChanged(String exchangeName, Account account) {
                eventBus.publish(event(
                        "BALANCE_UPDATE",
                        exchangeName,
                        account,
                        Map.of()
                ));
            }

            @Override
            public void onOpenOrder(String exchangeName, OpenOrder order) {
                eventBus.publish(event(
                        AgentEvent.ORDER_UPDATE,
                        exchangeName,
                        order,
                        Map.of()
                ));
            }

            @Override
            public void onPosition(String exchangeName, Position position) {
                eventBus.publish(event(
                        AgentEvent.POSITION_UPDATE,
                        exchangeName,
                        position,
                        Map.of()
                ));
            }

            @Override
            public void onOrderAccepted(String exchangeName, String orderId) {
                eventBus.publish(event(
                        "ORDER_ACCEPTED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))
                ));

                notifyAllChannels("Order accepted", "✅ Order accepted: " + orderId);
            }

            @Override
            public void onOrderRejected(String exchangeName, String clientOrderId, String reason) {
                eventBus.publish(event(
                        AgentEvent.ORDER_REJECTED,
                        exchangeName,
                        reason,
                        Map.of("clientOrderId", safe(clientOrderId))
                ));

                notifyAllChannels(
                        "Order rejected",
                        "❌ Order rejected: %s | Reason: %s".formatted(clientOrderId, reason)
                );
            }

            @Override
            public void onOrderFilled(String exchangeName, String orderId, Trade fill) {
                eventBus.publish(event(
                        AgentEvent.ORDER_FILLED,
                        exchangeName,
                        fill,
                        Map.of("orderId", safe(orderId))
                ));

                notifyAllChannels("Order filled", "💰 Order filled: " + orderId);
            }

            @Override
            public void onOrderCancelled(String exchangeName, String orderId) {
                eventBus.publish(event(
                        "ORDER_CANCELLED",
                        exchangeName,
                        orderId,
                        Map.of("orderId", safe(orderId))
                ));

                notifyAllChannels("Order cancelled", "🟡 Order cancelled: " + orderId);
            }

            @Override
            public void onRawMessage(String exchangeName, String channel, String rawJson) {
                eventBus.publish(event(
                        "RAW_STREAM_MESSAGE",
                        exchangeName,
                        rawJson,
                        Map.of("channel", safe(channel))
                ));
            }
        };
    }

    @Contract("_, _, _, _ -> new")
    private @NotNull AgentEvent event(
            String type,
            String source,
            Object payload,
            Map<String, Object> metadata
    ) {
        return new AgentEvent(
                safeEventType(type),
                safe(source).isBlank() ? "SmartBot" : safe(source),
                payload,
                Instant.now(),
                metadata == null ? Map.of() : metadata
        );
    }

    private void publishSystemEvent(String type, String message) {
        eventBus.publish(event(
                safeEventType(type),
                "SmartBot",
                message,
                Map.of()
        ));
    }

    private void publishErrorEvent(String source, Throwable throwable, String message) {
        eventBus.publish(event(
                AgentEvent.ERROR,
                source,
                throwable,
                Map.of(
                        "message", safe(message),
                        "error", rootMessage(throwable)
                )
        ));
    }

    private void configureNotifiers() {
        if (!telegramToken.isBlank()) {
            this.telegramNotifier = new TelegramNotifier(telegramToken);
        }

        if (!fromEmail.isBlank() && !toEmail.isBlank()) {
            this.emailNotifier = new EmailNotifier(fromEmail, toEmail);
        }
    }

    public void configureEmail(String fromEmail, String toEmail) {
        this.fromEmail = safe(fromEmail);
        this.toEmail = safe(toEmail);

        if (!this.fromEmail.isBlank() && !this.toEmail.isBlank()) {
            this.emailNotifier = new EmailNotifier(this.fromEmail, this.toEmail);
        }
    }

    public void configureTelegram(String telegramToken) {
        this.telegramToken = safe(telegramToken);

        if (!this.telegramToken.isBlank()) {
            this.telegramNotifier = new TelegramNotifier(this.telegramToken);
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
            logger.warn("Telegram notification failed: {}", exception.getMessage(), exception);
        }
    }

    private void notifyEmail(String title, String message) {
        if (emailNotifier == null || message == null || message.isBlank()) {
            return;
        }

        try {
            emailNotifier.send(NotificationMessage.info(
                    safe(title).isBlank() ? "InvestPro SmartBot" : title,
                    message
            ));
        } catch (Exception exception) {
            logger.warn("Email notification failed: {}", exception.getMessage(), exception);
        }
    }

    private String tradePairText(TradePair tradePair) {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    private @NotNull String safeEventType(String value) {
        String text = safe(value);
        return text.isBlank() ? "UNKNOWN_EVENT" : text;
    }

    @Contract(pure = true)
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

    private void ensureStarted() {
        if (!started.get() || context == null) {
            throw new IllegalStateException("SmartBot has not been started.");
        }
    }

    // ---------------------------------------------------------------------
    // Convenience streaming methods
    // ---------------------------------------------------------------------

    public void streamEverything(TradePair tradePair) {
        startStreaming(tradePair, StreamingMode.EVERYTHING);
    }

    public void streamMarketData(TradePair tradePair) {
        startStreaming(tradePair, StreamingMode.MARKET_DATA);
    }

    public void streamAccountData (){
        ensureStarted();

        TradePair selectedPair = context.getSelectedTradePair();

        if (selectedPair == null) {
            throw new IllegalStateException("No selected trade pair available for account-data stream.");
        }

        startStreaming(selectedPair, StreamingMode.ACCOUNT_DATA);
    }

    public void streamTicker(TradePair... tradePairs) {
        ensureStarted();

        Exchange exchange = context.getExchange();
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();

        subscription.setTradePairs(Set.of(tradePairs));
        subscription.setTicker(true);

        streamConsumer = createAgentStreamConsumer();
        exchange.stream(subscription, streamConsumer);

        activeSubscription = subscription;
        streaming.set(true);
        currentStreamingMode = StreamingMode.TICKER_ONLY;
    }

    public void streamTrades(TradePair... tradePairs) {
        ensureStarted();

        Exchange exchange = context.getExchange();
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();

        subscription.setTradePairs(Set.of(tradePairs));
        subscription.setTrades(true);

        streamConsumer = createAgentStreamConsumer();
        exchange.stream(subscription, streamConsumer);

        activeSubscription = subscription;
        streaming.set(true);
        currentStreamingMode = StreamingMode.TRADES_ONLY;
    }

    public void streamOrderBookOnly(TradePair... tradePairs) {
        ensureStarted();

        Exchange exchange = context.getExchange();
        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();

        subscription.setTradePairs(Set.of(tradePairs));
        subscription.setOrderBook(true);

        streamConsumer = createAgentStreamConsumer();
        exchange.stream(subscription, streamConsumer);

        activeSubscription = subscription;
        streaming.set(true);
        currentStreamingMode = StreamingMode.ORDER_BOOK_ONLY;
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
            boolean balances
    ) {
        ensureStarted();

        if (tradePair == null) {
            throw new IllegalArgumentException("tradePair must not be null");
        }

        stopStreaming();

        Exchange exchange = context.getExchange();

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
        exchange.stream(subscription, streamConsumer);

        activeSubscription = subscription;
        streaming.set(true);
        currentStreamingMode = StreamingMode.CUSTOM;

        publishSystemEvent(
                "SMART_BOT_CUSTOM_STREAMING_STARTED",
                "Custom streaming started for %s".formatted(tradePairText(tradePair))
        );
    }
    public StreamingMode[] getAvailableStreamingModes() {
        return StreamingMode.values();
    }

    public String getStreamingModeDescription(StreamingMode mode) {
        if (mode == null) {
            return "";
        }

        return mode.description;
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

    public void customizeSubscription(
            boolean ticker,
            boolean trades,
            boolean candles,
            boolean orderBook,
            boolean account,
            boolean orders,
            boolean fills,
            boolean positions,
            boolean balances
    ) {
        ensureStarted();

        TradePair selectedPair = context.getSelectedTradePair();

        if (selectedPair == null && (ticker || trades || candles || orderBook)) {
            throw new IllegalStateException(
                    "A selected trade pair is required for market-data streaming."
            );
        }

        stopStreaming();

        ExchangeStreamSubscription subscription = new ExchangeStreamSubscription();

        if (selectedPair != null) {
            subscription.setTradePairs(Set.of(selectedPair));
        } else {
            subscription.setTradePairs(Set.of());
        }

        subscription.setTicker(ticker);
        subscription.setTrades(trades);
        subscription.setCandles(candles);
        subscription.setOrderBook(orderBook);
        subscription.setAccount(account);
        subscription.setOrders(orders);
        subscription.setFills(fills);
        subscription.setPositions(positions);
        subscription.setBalances(balances);

        Exchange exchange = context.getExchange();

        streamConsumer = createAgentStreamConsumer();
        activeSubscription = subscription;
        currentStreamingMode = StreamingMode.CUSTOM;

        try {
            exchange.stream(activeSubscription, streamConsumer);
            streaming.set(true);

            publishSystemEvent(
                    "SMART_BOT_CUSTOM_STREAMING_STARTED",
                    "Custom streaming started: %s".formatted(getSubscriptionSummary())
            );
        } catch (Exception exception) {
            streaming.set(false);
            activeSubscription = null;
            streamConsumer = null;

            publishErrorEvent(
                    "SmartBot",
                    exception,
                    "Failed to start custom streaming."
            );

            throw new IllegalStateException("Failed to start custom streaming", exception);
        }
    }
}
