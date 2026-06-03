package org.investpro.strategy.management;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.api.UserStrategy;
import org.investpro.strategy.impl.UserStrategyAdapter;
import org.investpro.strategy.lifecycle.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyAssignmentManagerSafetyGateTest {

    static {
        System.setProperty("investpro.strategy.lifecycle.dbUrl", "jdbc:sqlite:target/test-strategy-lifecycle.db");
    }

    @Test
    void blocksPromotionWhenValidationEvidenceMissing() {
        String strategyId = uniqueStrategyId();
        registerUserStrategy(strategyId);

        StrategyAssignmentManager manager = StrategyAssignmentManager.getInstance();
        StrategyLifecycleRecord record = manager.assign("BTC/USD", Timeframe.H1, strategyId, "Test User", 75.0, "test");

        StrategyLifecycleRecord promoted = manager.promoteToLive(record.getAssignmentId(), "safety gate test");

        assertThat(promoted).isNull();
        assertThat(manager.getRecord(record.getAssignmentId())).isPresent();
        assertThat(manager.getRecord(record.getAssignmentId()).orElseThrow().getLifecycleStatus())
                .isEqualTo(StrategyLifecycleStatus.DISCOVERED);

        StrategyRegistry.getInstance().unregister(strategyId);
    }

    @Test
    void blocksPromotionWhenBacktestScoreBelowThreshold() {
        String strategyId = uniqueStrategyId();
        registerUserStrategy(strategyId);

        StrategyAssignmentManager manager = StrategyAssignmentManager.getInstance();
        StrategyLifecycleRecord record = manager.assign("BTC/USD", Timeframe.H1, strategyId, "Test User", 50.0, "test");

        StrategyValidationReport report = approvedValidationReport(record);
        manager.updateValidationContext(
                record.getAssignmentId(),
                approvedAiReview(record),
                report,
                null,
                0.80,
                StrategyLifecycleStatus.PAPER_APPROVED);

        StrategyLifecycleRecord promoted = manager.promoteToLive(record.getAssignmentId(), "safety gate test");

        assertThat(promoted).isNull();

        StrategyRegistry.getInstance().unregister(strategyId);
    }

    @Test
    void allowsPromotionWhenAllSafetyGatesPass() {
        String strategyId = uniqueStrategyId();
        registerUserStrategy(strategyId);

        StrategyAssignmentManager manager = StrategyAssignmentManager.getInstance();
        StrategyLifecycleRecord record = manager.assign("BTC/USD", Timeframe.H1, strategyId, "Test User", 82.0, "test");

        StrategyValidationReport report = approvedValidationReport(record);
        manager.updateValidationContext(
                record.getAssignmentId(),
                approvedAiReview(record),
                report,
                null,
                0.85,
                StrategyLifecycleStatus.PAPER_APPROVED);

        StrategyLifecycleRecord promoted = manager.promoteToLive(record.getAssignmentId(), "safety gate test");

        assertThat(promoted).isNotNull();
        assertThat(promoted.getLifecycleStatus()).isEqualTo(StrategyLifecycleStatus.LIVE_ACTIVE);

        StrategyRegistry.getInstance().unregister(strategyId);
    }

    private void registerUserStrategy(String strategyId) {
        StrategyRegistry.getInstance().unregister(strategyId);
        StrategyRegistry.getInstance().register(strategyId, new UserStrategyAdapter(new TestUserStrategy(strategyId)));
    }

    private String uniqueStrategyId() {
        return "user-safety-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private StrategyValidationReport approvedValidationReport(StrategyLifecycleRecord record) {
        return StrategyValidationReport.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .paperTradePeriodDays(10)
                .totalPaperTrades(25)
                .paperWinRate(0.72)
                .paperProfitFactor(1.80)
                .paperDrawdown(0.06)
                .paperExpectancy(0.02)
                .signalQualityScore(0.72)
                .executionQualityScore(0.70)
                .riskBehaviorScore(0.75)
                .consistencyScore(0.68)
                .aiDecision(AIReviewDecision.APPROVE)
                .aiConfidence(0.84)
                .aiReasoningSummary("Validation passed")
                .behavingAsExpected(true)
                .performanceDegrading(false)
                .regimeChanging(false)
                .confidenceStillValid(true)
                .approvedForLive(true)
                .rejectionReasons(List.of())
                .validatedAt(Instant.now())
                .build();
    }

    private AIStrategyReview approvedAiReview(StrategyLifecycleRecord record) {
        return AIStrategyReview.builder()
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .decision(AIReviewDecision.APPROVE)
                .aiConfidence(0.84)
                .reasoningSummary("AI approved")
                .rejectionReasons(List.of())
                .sampleSizeSufficient(true)
                .profitFactorAcceptable(true)
                .drawdownAcceptable(true)
                .statisticallyMeaningful(true)
                .reviewedAt(Instant.now())
                .build();
    }

    private record TestUserStrategy(String id) implements UserStrategy {

        @Override
            public String name() {
                return "Safety Gate Test";
            }

            @Override
            public StrategySignal generateSignal(StrategyContext context) {
                return StrategySignal.hold("BTC/USD", "1h", id, "test");
            }
        }
}
