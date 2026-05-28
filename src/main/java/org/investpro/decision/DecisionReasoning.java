package org.investpro.decision;

import lombok.Builder;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * AI reasoning layer for a trade decision.
 *
 * <p>Captures the AI model's assessment, veto logic, confidence scoring,
 * and supporting signal chain. In LIGHTWEIGHT and SIMULATION modes this record
 * is omitted entirely; only LIVE and PAPER modes should store full reasoning.</p>
 *
 * <p>Designed to support LLM-based reasoning (OpenAI, local models), rule-based
 * AI veto systems, and multi-signal confidence aggregation.</p>
 */
@Builder
public record DecisionReasoning(

        /** Identifier of the AI model used (e.g., "gpt-4o", "rule-based-v2"). */
         String aiModel,

        /** AI-assigned confidence score for this trade opportunity (0.0–1.0). */
        double aiConfidence,

        /** High-level summary of the AI's reasoning in plain language. */
         String reasoningSummary,

        /**
         * Veto reason if AI rejected the trade. Null if approved.
         * Populated when AI confidence falls below threshold or signal conflicts found.
         */
         String vetoReason,

        /**
         * Override reason if AI approved despite low raw confidence.
         * Documents why the override was granted (e.g., "macro context confirms trend").
         */
         String overrideReason,

        /**
         * Chain of reasoning steps. Each entry is one logical step in the AI assessment.
         * May be empty for rule-based engines that don't expose reasoning chains.
         */
        @NotNull List<String> reasoningChain,

        /**
         * Supporting signals considered during reasoning.
         * Each entry names a signal or indicator that contributed to the conclusion.
         */
        @NotNull List<String> supportingSignals,

        /** When the AI reasoning was completed. */
        @NotNull Instant reasonedAt

) {

    /** Returns true if the AI vetoed this trade. */
    public boolean isVetoed() {
        return vetoReason != null && !vetoReason.isBlank();
    }

    /** Returns true if this reasoning meets the AI confidence threshold (≥ 0.60). */
    public boolean meetsAiThreshold() {
        return aiConfidence >= 0.60;
    }

    /**
     * Returns a compact placeholder for decisions that skip AI reasoning
     * (e.g., LIGHTWEIGHT simulation or when AI service is unavailable).
     */
    public static DecisionReasoning skipped() {
        return new DecisionReasoning(
                null, 0.0, "AI reasoning skipped",
                null, null, List.of(), List.of(), Instant.now());
    }

    /**
     * Returns a neutral (non-vetoing) reasoning placeholder with a given confidence.
     * Suitable as a safe default when no AI model is wired — the trade is not vetoed
     * and the AI contributes a neutral score to the composite.
     *
     * @param aiConfidence baseline AI confidence to report (0.0–1.0)
     */
    public static DecisionReasoning neutral(double aiConfidence) {
        double clamped = Math.max(0.0, Math.min(1.0, aiConfidence));
        return new DecisionReasoning(
                "rule-based-default", clamped, "No AI model injected — neutral pass-through",
                null, null, List.of(), List.of(), Instant.now());
    }

    /**
     * Null-object constant representing absent AI reasoning.
     * Use instead of {@code null} to avoid null-pointer chains in pipelines.
     */
    public static final DecisionReasoning NONE = new DecisionReasoning(
            null, 0.0, "No reasoning available",
            null, null, List.of(), List.of(), Instant.EPOCH);

    /**
     * Returns a reasoning record representing an AI veto.
     *
     * @param model  the AI model that issued the veto
     * @param reason the veto explanation
     */
    public static DecisionReasoning vetoed(@Nullable String model, @NotNull String reason) {
        return new DecisionReasoning(
                model, 0.0, "Trade vetoed by AI",
                reason, null, List.of(), List.of(), Instant.now());
    }
}
