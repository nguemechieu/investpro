package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Output of the risk evaluation phase in the institutional decision pipeline.
 *
 * <p>Summarizes the result of all risk checks: exposure, concentration, leverage,
 * liquidity, drawdown, margin, and volatility. The {@link Verdict} drives the
 * downstream pipeline — only {@link Verdict#APPROVED} and {@link Verdict#REDUCED}
 * continue to execution planning.</p>
 */
public record RiskEvaluation(

        /** Overall risk verdict for this decision. */
        @NotNull Verdict verdict,

        /** Short human-readable summary of the verdict. */
        @NotNull String summary,

        /** List of hard-stop reasons that caused a REJECTED or WAIT verdict. */
        @NotNull List<String> rejectionReasons,

        /** Non-fatal warnings that accompany an APPROVED or REDUCED verdict. */
        @NotNull List<String> warnings,

        /** Suggested position-size reduction factor (1.0 = no reduction, 0.5 = halve size). */
        double sizeReductionFactor,

        /** Exposure utilization as a fraction of limit (0.0–1.0+). */
        double exposureUtilization,

        /** Concentration contribution as a fraction of total portfolio. */
        double concentrationFraction,

        /** Current drawdown as a percentage (0.0–100.0). */
        double currentDrawdownPct,

        /** Whether the trade required a margin check. */
        boolean marginChecked,

        /** Whether the margin check passed. */
        boolean marginSufficient

) {

    /**
     * Risk verdict returned by the evaluation phase.
     */
    public enum Verdict {

        /** All risk checks passed. Trade may proceed at full size. */
        APPROVED,

        /** Risk checks passed with caveats; position size should be reduced. */
        REDUCED,

        /** A hard-stop risk condition was hit; trade must not be executed. */
        REJECTED,

        /** Market conditions warrant waiting (e.g., extreme volatility, stale data). */
        WAIT;

        public boolean allowsExecution() {
            return this == APPROVED || this == REDUCED;
        }
    }

    /** Returns true if the verdict permits execution (possibly at reduced size). */
    public boolean allowsExecution() {
        return verdict.allowsExecution();
    }

    /** Returns true if no rejection reasons were generated. */
    public boolean isClean() {
        return rejectionReasons.isEmpty() && warnings.isEmpty();
    }

    /** Quick factory for a clean approval. */
    public static RiskEvaluation approved(double exposureUtilization) {
        return new RiskEvaluation(
                Verdict.APPROVED, "All risk checks passed",
                List.of(), List.of(),
                1.0, exposureUtilization, 0.0, 0.0,
                false, true);
    }

    /** Quick factory for a hard rejection. */
    public static RiskEvaluation rejected(@NotNull String reason) {
        return new RiskEvaluation(
                Verdict.REJECTED, "Risk rejected: " + reason,
                List.of(reason), List.of(),
                0.0, 0.0, 0.0, 0.0,
                false, false);
    }

    /** Quick factory for a size-reduced approval. */
    public static RiskEvaluation reduced(
            double reductionFactor,
            @NotNull List<String> warnings,
            @Nullable String summary) {
        return new RiskEvaluation(
                Verdict.REDUCED,
                summary != null ? summary : "Risk approved at reduced size",
                List.of(), List.copyOf(warnings),
                reductionFactor, 0.0, 0.0, 0.0,
                false, true);
    }
}
