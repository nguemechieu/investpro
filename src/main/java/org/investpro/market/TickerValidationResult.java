package org.investpro.market;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public record TickerValidationResult(
        boolean valid,
        @NotNull List<String> errors,
        @NotNull List<String> warnings) {
}
