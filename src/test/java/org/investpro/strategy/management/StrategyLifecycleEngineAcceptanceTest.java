package org.investpro.strategy.management;

import org.investpro.decision.MarketRegime;
import org.investpro.strategy.lifecycle.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StrategyLifecycleEngineAcceptanceTest {

    @Test
    void validatesPaperTradingAndRanksHealthyStrategy() {
        StrategyLifecycleRecord record = lifecycleRecord("strategy-a", 78.0, StrategyLifecycleStatus.PAPER_TRADING);
        StrategyPerformanceMetrics metrics = healthyMetrics(record.getAssignmentId());

        StrategyValidationReport validation = PaperTradingValidationEngine.getInstance()
                .validate(record, List.of(metrics));

        assertThat(validation.isApprovedForLive()).isTrue();

        StrategyHealthReport health = StrategyHealthEngine.getInstance().assessHealth(record, metrics);
        assertThat(health.getHealthLevel()).isIn(StrategyHealthLevel.GOOD, StrategyHealthLevel.EXCELLENT);

        StrategyLifecycleRecord reviewed = withEvidence(record, validation, health);
        StrategyRankScore rankScore = StrategyRankingEngine.getInstance().score(reviewed);

        assertThat(rankScore.getCompositeScore()).isGreaterThan(60.0);
    }

    @Test
    void recommendsReplacementForCriticalStrategyWithBetterCandidate() {
        StrategyLifecycleRecord degraded = withEvidence(
                lifecycleRecord("strategy-bad", 45.0, StrategyLifecycleStatus.DEGRADED),
                null,
                StrategyHealthReport.builder()
                        .assignmentId("assign-bad")
                        .strategyId("strategy-bad")
                        .symbol("BTC/USD")
                        .timeframe("1h")
                        .healthLevel(StrategyHealthLevel.CRITICAL)
                        .healthScore(20.0)
                        .profitFactor(0.7)
                        .maxDrawdown(0.30)
                        .winRate(0.25)
                        .triggeredAlerts(List.of("critical"))
                        .generatedAt(Instant.now())
                        .build());

        StrategyRankScore better = StrategyRankScore.builder()
                .strategyId("strategy-better")
                .symbol("BTC/USD")
                .timeframe("1h")
                .compositeScore(85.0)
                .regimeFitScore(80.0)
                .riskScore(90.0)
                .computedAt(Instant.now())
                .build();

        AIReplacementReport report = StrategyReplacementEngine.getInstance()
                .evaluateReplacement(degraded, List.of(better));

        assertThat(report.getRecommendation()).isIn(
                AIReplacementRecommendation.REPLACE,
                AIReplacementRecommendation.PAUSE,
                AIReplacementRecommendation.DEMOTE);
    }

    @Test
    void liveGateBlocksCriticalHealthEvenWithGoodScores() {
        StrategyLifecycleRecord record = lifecycleRecord("strategy-risky", 90.0, StrategyLifecycleStatus.PAPER_APPROVED);
        StrategyValidationReport validation = StrategyValidationReport.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .totalPaperTrades(80)
                .paperWinRate(0.70)
                .paperProfitFactor(1.9)
                .paperDrawdown(0.05)
                .riskBehaviorScore(0.85)
                .aiDecision(AIReviewDecision.APPROVE)
                .aiConfidence(0.88)
                .approvedForLive(true)
                .rejectionReasons(List.of())
                .validatedAt(Instant.now())
                .build();
        StrategyHealthReport critical = StrategyHealthReport.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .healthLevel(StrategyHealthLevel.CRITICAL)
                .healthScore(10.0)
                .generatedAt(Instant.now())
                .build();

        StrategyAssignmentGatekeeper.GateResult result = StrategyAssignmentGatekeeper.getInstance()
                .canPromoteLive(withEvidence(record, validation, critical));

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.summary()).contains("health");
    }

    private StrategyLifecycleRecord lifecycleRecord(String strategyId, double score, StrategyLifecycleStatus status) {
        return StrategyLifecycleRecord.builder()
                .assignmentId("assign-" + strategyId)
                .strategyId(strategyId)
                .strategyName(strategyId)
                .symbol("BTC/USD")
                .timeframe("1h")
                .assignmentScore(score)
                .confidence(score / 100.0)
                .assignedAt(Instant.now())
                .assignedBy("test")
                .assignmentReason("acceptance")
                .marketRegime(MarketRegime.UNKNOWN)
                .lifecycleStatus(status)
                .assignmentMode("AUTO")
                .aiApprovalStatus(AIReviewDecision.APPROVE)
                .aiConfidence(0.85)
                .promotionHistory(new ArrayList<>())
                .demotionHistory(new ArrayList<>())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private StrategyLifecycleRecord withEvidence(
            StrategyLifecycleRecord record,
            StrategyValidationReport validation,
            StrategyHealthReport health) {
        return StrategyLifecycleRecord.builder()
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .strategyName(record.getStrategyName())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .assignmentScore(record.getAssignmentScore())
                .confidence(record.getConfidence())
                .assignedAt(record.getAssignedAt())
                .assignedBy(record.getAssignedBy())
                .assignmentReason(record.getAssignmentReason())
                .marketRegime(record.getMarketRegime())
                .lifecycleStatus(record.getLifecycleStatus())
                .assignmentMode(record.getAssignmentMode())
                .aiApprovalStatus(record.getAiApprovalStatus())
                .aiConfidence(record.getAiConfidence())
                .lastAIReview(AIStrategyReview.builder()
                        .strategyId(record.getStrategyId())
                        .symbol(record.getSymbol())
                        .timeframe(record.getTimeframe())
                        .decision(AIReviewDecision.APPROVE)
                        .aiConfidence(record.getAiConfidence())
                        .reasoningSummary("approved")
                        .rejectionReasons(List.of())
                        .sampleSizeSufficient(true)
                        .profitFactorAcceptable(true)
                        .drawdownAcceptable(true)
                        .statisticallyMeaningful(true)
                        .reviewedAt(Instant.now())
                        .build())
                .lastValidationReport(validation)
                .lastHealthReport(health)
                .promotionHistory(record.getPromotionHistory())
                .demotionHistory(record.getDemotionHistory())
                .createdAt(record.getCreatedAt())
                .updatedAt(Instant.now())
                .build();
    }

    private StrategyPerformanceMetrics healthyMetrics(String assignmentId) {
        return StrategyPerformanceMetrics.builder()
                .assignmentId(assignmentId)
                .symbol("BTC/USD")
                .timeframe("1h")
                .netProfit(1250.0)
                .winRate(0.68)
                .profitFactor(1.9)
                .maxDrawdown(0.05)
                .expectancy(12.5)
                .sharpeRatio(1.8)
                .sortinoRatio(2.1)
                .averageTradeDuration(180)
                .averageTradeSize(1.0)
                .totalTrades(80)
                .winningTrades(54)
                .losingTrades(26)
                .signalAccuracy(0.70)
                .confidenceAccuracy(0.72)
                .aiApprovalAccuracy(0.75)
                .lastUpdated(Instant.now())
                .build();
    }
}
