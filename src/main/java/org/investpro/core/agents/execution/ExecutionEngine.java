package org.investpro.core.agents.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.FinalRiskGate;
import org.investpro.ai.PositionActionIntent;
import org.investpro.exchange.Exchange;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * ExecutionEngine - The Operator Role in Position Management.

 * Executes only approved PositionActionIntent objects passed from FinalRiskGate.
 * Never executes unverified or unapproved position actions.
 */
@Slf4j
public class ExecutionEngine {

    private Exchange exchange;
    private SymbolExecutionFilter symbolFilter;

    public ExecutionEngine() {
        this(null, new SymbolExecutionFilter(true));
    }

    public ExecutionEngine(@Nullable Exchange exchange) {
        this(exchange, new SymbolExecutionFilter(true));
    }

    public ExecutionEngine(
            @Nullable Exchange exchange,
            @Nullable SymbolExecutionFilter symbolFilter
    ) {
        this.exchange = exchange;
        this.symbolFilter = symbolFilter;
    }

    /**
     * Set the exchange for this execution engine.
     */
    public void setExchange(@Nullable Exchange exchange) {
        this.exchange = exchange;
    }

    /**
     * Set the symbol filter for this execution engine.
     */
    public void setSymbolFilter(@Nullable SymbolExecutionFilter symbolFilter) {
        this.symbolFilter = symbolFilter;
    }

    /**
     * Execute an approved position action intent.
     *
     * @param intent approved position action from FinalRiskGate
     * @return execution result
     */
    public CompletableFuture<PositionExecutionResult> execute(@NotNull PositionActionIntent intent) {
        try {
            PositionExecutionResult validationFailure = validateIntent(intent);
            if (validationFailure != null) {
                return CompletableFuture.completedFuture(validationFailure);
            }

            if (symbolFilter != null && symbolFilter.isSymbolEligible(intent.getSymbol())) {
                String reason = symbolFilter.getEligibilityReason(intent.getSymbol());
                log.warn("ExecutionEngine: Symbol {} rejected by filter. Reason: {}", intent.getSymbol(), reason);

                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("Symbol rejected: " + reason)
                );
            }

            log.info("ExecutionEngine: Executing approved action={} symbol={} positionId={}",
                    intent.getAction(), intent.getSymbol(), intent.getPositionId());

            return executeInternal(intent)
                    .thenApply(orderId -> {
                        log.info("ExecutionEngine: Action executed successfully. orderId={}", orderId);
                        return PositionExecutionResult.success(orderId);
                    })
                    .exceptionally(exception -> {
                        String message = rootMessage(exception);
                        log.warn("ExecutionEngine: Action execution failed: {}", message);
                        return PositionExecutionResult.failed(message);
                    });

        } catch (Exception exception) {
            log.error("ExecutionEngine: Unexpected error during execution", exception);
            return CompletableFuture.completedFuture(
                    PositionExecutionResult.failed(exception.getMessage())
            );
        }
    }

    /**
     * Validates intent before execution.
     */
    private @Nullable PositionExecutionResult validateIntent(@Nullable PositionActionIntent intent) {
        if (intent == null) {
            return PositionExecutionResult.failed("PositionActionIntent was null");
        }

        if (!intent.isApproved()) {
            return PositionExecutionResult.failed("PositionActionIntent is not approved by FinalRiskGate");
        }

        if (intent.getAction() == null) {
            return PositionExecutionResult.failed("Position action is missing");
        }

        if (intent.getSymbol() == null ) {
            return PositionExecutionResult.failed("Symbol is missing");
        }

        return null;
    }

    /**
     * Internal execution logic based on action type.
     */
    private CompletableFuture<String> executeInternal(@NotNull PositionActionIntent intent) {
        return switch (intent.getAction()) {
            case HOLD -> hold(intent);
            case REDUCE_SIZE -> reduceSize(intent);
            case TAKE_PARTIAL_PROFIT -> takePartialProfit(intent);
            case CLOSE_POSITION -> closePosition(intent);
            case MOVE_STOP_LOSS -> moveStopLoss(intent);
            case MOVE_TAKE_PROFIT -> moveTakeProfit(intent);
            case TRAIL_STOP -> trailStop(intent);
            case HEDGE -> CompletableFuture.failedFuture(
                    new UnsupportedOperationException("HEDGE requires manual approval and broker/account support")
            );
            case ESCALATE_TO_MANUAL_REVIEW -> CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Manual-review actions cannot be executed automatically")
            );
        };
    }

    private CompletableFuture<String> hold(@NotNull PositionActionIntent intent) {
        log.info("ExecutionEngine: HOLD maintained for {}", intent.getSymbol());
        return CompletableFuture.completedFuture("HOLD_MAINTAINED");
    }

    /**
     * Reduce an open position by a requested quantity or risk reduction percent.
     */
    private CompletableFuture<String> reduceSize(@NotNull PositionActionIntent intent) {
        requireExchange();

        if (intent.getPositionId() == null || intent.getPositionId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("REDUCE_SIZE requires positionId")
            );
        }

        double closeQuantity = resolveCloseQuantity(intent);

        if (closeQuantity <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("REDUCE_SIZE requires suggestedCloseQuantity or suggestedRiskReductionPercent")
            );
        }

        log.info("ExecutionEngine: Reducing position for {} positionId={} quantity={}",
                intent.getSymbol(), intent.getPositionId(), closeQuantity);

        return exchange.closePartialPosition(
                intent.getSymbol(),
                intent.getPositionId(),
                closeQuantity
        );
    }

    /**
     * Take partial profit by closing part of an open position.
     */
    private CompletableFuture<String> takePartialProfit(@NotNull PositionActionIntent intent) {
        requireExchange();

        if (intent.getPositionId() == null || intent.getPositionId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("TAKE_PARTIAL_PROFIT requires positionId")
            );
        }

        double closeQuantity = resolveCloseQuantity(intent);

        if (closeQuantity <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("TAKE_PARTIAL_PROFIT requires suggestedCloseQuantity")
            );
        }

        log.info("ExecutionEngine: Taking partial profit for {} positionId={} quantity={}",
                intent.getSymbol(), intent.getPositionId(), closeQuantity);

        return exchange.closePartialPosition(
                intent.getSymbol(),
                intent.getPositionId(),
                closeQuantity
        );
    }
    /**
     * Close an open position.
     */
    private CompletableFuture<String> closePosition(@NotNull PositionActionIntent intent) {
        requireExchange();

        if (intent.getPositionId() == null || intent.getPositionId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("CLOSE_POSITION requires positionId")
            );
        }

        log.info("ExecutionEngine: Closing position for {} positionId={}",
                intent.getSymbol(), intent.getPositionId());

        return exchange.closePosition(intent.getSymbol(), intent.getPositionId());
    }

    /**
     * Move stop loss to a new level.
     */
    private CompletableFuture<String> moveStopLoss(@NotNull PositionActionIntent intent) {
        requireExchange();

        if (intent.getPositionId() == null || intent.getPositionId().isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("MOVE_STOP_LOSS requires positionId")
            );
        }

        if ( intent.getSuggestedStopLoss() <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("MOVE_STOP_LOSS requires suggestedStopLoss > 0")
            );
        }

        log.info("ExecutionEngine: Moving stop loss for {} positionId={} to {}",
                intent.getSymbol(), intent.getPositionId(), intent.getSuggestedStopLoss());

        return exchange.modifyStopLoss(
                intent.getSymbol(),
                intent.getPositionId(),
                intent.getSuggestedStopLoss()
        );
    }

    /**
     * Move take profit to a new level.
     */
    private CompletableFuture<String> moveTakeProfit(@NotNull PositionActionIntent intent) {
        requireExchange();

        if ( intent.getSuggestedTakeProfit() <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("MOVE_TAKE_PROFIT requires suggestedTakeProfit > 0")
            );
        }

        log.info("ExecutionEngine: Moving take profit for {} to {}",
                intent.getSymbol(), intent.getSuggestedTakeProfit());



         return exchange.modifyTakeProfit(intent.getSymbol(), intent.getPositionId(), intent.getSuggestedTakeProfit());



    }

    /**
     * Trail the stop loss as price moves favorably.
     */
    private CompletableFuture<String> trailStop(@NotNull PositionActionIntent intent) {
        requireExchange();

        log.info("ExecutionEngine: Initiating trailing stop for {}", intent.getSymbol());


          return exchange.enableTrailingStop(intent.getSymbol(), intent.getPositionId(), intent.getTrailingDistance());


    }

    private void requireExchange() {
        if (exchange == null) {
            throw new IllegalStateException("Exchange not connected");
        }
    }

    private double resolveCloseQuantity(@NotNull PositionActionIntent intent) {
        if (intent.getSuggestedCloseQuantity() > 0) {
            return intent.getSuggestedCloseQuantity();
        }

        /*
         * If your PositionActionIntent contains currentPositionQuantity, use it here:
         *
         * if (intent.getCurrentPositionQuantity() != null && intent.getSuggestedRiskReductionPercent() != null) {
         *     return intent.getCurrentPositionQuantity() * (intent.getSuggestedRiskReductionPercent() / 100.0);
         * }
         */

        return 0.0;
    }

    private static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown execution error";
        }

        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
    /**
     * Execute a newly approved trade order.
     *
     * This is for NEW trade entries after:
     * StrategySignal
     * → RiskManagementSystem
     * → AiReasoningService
     * → FinalRiskGate
     * <p>
     * PositionActionIntent is for managing existing positions.
     * This method is for opening new positions.
     */
    public CompletableFuture<PositionExecutionResult> executeApprovedOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        try {
            if (!finalDecision.isApproved()) {
                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("FinalRiskGate did not approve this order")
                );
            }

            if (signal.getSymbol() == null ) {
                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("Signal symbol is missing")
                );
            }

            if (signal.getSide() == null) {
                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("Signal side is missing")
                );
            }

            if (finalDecision.getSuggestedPositionSize() <= 0) {
                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("Approved position size must be greater than zero")
                );
            }

            if (symbolFilter != null && symbolFilter.isSymbolEligible(signal.getSymbol())) {
                String reason = symbolFilter.getEligibilityReason(signal.getSymbol());
                log.warn("ExecutionEngine: Symbol {} rejected by filter. Reason: {}", signal.getSymbol(), reason);

                return CompletableFuture.completedFuture(
                        PositionExecutionResult.failed("Symbol rejected: " + reason)
                );
            }

            requireExchange();

            log.info(
                    "ExecutionEngine: Executing approved NEW order. symbol={} side={} size={} strategy={} execution={}",
                    signal.getSymbol(),
                    signal.getSide(),
                    finalDecision.getSuggestedPositionSize(),
                    signal.getStrategyId(),
                    finalDecision.getRecommendedExecutionStrategy()
            );

            return executeNewOrder(signal, riskContext, finalDecision)
                    .thenApply(orderId -> {
                        log.info("ExecutionEngine: New order executed successfully. orderId={}", orderId);
                        return PositionExecutionResult.success(orderId);
                    })
                    .exceptionally(exception -> {
                        String message = rootMessage(exception);
                        log.warn("ExecutionEngine: New order execution failed: {}", message);
                        return PositionExecutionResult.failed(message);
                    });

        } catch (Exception exception) {
            log.error("ExecutionEngine: Unexpected error during approved order execution", exception);
            return CompletableFuture.completedFuture(
                    PositionExecutionResult.failed(exception.getMessage())
            );
        }
    }
    /**
     * Internal new-order execution logic.
     * <p>
     * Replace placeholders with your real Exchange adapter methods once available.
     */
    private CompletableFuture<String> executeNewOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        requireExchange();

        String executionStrategy = finalDecision.getRecommendedExecutionStrategy();

        if (executionStrategy == null || executionStrategy.isBlank()) {
            executionStrategy = "MARKET";
        }

        double positionSize = finalDecision.getSuggestedPositionSize();

        return switch (executionStrategy.toUpperCase()) {
            case "LIMIT", "LIMIT_ORDER" -> placeLimitOrder(signal, riskContext, positionSize);
            case "MARKET", "MARKET_ORDER" -> placeMarketOrder(signal, riskContext, positionSize);
            case "VWAP" -> placeVwapOrder(signal, riskContext, positionSize);
            case "TWAP" -> placeTwapOrder(signal, riskContext, positionSize);
            case "ICEBERG" -> placeIcebergOrder(signal, riskContext, positionSize);
            case "SCALED", "SCALED_ENTRY" -> placeScaledEntryOrder(signal, riskContext, positionSize);
            case "ALGORITHMIC" -> placeAlgorithmicOrder(signal, riskContext, positionSize);
            default -> CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Unsupported execution strategy: " + executionStrategy)
            );
        };
    }
    private CompletableFuture<String> placeMarketOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing MARKET order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);


        return exchange.placeMarketOrder(signal.getSymbol(), signal.getSide(), positionSize);

    }

    private CompletableFuture<String> placeLimitOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        double limitPrice = signal.getEntryPrice() > 0
                ? signal.getEntryPrice()
                : riskContext.getEntryPrice();

        if (limitPrice <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("LIMIT order requires valid entry price")
            );
        }

        log.info("ExecutionEngine: Placing LIMIT order symbol={} side={} size={} price={}",
                signal.getSymbol(), signal.getSide(), positionSize, limitPrice);

         return exchange.placeLimitOrder(signal.getSymbol(), signal.getSide(), positionSize, limitPrice);



    }

    private CompletableFuture<String> placeVwapOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing VWAP order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);

        return CompletableFuture.completedFuture("VWAP_ORDER_STARTED");
    }

    private CompletableFuture<String> placeTwapOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing TWAP order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);

        return CompletableFuture.completedFuture("TWAP_ORDER_STARTED");
    }

    private CompletableFuture<String> placeIcebergOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing ICEBERG order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);
        log.info(riskContext.toString());

        return CompletableFuture.completedFuture("ICEBERG_ORDER_STARTED");
    }

    private @NotNull CompletableFuture<String> placeScaledEntryOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing SCALED_ENTRY order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);
        log.info(riskContext.toString());

        return CompletableFuture.completedFuture("SCALED_ENTRY_STARTED");
    }

    private @NotNull CompletableFuture<String> placeAlgorithmicOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info("ExecutionEngine: Placing ALGORITHMIC order symbol={} side={} size={}",
                signal.getSymbol(), signal.getSide(), positionSize);
        log.info(riskContext.toString());


        return CompletableFuture.completedFuture("ALGORITHMIC_ORDER_STARTED");
    }
    /**
     * Execution result record.
     */
    public record PositionExecutionResult(
            boolean success,
            @Nullable String orderId,
            @Nullable String errorMessage
    ) {
        public static @NotNull PositionExecutionResult success(@Nullable String orderId) {
            return new PositionExecutionResult(true, orderId, null);
        }

        public static @NotNull PositionExecutionResult failed(@Nullable String errorMessage) {
            return new PositionExecutionResult(false, null, errorMessage);
        }

        public boolean isSuccessful() {
            return success;
        }

        public @NotNull String statusMessage() {
            return success
                    ? "Executed: %s".formatted(orderId)
                    : "Failed: %s".formatted(errorMessage);
        }
    }
}