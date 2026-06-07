package org.investpro.agent.symbol;

import java.math.BigDecimal;
import java.util.List;

public record RiskDecision(
        boolean approved,
        String reason,
        BigDecimal approvedQuantity,
        List<String> warnings) {

    public RiskDecision {
        approvedQuantity = approvedQuantity == null ? BigDecimal.ZERO : approvedQuantity;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static RiskDecision approved(BigDecimal quantity, String reason) {
        return new RiskDecision(true, reason == null ? "Approved" : reason, quantity, List.of());
    }

    public static RiskDecision rejected(String reason) {
        return new RiskDecision(false, reason == null ? "Rejected" : reason, BigDecimal.ZERO, List.of());
    }
}
