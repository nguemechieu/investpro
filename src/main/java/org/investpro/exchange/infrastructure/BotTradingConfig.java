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
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
    
    public void removeTradingSymbol(TradePair symbol) {
        tradingSymbols.remove(symbol);
    }
    
    public SymbolTradingMode getSymbolTradingMode() {
        return symbolTradingMode;
    }
    
    public void setSymbolTradingMode(SymbolTradingMode mode) {
        this.symbolTradingMode = mode != null ? mode : SymbolTradingMode.SELECTED_SYMBOLS;
    }
    
    public double getTradeSize() {
        return tradeSize;
    }
    
    public void setTradeSize(double tradeSize) {
        this.tradeSize = Math.max(0, tradeSize);
    }

    public void setStopLoss(double stopLoss) {
        this.stopLoss = Math.max(0, stopLoss);
    }

    public void setTakeProfit(double takeProfit) {
        this.takeProfit = Math.max(0, takeProfit);
    }
    
    public double getMinProfitPercent() {
        return minProfitPercent;
    }
    
    public void setMinProfitPercent(double percent) {
        this.minProfitPercent = Math.max(0, percent);
    }
    
    public double getMaxPortfolioRiskPercent() {
        return maxPortfolioRiskPercent;
    }
    
    public void setMaxPortfolioRiskPercent(double percent) {
        this.maxPortfolioRiskPercent = Math.max(0, percent);
    }
    
    public Set<String> getAllowedSignals() {
        return new HashSet<>(allowedSignals);
    }
    
    public void setAllowedSignals(Set<String> signals) {
        this.allowedSignals = signals != null ? new HashSet<>(signals) : new HashSet<>();
    }
    
    public void addAllowedSignal(String signal) {
        if (signal != null && !signal.isBlank()) {
            allowedSignals.add(signal.toUpperCase().trim());
        }
    }
    
    public boolean isSignalAllowed(String signal) {
        if (signal == null || signal.isBlank() || allowedSignals.isEmpty()) {
            return true;
        }
        return allowedSignals.contains(signal.toUpperCase().trim());
    }
    
    public long getLastTradeTime() {
        return lastTradeTime;
    }
    
    public void setLastTradeTime(long time) {
        this.lastTradeTime = time;
    }
    
    public long getMinTimeBetweenTrades() {
        return minTimeBetweenTrades;
    }
    
    public void setMinTimeBetweenTrades(long milliseconds) {
        this.minTimeBetweenTrades = Math.max(1000, milliseconds);
    }
    
    public boolean canTrade() {
        return enabled && !tradingSymbols.isEmpty() && 
               (System.currentTimeMillis() - lastTradeTime) >= minTimeBetweenTrades;
    }
    
    // Leverage settings
    public double getLeverage() {
        return leverage;
    }
    
    public void setLeverage(double leverage) {
        this.leverage = Math.max(1.0, Math.min(100.0, leverage)); // Clamp between 1x and 100x
    }
    
    public MarginMode getMarginMode() {
        return marginMode;
    }
    
    public void setMarginMode(MarginMode marginMode) {
        this.marginMode = marginMode != null ? marginMode : MarginMode.NO_MARGIN;
    }
    
    public double getMaxLeverageRisk() {
        return maxLeverageRisk;
    }
    
    public void setMaxLeverageRisk(double percent) {
        this.maxLeverageRisk = Math.max(0, percent);
    }
    
    // Backtesting settings
    public double getBacktestRiskPercentPerTrade() {
        return backtestRiskPercentPerTrade;
    }
    
    public void setBacktestRiskPercentPerTrade(double percent) {
        this.backtestRiskPercentPerTrade = Math.max(0.1, Math.min(5.0, percent)); // 0.1% to 5%
    }
    
    public double getBacktestStartingBalance() {
        return backtestStartingBalance;
    }
    
    public void setBacktestStartingBalance(double balance) {
        this.backtestStartingBalance = Math.max(100, balance);
    }
    
    public double getBacktestMaxDrawdownPercent() {
        return backtestMaxDrawdownPercent;
    }
    
    public void setBacktestMaxDrawdownPercent(double percent) {
        this.backtestMaxDrawdownPercent = Math.max(1, Math.min(100, percent));
    }
    
    public boolean isBacktestUseRealFees() {
        return backtestUseRealFees;
    }
    
    public void setBacktestUseRealFees(boolean useRealFees) {
        this.backtestUseRealFees = useRealFees;
    }
    
    // Streaming settings
    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }
    
    public void setStreamingEnabled(boolean enabled) {
        this.streamingEnabled = enabled;
    }
    
    public long getStreamingUpdateInterval() {
        return streamingUpdateInterval;
    }
    
    public void setStreamingUpdateInterval(long intervalMs) {
        this.streamingUpdateInterval = Math.max(100, intervalMs);
    }
    
    public boolean isUseWebsockets() {
        return useWebsockets;
    }
    
    public void setUseWebsockets(boolean useWebsockets) {
        this.useWebsockets = useWebsockets;
    }
    
    public int getMaxWebsocketConnections() {
        return maxWebsocketConnections;
    }
    
    public void setMaxWebsocketConnections(int max) {
        this.maxWebsocketConnections = Math.max(1, Math.min(20, max));
    }
    
    // Position management
    public int getMaxOpenPositions() {
        return maxOpenPositions;
    }
    
    public void setMaxOpenPositions(int max) {
        this.maxOpenPositions = Math.max(1, max);
    }
    
    public double getMaxDailyLosses() {
        return maxDailyLosses;
    }
    
    public void setMaxDailyLosses(double percent) {
        this.maxDailyLosses = Math.max(0, percent);
    }
    
    public double getPositionSizePercent() {
        return positionSizePercent;
    }
    
    public void setPositionSizePercent(double percent) {
        this.positionSizePercent = Math.max(0.1, Math.min(50.0, percent));
    }
    
    public PositionSizingStrategy getPositionSizingStrategy() {
        return positionSizingStrategy;
    }
    
    public void setPositionSizingStrategy(PositionSizingStrategy strategy) {
        this.positionSizingStrategy = strategy != null ? strategy : PositionSizingStrategy.PERCENTAGE;
    }
    
    // Risk management
    public boolean isEnableStrictMoneyManagement() {
        return enableStrictMoneyManagement;
    }
    
    public void setEnableStrictMoneyManagement(boolean enable) {
        this.enableStrictMoneyManagement = enable;
    }
    
    public boolean isEnableDynamicPositionSizing() {
        return enableDynamicPositionSizing;
    }
    
    public void setEnableDynamicPositionSizing(boolean enable) {
        this.enableDynamicPositionSizing = enable;
    }
    
    public double getProfitTakingPercent() {
        return profitTakingPercent;
    }
    
    public void setProfitTakingPercent(double percent) {
        this.profitTakingPercent = Math.max(0, Math.min(100.0, percent));
    }
    
    public long getTrailingStopUpdateInterval() {
        return trailingStopUpdateInterval;
    }
    
    public void setTrailingStopUpdateInterval(long intervalMs) {
        this.trailingStopUpdateInterval = Math.max(1000, intervalMs);
    }
    
    public boolean isEnablePartialProfitTaking() {
        return enablePartialProfitTaking;
    }
    
    public void setEnablePartialProfitTaking(boolean enable) {
        this.enablePartialProfitTaking = enable;
    }

    public boolean isSmallAccountModeEnabled() {
        return smallAccountModeEnabled;
    }

    public void setSmallAccountModeEnabled(boolean smallAccountModeEnabled) {
        this.smallAccountModeEnabled = smallAccountModeEnabled;
    }

    public double getSmallAccountBalanceThreshold() {
        return smallAccountBalanceThreshold;
    }

    public void setSmallAccountBalanceThreshold(double smallAccountBalanceThreshold) {
        this.smallAccountBalanceThreshold = Math.max(0.0, smallAccountBalanceThreshold);
    }

    public double getSmallAccountOandaUnits() {
        return smallAccountOandaUnits;
    }

    public void setSmallAccountOandaUnits(double smallAccountOandaUnits) {
        this.smallAccountOandaUnits = Math.max(0.0, smallAccountOandaUnits);
    }
    
    public StreamingMode getStreamingMode() {
        return streamingMode;
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
     * Save all bot configuration to Java Preferences
     * This is called whenever settings are modified and saved
     */
    public void saveToPreferences() {
        Preferences prefs = Preferences.userNodeForPackage(BotTradingConfig.class);
        
        // Basic settings
        prefs.putBoolean("bot_enabled", enabled);
        prefs.putDouble("bot_trade_size", tradeSize);
        prefs.putDouble("bot_stop_loss", stopLoss);
        prefs.putDouble("bot_take_profit", takeProfit);
        prefs.putDouble("bot_min_profit", minProfitPercent);
        prefs.putDouble("bot_max_risk", maxPortfolioRiskPercent);
        prefs.putLong("bot_min_time_between", minTimeBetweenTrades);
        prefs.put("bot_allowed_signals", String.join(",", allowedSignals));
        prefs.put("bot_trading_symbols", formatTradingSymbols());
        
        // Leverage and margin
        prefs.putDouble("bot_leverage", leverage);
        prefs.put("bot_margin_mode", marginMode.name());
        prefs.putDouble("bot_max_leverage_risk", maxLeverageRisk);
        
        // Backtesting settings
        prefs.putDouble("bot_backtest_risk", backtestRiskPercentPerTrade);
        prefs.putDouble("bot_backtest_balance", backtestStartingBalance);
        prefs.putDouble("bot_backtest_drawdown", backtestMaxDrawdownPercent);
        prefs.putBoolean("bot_backtest_fees", backtestUseRealFees);
        
        // Streaming settings
        prefs.put("bot_streaming_mode", streamingMode.name());
        prefs.putBoolean("bot_streaming", streamingEnabled);
        prefs.putLong("bot_stream_interval", streamingUpdateInterval);
        prefs.putBoolean("bot_websockets", useWebsockets);
        prefs.putInt("bot_max_websockets", maxWebsocketConnections);
        
        // Position management
        prefs.putInt("bot_max_positions", maxOpenPositions);
        prefs.putDouble("bot_max_daily_loss", maxDailyLosses);
        prefs.putDouble("bot_position_size", positionSizePercent);
        prefs.put("bot_position_strategy", positionSizingStrategy.name());
        
        // Risk management
        prefs.putBoolean("bot_strict_mm", enableStrictMoneyManagement);
        prefs.putBoolean("bot_dynamic_sizing", enableDynamicPositionSizing);
        prefs.putDouble("bot_profit_taking", profitTakingPercent);
        prefs.putLong("bot_trailing_stop_interval", trailingStopUpdateInterval);
        prefs.putBoolean("bot_partial_profit", enablePartialProfitTaking);
        prefs.putBoolean("bot_small_account_mode", smallAccountModeEnabled);
        prefs.putDouble("bot_small_account_threshold", smallAccountBalanceThreshold);
        prefs.putDouble("bot_small_account_oanda_units", smallAccountOandaUnits);
        
        // Symbol trading mode
        prefs.put("bot_symbol_mode", symbolTradingMode.name());
        
        try {
            prefs.sync();
        } catch (Exception e) {
            System.err.println("Failed to sync preferences: " + e.getMessage());
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
            if (signal != null && !signal.isBlank()) {
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
            String normalized = symbol == null ? "" : symbol.trim();
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
