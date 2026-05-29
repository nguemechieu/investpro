package org.investpro.strategy.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.lifecycle.AISignalReview;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.position.PositionSizeResult;
import org.investpro.model.StrategySignal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates ExecutionPlans from signals, position sizing results, and AI reviews.
 * Plans are valid for 5 minutes from creation to prevent stale execution.
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
     * @param signalReview the AI signal review
     * @param record       the strategy lifecycle record
     * @return a fully populated ExecutionPlan (not yet submitted or executed)
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
        double stopLoss = signal.getStopLoss() > 0 ? signal.getStopLoss()
                : side.equals("BUY") ? entryPrice * 0.99 : entryPrice * 1.01;
        double takeProfit = signal.getTakeProfit() > 0 ? signal.getTakeProfit()
                : side.equals("BUY") ? entryPrice * 1.02 : entryPrice * 0.98;

        double riskRewardRatio = Math.abs(takeProfit - entryPrice)
                / Math.max(Math.abs(entryPrice - stopLoss), 0.0001);

        // Infer order type
        String orderType = inferOrderType(signal);

        // Build validation notes
        List<String> validationNotes = new ArrayList<>();
        validationNotes.add("AI confidence: " + String.format("%.2f", signalReview != null ? signalReview.getConfidence() : 0.0));
        validationNotes.add("Position units: " + String.format("%.4f", sizeResult.getPositionUnits()));
        validationNotes.add("Risk/reward: " + String.format("%.2f", riskRewardRatio));
        if (sizeResult.isCappedByMax()) validationNotes.add("Position capped at maximum");

        boolean isValid = sizeResult.isValid()
                && sizeResult.getPositionUnits() > 0
                && !"HOLD".equals(side)
                && riskRewardRatio >= 1.0;

        Instant now = Instant.now();
        Instant planValidUntil = now.plusSeconds(PLAN_VALID_SECONDS);

        log.debug("Execution plan created: assignment={} side={} units={} R/R={}",
                record.getAssignmentId(), side,
                sizeResult.getPositionUnits(), riskRewardRatio);

        return ExecutionPlan.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(signal.getSymbol())
                .side(side)
                .orderType(orderType)
                .units(sizeResult.getPositionUnits())
                .notionalValue(sizeResult.getNotionalValue())
                .entryPrice(entryPrice)
                .stopLoss(stopLoss)
                .takeProfit(takeProfit)
                .riskRewardRatio(riskRewardRatio)
                .riskAmount(sizeResult.getRiskAmount())
                .riskPercent(sizeResult.getRiskPercent())
                .venue(ExecutionVenue.OANDA_REST) // default; router may override
                .aiApproved(signalReview != null && signalReview.isApproved())
                .aiConfidence(signalReview != null ? signalReview.getConfidence() : 0.0)
                .aiReasoningSummary(signalReview != null ? signalReview.getReasoningSummary() : "")
                .validationNotes(validationNotes)
                .isValid(isValid)
                .createdAt(now)
                .planValidUntil(planValidUntil)
                .build();
    }

    private String inferOrderType(StrategySignal signal) {
        if (signal.getEntryPrice() <= 0) return "MARKET";
        // If signal has explicit limit price use LIMIT, else MARKET
        return signal.getLimitPrice() > 0 ? "LIMIT" : "MARKET";
    }
}
