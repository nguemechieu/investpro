package org.investpro.execution;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record ExecutionGuardDecision(
        boolean approved,
        @NotNull List<String> blockers,
        @NotNull List<String> warnings,
        @NotNull String reason) {
}
