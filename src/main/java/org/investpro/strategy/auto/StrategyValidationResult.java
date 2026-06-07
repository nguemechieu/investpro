package org.investpro.strategy.auto;

import java.util.List;

public record StrategyValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings) {

    public static StrategyValidationResult valid(List<String> warnings) {
        return new StrategyValidationResult(true, List.of(), warnings == null ? List.of() : List.copyOf(warnings));
    }

    public static StrategyValidationResult invalid(List<String> errors, List<String> warnings) {
        return new StrategyValidationResult(false,
                errors == null ? List.of() : List.copyOf(errors),
                warnings == null ? List.of() : List.copyOf(warnings));
    }
}
