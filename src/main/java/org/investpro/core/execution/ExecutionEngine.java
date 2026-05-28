package org.investpro.core.execution;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.FinalRiskGate;
import org.investpro.ai.PositionActionIntent;
import org.investpro.core.SystemCore;
import org.investpro.data.Db1;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.persistence.repository.CurrencyRepository;
import org.investpro.persistence.repository.CurrencyRepositoryImpl;
import org.investpro.persistence.repository.OrderRepository;
import org.investpro.persistence.repository.OrderRepositoryImpl;
import org.investpro.persistence.repository.TradeRepository;
import org.investpro.persistence.repository.TradeRepositoryImpl;
import org.investpro.risk.BehaviourGuardConfig;
import org.investpro.risk.BehaviourGuardService;
import org.investpro.risk.TradeRiskContext;
import org.investpro.service.CurrencyService;
import org.investpro.service.OrderService;
import org.investpro.service.TradeService;
import org.investpro.service.TradingService;
import org.investpro.strategy.StrategySignal;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityScope;
import org.investpro.trading.tradability.UniversalTradabilityService;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * ExecutionEngine - operator layer of InvestPro.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Execute only approved new-order decisions from FinalRiskGate.</li>
 *     <li>Execute only approved open-position actions from FinalRiskGate.</li>
 *     <li>Call Exchange adapter for broker/network operations.</li>
 *     <li>Apply symbol execution filtering.</li>
 *     <li>Apply behavior guard before execution.</li>
 *     <li>Reject unsupported/manual-review actions safely.</li>
 * </ul>
 *
 * <p>This class must not:</p>
 * <ul>
 *     <li>Generate signals.</li>
 *     <li>Decision risk.</li>
 *     <li>Ask AI for decisions.</li>
 *     <li>Bypass FinalRiskGate.</li>
 * </ul>
 */
@Slf4j
@Getter
@Setter
public class ExecutionEngine {

    private Exchange exchange;
    private SymbolExecutionFilter symbolFilter;

    private TradingService tradingService;
    private TradeRepository tradeRepository;
    private OrderRepository orderRepository;
    private CurrencyRepository currencyRepository;

    private BehaviourGuardService behaviourGuardService;
    private BehaviourGuardConfig behaviourGuardConfig;
    private UniversalTradabilityService universalTradabilityService;

    public ExecutionEngine(
            @NotNull Exchange exchange,
            @NotNull SymbolExecutionFilter symbolFilter,
            @NotNull Db1 db,
            @NotNull SystemCore systemCore
    ) {
        this.exchange = exchange;
        this.universalTradabilityService = new UniversalTradabilityService(exchange, null);
        this.symbolFilter = symbolFilter;
        this.systemCore=systemCore;

        initializeServices(db, systemCore);
    }

    public void setExchange(@Nullable Exchange exchange) {
        this.exchange = exchange;
        this.universalTradabilityService = exchange == null
                ? null
                : new UniversalTradabilityService(exchange, null);
    }

    private void initializeServices(@Nullable Db1 db, @Nullable SystemCore systemCore) {
        if (db == null) {
            log.warn("ExecutionEngine: Db1 is null. Repository-backed services may be limited.");
        }

        this.tradeRepository = new TradeRepositoryImpl(db);
        this.orderRepository = new OrderRepositoryImpl(db);
        this.currencyRepository = new CurrencyRepositoryImpl(db);

        this.tradingService = new TradingService(
                systemCore,
                new TradeService(tradeRepository),
                new OrderService(orderRepository),
                new CurrencyService(currencyRepository)
        );

        this.behaviourGuardService = new BehaviourGuardService();

        try {
            behaviourGuardService.loadConfig();
            log.info("ExecutionEngine: Behaviour guard service initialized");
        } catch (Exception exception) {
            log.warn(
                    "ExecutionEngine: Failed to load behaviour guard service config. reason={}",
                    rootMessage(exception),
                    exception
            );
        }

        this.behaviourGuardConfig = defaultBehaviourGuardConfig();
    }

    private BehaviourGuardConfig defaultBehaviourGuardConfig() {
        return new BehaviourGuardConfig(
                true,       // guardEnabled
                true,       // drawdownProtectionEnabled
                20.0,       // maxDrawdownPercent
                true,       // equityGuardEnabled
                25.0,       // minEquityThreshold
                false,      // winStreakLimitEnabled
                5,          // maxConsecutiveWins
                true,       // lossStreakLimitEnabled
                5,          // maxConsecutiveLosses
                false,      // tradingHoursEnabled
                "00:00",    // tradingStartTime
                "23:59",    // tradingEndTime
                false,      // volatilityFilterEnabled
                10.0,       // maxVolatilityPercent
                "ATR",      // volatilitySource
                "Default execution guard config"
        );
    }

    public void updateBehaviourGuardConfig( BehaviourGuardConfig config) {
        if (config == null) {
            log.warn("ExecutionEngine: Received null BehaviourGuardConfig. Using safe default config.");
            this.behaviourGuardConfig = defaultBehaviourGuardConfig();
            return;
        }

        this.behaviourGuardConfig = config;

        log.info(
                "ExecutionEngine: Behaviour guard config updated. enabled={} drawdownProtection={} equityGuard={} lossLimit={} winLimit={} tradingHours={} volatilityFilter={}",
                config.getGuardEnabled(),
                config.getDrawdownProtectionEnabled(),
                config.getEquityGuardEnabled(),
                config.getLossStreakLimitEnabled(),
                config.getWinStreakLimitEnabled(),
                config.getTradingHoursEnabled(),
                config.getVolatilityFilterEnabled()
        );
    }

    // =========================================================================
    // Open-position action execution
    // =========================================================================

    /**
     * Execute an approved position action intent.
     *
     * <p>Used for managing existing positions:</p>
     * <ul>
     *     <li>reduce size</li>
     *     <li>take partial profit</li>
     *     <li>close position</li>
     *     <li>move stop loss</li>
     *     <li>move take profit</li>
     *     <li>enable trailing stop</li>
     * </ul>
     */
    public CompletableFuture<PositionExecutionResult> execute(@NotNull PositionActionIntent intent) {
        try {
            PositionExecutionResult guardFailure = validateBehaviourGuard("POSITION_ACTION");
            if (guardFailure != null) {
                return CompletableFuture.completedFuture(guardFailure);
            }

            PositionExecutionResult validationFailure = validatePositionIntent(intent);
            if (validationFailure != null) {
                return CompletableFuture.completedFuture(validationFailure);
            }

            PositionExecutionResult symbolFailure = validateSymbolEligibility(intent.getSymbol());
            if (symbolFailure != null) {
                return CompletableFuture.completedFuture(symbolFailure);
            }

            log.info(
                    "ExecutionEngine: Executing approved position action. action={} symbol={} positionId={}",
                    intent.getAction(),
                    intent.getSymbol(),
                    intent.getPositionId()
            );

            return executeInternal(intent)
                    .thenApply(orderId -> {
                        log.info("ExecutionEngine: Position action executed successfully. resultId={}", orderId);
                        return PositionExecutionResult.success(orderId);
                    })
                    .exceptionally(exception -> {
                        String message = rootMessage(exception);
                        log.warn("ExecutionEngine: Position action execution failed: {}", message, exception);
                        return PositionExecutionResult.failed(message);
                    });

        } catch (Exception exception) {
            log.error("ExecutionEngine: Unexpected error during position action execution", exception);
            return CompletableFuture.completedFuture(PositionExecutionResult.failed(rootMessage(exception)));
        }
    }

    private @Nullable PositionExecutionResult validatePositionIntent(@Nullable PositionActionIntent intent) {
        if (intent == null) {
            return PositionExecutionResult.failed("PositionActionIntent was null");
        }

        if (!intent.isApproved()) {
            return PositionExecutionResult.failed("PositionActionIntent is not approved by FinalRiskGate");
        }

        if (intent.getAction() == null) {
            return PositionExecutionResult.failed("Position action is missing");
        }

        if (intent.getSymbol() == null || isBlank(toSymbolText(intent.getSymbol()))) {
            return PositionExecutionResult.failed("Symbol is missing");
        }

        return null;
    }

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
                    new UnsupportedOperationException(
                            "HEDGE requires manual approval and broker/account support"
                    )
            );
            case ESCALATE_TO_MANUAL_REVIEW -> CompletableFuture.failedFuture(
                    new UnsupportedOperationException(
                            "Manual-review actions cannot be executed automatically"
                    )
            );
        };
    }

    private @NotNull CompletableFuture<String> hold(@NotNull PositionActionIntent intent) {
        log.info("ExecutionEngine: HOLD maintained for {}", intent.getSymbol());
        return CompletableFuture.completedFuture("HOLD_MAINTAINED");
    }

    private CompletableFuture<String> reduceSize(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "REDUCE_SIZE");

        double closeQuantity = resolveCloseQuantity(intent);
        if (closeQuantity <= 0.0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "REDUCE_SIZE requires suggestedCloseQuantity or suggestedRiskReductionPercent"
                    )
            );
        }

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Reducing position. symbol={} positionId={} quantity={}",
                symbol,
                intent.getPositionId(),
                closeQuantity
        );

        return exchange.closePartialPosition(symbol, intent.getPositionId(), closeQuantity);
    }

    private CompletableFuture<String> takePartialProfit(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "TAKE_PARTIAL_PROFIT");

        double closeQuantity = resolveCloseQuantity(intent);
        if (closeQuantity <= 0.0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("TAKE_PARTIAL_PROFIT requires suggestedCloseQuantity")
            );
        }

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Taking partial profit. symbol={} positionId={} quantity={}",
                symbol,
                intent.getPositionId(),
                closeQuantity
        );

        return exchange.closePartialPosition(symbol, intent.getPositionId(), closeQuantity);
    }

    private CompletableFuture<String> closePosition(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "CLOSE_POSITION");

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Closing position. symbol={} positionId={}",
                symbol,
                intent.getPositionId()
        );

        return exchange.closePosition(symbol, intent.getPositionId());
    }

    private CompletableFuture<String> moveStopLoss(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "MOVE_STOP_LOSS");

        double suggestedStopLoss = intent.getSuggestedStopLoss();
        if (suggestedStopLoss <= 0.0 || !Double.isFinite(suggestedStopLoss)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("MOVE_STOP_LOSS requires suggestedStopLoss > 0")
            );
        }

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Moving stop loss. symbol={} positionId={} stopLoss={}",
                symbol,
                intent.getPositionId(),
                suggestedStopLoss
        );

        return exchange.modifyStopLoss(symbol, intent.getPositionId(), suggestedStopLoss);
    }

    private CompletableFuture<String> moveTakeProfit(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "MOVE_TAKE_PROFIT");

        double suggestedTakeProfit = intent.getSuggestedTakeProfit();
        if (suggestedTakeProfit <= 0.0 || !Double.isFinite(suggestedTakeProfit)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("MOVE_TAKE_PROFIT requires suggestedTakeProfit > 0")
            );
        }

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Moving take profit. symbol={} positionId={} takeProfit={}",
                symbol,
                intent.getPositionId(),
                suggestedTakeProfit
        );

        return exchange.modifyTakeProfit(symbol, intent.getPositionId(), suggestedTakeProfit);
    }

    private CompletableFuture<String> trailStop(@NotNull PositionActionIntent intent) {
        requireExchange();
        requirePositionId(intent, "TRAIL_STOP");

        double trailingDistance = intent.getTrailingDistance();
        if (trailingDistance <= 0.0 || !Double.isFinite(trailingDistance)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("TRAIL_STOP requires trailingDistance > 0")
            );
        }

        TradePair symbol = intent.getSymbol();

        log.info(
                "ExecutionEngine: Enabling trailing stop. symbol={} positionId={} trailingDistance={}",
                symbol,
                intent.getPositionId(),
                trailingDistance
        );

        return exchange.enableTrailingStop(symbol, intent.getPositionId(), trailingDistance);
    }

    // =========================================================================
    // New-order execution
    // =========================================================================

    /**
     * Preferred method for opening new positions.
     *
     * <p>Flow:</p>
     * <pre>
     * StrategySignal
     * -> RiskManagementSystem
     * -> AiReasoningService
     * -> FinalRiskGate
     * -> ExecutionEngine
     * </pre>
     */
    public CompletableFuture<PositionExecutionResult> executeApprovedOrder(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        Objects.requireNonNull(signal, "signal cannot be null");
        Objects.requireNonNull(riskContext, "riskContext cannot be null");
        Objects.requireNonNull(finalDecision, "finalDecision cannot be null");

        TradePair symbol = resolveOrderSymbol(signal, riskContext);
        double entryPrice = signal.getEntryPrice() > 0.0
                ? signal.getEntryPrice()
                : riskContext.getEntryPrice();

        return executeApprovedOrderInternal(
                signal.getSide(),
                symbol,
                entryPrice,
                riskContext,
                finalDecision,
                signal.getStrategyId()
        );
    }

    /**
     * Compatibility overload for legacy code that only passes side direction.
     *
     * <p>Prefer {@link #executeApprovedOrder(StrategySignal, TradeRiskContext, FinalRiskGate.OrderApprovalDecision)}.</p>
     */
    public CompletableFuture<PositionExecutionResult> executeApprovedOrder(
            @NotNull Side side,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(riskContext, "riskContext cannot be null");
        Objects.requireNonNull(finalDecision, "finalDecision cannot be null");

        return executeApprovedOrderInternal(
                side,
                riskContext.getSymbol(),
                riskContext.getEntryPrice(),
                riskContext,
                finalDecision,
                "LEGACY_SIDE_SIGNAL"
        );
    }

    private CompletableFuture<PositionExecutionResult> executeApprovedOrderInternal(
            @NotNull Side side,
            @NotNull TradePair symbol,
            double entryPrice,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision,
            @NotNull String strategyId
    ) {
        try {
            PositionExecutionResult guardFailure = validateBehaviourGuard("NEW_ORDER");
            if (guardFailure != null) {
                return CompletableFuture.completedFuture(guardFailure);
            }

            PositionExecutionResult validationFailure = validateNewOrder(side, symbol, finalDecision);
            if (validationFailure != null) {
                return CompletableFuture.completedFuture(validationFailure);
            }

            PositionExecutionResult symbolFailure = validateSymbolEligibility(symbol);
            if (symbolFailure != null) {
                return CompletableFuture.completedFuture(symbolFailure);
            }

            requireExchange();

            log.info(
                    "ExecutionEngine: Executing approved NEW order. symbol={} side={} size={} strategy={} execution={}",
                    symbol,
                    side,
                    finalDecision.getSuggestedPositionSize(),
                    strategyId,
                    finalDecision.getRecommendedExecutionStrategy()
            );

            return executeNewOrder(side, symbol, entryPrice, riskContext, finalDecision)
                    .thenApply(orderId -> {
                        log.info("ExecutionEngine: New order executed successfully. orderId={}", orderId);
                        return PositionExecutionResult.success(orderId);
                    })
                    .exceptionally(exception -> {
                        String message = rootMessage(exception);
                        log.warn("ExecutionEngine: New order execution failed: {}", message, exception);
                        return PositionExecutionResult.failed(message);
                    });

        } catch (Exception exception) {
            log.error("ExecutionEngine: Unexpected error during approved order execution", exception);
            return CompletableFuture.completedFuture(PositionExecutionResult.failed(rootMessage(exception)));
        }
    }

    private @Nullable PositionExecutionResult validateNewOrder(
            @Nullable Side side,
            @Nullable TradePair symbol,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        if (!finalDecision.isApproved()) {
            return PositionExecutionResult.failed("FinalRiskGate did not approve this order");
        }

        if (side == null) {
            return PositionExecutionResult.failed("Order side is missing");
        }

        if (side == Side.HOLD) {
            return PositionExecutionResult.failed("HOLD is not an executable new-order side");
        }

        if (symbol == null || isBlank(toSymbolText(symbol))) {
            return PositionExecutionResult.failed("Symbol is missing");
        }

        /*
         * Correct behavior:
         * If a trading session exists and the symbol is NOT tradable now,
         * block the order.
         */
        if (symbol.getTradingSession() != null && !symbol.isTradableNow()) {
            return PositionExecutionResult.failed(
                    "Trading session is not open: " + symbol.getTradingSessionStatus()
            );
        }

        double positionSize = finalDecision.getSuggestedPositionSize();
        if (positionSize <= 0.0 || !Double.isFinite(positionSize)) {
            return PositionExecutionResult.failed("Approved position size must be greater than zero and finite");
        }

        return null;
    }

    private CompletableFuture<String> executeNewOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            double entryPrice,
            @NotNull TradeRiskContext riskContext,
            @NotNull FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        requireExchange();

        String executionStrategy = finalDecision.getRecommendedExecutionStrategy();
        if (isBlank(executionStrategy)) {
            executionStrategy = "MARKET";
        }

        double positionSize = finalDecision.getSuggestedPositionSize();
        String normalizedStrategy = executionStrategy.trim().toUpperCase(Locale.ROOT);

        return switch (normalizedStrategy) {
            case "MARKET", "MARKET_ORDER" -> placeMarketOrder(side, symbol, positionSize);
            case "LIMIT", "LIMIT_ORDER" -> placeLimitOrder(side, symbol, entryPrice, positionSize);
            case "VWAP" -> placeVwapOrder(side, symbol, riskContext, positionSize);
            case "TWAP" -> placeTwapOrder(side, symbol, riskContext, positionSize);
            case "ICEBERG" -> placeIcebergOrder(side, symbol, riskContext, positionSize);
            case "SCALED", "SCALED_ENTRY" -> placeScaledEntryOrder(side, symbol, riskContext, positionSize);
            case "ALGORITHMIC", "ALGO" -> placeAlgorithmicOrder(side, symbol, riskContext, positionSize);
            default -> CompletableFuture.failedFuture(
                    new UnsupportedOperationException("Unsupported execution strategy: " + executionStrategy)
            );
        };
    }

    private CompletableFuture<String> placeMarketOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            double positionSize
    ) {
        log.info(
                "ExecutionEngine: Placing MARKET order. symbol={} side={} size={}",
                symbol,
                side,
                positionSize
        );

        SymbolTradability tradability = recheckOrderTradability(symbol, OpenOrder.OrderType.MARKET);
        if (tradability == null || !tradability.orderSubmissionAllowed() || !tradability.marketOrderAllowed()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Order blocked by tradability policy for "
                            + symbol
                            + ": "
                            + tradabilityBlockReason(tradability)
            ));
        }

        return exchange.placeMarketOrder(symbol, side, positionSize);
    }

    private CompletableFuture<String> placeLimitOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            double limitPrice,
            double positionSize
    ) {
        if (limitPrice <= 0.0 || !Double.isFinite(limitPrice)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("LIMIT order requires valid entry price")
            );
        }

        log.info(
                "ExecutionEngine: Placing LIMIT order. symbol={} side={} size={} price={}",
                symbol,
                side,
                positionSize,
                limitPrice
        );

        SymbolTradability tradability = recheckOrderTradability(symbol, OpenOrder.OrderType.LIMIT);
        if (tradability == null || !tradability.orderSubmissionAllowed() || !tradability.limitOrderAllowed()) {
            return CompletableFuture.failedFuture(new IllegalStateException(
                    "Order blocked by tradability policy for "
                            + symbol
                            + ": "
                            + tradabilityBlockReason(tradability)
            ));
        }

        return exchange.placeLimitOrder(symbol, side, positionSize, limitPrice);
    }

    private @Nullable SymbolTradability recheckOrderTradability(
            @NotNull TradePair symbol,
            @NotNull OpenOrder.OrderType orderType
    ) {
        if (universalTradabilityService == null) {
            return null;
        }

        try {
            return universalTradabilityService
                    .getTradability(symbol, TradabilityScope.ORDER_SUBMISSION, true)
                    .get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn(
                    "ExecutionEngine: Tradability recheck failed for symbol={} type={}",
                    symbol,
                    orderType,
                    exception
            );
            return null;
        }
    }

    private String tradabilityBlockReason(@Nullable SymbolTradability tradability) {
        if (tradability == null) {
            return "tradability unavailable";
        }

        String status = tradability.status() == null
                ? "UNKNOWN"
                : tradability.status().name();

        String reason = tradability.reason();

        if (reason == null || reason.isBlank()) {
            return status;
        }

        return status + " - " + reason;
    }

    /**
     * Placeholder until Exchange supports native VWAP execution.
     * Currently falls back to MARKET order after logging execution intent.
     */
    private CompletableFuture<String> placeVwapOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info(
                "ExecutionEngine: VWAP requested. Falling back to MARKET. symbol={} side={} size={}",
                symbol,
                side,
                positionSize
        );

        return placeMarketOrder(side, symbol, positionSize);
    }

    /**
     * Placeholder until Exchange supports native TWAP execution.
     * Currently falls back to MARKET order after logging execution intent.
     */
    private CompletableFuture<String> placeTwapOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info(
                "ExecutionEngine: TWAP requested. Falling back to MARKET. symbol={} side={} size={}",
                symbol,
                side,
                positionSize
        );

        return placeMarketOrder(side, symbol, positionSize);
    }

    /**
     * Placeholder until Exchange supports native ICEBERG execution.
     * Currently falls back to LIMIT if entry price exists, otherwise MARKET.
     */
    private CompletableFuture<String> placeIcebergOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info(
                "ExecutionEngine: ICEBERG requested. symbol={} side={} size={}",
                symbol,
                side,
                positionSize
        );

        double entryPrice = riskContext.getEntryPrice();
        if (entryPrice > 0.0 && Double.isFinite(entryPrice)) {
            return placeLimitOrder(side, symbol, entryPrice, positionSize);
        }

        return placeMarketOrder(side, symbol, positionSize);
    }

    /**
     * Placeholder scaled-entry implementation.
     * Currently places the first chunk as a market order.
     */
    private CompletableFuture<String> placeScaledEntryOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        double firstChunk = positionSize / 3.0;

        if (firstChunk <= 0.0 || !Double.isFinite(firstChunk)) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("SCALED_ENTRY requires positive position size")
            );
        }

        log.info(
                "ExecutionEngine: SCALED_ENTRY requested. Placing first chunk. symbol={} side={} chunk={} total={}",
                symbol,
                side,
                firstChunk,
                positionSize
        );

        return placeMarketOrder(side, symbol, firstChunk);
    }

    /**
     * Placeholder algorithmic order implementation.
     * Currently falls back to MARKET order.
     */
    private CompletableFuture<String> placeAlgorithmicOrder(
            @NotNull Side side,
            @NotNull TradePair symbol,
            @NotNull TradeRiskContext riskContext,
            double positionSize
    ) {
        log.info(
                "ExecutionEngine: ALGORITHMIC requested. Falling back to MARKET. symbol={} side={} size={} riskContext={}",
                symbol,
                side,
                positionSize,
                riskContext
        );

        return placeMarketOrder(side, symbol, positionSize);
    }

    // =========================================================================
    // Guard validation
    // =========================================================================

    private @Nullable PositionExecutionResult validateBehaviourGuard(@NotNull String operationType) {
        if (behaviourGuardService == null) {
            log.warn("ExecutionEngine: Behaviour guard service is not initialized. operation={}", operationType);
            return PositionExecutionResult.failed("Behaviour guard service is not initialized");
        }

        BehaviourGuardConfig config = behaviourGuardConfig;

        if (config == null) {
            return PositionExecutionResult.failed("Behaviour guard config is missing");
        }

        if (Boolean.FALSE.equals(config.getGuardEnabled())) {
            log.info("ExecutionEngine: Behaviour guard disabled by trading profile. operation={}", operationType);
            return null;
        }

        try {
            var validation = behaviourGuardService.validate(config);

            if (validation == null) {
                return PositionExecutionResult.failed("Behaviour guard validation returned null");
            }

            if (!validation.isValid()) {
                String reason = validation.toString();

                log.warn(
                        "ExecutionEngine: Behaviour guard rejected execution. operation={} reason={}",
                        operationType,
                        reason
                );

                return PositionExecutionResult.failed("Behaviour guard rejected execution: " + reason);
            }

            return null;

        } catch (Exception exception) {
            String message = "Behaviour guard validation failed: " + rootMessage(exception);
            log.warn("ExecutionEngine: {}", message, exception);
            return PositionExecutionResult.failed(message);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private void requireExchange() {
        if (exchange == null) {
            throw new IllegalStateException("Exchange not connected");
        }
        if (!Boolean.TRUE.equals(exchange.isConnected())) {
            throw new IllegalStateException("Exchange is not connected");
        }
        if (!exchange.canSubmitOrders()) {
            throw new IllegalStateException("Exchange is connected but cannot submit orders");
        }
    }

    private void requirePositionId(@NotNull PositionActionIntent intent, @NotNull String actionName) {
        if (isBlank(intent.getPositionId())) {
            throw new IllegalArgumentException(actionName + " requires positionId");
        }
    }

    private @Nullable PositionExecutionResult validateSymbolEligibility(@Nullable TradePair symbol) {
        if (symbolFilter == null) {
            return null;
        }

        if (symbol == null || isBlank(toSymbolText(symbol))) {
            return PositionExecutionResult.failed("Symbol is missing");
        }

        try {
            if (!symbolFilter.isSymbolEligible(symbol)) {
                String reason = symbolFilter.getEligibilityReason(symbol);

                log.warn(
                        "ExecutionEngine: Symbol {} rejected by filter. reason={}",
                        symbol,
                        reason
                );

                return PositionExecutionResult.failed("Symbol rejected: " + reason);
            }
        } catch (Exception exception) {
            String message = "Failed to validate symbol eligibility: " + rootMessage(exception);
            log.warn(message, exception);
            return PositionExecutionResult.failed(message);
        }

        return null;
    }

    private double resolveCloseQuantity(@NotNull PositionActionIntent intent) {
        double suggestedCloseQuantity = intent.getSuggestedCloseQuantity();
        if (suggestedCloseQuantity > 0.0 && Double.isFinite(suggestedCloseQuantity)) {
            return suggestedCloseQuantity;
        }

        double currentPositionQuantity = intent.getCurrentPositionQuantity();
        double suggestedRiskReductionPercent = intent.getSuggestedRiskReductionPercent();

        if (currentPositionQuantity > 0.0
                && suggestedRiskReductionPercent > 0.0
                && Double.isFinite(currentPositionQuantity)
                && Double.isFinite(suggestedRiskReductionPercent)) {
            return currentPositionQuantity * (suggestedRiskReductionPercent / 100.0);
        }

        return 0.0;
    }

    private TradePair resolveOrderSymbol(
            @NotNull StrategySignal signal,
            @NotNull TradeRiskContext riskContext
    ) {
        if (riskContext.getSymbol() != null) {
            return riskContext.getSymbol();
        }

        return convertSymbolToTradePair(signal.getSymbol());
    }

    /**
     * Convert symbol text to TradePair.
     *
     * <p>Supported examples:</p>
     * <ul>
     *     <li>BTC/USD</li>
     *     <li>BTC-USD</li>
     *     <li>BTC_USD</li>
     *     <li>BTCUSDT when quote can be inferred as USDT</li>
     * </ul>
     */
    private TradePair convertSymbolToTradePair(String symbol) {
        if (isBlank(symbol)) {
            throw new IllegalArgumentException("Symbol cannot be null or empty");
        }

        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        String[] parts;

        if (normalized.contains("/")) {
            parts = normalized.split("/");
        } else if (normalized.contains("-")) {
            parts = normalized.split("-");
        } else if (normalized.contains("_")) {
            parts = normalized.split("_");
        } else {
            parts = inferPairParts(normalized);
        }

        if (parts.length != 2 || isBlank(parts[0]) || isBlank(parts[1])) {
            throw new IllegalArgumentException(
                    "Invalid symbol format: " + symbol + " expected format BASE/QUOTE"
            );
        }

        try {
            return TradePair.fromSymbol(parts[0].trim() + "/" + parts[1].trim());
        } catch (SQLException | ClassNotFoundException exception) {
            throw new IllegalStateException("Failed to create TradePair from symbol: " + symbol, exception);
        }
    }

    private String[] inferPairParts(String normalizedSymbol) {
        String[] commonQuotes = {
                "USDT",
                "USDC",
                "USD",
                "EUR",
                "GBP",
                "JPY",
                "BTC",
                "ETH"
        };

        for (String quote : commonQuotes) {
            if (normalizedSymbol.endsWith(quote) && normalizedSymbol.length() > quote.length()) {
                String base = normalizedSymbol.substring(0, normalizedSymbol.length() - quote.length());
                return new String[]{base, quote};
            }
        }

        return new String[]{normalizedSymbol};
    }

    private String toSymbolText(@Nullable TradePair symbol) {
        if (symbol == null) {
            return "";
        }

        try {
            return symbol.toString('/');
        } catch (Exception exception) {
            return symbol.toString();
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String rootMessage(@Nullable Throwable throwable) {
        if (throwable == null) {
            return "Unknown execution error";
        }

        Throwable cause = throwable;

        while (cause.getCause() != null) {
            cause = cause.getCause();
        }

        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
    private SystemCore systemCore;

    public StrategySignal getTradingSignal() {
        try {
            if (systemCore == null || systemCore.getStrategyEngine() == null) {
                return null;
            }

            return systemCore.getStrategyEngine()
                    .getLastSignalCache()
                    .values()
                    .stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparing(StrategySignal::getCreatedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        } catch (Exception exception) {
            log.debug("Error getting last strategy signal", exception);
            return null;
        }
    }

    /**
     * Execution result.
     */
    public record PositionExecutionResult(
            boolean success,
            @NotNull String orderId,
            @Nullable String errorMessage
    ) {
        public static @NotNull PositionExecutionResult success(@NotNull String orderId) {
            return new PositionExecutionResult(true, orderId, "n/a");
        }

        public static @NotNull PositionExecutionResult failed(@NotNull String errorMessage) {
            return new PositionExecutionResult(false, UUID.randomUUID().toString(), errorMessage);
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
