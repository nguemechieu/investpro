package org.investpro.core.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.core.agents.Agent;
import org.investpro.core.agents.AgentContext;
import org.investpro.core.agents.AgentEvent;
import org.investpro.enums.CapitalProtection;
import org.investpro.enums.ExecutionStrategy;
import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.ProbabilityLevel;
import org.investpro.enums.PsychologyProfile;
import org.investpro.enums.RiskProfile;
import org.investpro.enums.SystemDesign;
import org.investpro.exchange.Exchange;
import org.investpro.models.Account;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class StrategyExecutionBridgeAgent implements Agent {
    private static final String AGENT_NAME = "StrategyExecutionBridgeAgent";
    private static final Duration ACCOUNT_CACHE_TTL = Duration.ofSeconds(15);

    private final TradeExecutionCoordinator coordinator;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile AgentContext context;
    private volatile Account cachedAccount;
    private volatile Instant cachedAccountAt = Instant.EPOCH;

    public StrategyExecutionBridgeAgent(@NotNull TradeExecutionCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
    }

    @Override
    public String name() {
        return AGENT_NAME;
    }

    @Override
    public void start(AgentContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        running.set(true);
        log.info("{} started", AGENT_NAME);
    }

    @Override
    public void stop() {
        running.set(false);
        context = null;
        log.info("{} stopped", AGENT_NAME);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public void onEvent(AgentEvent event) {
        if (!running.get() || event == null || !AgentEvent.STRATEGY_SIGNAL_APPROVED.equals(event.type())) {
            return;
        }
        if (!(event.payload() instanceof StrategySignal signal)) {
            return;
        }

        AgentContext activeContext = context;
        if (activeContext == null || !activeContext.isAutoTradingEnabled()) {
            publishExecutionEvent(AgentEvent.ORDER_REJECTED, "Auto trading disabled.");
            return;
        }

        CompletableFuture
                .supplyAsync(() -> buildRiskContext(signal, event, activeContext))
                .thenCompose(riskContext -> coordinator.processSignal(signal.normalized(), riskContext))
                .thenAccept(result -> {
                    if (result.executed()) {
                        publishExecutionEvent(AgentEvent.ORDER_SUBMITTED, result.orderId());
                    } else {
                        publishExecutionEvent(AgentEvent.ORDER_REJECTED, result.message());
                    }
                })
                .exceptionally(exception -> {
                    String message = rootMessage(exception);
                    log.warn("{} failed to route strategy signal: {}", AGENT_NAME, message, exception);
                    publishExecutionEvent(AgentEvent.ORDER_REJECTED, message);
                    return null;
                });
    }

    private TradeRiskContext buildRiskContext(
            @NotNull StrategySignal rawSignal,
            @NotNull AgentEvent sourceEvent,
            @NotNull AgentContext activeContext
    ) {
        StrategySignal signal = rawSignal.normalized();
        Exchange exchange = activeContext.getExchange();
        if (exchange == null) {
            throw new IllegalStateException("No exchange is available for live execution.");
        }
        if (!exchange.canSubmitOrders()) {
            throw new IllegalStateException("Exchange is not ready for order submission: " + exchange.getName());
        }

        TradePair pair = resolvePair(signal, sourceEvent, activeContext);
        if (pair == null) {
            throw new IllegalStateException("Unable to resolve trade pair for strategy signal " + signal.getSymbol());
        }

        Account account = fetchAccount(exchange);
        double equity = firstPositive(account.getEquity(), account.getPortfolioValue(), account.getTotalBalance());
        if (equity <= 0.0) {
            throw new IllegalStateException("Account equity is unavailable; refusing live order.");
        }

        double price = firstPositive(
                signal.getEntryPrice(),
                numberMetadata(sourceEvent, "currentPrice"),
                numberMetadata(sourceEvent, "price"),
                numberMetadata(sourceEvent, "last"),
                pair.getLast(),
                pair.getAsk(),
                pair.getBid());
        if (price <= 0.0) {
            throw new IllegalStateException("No executable market price is available for " + pair.toString('/'));
        }

        double stopLoss = signal.getStopLossPrice() > 0.0
                ? signal.getStopLossPrice()
                : defaultStopLoss(signal, price);
        double takeProfit = signal.getTakeProfitPrice() > 0.0
                ? signal.getTakeProfitPrice()
                : defaultTakeProfit(signal, price);
        double requestedSize = signal.getAmount() > 0.0
                ? signal.getAmount()
                : AppConfig.getDouble("investpro.execution.defaultSignalTradeUnits", 1.0);

        return TradeRiskContext.builder()
                .symbol(pair)
                .assetClass(pair.getAssetClass() == null ? "UNKNOWN" : pair.getAssetClass().name())
                .contractType(pair.getContractType() == null ? "SPOT" : pair.getContractType().name())
                .broker(exchange.getName())
                .accountEquity(equity)
                .availableCash(firstPositive(account.getAvailableBalance(), account.getBuyingPower(), account.getCash(), equity))
                .accountBalance(firstPositive(account.getTotalBalance(), equity))
                .freeMargin(firstPositive(account.getFreeMargin(), account.getMarginAvailable(), equity))
                .usedMargin(Math.max(0.0, account.getMarginUsed()))
                .requestedPositionSize(requestedSize)
                .requestedLeverage(1.0)
                .entryPrice(price)
                .currentPrice(price)
                .bidPrice(firstPositive(numberMetadata(sourceEvent, "bid"), pair.getBid(), price))
                .askPrice(firstPositive(numberMetadata(sourceEvent, "ask"), pair.getAsk(), price))
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .expectedWinRate(firstPositive(signal.getWinProbability(), ProbabilityLevel.MODERATE.getExpectedWinRate()))
                .expectedRewardRiskRatio(firstPositive(signal.getRiskRewardRatio(), 2.0))
                .expectedValue(signal.getExpectedValue())
                .riskProfile(RiskProfile.MODERATE)
                .marketBehavior(signal.getMarketBehavior() == null ? MarketBehavior.UNKNOWN : signal.getMarketBehavior())
                .executionStrategy(ExecutionStrategy.MARKET_ORDER)
                .liquidityProfile(LiquidityProfile.NORMAL)
                .psychologyProfile(PsychologyProfile.DISCIPLINED)
                .probabilityLevel(probabilityFromConfidence(signal.getConfidence()))
                .capitalProtection(CapitalProtection.STRICT_STOPS)
                .systemDesign(SystemDesign.MECHANICAL_RULES)
                .tradingSessionStatus(signal.getSessionStatus() != null ? signal.getSessionStatus() : pair.getTradingSessionStatus())
                .tradingSessionNotes(signal.getSessionNotes() == null ? "" : signal.getSessionNotes())
                .volatility(Math.max(0.0, numberMetadata(sourceEvent, "volatility")))
                .maxRiskPerTrade(AppConfig.getDouble("investpro.execution.maxRiskPerTradePercent", 2.0))
                .maxCumulativeRisk(AppConfig.getDouble("investpro.execution.maxCumulativeRiskPercent", 5.0))
                .maxAllowedLeverage(1.0)
                .estimatedSlippagePercent(AppConfig.getDouble("investpro.execution.estimatedSlippagePercent", 0.02))
                .build();
    }

    private Account fetchAccount(Exchange exchange) {
        Instant now = Instant.now();
        Account account = cachedAccount;
        if (account != null && Duration.between(cachedAccountAt, now).compareTo(ACCOUNT_CACHE_TTL) < 0) {
            return account;
        }
        try {
            Account fresh = exchange.fetchAccount().get(5, TimeUnit.SECONDS);
            if (fresh == null) {
                throw new IllegalStateException("Broker returned no account snapshot.");
            }
            cachedAccount = fresh;
            cachedAccountAt = now;
            return fresh;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load account snapshot: " + rootMessage(exception), exception);
        }
    }

    private TradePair resolvePair(StrategySignal signal, AgentEvent event, AgentContext activeContext) {
        Object object = event.metadata() == null ? null : event.metadata().get("tradePairObject");
        if (object instanceof TradePair pair) {
            return pair;
        }
        if (activeContext.getSelectedTradePair() != null
                && sameSymbol(activeContext.getSelectedTradePair(), signal.getSymbol())) {
            return activeContext.getSelectedTradePair();
        }
        try {
            return TradePair.fromSymbol(signal.getSymbol());
        } catch (SQLException | ClassNotFoundException | RuntimeException exception) {
            return null;
        }
    }

    private boolean sameSymbol(TradePair pair, String symbol) {
        return pair != null
                && symbol != null
                && pair.toString('/').equalsIgnoreCase(symbol.replace('_', '/').replace('-', '/'));
    }

    private double defaultStopLoss(StrategySignal signal, double price) {
        double distance = AppConfig.getDouble("investpro.execution.defaultStopDistancePercent", 0.002);
        return signal.isSell() ? price * (1.0 + distance) : price * (1.0 - distance);
    }

    private double defaultTakeProfit(StrategySignal signal, double price) {
        double distance = AppConfig.getDouble("investpro.execution.defaultTakeProfitDistancePercent", 0.004);
        return signal.isSell() ? price * (1.0 - distance) : price * (1.0 + distance);
    }

    private ProbabilityLevel probabilityFromConfidence(double confidence) {
        if (confidence >= 0.90) {
            return ProbabilityLevel.VERY_HIGH;
        }
        if (confidence >= 0.70) {
            return ProbabilityLevel.HIGH;
        }
        if (confidence >= 0.50) {
            return ProbabilityLevel.MODERATE;
        }
        if (confidence >= 0.30) {
            return ProbabilityLevel.LOW;
        }
        return ProbabilityLevel.VERY_LOW;
    }

    private double numberMetadata(AgentEvent event, String key) {
        Object value = event == null || event.metadata() == null ? null : event.metadata().get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private double firstPositive(double... values) {
        if (values == null) {
            return 0.0;
        }
        for (double value : values) {
            if (value > 0.0 && Double.isFinite(value)) {
                return value;
            }
        }
        return 0.0;
    }

    private void publishExecutionEvent(String type, @Nullable Object payload) {
        AgentContext activeContext = context;
        if (activeContext != null && activeContext.getEventBus() != null) {
            activeContext.getEventBus().publishAsync(AgentEvent.execution(type, AGENT_NAME, payload));
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        return message == null || message.isBlank()
                ? (current == null ? "Unknown error" : current.getClass().getSimpleName())
                : message;
    }
}
