package org.investpro.strategy.user;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.util.List;

/**
 * Result of validating a user-developed strategy.
 *
 * Tracks whether the strategy is valid and what issues were found.
 */
@Getter
@Builder(toBuilder = true)
public class UserStrategyValidationResult {

    /**
     * Strategy ID that was validated.
     */
    private final String strategyId;

    /**
     * Strategy name that was validated.
     */
    private final String strategyName;

    /**
     * Whether the strategy passed all validation checks.
     */
    private final boolean valid;

    /**
     * Critical errors that prevent the strategy from being used.
     */
    @Singular("error")
    private final List<String> errors;

    /**
     * Non-critical warnings that may impact strategy performance.
     */
    @Singular("warning")
    private final List<String> warnings;

    /**
     * Overall validation summary message.
     */
    private final String summary;

    /**
     * Optional detailed message explaining the validation result.
     */
    private final String details;

    /**
     * When this validation was performed.
     */
    private final long validatedAtEpochMs;

    // =========================================================================
    // Convenience methods
    // =========================================================================

    public boolean hasErrors() {
        return errors != null && !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return warnings != null && !warnings.isEmpty();
    }

    /**
     * Create a successful validation result.
     */
    public static UserStrategyValidationResult success(String strategyId, String strategyName) {
        return UserStrategyValidationResult.builder()
                .strategyId(strategyId)
                .strategyName(strategyName)
                .valid(true)
                .summary("Validation passed")
                .validatedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a failed validation result with errors.
     */
    public static UserStrategyValidationResult failure(
            String strategyId,
            String strategyName,
            String summary,
            List<String> errors) {
        return UserStrategyValidationResult.builder()
                .strategyId(strategyId)
                .strategyName(strategyName)
                .valid(false)
                .summary(summary)
                .errors(errors)
                .validatedAtEpochMs(System.currentTimeMillis())
                .build();
    }

    /**
     * Create a failed validation result with detailed message.
     */
    public static UserStrategyValidationResult failure(
            String strategyId,
            String strategyName,
            String summary,
            String details) {
        return UserStrategyValidationResult.builder()
                .strategyId(strategyId)
                .strategyName(strategyName)
                .valid(false)
                .summary(summary)
                .details(details)
                .validatedAtEpochMs(System.currentTimeMillis())
                .build();
    }
}
