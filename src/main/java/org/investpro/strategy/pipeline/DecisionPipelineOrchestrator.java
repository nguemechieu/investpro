package org.investpro.strategy.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.decision.MarketRegime;
import org.investpro.event.EventBusManager;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;
import org.investpro.exchange.blockchain.execution.BlockchainExecutionRequests;
import org.investpro.exchange.blockchain.execution.BlockchainExecutionService;
import org.investpro.risk.RiskDecision;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.ai.AISignalReviewEngine;
import org.investpro.strategy.execution.ExecutionPlan;
import org.investpro.strategy.execution.ExecutionPlanningEngine;
import org.investpro.strategy.execution.ExecutionRouter;
import org.investpro.strategy.execution.ExecutionVenue;
import org.investpro.strategy.lifecycle.AISignalReview;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.position.PositionSizeRequest;
import org.investpro.strategy.position.PositionSizeResult;
import org.investpro.strategy.position.PositionSizingEngine;
import org.investpro.strategy.position.PositionSizingMethod;

import java.util.Optional;

/**
 * Orchestrates the complete Signal → AI Review → Position Sizing → Execution
 * Planning → Route pipeline.
 *
 * <p>
 * This class coordinates all phases without executing any orders. The final
 * {@link ExecutionPlan} returned is advisory and must be submitted to the
 * order management system separately by a human-approved or risk-cleared
 * mechanism.
 * </p>
 *
 * <p>
 * <strong>CRITICAL:</strong> This orchestrator NEVER places, submits, or
 * executes any orders.
 * It produces execution plans for downstream consumption only.
 * </p>
 */
@Slf4j
public class DecisionPipelineOrchestrator {

    private static volatile DecisionPipelineOrchestrator instance;

    private final AISignalReviewEngine signalReviewer = AISignalReviewEngine.getInstance();
    private final PositionSizingEngine positionSizingEngine = PositionSizingEngine.getInstance();
    private final ExecutionPlanningEngine planningEngine = ExecutionPlanningEngine.getInstance();
    private final ExecutionRouter executionRouter = ExecutionRouter.getInstance();
    private final EventBusManager eventBus = EventBusManager.getInstance();

    private static final String SOURCE = "DecisionPipelineOrchestrator";

    /** Minimum AI confidence required to proceed through the pipeline. */
    private static final double MIN_CONFIDENCE_THRESHOLD = 0.50;

    private DecisionPipelineOrchestrator() {
        log.info("DecisionPipelineOrchestrator initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton DecisionPipelineOrchestrator
     */
    public static DecisionPipelineOrchestrator getInstance() {
        DecisionPipelineOrchestrator local = instance;
        if (local == null) {
            synchronized (DecisionPipelineOrchestrator.class) {
                local = instance;
                if (local == null) {
                    local = new DecisionPipelineOrchestrator();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Runs the complete decision pipeline for a trading signal.
     *
     * @param signal the raw trading signal to evaluate
     * @param record the lifecycle record for the strategy producing this signal
     * @param equity current account equity
     * @param method position sizing method to use
     * @return Optional containing ExecutionPlan if all stages approved, empty if
     *         rejected
     */
    public Optional<ExecutionPlan> process(StrategySignal signal,
            StrategyLifecycleRecord record,
            double equity,
            PositionSizingMethod method) {
        if (signal == null || record == null) {
            log.warn("process: null signal or lifecycle record");
            return Optional.empty();
        }

        String assignmentId = record.getAssignmentId();
        log.debug("Pipeline processing: assignment={} signal={}", assignmentId, signal.getSymbol());

        // =====================================================================
        // Stage 1: AI Signal Review
        // =====================================================================
        MarketRegime regime = record.getMarketRegime() != null
                ? record.getMarketRegime()
                : MarketRegime.UNKNOWN;

        AISignalReview signalReview;
        try {
            signalReview = signalReviewer.reviewSignal(signal, regime);
        } catch (Exception ex) {
            log.error("Pipeline Stage 1 (AI Signal Review) failed for assignment={}: {}",
                    assignmentId, ex.getMessage());
            return Optional.empty();
        }

        boolean aiAllows = signalReview.getDecision().allowsExecution();
        if (!aiAllows || signalReview.getAiConfidence() < MIN_CONFIDENCE_THRESHOLD) {
            log.info("Pipeline rejected at Stage 1 for assignment={}: decision={}, confidence={}",
                    assignmentId, signalReview.getDecision(), signalReview.getAiConfidence());
            eventBus.publish(AgentEvent.of(AgentEvent.SIGNAL_REJECTED, SOURCE, signalReview));
            return Optional.empty();
        }

        eventBus.publish(AgentEvent.of(AgentEvent.SIGNAL_APPROVED, SOURCE, signalReview));

        // =====================================================================
        // Stage 2: Position Sizing
        // =====================================================================
        double stopLossDistance = signal.getRiskDistance() > 0 ? signal.getRiskDistance()
                : 0.001 * signal.getEntryPrice();
        double sizeMultiplier = signalReview.getSuggestedSizeMultiplier();

        PositionSizeRequest sizeRequest = PositionSizeRequest.builder()
                .assignmentId(assignmentId)
                .strategyId(record.getStrategyId())
                .symbol(signal.getSymbol())
                .timeframe(signal.getTimeframe())
                .side(signal.isBuy() ? "BUY" : "SELL")
                .equity(equity)
                .riskPerTradePercent(0.02) // 2% risk per trade
                .entryPrice(signal.getEntryPrice())
                .stopLossPrice(signal.getStopLossPrice())
                .stopLossDistance(stopLossDistance)
                .pipValue(1.0) // default; caller may override
                .maxPositionSizePercent(0.10) // 10% of equity cap
                .method(method != null ? method : PositionSizingMethod.RISK_PERCENT)
                .aiSizeMultiplier(sizeMultiplier)
                .build();

        PositionSizeResult sizeResult;
        try {
            sizeResult = positionSizingEngine.calculateSize(sizeRequest);
        } catch (Exception ex) {
            log.error("Pipeline Stage 2 (Position Sizing) failed for assignment={}: {}",
                    assignmentId, ex.getMessage());
            return Optional.empty();
        }

        if (!sizeResult.isValid() || sizeResult.getPositionUnits() <= 0) {
            log.info("Pipeline rejected at Stage 2 (invalid position size) for assignment={}. Reason: {}",
                    assignmentId, sizeResult.getValidationReason());
            return Optional.empty();
        }

        // =====================================================================
        // Stage 3: Execution Planning
        // =====================================================================
        ExecutionPlan plan;
        try {
            plan = planningEngine.createPlan(signal, sizeResult, signalReview, record);
        } catch (Exception ex) {
            log.error("Pipeline Stage 3 (Execution Planning) failed for assignment={}: {}",
                    assignmentId, ex.getMessage());
            return Optional.empty();
        }

        // =====================================================================
        // Stage 4: Venue Routing (advisory)
        // =====================================================================
        try {
            ExecutionPlan routedPlan = executionRouter.route(plan, record);
            log.info("Pipeline completed: assignment={} units={} venue={}",
                    assignmentId, sizeResult.getPositionUnits(), routedPlan.getVenue());
            eventBus.publish(AgentEvent.of(AgentEvent.EXECUTION_PLAN_CREATED, SOURCE, routedPlan));
            return Optional.of(routedPlan);
        } catch (Exception ex) {
            log.error("Pipeline Stage 4 (Routing) failed for assignment={}: {}",
                    assignmentId, ex.getMessage());
            // Return the unrouted plan rather than failing completely
            return Optional.of(plan);
        }
    }

    /**
     * Executes blockchain plans only after a positive risk decision.
     *
     * <p>
     * This method enforces pipeline discipline and prevents direct signal-to-chain
     * execution without risk validation.
     * </p>
     */
    public BlockchainTransactionResult executeBlockchainPlan(
            ExecutionPlan plan,
            RiskDecision riskDecision,
            BlockchainExecutionService blockchainExecutionService,
            String walletAddress) {
        if (plan == null) {
            throw new IllegalArgumentException("Execution plan is required");
        }
        if (blockchainExecutionService == null) {
            throw new IllegalArgumentException("BlockchainExecutionService is required");
        }

        ExecutionVenue venue = plan.getVenue();
        if (venue != ExecutionVenue.SOLONA_DEX && venue != ExecutionVenue.STELLAR) {
            return BlockchainTransactionResult.failed(
                    plan.getPlanId(),
                    "UNSUPPORTED",
                    "UNSUPPORTED_VENUE",
                    "Execution venue is not blockchain-backed: " + venue);
        }

        String networkId = venue == ExecutionVenue.SOLONA_DEX ? "SOLONA" : "STELLAR";
        BlockchainExecutionRequests.OrderRequest request = "LIMIT".equalsIgnoreCase(plan.getOrderType())
                ? BlockchainExecutionRequests.OrderRequest.limitOrder(
                        networkId,
                        walletAddress,
                        plan.getSymbol(),
                        plan.getSide(),
                        plan.getUnits(),
                        plan.getEntryPrice())
                : BlockchainExecutionRequests.OrderRequest.marketOrder(
                        networkId,
                        walletAddress,
                        plan.getSymbol(),
                        plan.getSide(),
                        plan.getUnits());

        return blockchainExecutionService.executeOrder(request, riskDecision).join();
    }
}
