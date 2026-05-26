package org.investpro.execution;

import java.util.List;

public record ExecutionGuardDecision(
        boolean allowed,
        String reason,
        String blockingCondition,
        List<String> warnings) {

    public ExecutionGuardDecision {
        reason = reason == null ? "" : reason.trim();
        blockingCondition = blockingCondition == null ? "" : blockingCondition.trim();
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static ExecutionGuardDecision allowed(List<String> warnings) {
        return new ExecutionGuardDecision(true, "Execution guard allowed order submission.", "", warnings);
    }

    public static ExecutionGuardDecision blocked(String condition, String reason) {
        return new ExecutionGuardDecision(false, reason, condition, List.of());
    }
}
