package org.investpro.terminal.config;

public record UiConfig(
        String theme,
        boolean restoreWorkspaceLayout,
        int maxWatchlistSymbols,
        boolean debounceMarketDataUpdates
) {
    public static UiConfig load() {
        return new UiConfig(
                InvestProConfig.text("investpro.ui.theme", "dark"),
                InvestProConfig.bool("investpro.ui.restoreWorkspaceLayout", true),
                InvestProConfig.integer("investpro.ui.maxWatchlistSymbols", 250),
                InvestProConfig.bool("investpro.ui.debounceMarketDataUpdates", true));
    }
}
