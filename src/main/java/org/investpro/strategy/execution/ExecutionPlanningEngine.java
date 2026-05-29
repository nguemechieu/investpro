package org.investpro.strategy.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.lifecycle.AISignalReview;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.position.PositionSizeResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates {@link ExecutionPlan} objects from signals, position sizing results, and AI reviews.
 * Plans are valid for 5 minutes from creation to prevent stale execution.
 *
 * <p><strong>CRITICAL:</strong> This engine plans but NEVER executes.
 * It NEVER places, submits, or routes orders to any exchange.</p>
 */
@Slf4j
public class ExecutionPlanningEngine {

    private static volatile ExecutionPlanningEngine instance;

    private static final long PLAN_VALID_SECONDS = 300L; // 5 minutes

    private ExecutionPlanningEngine() {
        log.info("ExecutionPlanningEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton ExecutionPlanningEngine
     */
    public static ExecutionPlanningEngine getInstance() {
        ExecutionPlanningEngine local = instance;
        if (local == null) {
            synchronized (ExecutionPlanningEngine.class) {
                local = instance;
                if (local == null) {
                    local = new ExecutionPlanningEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Creates an execution plan from the provided inputs.
     *
     * @param signal       the trading signal
     * @param sizeResult   the position sizing result
     * @param signalReview the AI signal review (may be null)
     * @param record       the strategy lifecycle record
     * @return a fully populated ExecutionPlan (not yet submitted or executed)
     * @throws IllegalArgumentException if signal, sizeResult, or record is null
     */
    public ExecutionPlan createPlan(StrategySignal signal,
                                    PositionSizeResult sizeResult,
                                    AISignalReview signalReview,
                                    StrategyLifecycleRecord record) {
        if (signal == null || sizeResult == null || record == null) {
            throw new IllegalArgumentException("signal, sizeResult, and record must not be null");
        }

        String side = signal.isBuy() ? "BUY" : signal.isSell() ? "SELL" : "HOLD";
        double entryPrice = signal.getEntryPrice();

        double stopLoss = signal.getStopLossPrice() > 0 ? signal.getStopLossPrice()
                : "BUY".equals(side) ? entryPrice * 0.99 : entryPrice * 1.01;
        double takeProfit = signal.getTakeProfitPrice() > 0 ? signal.getTakeProfitPrice()
                : "BUY".equals(side) ? entryPrice * 1.02 : entryPrice * 0.98;

        double riskRewardRatio = Math.abs(takeProfit - entryPrice)
                / Math.max(Math.abs(entryPrice - stopLoss), 0.0001);

        // Build validation notes
        List<String> validationNotes = new ArrayList<>();
        if (signalReview != null) {
            validationNotes.add("AI confidence: " + String.format("%.2f", signalReview.getAiConfidence()));
        }
        validationNotes.add("Position units: " + String.format("%.4f", sizeResult.getPositionUnits()));
        validationNotes.add("Risk/reward: " + String.format("%.2f", riskRewardRatio));
        if (sizeResult.isCappedByMax()) {
            validationNotes.add("Position capped at maximum allowed size");
        }

        boolean aiApprovedFlag = signalReview != null
                && signalReview.getDecision().allowsExecution();
        double aiConfidenceValue = signalReview != null ? signalReview.getAiConfidence() : 0.0;
        String aiSummary = signalReview != null ? signalReview.getReasoningSummary() : "";

        boolean planIsValid = sizeResult.isValid()
                && sizeResult.getPositionUnits() > 0
                && !"HOLD".equals(side)
                && riskRewardRatio >= 1.0;

        Instant now = Instant.now();
        Instant planValidUntil = now.plusSeconds(PLAN_VALID_SECONDS);

        log.debug("Execution plan created: assignment={} side={} units={} R/R={}",
                record.getAssignmentId(), side,
                sizeResult.getPositionUnits(), String.format("%.2f", riskRewardRatio));

        ExecutionPlan.ExecutionPlanBuilder builder = ExecutionPlan.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(signal.getSymbol())
                .timeframe(signal.getTimeframe())
                .side(side)
                .orderType("MARKET")
                .units(sizeResult.getPositionUnits())
                .notionalValue(sizeResult.getNotionalValue())
                .entryPrice(entryPrice)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .riskRewardRatio(riskRewardRatio)
                .riskAmount(sizeResult.getRiskAmount())
                .riskPercent(sizeResult.getRiskPercent())
                .venue(ExecutionVenue.PAPER_TRADE) // default; router will override
                .aiApproved(aiApprovedFlag)
                .aiConfidence(aiConfidenceValue)
                .aiReasoningSummary(aiSummary)
                .isValid(planIsValid)
                .createdAt(now)
                .planValidUntil(planValidUntil);

        for (String note : validationNotes) {
            builder.validationNote(note);
        }

        return builder.build();
    }
}
