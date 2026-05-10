package org.investpro.exchange.oanda;

import org.investpro.exchange.core.AssetClass;
import org.investpro.exchange.core.InstrumentType;

import java.util.Map;
import java.util.logging.Logger;

/**
 * OandaInstrumentClassifier - Classifies OANDA instruments (CFD/Margin only, no futures/perpetuals).
 */
public class OandaInstrumentClassifier {
    private static final Logger logger = Logger.getLogger(OandaInstrumentClassifier.class.getName());

    public record Classification(InstrumentType instrumentType, AssetClass assetClass, String baseAsset,
                                 String quoteAsset, String settlementAsset) {
    }
    
    /**
     * Classify an OANDA instrument by name.
     * Examples: EUR_USD (forex), XAU_USD (metal), DE30_EUR (index), GER30 (index)
     */
    public Classification classify(String instrumentName, Map<String, Object> instrumentMetadata) {
        // Parse currency pair (e.g., EUR_USD -> EUR base, USD quote)
        String[] parts = instrumentName.split("_");
        String baseAsset = parts.length > 0 ? parts[0] : "";
        String quoteAsset = parts.length > 1 ? parts[1] : "";
        
        // Classify by asset type
        if (isCurrencyPair(baseAsset, quoteAsset)) {
            return new Classification(
                InstrumentType.FOREX_CFD,
                AssetClass.FOREX,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        } else if (isMetalAsset(baseAsset)) {
            return new Classification(
                InstrumentType.METAL_CFD,
                AssetClass.COMMODITY,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        } else if (isIndexAsset(baseAsset, instrumentName)) {
            return new Classification(
                InstrumentType.INDEX_CFD,
                AssetClass.INDEX,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        } else if (isCommodityAsset(baseAsset)) {
            return new Classification(
                InstrumentType.COMMODITY_CFD,
                AssetClass.COMMODITY,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        } else if (isStockAsset(baseAsset)) {
            return new Classification(
                InstrumentType.STOCK_CFD,
                AssetClass.EQUITY,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        } else {
            logger.warning("Unknown OANDA instrument type: " + instrumentName);
            return new Classification(
                InstrumentType.UNKNOWN,
                AssetClass.UNKNOWN,
                baseAsset,
                quoteAsset,
                quoteAsset
            );
        }
    }
    
    private boolean isCurrencyPair(String base, String quote) {
        // Standard 3-letter currency codes
        return base.matches("^[A-Z]{3}$") && quote.matches("^[A-Z]{3}$") &&
               isValidCurrency(base) && isValidCurrency(quote);
    }
    
    private boolean isMetalAsset(String symbol) {
        // XAU (Gold), XAG (Silver), XPT (Platinum), XPD (Palladium)
        return symbol.matches("^X(AU|AG|PT|PD)$");
    }
    
    private boolean isIndexAsset(String symbol, String fullName) {
        // Index patterns: DE30 (DAX), UK100 (FTSE), US30 (Dow), NQ100 (Nasdaq)
        // Or currency-based indices: DAX_EUR, FTSE_GBP
        return symbol.matches("^(DE|UK|US|NQ|DAX|FTSE|IBEX|CAC|DJIA|SPX)\\d*$") ||
               fullName.matches(".*(30|100|500).*");
    }
    
    private boolean isCommodityAsset(String symbol) {
        // Oil (WTICO, BRENT), Natural Gas, etc.
        return symbol.matches("^(WTICO|BRENT|NGAS)$");
    }
    
    private boolean isStockAsset(String symbol) {
        // Individual stock symbols like AAPL, MSFT (if OANDA offers CFD stocks)
        return symbol.matches("^[A-Z]{1,5}$") && !isValidCurrency(symbol) && !isMetalAsset(symbol);
    }
    
    private boolean isValidCurrency(String code) {
        // Common currency codes
        return code.matches("^(USD|EUR|GBP|JPY|CHF|CAD|AUD|NZD|SGD|HKD|INR|MXN|CNY|RUB|ZAR|BRL|SEK|NOK|DKK|PLN|CZK|HUF|RON|BGN|HRK)$");
    }
}
