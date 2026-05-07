package org.investpro.core.agents.risk;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class RiskReviewResult {
    private final boolean approved;
    private final String stage;
    private final String reason;
    private final double amount;
    private final double price;
    private final String strategyName;
    private final String timeframe;
    private final String side;
    private final String executionStrategy;

    @Builder.Default
    private final List<String> blockers = List.of();

    @Builder.Default
    private final List<String> warnings = List.of();

    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    public static RiskReviewResult approved(
            double amount,
            double price,
            String strategyName,
            String timeframe,
            String side,
            String executionStrategy) {
        return RiskReviewResult.builder()
                .approved(true)
                .stage("RISK_ENGINE")
                .reason("Approved by risk engine")
                .amount(amount)
                .price(price)
                .strategyName(strategyName)
                .timeframe(timeframe)
                .side(side)
                .executionStrategy(executionStrategy)
                .blockers(List.of())
                .warnings(List.of())
                .metadata(Map.of())
                .build();
    }

    public static RiskReviewResult rejected(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "Rejected by risk engine" : reason;
        return RiskReviewResult.builder()
                .approved(false)
                .stage("RISK_ENGINE")
                .reason(safeReason)
                .blockers(List.of(safeReason))
                .warnings(List.of())
                .metadata(Map.of())
                .build();
    }
}
