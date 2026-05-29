package org.investpro.strategy.nocode;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of validating a {@link NoCodeStrategyDefinition} via
 * {@link NoCodeStrategyValidator}.
 *
 * <p>A {@code ValidationResult} is considered passing when
 * {@link #isValid()} returns {@code true} (no errors, though warnings are allowed).</p>
 */
public final class ValidationResult {

    private final List<String> errors;
    private final List<String> warnings;

    private ValidationResult(List<String> errors, List<String> warnings) {
        this.errors = List.copyOf(errors);
        this.warnings = List.copyOf(warnings);
    }

    // =========================================================================
    // Factory helpers
    // =========================================================================

    /** @return a new empty (passing) result with no errors or warnings. */
    public static ValidationResult empty() {
        return new ValidationResult(List.of(), List.of());
    }

    /** Mutable builder used by the validator. */
    public static Builder builder() {
        return new Builder();
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /** @return unmodifiable list of validation errors (blocking issues). */
    public List<String> getErrors() {
        return errors;
    }

    /** @return unmodifiable list of validation warnings (non-blocking notices). */
    public List<String> getWarnings() {
        return warnings;
    }

    /**
     * @return {@code true} when there are no errors (warnings do not fail validation).
     */
    public boolean isValid() {
        return errors.isEmpty();
    }

    @Override
    public String toString() {
        return "ValidationResult{valid=" + isValid()
                + ", errors=" + errors.size()
                + ", warnings=" + warnings.size() + "}";
    }

    // =========================================================================
    // Builder
    // =========================================================================

    /** Mutable builder for constructing a {@link ValidationResult}. */
    public static final class Builder {

        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        private Builder() {}

        /**
         * Adds a blocking error.
         *
         * @param msg error message
         * @return this
         */
        public Builder error(String msg) {
            errors.add(msg);
            return this;
        }

        /**
         * Adds a non-blocking warning.
         *
         * @param msg warning message
         * @return this
         */
        public Builder warning(String msg) {
            warnings.add(msg);
            return this;
        }

        /**
         * Merges another result into this builder.
         *
         * @param other result to merge
         * @return this
         */
        public Builder merge(ValidationResult other) {
            errors.addAll(other.errors);
            warnings.addAll(other.warnings);
            return this;
        }

        /** @return the immutable {@link ValidationResult}. */
        public ValidationResult build() {
            return new ValidationResult(errors, warnings);
        }
    }
}
