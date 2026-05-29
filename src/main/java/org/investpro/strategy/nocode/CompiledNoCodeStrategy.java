package org.investpro.strategy.nocode;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Output of compiling a {@link NoCodeStrategyDefinition} via
 * {@link NoCodeStrategyCompiler}.
 *
 * <p>A compiled result pairs the original definition with computed metadata
 * (warmup bars, validation outcome) and is ready for use in a
 * {@link NoCodeStrategyRuntime}.</p>
 */
@Getter
@Builder
@ToString
public class CompiledNoCodeStrategy {

    /** The validated and compiled strategy definition. */
    private final NoCodeStrategyDefinition definition;

    /** Total minimum warmup bars required across all rules. */
    private final int warmupBars;

    /** Validation result produced during compilation. */
    private final ValidationResult validationResult;

    /** Whether the strategy passed all validation checks. */
    public boolean isValid() {
        return validationResult != null && validationResult.isValid();
    }
}
