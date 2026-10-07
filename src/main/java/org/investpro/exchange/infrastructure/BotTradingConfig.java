package org.investpro.exchange.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.investpro.models.trading.TradePair;
import java.util.*;
import java.util.prefs.Preferences;

/**
 * Configuration for bot trading settings
 */
@Getter
@Setter

public class BotTradingConfig {
    

    public enum SymbolTradingMode {
        ALL_SYMBOLS("Trade all available symbols"),
        BEST_SYMBOLS("Trade only best performing symbols"),
        SELECTED_SYMBOLS("Trade only user-selected symbols");
        
        public final String description;
        
        SymbolTradingMode(String description) {
            this.description = description;
        }

    }
    
    @Getter
    public enum PositionSizingStrategy {
        FIXED_SIZE("Fixed position size"),
        PERCENTAGE("Percentage of account balance"),
        KELLY_CRITERION("Kelly Criterion (risk-based)"),
        VOLATILITY_ADJUSTED("Adjusted by market volatility");
        
        public final String description;
        
        PositionSizingStrategy(String description) {
            this.description = description;
        }

    }
    
    @Getter
    public enum MarginMode {
        NO_MARGIN("No leverage (spot trading)"),
        ISOLATED("Isolated margin per position"),
        CROSS("Cross margin (uses full balance)");
        
        public final String description;
        
        MarginMode(String description) {
            this.description = description;
        }

    }
    
    @Getter
    public enum StreamingMode {
        DISABLED("No real-time streaming"),
        REST_POLLING("REST API polling"),
        WEBSOCKET_ONLY("WebSocket connections only"),
        HYBRID("REST + WebSocket (hybrid)");
        
        public final String description;
        
        StreamingMode(String description) {
            this.description = description;
        }
    }
    
    private boolean enabled;
    private List<TradePair> tradingSymbols;
    private SymbolTradingMode symbolTradingMode;
    private double tradeSize;
    private double stopLoss;
    private double takeProfit;
    private double minProfitPercent = 0.5;
    private double maxPortfolioRiskPercent = 2.0;
    private Set<String> allowedSignals;
    private long lastTradeTime = 0;
    private long minTimeBetweenTrades = 5000; // 5 seconds minimum between trades

    // Leverage settings
    // Leverage and margin settings
    private double leverage = 1.0; // 1x to 100x
    private MarginMode marginMode = MarginMode.NO_MARGIN;
    private double maxLeverageRisk = 5.0; // Max % of portfolio to risk with leverage
    
    // Backtesting specific settings
    private double backtestRiskPercentPerTrade = 1.0; // Risk % per trade during backtesting
    private double backtestStartingBalance = 10000.0; // Starting balance for backtesting
    private double backtestMaxDrawdownPercent = 20.0; // Stop backtest if drawdown exceeds this
    private boolean backtestUseRealFees = true; // Apply realistic trading fees in backtest
    
    // Streaming and real-time settings
    private StreamingMode streamingMode = StreamingMode.HYBRID; // Streaming strategy mode
    private boolean streamingEnabled = true; // Enable real-time market data streaming
    private long streamingUpdateInterval = 1000; // Update interval in ms
    private boolean useWebsockets = true; // Use websocket connections when available
    private int maxWebsocketConnections = 5; // Max concurrent websocket connections
    
    // Position management
    private int maxOpenPositions = 10; // Maximum concurrent open positions
    private double maxDailyLosses = 5.0; // Stop trading if daily losses exceed this %
    private double positionSizePercent = 2.0; // % of account balance per position
    private PositionSizingStrategy positionSizingStrategy = PositionSizingStrategy.PERCENTAGE;
    
    // Additional risk management
    private boolean enableStrictMoneyManagement = true;
    private boolean enableDynamicPositionSizing = false;
    private double profitTakingPercent = 50.0; // Take % of profit at defined levels
    private long trailingStopUpdateInterval = 5000; // Update trailing stop every N ms
    private boolean enablePartialProfitTaking = true;

    // Small-account execution safety
    private boolean smallAccountModeEnabled = true;
    private double smallAccountBalanceThreshold = 100.0;
    private double smallAccountOandaUnits = 1.0;
    
    public BotTradingConfig() {
        this.enabled = false;
        this.tradingSymbols = new ArrayList<>();
        this.symbolTradingMode = SymbolTradingMode.SELECTED_SYMBOLS;
        this.tradeSize = 1.0;
        this.stopLoss = 0.0;
        this.takeProfit = 0.0;
        this.allowedSignals = new HashSet<>();
    }

    public List<TradePair> getTradingSymbols() {
        return new ArrayList<>(tradingSymbols);
    }
    
    public void setTradingSymbols(List<TradePair> symbols) {
        this.tradingSymbols = symbols != null ? new ArrayList<>(symbols) : new ArrayList<>();
    }
    
    public void addTradingSymbol(TradePair symbol) {
        if (symbol != null && !tradingSymbols.contains(symbol)) {
            tradingSymbols.add(symbol);
        }
    }
    




    public void setStopLoss(double stopLoss) {
        this.stopLoss = Math.max(0, stopLoss);
    }

    public void setTakeProfit(double takeProfit) {
        this.takeProfit = Math.max(0, takeProfit);
    }

    
    public boolean isSignalAllowed(String signal) {
        if (signal == null || signal.isBlank() || allowedSignals.isEmpty()) {
            return true;
        }
        return allowedSignals.contains(signal.toUpperCase().trim());
    }


    public boolean canTrade() {
        return enabled && !tradingSymbols.isEmpty() && 
               (System.currentTimeMillis() - lastTradeTime) >= minTimeBetweenTrades;
    }

    public void setLeverage(double leverage) {
        this.leverage = Math.max(1.0, Math.min(100.0, leverage)); // Clamp between 1x and 100x
    }


    public void setStreamingMode(StreamingMode mode) {
        this.streamingMode = mode != null ? mode : StreamingMode.HYBRID;
    }
    
    // ===== CONFIGURATION LOADING & PERSISTENCE =====
    
    /**
     * Load all bot configuration from Java Preferences
     * This is called on application startup to restore previous settings
     */
    public void loadFromPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(BotTradingConfig.class);
        
        // Basic settings
        this.enabled = prefs.getBoolean("bot_enabled", false);
        this.tradeSize = prefs.getDouble("bot_trade_size", 1.0);
        this.stopLoss = prefs.getDouble("bot_stop_loss", 0.0);
        this.takeProfit = prefs.getDouble("bot_take_profit", 0.0);
        this.minProfitPercent = prefs.getDouble("bot_min_profit", 0.5);
        this.maxPortfolioRiskPercent = prefs.getDouble("bot_max_risk", 2.0);
        this.minTimeBetweenTrades = prefs.getLong("bot_min_time_between", 5000);
        this.allowedSignals = parseAllowedSignals(prefs.get("bot_allowed_signals", ""));
        this.tradingSymbols = parseTradingSymbols(prefs.get("bot_trading_symbols", ""));
        
        // Leverage and margin
        this.leverage = prefs.getDouble("bot_leverage", 1.0);
        String marginModeStr = prefs.get("bot_margin_mode", "NO_MARGIN");
        try {
            this.marginMode = MarginMode.valueOf(marginModeStr);
        } catch (IllegalArgumentException e) {
            this.marginMode = MarginMode.NO_MARGIN;
        }
        this.maxLeverageRisk = prefs.getDouble("bot_max_leverage_risk", 5.0);
        
        // Backtesting settings
        this.backtestRiskPercentPerTrade = prefs.getDouble("bot_backtest_risk", 1.0);
        this.backtestStartingBalance = prefs.getDouble("bot_backtest_balance", 10000.0);
        this.backtestMaxDrawdownPercent = prefs.getDouble("bot_backtest_drawdown", 20.0);
        this.backtestUseRealFees = prefs.getBoolean("bot_backtest_fees", true);
        
        // Streaming settings with safe type conversion
        String streamingModeStr = prefs.get("bot_streaming_mode", "HYBRID");
        try {
            this.streamingMode = StreamingMode.valueOf(streamingModeStr);
        } catch (IllegalArgumentException e) {
            this.streamingMode = StreamingMode.HYBRID;
        }
        
        this.streamingEnabled = prefs.getBoolean("bot_streaming", true);
        try {
            // Safe loading: try Long first, fallback to default if type mismatch
            this.streamingUpdateInterval = prefs.getLong("bot_stream_interval", 1000);
        } catch (ClassCastException e) {
            // Handle case where value was stored as Double
            this.streamingUpdateInterval = 1000;
        }
        this.useWebsockets = prefs.getBoolean("bot_websockets", true);
        this.maxWebsocketConnections = prefs.getInt("bot_max_websockets", 5);
        
        // Position management
        this.maxOpenPositions = prefs.getInt("bot_max_positions", 10);
        this.maxDailyLosses = prefs.getDouble("bot_max_daily_loss", 5.0);
        this.positionSizePercent = prefs.getDouble("bot_position_size", 2.0);
        String positionStrategyStr = prefs.get("bot_position_strategy", "PERCENTAGE");
        try {
            this.positionSizingStrategy = PositionSizingStrategy.valueOf(positionStrategyStr);
        } catch (IllegalArgumentException e) {
            this.positionSizingStrategy = PositionSizingStrategy.PERCENTAGE;
        }
        
        // Risk management
        this.enableStrictMoneyManagement = prefs.getBoolean("bot_strict_mm", true);
        this.enableDynamicPositionSizing = prefs.getBoolean("bot_dynamic_sizing", false);
        this.profitTakingPercent = prefs.getDouble("bot_profit_taking", 50.0);
        this.trailingStopUpdateInterval = prefs.getLong("bot_trailing_stop_interval", 5000);
        this.enablePartialProfitTaking = prefs.getBoolean("bot_partial_profit", true);
        this.smallAccountModeEnabled = prefs.getBoolean("bot_small_account_mode", true);
        this.smallAccountBalanceThreshold = prefs.getDouble("bot_small_account_threshold", 100.0);
        this.smallAccountOandaUnits = prefs.getDouble("bot_small_account_oanda_units", 1.0);
        
        // Symbol trading mode
        String symbolModeStr = prefs.get("bot_symbol_mode", "SELECTED_SYMBOLS");
        try {
            this.symbolTradingMode = SymbolTradingMode.valueOf(symbolModeStr);
        } catch (IllegalArgumentException e) {
            this.symbolTradingMode = SymbolTradingMode.SELECTED_SYMBOLS;
        }
    }

    /**
     * Reset all settings to defaults
     */
    public void resetToDefaults() {
        Preferences prefs = Preferences.userNodeForPackage(BotTradingConfig.class);
        try {
            prefs.clear(); // Clear all settings for this class
            prefs.sync();
        } catch (Exception e) {
            System.err.println("Failed to reset preferences: " + e.getMessage());
        }
        
        // Reinitialize with defaults
        this.enabled = false;
        this.tradingSymbols = new ArrayList<>();
        this.symbolTradingMode = SymbolTradingMode.SELECTED_SYMBOLS;
        this.tradeSize = 1.0;
        this.stopLoss = 0.0;
        this.takeProfit = 0.0;
        this.minProfitPercent = 0.5;
        this.maxPortfolioRiskPercent = 2.0;
        this.allowedSignals = new HashSet<>();
        this.lastTradeTime = 0;
        this.minTimeBetweenTrades = 5000;
        
        this.leverage = 1.0;
        this.marginMode = MarginMode.NO_MARGIN;
        this.maxLeverageRisk = 5.0;
        
        this.backtestRiskPercentPerTrade = 1.0;
        this.backtestStartingBalance = 10000.0;
        this.backtestMaxDrawdownPercent = 20.0;
        this.backtestUseRealFees = true;
        
        this.streamingMode = StreamingMode.HYBRID;
        this.streamingEnabled = true;
        this.streamingUpdateInterval = 1000;
        this.useWebsockets = true;
        this.maxWebsocketConnections = 5;
        
        this.maxOpenPositions = 10;
        this.maxDailyLosses = 5.0;
        this.positionSizePercent = 2.0;
        this.positionSizingStrategy = PositionSizingStrategy.PERCENTAGE;
        
        this.enableStrictMoneyManagement = true;
        this.enableDynamicPositionSizing = false;
        this.profitTakingPercent = 50.0;
        this.trailingStopUpdateInterval = 5000;
        this.enablePartialProfitTaking = true;
        this.smallAccountModeEnabled = true;
        this.smallAccountBalanceThreshold = 100.0;
        this.smallAccountOandaUnits = 1.0;
    }
    
    @Override
    public String toString() {
        return "BotTradingConfig{enabled=%s, symbols=%d, tradeSize=%s, stopLoss=%s, takeProfit=%s, leverage=%s, marginMode=%s, streaming=%s, maxOpenPositions=%d, positionSizingStrategy=%s, allowedSignals=%s}".formatted(enabled, tradingSymbols.size(), tradeSize, stopLoss, takeProfit, leverage, marginMode, streamingEnabled, maxOpenPositions, positionSizingStrategy, allowedSignals);
    }

    private Set<String> parseAllowedSignals(String rawSignals) {
        Set<String> parsedSignals = new HashSet<>();
        if (rawSignals == null || rawSignals.isBlank()) {
            return parsedSignals;
        }

        for (String signal : rawSignals.split(",")) {
            if (!signal.isBlank()) {
                parsedSignals.add(signal.trim().toUpperCase(Locale.ROOT));
            }
        }
        return parsedSignals;
    }

    private List<TradePair> parseTradingSymbols(String rawSymbols) {
        List<TradePair> parsedSymbols = new ArrayList<>();
        if (rawSymbols == null || rawSymbols.isBlank()) {
            return parsedSymbols;
        }

        for (String symbol : rawSymbols.split(",")) {
            String normalized = symbol.trim();
            if (normalized.isBlank()) {
                continue;
            }

            String[] parts = normalized.contains("/")
                    ? normalized.split("/")
                    : normalized.split("-");
            if (parts.length != 2) {
                continue;
            }

            try {
                parsedSymbols.add(new TradePair(parts[0], parts[1]));
            } catch (Exception ignored) {
                // Ignore symbols that are no longer known to the local currency registry.
            }
        }
        return parsedSymbols;
    }

    private String formatTradingSymbols() {
        List<String> symbols = new ArrayList<>();
        for (TradePair pair : tradingSymbols) {
            if (pair != null) {
                symbols.add(pair.toSlashSymbol());
            }
        }
        return String.join(",", symbols);
    }
}
