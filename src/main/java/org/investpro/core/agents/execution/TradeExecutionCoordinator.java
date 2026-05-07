package org.investpro.core.agents.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.ai.FinalRiskGate;
import org.investpro.data.Account;
import org.investpro.execution.ExecutionIntent;
import org.investpro.execution.PositionTransitionPolicy;
import org.investpro.execution.SymbolTradeLockManager;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.investpro.utils.Side.HOLD;

/**
 * Coordinates the full new-trade execution pipeline.
 *
 * Pipeline:
 * StrategySignal (BUY/SELL/HOLD with full context)
 * -> RiskManagementSystem
 * -> AiReasoningService
 * -> FinalRiskGate
 * -> ExecutionEngine
 *
 * This class does not place orders directly.
 * It only coordinates validation, AI review, final approval, and execution.
 */
@Slf4j
public class TradeExecutionCoordinator {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(TradeExecutionCoordinator.class);

    private final RiskManagementSystem riskManagementSystem;
    private final AiReasoningService aiReasoningService;
    private final ExecutionEngine executionEngine;
    private final PositionTransitionPolicy positionTransitionPolicy = new PositionTransitionPolicy();
    private final SymbolTradeLockManager symbolTradeLockManager = new SymbolTradeLockManager();
    private final ConcurrentHashMap<String, Instant> lastActionTimes = new ConcurrentHashMap<>();

    private static final Duration TRANSITION_COOLDOWN = Duration.ofSeconds(10);

    public TradeExecutionCoordinator(
            @NotNull RiskManagementSystem riskManagementSystem,
            @NotNull AiReasoningService aiReasoningService,
            @NotNull ExecutionEngine executionEngine) {
        this.riskManagementSystem = Objects.requireNonNull(
                riskManagementSystem,
                "riskManagementSystem cannot be null");
        this.aiReasoningService = Objects.requireNonNull(
                aiReasoningService,
                "aiReasoningService cannot be null");
        this.executionEngine = Objects.requireNonNull(
                executionEngine,
                "executionEngine cannot be null");
    }

    /**
     * Fire-and-forget compatibility method.
     *
     * Prefer processSignal(...) when the caller needs the execution result.
     */
    public void handleSignal(
            @NotNull Side side,
            @NotNull TradeRiskContext riskContext) {
        processSignal(side, riskContext)
                .thenAccept(result -> {
                    if (result.executed()) {
                        log.info("TradeExecutionCoordinator: Trade executed. orderId={}", result.orderId());
                    } else if (result.manualReviewRequired()) {
                        log.info("TradeExecutionCoordinator: Manual review required. {}", result.message());
                    } else if (result.waiter()) {
                        log.info("TradeExecutionCoordinator: Waiting. {}", result.message());
                    } else if (result.rejected()) {
                        log.info("TradeExecutionCoordinator: Rejected. {}", result.message());
                    }
                })
                .exceptionally(exception -> {
                    log.error("TradeExecutionCoordinator: handleSignal failed", exception);
                    return null;
                });
    }

    /**
     * Main method used by StrategyEngine / SignalAgent / AutoTradingController.
     */
    public CompletableFuture<TradeExecutionResult> processSignal(
            @NotNull Side side,
            @NotNull TradeRiskContext riskContext) {
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(riskContext, "riskContext cannot be null");
        return processSignalInternal(null, side, riskContext);
    }

    public CompletableFuture<TradeExecutionResult> processSignal(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext) {
        Objects.requireNonNull(signal, "signal cannot be null");
        Objects.requireNonNull(riskContext, "riskContext cannot be null");
        return processSignalInternal(signal, signal.getSide(), riskContext);
    }

    private CompletableFuture<TradeExecutionResult> processSignalInternal(
            @Nullable StrategySignal signal,
            @Nullable Side side,
            @NotNull TradeRiskContext riskContext) {
        try {
            if (side == null || side == HOLD) {
                return completed(TradeExecutionResult.wait(
                        "Signal is HOLD or missing side",
                        "No executable trade direction was provided."));
            }

            log.info(
                    "TradeExecutionCoordinator: Processing signal symbol={} side={}",
                    riskContext.getSymbol(),
                    side);

            if (riskContext.getSymbol() != null
                    && riskContext.getSymbol().getTradingSession() != null
                    && !riskContext.getSymbol().isTradableNow()) {
                String message = "Trading session is not open: " + riskContext.getSymbol().getTradingSessionStatus();
                log.warn("TradeExecutionCoordinator: Rejecting locally before live order. symbol={} {}",
                        riskContext.getSymbol(), message);
                return completed(TradeExecutionResult.rejected(message));
            }

            return executeWithTransitionGuards(signal, side, riskContext);

        } catch (Exception exception) {
            log.error("TradeExecutionCoordinator: Unexpected error while processing signal", exception);
            return completed(TradeExecutionResult.failed(rootMessage(exception)));
        }
    }

    private CompletableFuture<TradeExecutionResult> executeWithTransitionGuards(
            @Nullable StrategySignal signal,
            @NotNull Side side,
            @NotNull TradeRiskContext riskContext) {
        Exchange exchange = executionEngine.getExchange();
        TradePair symbol = riskContext.getSymbol();
        String exchangeName = exchange != null ? exchange.getName() : riskContext.getBroker();
        String symbolText = toSymbolText(symbol);
        String lockReason = "Processing " + side + " signal";

        if (isCooldownActive(exchangeName, symbolText)) {
            log.info(
                    "TradeExecutionCoordinator: cooldown active. exchange={} symbol={} signal={}",
                    exchangeName,
                    symbolText,
                    side);
            return completed(TradeExecutionResult.rejected(
                    "Order blocked: transition cooldown active for " + symbolText));
        }

        boolean lockAcquired = symbolTradeLockManager.tryLock(exchangeName, symbolText, lockReason);
        log.info(
                "TradeExecutionCoordinator: symbol lock {}. exchange={} symbol={} reason={}",
                lockAcquired ? "acquired" : "rejected",
                exchangeName,
                symbolText,
                lockReason);
        if (!lockAcquired) {
            return completed(TradeExecutionResult.rejected(
                    "Order blocked: another order transition is already running for " + symbolText));
        }

        CompletableFuture<TradeExecutionResult> guardedExecution = hasPendingOrder(exchange, symbol)
                .thenCompose(hasPending -> {
                    if (hasPending) {
                        return completed(TradeExecutionResult.rejected(
                                "Pending order already exists for " + symbolText + "; skipping duplicate action."));
                    }

                    return fetchCurrentPosition(exchange, symbol)
                            .thenCompose(currentPosition -> {
                                Side currentPositionSide = currentPosition
                                        .map(Position::getSide)
                                        .orElse(null);
                                ExecutionIntent intent = positionTransitionPolicy.resolveIntent(
                                        currentPositionSide,
                                        side);
                                log.info(
                                        "TradeExecutionCoordinator: resolved intent. exchange={} symbol={} currentPositionSide={} signalSide={} executionIntent={}",
                                        exchangeName,
                                        symbolText,
                                        currentPositionSide,
                                        side,
                                        intent);

                                return switch (intent) {
                                    case NO_ACTION -> completed(TradeExecutionResult.wait(
                                            "No execution action required for " + symbolText,
                                            "Signal matches current position or requested HOLD."));
                                    case CLOSE_LONG_ONLY, CLOSE_SHORT_ONLY -> closeExistingPositionOnly(
                                            exchange,
                                            symbol,
                                            symbolText,
                                            exchangeName,
                                            currentPosition.orElse(null),
                                            intent);
                                    case OPEN_LONG, OPEN_SHORT -> continueRiskAiAndExecution(
                                            signal,
                                            side,
                                            withCachedAccountSnapshot(exchange, riskContext),
                                            exchangeName,
                                            symbolText);
                                };
                            });
                })
                .exceptionally(exception -> TradeExecutionResult.failed(rootMessage(exception)));

        return guardedExecution.whenComplete((ignored, throwable) -> {
            symbolTradeLockManager.unlock(exchangeName, symbolText);
            log.info("TradeExecutionCoordinator: symbol lock released. exchange={} symbol={}", exchangeName, symbolText);
        });
    }

    private CompletableFuture<TradeExecutionResult> continueRiskAiAndExecution(
            @Nullable StrategySignal signal,
            @NotNull Side side,
            @NotNull TradeRiskContext riskContext,
            @Nullable String exchangeName,
            @NotNull String symbolText) {
        try {
            RiskDecision riskDecision = riskManagementSystem.evaluateTrade(riskContext);

            if (riskDecision == null) {
                return completed(TradeExecutionResult.rejected(
                        "RiskManagementSystem returned null decision"));
            }

            AiTradeReviewRequest aiRequest = AiTradeReviewRequest.from(
                    side,
                    riskContext,
                    riskDecision);

            AiTradeReviewResponse aiResponse = aiReasoningService.reviewTrade(aiRequest);

            if (aiResponse == null) {
                return completed(TradeExecutionResult.manualReview(
                        "AI review returned null",
                        "Trade requires manual review because AI did not return a valid response."));
            }

            FinalRiskGate.OrderApprovalDecision finalDecision = FinalRiskGate.makeDecision(riskDecision, aiResponse);

            if (finalDecision.isApproved()) {
                CompletableFuture<ExecutionEngine.PositionExecutionResult> executionFuture = signal != null
                        ? executionEngine.executeApprovedOrder(signal, riskContext, finalDecision)
                        : executionEngine.executeApprovedOrder(side, riskContext, finalDecision);

                return executionFuture
                        .thenApply(executionResult -> {
                            if (executionResult.isSuccessful()) {
                                recordActionTime(exchangeName, symbolText);
                                return TradeExecutionResult.executed(
                                        executionResult.orderId(),
                                        finalDecision.getSummary(),
                                        finalDecision.getExplanation());
                            }

                            return TradeExecutionResult.failed(
                                    executionResult.errorMessage());
                        })
                        .exceptionally(exception -> TradeExecutionResult.failed(rootMessage(exception)));
            }

            if (finalDecision.requiresManualReview()) {
                createManualReviewTicket(side, riskContext, riskDecision, aiResponse, finalDecision);

                return completed(TradeExecutionResult.manualReview(
                        finalDecision.getSummary(),
                        finalDecision.getExplanation()));
            }

            if (finalDecision.shouldWait()) {
                logWaitDecision(side, riskContext, finalDecision);

                return completed(TradeExecutionResult.wait(
                        finalDecision.getSummary(),
                        finalDecision.getExplanation()));
            }

            if (finalDecision.isRejected()) {
                logRejectedTrade(side, finalDecision);

                return completed(TradeExecutionResult.rejected(
                        finalDecision.getSummary()));
            }

            return completed(TradeExecutionResult.manualReview(
                    "Unexpected final decision state",
                    finalDecision.getExplanation()));

        } catch (Exception exception) {
            log.error("TradeExecutionCoordinator: Unexpected error in risk/AI/execution pipeline", exception);
            return completed(TradeExecutionResult.failed(rootMessage(exception)));
        }
    }

    private CompletableFuture<TradeExecutionResult> closeExistingPositionOnly(
            @Nullable Exchange exchange,
            @Nullable TradePair symbol,
            @NotNull String symbolText,
            @Nullable String exchangeName,
            @Nullable Position currentPosition,
            @NotNull ExecutionIntent intent) {
        if (exchange == null) {
            return completed(TradeExecutionResult.rejected("Exchange is not configured for " + symbolText));
        }
        if (symbol == null) {
            return completed(TradeExecutionResult.rejected("Symbol is missing"));
        }
        if (currentPosition == null) {
            return completed(TradeExecutionResult.wait(
                    "No existing position to close for " + symbolText,
                    intent + " required an open position, but none was found."));
        }

        String positionId = currentPosition.getPositionId();
        log.info(
                "TradeExecutionCoordinator: close-only transition. exchange={} symbol={} positionId={} intent={}",
                exchangeName,
                symbolText,
                positionId,
                intent);

        CompletableFuture<String> closeFuture = positionId == null || positionId.isBlank()
                ? exchange.closePosition(symbol)
                : exchange.closePosition(symbol, positionId);

        return closeFuture
                .thenApply(orderId -> {
                    recordActionTime(exchangeName, symbolText);
                    return TradeExecutionResult.executed(
                            orderId,
                            "Closed existing position only for " + symbolText,
                            "Opposite signal produced " + intent + "; opening the opposite side is deferred to a later strategy cycle.");
                })
                .exceptionally(exception -> TradeExecutionResult.failed(rootMessage(exception)));
    }

    private CompletableFuture<Boolean> hasPendingOrder(@Nullable Exchange exchange, @Nullable TradePair symbol) {
        if (exchange == null || symbol == null) {
            return CompletableFuture.completedFuture(false);
        }

        return exchange.fetchOpenOrders(symbol)
                .thenApply(orders -> hasActiveOrderForSymbol(orders, symbol))
                .exceptionally(exception -> {
                    log.warn(
                            "TradeExecutionCoordinator: pending order check failed for {}: {}",
                            symbol,
                            rootMessage(exception));
                    return false;
                });
    }

    private boolean hasActiveOrderForSymbol(@Nullable List<OpenOrder> orders, @NotNull TradePair symbol) {
        if (orders == null || orders.isEmpty()) {
            return false;
        }

        String symbolText = toSymbolText(symbol);
        for (OpenOrder order : orders) {
            if (order == null || order.getStatus() == null) {
                continue;
            }
            boolean activeStatus = order.getStatus() == OpenOrder.OrderStatus.PENDING
                    || order.getStatus() == OpenOrder.OrderStatus.OPEN
                    || order.getStatus() == OpenOrder.OrderStatus.PARTIALLY_FILLED
                    || order.isStillOpen();
            if (activeStatus && Objects.equals(symbolText, toSymbolText(order.getTradePair()))) {
                return true;
            }
        }
        return false;
    }

    private CompletableFuture<Optional<Position>> fetchCurrentPosition(
            @Nullable Exchange exchange,
            @Nullable TradePair symbol) {
        if (exchange == null || symbol == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return exchange.fetchPosition(symbol)
                .thenApply(position -> position
                        .filter(this::isOpenPosition))
                .exceptionally(exception -> {
                    log.warn(
                            "TradeExecutionCoordinator: current position check failed for {}: {}",
                            symbol,
                            rootMessage(exception));
                    return Optional.empty();
                });
    }

    private boolean isOpenPosition(@Nullable Position position) {
        return position != null
                && position.isOpen()
                && position.getQuantity() > 0.0
                && (position.getSide() == Side.BUY || position.getSide() == Side.SELL);
    }

    private TradeRiskContext withCachedAccountSnapshot(
            @Nullable Exchange exchange,
            @NotNull TradeRiskContext riskContext) {
        if (exchange == null || !isOanda(exchange.getName())) {
            return riskContext;
        }
        if (riskContext.getAccountBalance() > 0.0) {
            return riskContext;
        }

        try {
            Account account = exchange.getAccount();
            if (account == null) {
                return riskContext;
            }

            double balance = firstPositive(account.getTotalBalance(), account.getAvailableBalance(), account.getEquity());
            double equity = riskContext.getAccountEquity() > 0.0
                    ? riskContext.getAccountEquity()
                    : firstPositive(account.getEquity(), account.getTotalBalance(), account.getAvailableBalance());
            if (balance <= 0.0 && equity <= 0.0) {
                return riskContext;
            }

            log.info(
                    "TradeExecutionCoordinator: cached account snapshot loaded for sizing. exchange={} balance={} equity={}",
                    exchange.getName(),
                    balance,
                    equity);
            return riskContext.toBuilder()
                    .accountBalance(balance)
                    .accountEquity(equity > 0.0 ? equity : riskContext.getAccountEquity())
                    .build();
        } catch (Exception exception) {
            log.warn(
                    "TradeExecutionCoordinator: cached account snapshot unavailable for {}: {}",
                    riskContext.getSymbol(),
                    rootMessage(exception));
            return riskContext;
        }
    }

    private boolean isCooldownActive(@Nullable String exchangeName, @NotNull String symbolText) {
        Instant lastAction = lastActionTimes.get(actionKey(exchangeName, symbolText));
        return lastAction != null && Instant.now().isBefore(lastAction.plus(TRANSITION_COOLDOWN));
    }

    private void recordActionTime(@Nullable String exchangeName, @NotNull String symbolText) {
        lastActionTimes.put(actionKey(exchangeName, symbolText), Instant.now());
        log.info(
                "TradeExecutionCoordinator: transition cooldown recorded. exchange={} symbol={} cooldownSeconds={}",
                exchangeName,
                symbolText,
                TRANSITION_COOLDOWN.toSeconds());
    }

    private static String actionKey(@Nullable String exchangeName, @NotNull String symbolText) {
        return String.valueOf(exchangeName).trim().toUpperCase() + "::" + symbolText.trim().toUpperCase();
    }

    private static double firstPositive(double... values) {
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

    private static boolean isOanda(@Nullable String exchangeName) {
        return exchangeName != null && exchangeName.trim().equalsIgnoreCase("OANDA");
    }

    private static @NotNull String toSymbolText(@Nullable TradePair symbol) {
        if (symbol == null) {
            return "UNKNOWN";
        }
        try {
            return symbol.toString('/');
        } catch (Exception exception) {
            return symbol.toString();
        }
    }

    private void createManualReviewTicket(
            Side side,
            TradeRiskContext riskContext,
            RiskDecision riskDecision,
            AiTradeReviewResponse aiResponse,
            FinalRiskGate.OrderApprovalDecision finalDecision) {
        log.warn(
                "Manual review required. symbol={} side={} summary={} explanation={}",
                riskContext.getSymbol(),
                side,
                finalDecision.getSummary(),
                finalDecision.getExplanation());

        /*
         * Later:
         * - save to database
         * - show in UI
         * - notify Telegram/email
         * - create ManualReviewTicket entity
         */
    }

    private void logWaitDecision(
            Side side,
            TradeRiskContext riskContext,
            FinalRiskGate.OrderApprovalDecision finalDecision) {
        log.info(
                "Trade wait decision. symbol={} side={} summary={} explanation={}",
                riskContext.getSymbol(),
                side,
                finalDecision.getSummary(),
                finalDecision.getExplanation());
    }

    private void logRejectedTrade(
            Side side,
            FinalRiskGate.OrderApprovalDecision finalDecision) {
        log.warn(
                "Trade rejected. side={} summary={} explanation={}",
                side,
                finalDecision.getSummary(),
                finalDecision.getExplanation());
    }

    private static CompletableFuture<TradeExecutionResult> completed(TradeExecutionResult result) {
        return CompletableFuture.completedFuture(result);
    }

    private static String rootMessage(Throwable throwable) {
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
     * Result returned after coordinating risk, AI, final gate, and execution.
     */
    public record TradeExecutionResult(
            boolean executed,
            boolean approved,
            boolean manualReviewRequired,
            boolean waiter,
            boolean rejected,
            @Nullable String orderId,
            @Nullable String message,
            @Nullable String explanation) {
        public static TradeExecutionResult executed(
                @Nullable String orderId,
                @Nullable String message,
                @Nullable String explanation) {
            return new TradeExecutionResult(
                    true,
                    true,
                    false,
                    false,
                    false,
                    orderId,
                    message,
                    explanation);
        }

        public static TradeExecutionResult manualReview(
                @Nullable String message,
                @Nullable String explanation) {
            return new TradeExecutionResult(
                    false,
                    false,
                    true,
                    false,
                    false,
                    null,
                    message,
                    explanation);
        }

        public static TradeExecutionResult wait(
                @Nullable String message,
                @Nullable String explanation) {
            return new TradeExecutionResult(
                    false,
                    false,
                    false,
                    true,
                    false,
                    null,
                    message,
                    explanation);
        }

        public static TradeExecutionResult rejected(@Nullable String message) {
            return new TradeExecutionResult(
                    false,
                    false,
                    false,
                    false,
                    true,
                    null,
                    message,
                    null);
        }

        public static TradeExecutionResult failed(@Nullable String message) {
            return new TradeExecutionResult(
                    false,
                    false,
                    false,
                    false,
                    true,
                    null,
                    "Execution failed: " + message,
                    null);
        }
    }
}
