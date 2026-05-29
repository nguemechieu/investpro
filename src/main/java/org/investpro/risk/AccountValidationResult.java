package org.investpro.risk;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record AccountValidationResult(
        @NotNull List<String> reasons,
        @NotNull List<String> warnings,
        @NotNull List<String> blockers) {

    public boolean isApproved() {
        return blockers.isEmpty();
    }
}
