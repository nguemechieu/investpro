package org.investpro.core.agents.symbol;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the state of all symbol agents in the system.
 * 
 * Provides access to symbol agent states for UI components like MarketWatch
 * and System Monitor to display real-time status.
 */
@Slf4j
public class SymbolAgentManager {

    private final Map<String, SymbolAgentState> symbolStates = new ConcurrentHashMap<>();

    /**
     * Get the current state of a symbol.
     */
    public Optional<SymbolAgentState> getState(@NotNull TradePair symbol) {
        return Optional.ofNullable(symbolStates.get(symbolKey(symbol)));
    }

    /**
     * Get all symbol states.
     */
    public List<SymbolAgentState> getAllStates() {
        return new ArrayList<>(symbolStates.values());
    }

    /**
     * Ensure a symbol is visible to UI/status surfaces even before evaluation has
     * started.
     */
    public SymbolAgentState ensureSymbol(@NotNull TradePair symbol) {
        return symbolStates.computeIfAbsent(symbolKey(symbol), ignored -> defaultState(symbol));
    }

    /**
     * Seed the manager with all currently loaded market-watch symbols.
     */
    public void initializeSymbols(@Nullable Collection<TradePair> symbols) {
        if (symbols == null || symbols.isEmpty()) {
            return;
        }
        for (TradePair symbol : symbols) {
            if (symbol != null) {
                ensureSymbol(symbol);
            }
        }
    }

    /**
     * Update the state of a symbol.
     */
    public void updateState(@NotNull TradePair symbol, @NotNull SymbolAgentState state) {
        if (state.getSymbol() == null || !state.getSymbol().equals(symbol)) {
            state.setSymbol(symbol);
        }
        state.updateTimestamp();
        symbolStates.put(symbolKey(symbol), state);
        log.debug("Updated symbol state: {} -> {}", symbol, state.getTradingMode());
    }

    /**
     * Remove the state of a symbol.
     */
    public void removeState(@NotNull TradePair symbol) {
        symbolStates.remove(symbolKey(symbol));
    }

    /**
     * Get all symbols in a specific trading mode.
     */
    public List<SymbolAgentState> getStatesByMode(@NotNull SymbolTradingMode mode) {
        return symbolStates.values().stream()
                .filter(state -> state.getTradingMode() == mode)
                .toList();
    }

    /**
     * Get count of symbols in each trading mode.
     */
    public Map<SymbolTradingMode, Integer> getModeCounts() {
        Map<SymbolTradingMode, Integer> counts = new EnumMap<>(SymbolTradingMode.class);
        for (SymbolTradingMode mode : SymbolTradingMode.values()) {
            counts.put(mode, getStatesByMode(mode).size());
        }
        return counts;
    }

    /**
     * Get count of symbols by evaluation state.
     */
    public Map<SymbolEvaluationState, Integer> getEvaluationStateCounts() {
        Map<SymbolEvaluationState, Integer> counts = new EnumMap<>(SymbolEvaluationState.class);
        for (SymbolEvaluationState state : SymbolEvaluationState.values()) {
            int count = (int) symbolStates.values().stream()
                    .filter(s -> s.getState() == state)
                    .count();
            counts.put(state, count);
        }
        return counts;
    }

    /**
     * Get total number of symbols being tracked.
     */
    public int getTotalSymbolCount() {
        return symbolStates.size();
    }

    /**
     * Get count of symbols allowed to trade live.
     */
    public int getLiveAllowedCount() {
        return (int) symbolStates.values().stream()
                .filter(SymbolAgentState::isLiveAllowed)
                .count();
    }

    /**
     * Clear all states (useful for testing and shutdown).
     */
    public void clear() {
        symbolStates.clear();
    }

    private static @NotNull String symbolKey(@NotNull TradePair symbol) {
        return symbol.toString('/').trim().toUpperCase(Locale.ROOT);
    }

    private SymbolAgentState defaultState(@NotNull TradePair symbol) {
        SymbolAgentState state = SymbolAgentState.builder()
                .symbol(symbol)
                .state(SymbolEvaluationState.NOT_STARTED)
                .canTradeLive(false)
                .lastIssue("Waiting for strategy evaluation")
                .build();
        state.updateTimestamp();
        return state;
    }

    /**
     * Get a summary for System Monitor display.
     */
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<SymbolTradingMode, Integer> modeCounts = getModeCounts();

        summary.put("total", getTotalSymbolCount());
        summary.put("training", modeCounts.getOrDefault(SymbolTradingMode.TRAINING, 0));
        summary.put("paperTrading", modeCounts.getOrDefault(SymbolTradingMode.PAPER_TRADING, 0));
        summary.put("liveReady", modeCounts.getOrDefault(SymbolTradingMode.LIVE_READY, 0));
        summary.put("liveTrading", modeCounts.getOrDefault(SymbolTradingMode.LIVE_TRADING, 0));
        summary.put("blocked", modeCounts.getOrDefault(SymbolTradingMode.BLOCKED, 0));
        summary.put("paused", modeCounts.getOrDefault(SymbolTradingMode.PAUSED, 0));
        summary.put("failed", modeCounts.getOrDefault(SymbolTradingMode.FAILED, 0));
        summary.put("liveAllowed", getLiveAllowedCount());

        return summary;
    }
}
