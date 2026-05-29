package org.investpro.strategy.nocode;

import lombok.extern.slf4j.Slf4j;

/**
 * Compiles a {@link NoCodeStrategyDefinition} into a
 * {@link CompiledNoCodeStrategy} ready for runtime evaluation.
 *
 * <p>Compilation steps:
 * <ol>
 *   <li>Validate the definition via {@link NoCodeStrategyValidator}.</li>
 *   <li>Compute the total warmup bar requirement.</li>
 *   <li>Return a {@link CompiledNoCodeStrategy} that encapsulates the definition,
 *       validation result, and computed warmup count.</li>
 * </ol>
 * </p>
 *
 * <p>This class is stateless and thread-safe.</p>
 */
@Slf4j
public class NoCodeStrategyCompiler {

    private final NoCodeStrategyValidator validator;

    /** Creates a compiler using the default validator. */
    public NoCodeStrategyCompiler() {
        this.validator = new NoCodeStrategyValidator();
    }

    /** Creates a compiler using the provided validator. */
    public NoCodeStrategyCompiler(NoCodeStrategyValidator validator) {
        this.validator = validator;
    }

    /**
     * Compiles the given strategy definition.
     *
     * <p>Compilation always succeeds — even invalid strategies produce a
     * {@link CompiledNoCodeStrategy} with {@link CompiledNoCodeStrategy#isValid()}
     * returning {@code false}. Callers must check validity before using the strategy
     * in a production context.</p>
     *
     * @param def the strategy definition to compile
     * @return compiled result
     */
    public CompiledNoCodeStrategy compile(NoCodeStrategyDefinition def) {
        if (def == null) {
            ValidationResult err = ValidationResult.builder()
                    .error("Cannot compile a null strategy definition.")
                    .build();
            return CompiledNoCodeStrategy.builder()
                    .definition(null)
                    .warmupBars(0)
                    .validationResult(err)
                    .build();
        }

        log.debug("Compiling no-code strategy: {}", def.getName());
        ValidationResult validation = validator.validate(def);
        int warmupBars = def.computeWarmupBars();

        CompiledNoCodeStrategy compiled = CompiledNoCodeStrategy.builder()
                .definition(def)
                .warmupBars(warmupBars)
                .validationResult(validation)
                .build();

        if (!compiled.isValid()) {
            log.warn("Strategy '{}' compiled with {} error(s): {}",
                    def.getName(), validation.getErrors().size(), validation.getErrors());
        } else {
            log.info("Strategy '{}' compiled successfully. Warmup bars: {}",
                    def.getName(), warmupBars);
        }

        return compiled;
    }
}
