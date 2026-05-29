package org.investpro.risk;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record SpreadValidationResult(
        boolean acceptable,
        @NotNull BigDecimal spread,
        @NotNull BigDecimal spreadPercent,
        @NotNull String reason,
        @NotNull List<String> warnings) {
}
