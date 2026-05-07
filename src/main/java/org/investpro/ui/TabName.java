package org.investpro.ui;

/**
 * Enum for tab names in the trading terminal UI.
 * Centralizes tab naming for consistency and easier refactoring.
 */
public enum TabName {
    MARKET_INFO("MarketInfo", "Market Statistics", "📊"),
    OVERVIEW("Overview", "Trading Overview", "👁️"),
    BALANCES("Balances", "Account Balances", "💰"),
    DEPTH("Depth", "Market Depth Chart", "📈"),
    PORTFOLIO("Portfolio", "Portfolio Analysis", "🎯"),
    POSITIONS("Positions", "Open Positions", "📍"),
    RISK_MONITOR("Risk Monitor", "Risk Management", "⚠️"),
    SIGNALS("Signals", "Trading Signals", "⚡"),
    NEWS_CALENDAR("News Calendar", "Market News Calendar", "📰"),
    ALERTS("Alerts", "Trading Alerts", "🔔"),
    MAILBOX("Mailbox", "Messages", "📧"),
    CHAT("Chat", "AI Assistant Chat", "💬"),
    BROWSER("Browser", "Market Analysis", "🔍"),
    AGENTS("Agents", "AI Agents", "🤖"),
    JOURNAL("Journal", "Trading Journal", "📔"),
    STRATEGIES("Strategies", "Strategy Management", "🛠️"),
    STRATEGY_BUILDER("Strategy Builder", "Build Custom Strategies", "🏗️"),
    BACKTESTING("Backtesting", "Historical Backtest", "📊"),
    ANALYSIS("Analysis", "Strategy Analysis", "📈"),
    MARKET_RESEARCH("Market Research", "Market Research & Analysis", "🔬"),
    STRATEGY_RESEARCH("Strategy Research", "Strategy Research & Performance", "🔍"),
    RESEARCH_REPORTS("Research Reports", "Market Research Reports", "📑");

    private final String tabId;
    private final String displayName;
    private final String icon;

    TabName(String tabId, String displayName, String icon) {
        this.tabId = tabId;
        this.displayName = displayName;
        this.icon = icon;
    }

    public String getTabId() {
        return tabId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIcon() {
        return icon;
    }

    /**
     * Get TabName by its ID
     */
    public static TabName fromId(String tabId) {
        for (TabName tab : TabName.values()) {
            if (tab.tabId.equals(tabId)) {
                return tab;
            }
        }
        throw new IllegalArgumentException("Unknown tab ID: " + tabId);
    }
}
