package org.investpro.strategy.user;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.investpro.strategy.api.UserStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates user-developed strategies before they are eligible for use.
 *
 * Validation checks:
 * - Strategy ID is non-blank and valid
 * - Strategy name is non-blank and valid
 * - Warmup bars requirement is positive
 * - generateSignal() executes without throwing exceptions
 * - Generated StrategySignal is not null
 * - Signal has valid side (BUY, SELL, or HOLD)
 * - Signal normalization works
 *
 * Invalid strategies cannot be:
 * - Backtested
 * - Paper traded
 * - Assigned to live trading
 */
@Slf4j
@Getter
@Setter
public class UserStrategyValidator {

    private UserStrategyValidator() {
        // Utility class
    }

    /**
     * Validate a user strategy.
     *
     * @param strategy the user strategy to validate
     * @return validation result (always non-null)
     */
    public static UserStrategyValidationResult validate(@NotNull UserStrategy strategy) {
        try {
            return validateInternal(strategy);
        } catch (Exception e) {
            log.error("Unexpected error during strategy validation", e);
            return UserStrategyValidationResult.failure(
                    "unknown",
                    "Unknown",
                    "Validation failed with unexpected error",
                    e.getMessage());
        }
    }

    private static UserStrategyValidationResult validateInternal(@NotNull UserStrategy strategy) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Check ID
        String id = validateId(strategy, errors);
        if (id == null) {
            return createFailureResult(strategy, errors, warnings);
        }

        // Check name
        String name = validateName(strategy, errors);
        if (name == null) {
            return createFailureResult(strategy, errors, warnings);
        }

        // Check warmup bars
        validateWarmupBars(strategy, errors, warnings);

        // Check signal generation capability (safe test)
        validateSignalGeneration(strategy, id, name, errors, warnings);

        if (!errors.isEmpty()) {
            return createFailureResult(id, name, errors, warnings);
        }

        if (!warnings.isEmpty()) {
            log.warn("Strategy {} has warnings: {}", id, warnings);
        }

        return UserStrategyValidationResult.success(id, name);
    }

    private static String validateId(@NotNull UserStrategy strategy, List<String> errors) {
        try {
            String id = strategy.getId();
            if (id == null || id.isBlank()) {
                errors.add("Strategy ID is null or blank");
                return null;
            }

            // Validate ID format: alphanumeric, hyphens, underscores only
            if (!id.matches("^[a-zA-Z0-9_-]+$")) {
                errors.add("Strategy ID contains invalid characters. Use only: a-z, A-Z, 0-9, -, _");
                return null;
            }

            if (id.length() > 128) {
                errors.add("Strategy ID is too long (max 128 characters)");
                return null;
            }

            return id;
        } catch (Exception e) {
            errors.add("Failed to get strategy ID: " + e.getMessage());
            log.error("Error getting strategy ID", e);
            return null;
        }
    }

    private static String validateName(@NotNull UserStrategy strategy, List<String> errors) {
        try {
            String name = strategy.getName();
            if (name == null || name.isBlank()) {
                errors.add("Strategy name is null or blank");
                return null;
            }

            if (name.length() > 200) {
                errors.add("Strategy name is too long (max 200 characters)");
                return null;
            }

            return name;
        } catch (Exception e) {
            errors.add("Failed to get strategy name: " + e.getMessage());
            log.error("Error getting strategy name", e);
            return null;
        }
    }

    private static void validateWarmupBars(
            @NotNull UserStrategy strategy,
            List<String> errors,
            List<String> warnings) {
        try {
            int warmupBars = strategy.requiredWarmupBars();
            if (warmupBars <= 0) {
                errors.add("Required warmup bars must be > 0, got: " + warmupBars);
            } else if (warmupBars < 10) {
                warnings.add("Very low warmup requirement (" + warmupBars + " bars). May produce unreliable signals.");
            } else if (warmupBars > 5000) {
                warnings.add(
                        "Very high warmup requirement (" + warmupBars + " bars). Strategy will be slow to activate.");
            }
        } catch (Exception e) {
            errors.add("Error getting warmup bars requirement: " + e.getMessage());
            log.error("Error checking warmup bars", e);
        }
    }

    private static void validateSignalGeneration(
            @NotNull UserStrategy strategy,
            String id,
            String name,
            List<String> errors,
            List<String> warnings) {
        try {
            // Create a minimal test context
            StrategyContext testContext = StrategyContext.builder()
                    .symbol(null) // Will be set later
                    .timeframe(null) // Will be set later
                    .candles(List.of()) // Empty list for basic test
                    .currentPrice(100.0)
                    .bid(99.95)
                    .ask(100.05)
                    .barsAvailable(0)
                    .build();

            // Try generating a signal
            StrategySignal signal = strategy.generateSignal(testContext);

            if (signal == null) {
                errors.add("Strategy.generateSignal() returned null");
                return;
            }

            // Check signal has side
            if (signal.getSide() == null) {
                errors.add("Generated signal has null side (BUY/SELL/HOLD)");
                return;
            }

            // Check normalization works
            try {
                StrategySignal normalized = signal.normalized();
                if (normalized == null) {
                    errors.add("Signal normalization returned null");
                }
            } catch (Exception e) {
                errors.add("Signal normalization failed: " + e.getMessage());
                log.error("Normalization error for strategy {}", id, e);
            }

        } catch (Exception e) {
            errors.add("Strategy.generateSignal() threw exception: " + e.getMessage());
            log.error("Signal generation error for strategy {}", id, e);
        }
    }

    private static UserStrategyValidationResult createFailureResult(
            @NotNull UserStrategy strategy,
            List<String> errors,
            List<String> warnings) {
        String id = "unknown";
        String name = "Unknown";

        try {
            id = strategy.getId();
        } catch (Exception ignored) {
        }

        try {
            name = strategy.getName();
        } catch (Exception ignored) {
        }

        return createFailureResult(id, name, errors, warnings);
    }

    private static UserStrategyValidationResult createFailureResult(
            String strategyId,
            String strategyName,
            List<String> errors,
            List<String> warnings) {
        String summary = errors.size() + " error(s) found";
        if (!warnings.isEmpty()) {
            summary += ", " + warnings.size() + " warning(s)";
        }

        return UserStrategyValidationResult.builder()
                .strategyId(strategyId)
                .strategyName(strategyName)
                .valid(false)
                .summary(summary)
                .errors(errors)
                .warnings(warnings)
                .validatedAtEpochMs(System.currentTimeMillis())
                .build();
    }
}
