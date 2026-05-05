package org.investpro.exchange.infrastructure;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.Ticker;
import org.investpro.risk.RiskManager;
import org.investpro.risk.TradeRiskParameters;
import org.investpro.risk.VolatilityCalculator;
import org.investpro.exchange.Exchange;
import java.util.*;


/**
 * Processes trading signals and executes bot trades
 */
public class SignalProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SignalProcessor.class);
    
    public enum SignalType {
        BUY("BUY", "LONG", "BULLISH"),
        SELL("SELL", "SHORT", "BEARISH"),
        CLOSE("CLOSE", "EXIT"),
        UNKNOWN("UNKNOWN");
        
        private final String[] aliases;
        
        SignalType(String... aliases) {
            this.aliases = aliases;
        }
        
        public static SignalType parse(String signal) {
            if (signal == null || signal.isBlank()) {
                return UNKNOWN;
            }
            
            String normalized = signal.toUpperCase().trim();
            
            for (SignalType type : SignalType.values()) {
                for (String alias : type.aliases) {
                    if (normalized.contains(alias)) {
                        return type;
                    }
                }
            }
            
            return UNKNOWN;
        }
    }
    
    @Getter
    public static class Signal {
        private final String rawSignal;
        private final SignalType type;
        private final TradePair tradePair;
        private final double price;
        private final long timestamp;
        private final Map<String, String> metadata;
        
        public Signal(String rawSignal, SignalType type, TradePair tradePair, 
                     double price, long timestamp) {
            this.rawSignal = rawSignal;
            this.type = type;
            this.tradePair = tradePair;
            this.price = price;
            this.timestamp = timestamp;
            this.metadata = new HashMap<>();
        }

        public void addMetadata(String key, String value) {
            metadata.put(key, value);
        }

        @Override
        public String toString() {
            return String.format("Signal{type=%s, pair=%s, price=%f, raw='%s'}", 
                type, tradePair, price, rawSignal);
        }
    }
    
    private final Exchange exchange;
    private final BotTradingConfig config;
    /**
     * -- GETTER --
     *  Get risk manager instance
     */
    @Getter
    private final RiskManager riskManager;
    
    public SignalProcessor(Exchange exchange, BotTradingConfig config, double accountBalance) {
        this.exchange = exchange;
        this.config = config;
        this.riskManager = new RiskManager(accountBalance);
        logger.info("SignalProcessor initialized with {}", riskManager);
    }
    
    /**
     * Process a trading signal and execute trades if conditions are met
     */
    public void processSignal(String rawSignal) {
        if (!config.isEnabled() || config.getTradingSymbols().isEmpty()) {
            logger.debug("Bot trading is disabled or no symbols configured");
            return;
        }
        
        SignalType signalType = SignalType.parse(rawSignal);
        
        if (signalType == SignalType.UNKNOWN) {
            logger.warn("Unknown signal type: {}", rawSignal);
            return;
        }
        
        if (!config.isSignalAllowed(rawSignal)) {
            logger.debug("Signal not allowed by configuration: {}", rawSignal);
            return;
        }
        
        if (!config.canTrade()) {
            logger.debug("Trading cooldown active, skipping signal: {}", rawSignal);
            return;
        }
        
        logger.info("Processing signal: {} (type: {})", rawSignal, signalType);
        
        // Execute trades on all configured symbols
        List<TradePair> symbols = config.getTradingSymbols();
        
        for (TradePair symbol : symbols) {
            try {
                executeTradeForSignal(signalType, symbol);
            } catch (Exception e) {
                logger.error("Failed to execute trade for signal {} on symbol {}", 
                    rawSignal, symbol, e);
            }
        }
        
        // Update last trade time
        config.setLastTradeTime(System.currentTimeMillis());
    }
    
    /**
     * Execute a trade based on signal type with risk-managed parameters
     */
    private void executeTradeForSignal(SignalType type, TradePair symbol) {
        try {
            // Get current price and ticker
            Ticker ticker ;
            try {
                ticker = exchange.getLivePrice(symbol);
            } catch (Exception e) {
                logger.warn("Could not fetch ticker for {}: {}", symbol, e.getMessage());
                // Use fallback with manual settings
                executeFallbackTrade(type, symbol);
                return;
            }
            
            if (ticker == null || ticker.getAskPrice() <= 0) {
                logger.warn("Invalid ticker data for {}", symbol);
                executeFallbackTrade(type, symbol);
                return;
            }
            
            // Calculate volatility (default 2% if candles not available)
            double volatility = 2.0;
            try {
                // Try to get historical candles for volatility calculation
                volatility = VolatilityCalculator.calculateCompositeVolatility(
                    exchange.getCandleDataSupplier(3600, symbol).get().get()  // 1 hour candles
                );
            } catch (Exception e) {
                logger.debug("Using default volatility for {}", symbol);
            }
            
            // Calculate risk parameters based on account risk
            boolean isBuySignal = (type == SignalType.BUY);
            TradeRiskParameters riskParams = riskManager.calculateRiskParameters(
                symbol, ticker, volatility, isBuySignal
            );
            
            if (!riskParams.isValid()) {
                logger.warn("Invalid risk parameters calculated for {}", symbol);
                return;
            }
            
            // Execute trade with risk-managed parameters
            double positionSize = riskParams.getPositionSize();
            double stopLossPercent = riskParams.getStopLossPercent();
            double takeProfitPercent = riskParams.getTakeProfitPercent();
            
            logger.info("Executing {} trade for {} - size={}, SL={}%, TP={}% [{}]",
                type, symbol, positionSize, stopLossPercent, takeProfitPercent,
                VolatilityCalculator.classifyVolatility(volatility));
            
            switch (type) {
                case BUY:
                    exchange.buy(symbol, null, positionSize, 1.0, stopLossPercent, takeProfitPercent, 0.0);
                    logger.info("BUY signal executed for {} with risk-managed position", symbol);
                    break;
                    
                case SELL:
                    exchange.sell(symbol, null, positionSize, -1.0, stopLossPercent, takeProfitPercent, 0.0);
                    logger.info("SELL signal executed for {} with risk-managed position", symbol);
                    break;
                    
                case CLOSE:
                    exchange.sell(symbol, null, positionSize, -1.0, 0.0, 0.0, 0.0);
                    logger.info("CLOSE signal executed for {}", symbol);
                    break;
                    
                default:
                    logger.warn("Cannot execute trade for signal type: {}", type);
            }
            
        } catch (Exception e) {
            logger.error("Error executing trade signal for {}: {}", symbol, e.getMessage(), e);
            executeFallbackTrade(type, symbol);
        }
    }
    
    /**
     * Fallback trade execution using manual settings
     */
    private void executeFallbackTrade(SignalType type, TradePair symbol) {
        double size = config.getTradeSize();
        double stopLoss = config.getStopLoss();
        double takeProfit = config.getTakeProfit();
        
        logger.info("Fallback: Executing {} trade for {} - size={}, SL={}, TP={}",
            type, symbol, size, stopLoss, takeProfit);
        
        switch (type) {
            case BUY:
                exchange.buy(symbol, null, size, 1.0, stopLoss, takeProfit, 0.0);
                break;
            case SELL:
                exchange.sell(symbol, null, size, -1.0, stopLoss, takeProfit, 0.0);
                break;
            case CLOSE:
                exchange.sell(symbol, null, size, -1.0, 0.0, 0.0, 0.0);
                break;
            default:
                break;
        }
    }
    

    
    /**
     * Get all configured symbols as comma-separated string
     */
    public String getConfiguredSymbolsAsString() {
        return config.getTradingSymbols().stream()
            .map(TradePair::toString)
            .reduce("%s, %s"::formatted)
            .orElse("None");
    }

    /**
     * Update account balance (should be called after trades)
     */
    public void updateAccountBalance(double balance) {
        riskManager.updateAccountBalance(balance);
    }
    
    /**
     * Set risk parameters
     */
    public void setRiskParameters(double riskPercent, double rewardRiskRatio) {
        riskManager.setRiskPercentage(riskPercent);
        riskManager.setRewardRiskRatio(rewardRiskRatio);
        logger.info("Risk parameters updated: risk={}%, reward-risk=1:{}", riskPercent, rewardRiskRatio);
    }
}
