package org.investpro.core.agents.execution;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Filters symbols before execution.
 *
 * Purpose:
 * - prevent bot trading on disabled symbols
 * - allow manual overrides
 * - block unsupported / risky / maintenance symbols
 * - centralize symbol execution eligibility
 */
@Slf4j
public class SymbolExecutionFilter {
    private final Set<String> enabledSymbols = new HashSet<>();
    private final Set<String> disabledSymbols = new HashSet<>();
    private boolean allowAllByDefault = true;

    public SymbolExecutionFilter() {
    }

    public SymbolExecutionFilter(boolean allowAllByDefault) {
        this.allowAllByDefault = allowAllByDefault;
    }

    public boolean isSymbolEligible(@Nullable TradePair symbol) {
        assert symbol != null;
        return !getDecision(symbol).eligible();
    }

    public @NotNull String getEligibilityReason(@NotNull TradePair symbol) {

        return getDecision(symbol).reason();
    }

    public @NotNull SymbolExecutionDecision getDecision(@NotNull TradePair symbol) {

        String normalized = normalize(symbol.toString('/'));

        if (disabledSymbols.contains(normalized)) {
            return SymbolExecutionDecision.rejected("Symbol is explicitly disabled for bot execution");
        }

        if (enabledSymbols.contains(normalized)) {
            return SymbolExecutionDecision.approved("Symbol is explicitly enabled for bot execution");
        }

        if (allowAllByDefault) {
            return SymbolExecutionDecision.approved("Symbol allowed by default policy");
        }

        return SymbolExecutionDecision.rejected("Symbol is not in the enabled execution list");
    }

    public void enableSymbol(@NotNull String symbol) {
        String normalized = normalize(symbol);
        disabledSymbols.remove(normalized);
        enabledSymbols.add(normalized);
        log.info("Symbol enabled for execution: {}", normalized);
    }

    public void disableSymbol(@NotNull String symbol) {
        String normalized = normalize(symbol);
        enabledSymbols.remove(normalized);
        disabledSymbols.add(normalized);
        log.warn("Symbol disabled for execution: {}", normalized);
    }

    public void removeSymbolRule(@NotNull String symbol) {
        String normalized = normalize(symbol);
        enabledSymbols.remove(normalized);
        disabledSymbols.remove(normalized);
        log.info("Symbol execution rule removed: {}", normalized);
    }

    private static @NotNull String normalize(@NotNull String symbol) {
        return Objects.requireNonNull(symbol, "symbol cannot be null")
                .trim()
                .toUpperCase()
                .replace("-", "/");
    }

    public record SymbolExecutionDecision(
            boolean eligible,
            @NotNull String reason) {
        public static @NotNull SymbolExecutionDecision approved(@NotNull String reason) {
            return new SymbolExecutionDecision(true, reason);
        }

        public static @NotNull SymbolExecutionDecision rejected(@NotNull String reason) {
            return new SymbolExecutionDecision(false, reason);
        }
    }
}
