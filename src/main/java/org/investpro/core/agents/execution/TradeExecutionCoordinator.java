package org.investpro.core.agents.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.ai.FinalRiskGate;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.risk.TradeRiskContext;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.investpro.utils.Side.HOLD;

/**
 * Coordinates the full new-trade execution pipeline.
 *
 * Pipeline:
 * Side (BUY/SELL/HOLD)
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
                return executionEngine
                        .executeApprovedOrder(side, riskContext, finalDecision)
                        .thenApply(executionResult -> {
                            if (executionResult.isSuccessful()) {
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
            log.error("TradeExecutionCoordinator: Unexpected error while processing signal", exception);
            return completed(TradeExecutionResult.failed(rootMessage(exception)));
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
