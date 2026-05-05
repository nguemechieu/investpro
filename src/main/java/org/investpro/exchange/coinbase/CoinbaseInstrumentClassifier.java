package org.investpro.exchange.coinbase;

import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.logging.Logger;

/**
 * CoinbaseInstrumentClassifier - Classifies Coinbase products by type (Spot, Futures, Perpetuals).
 */
public class CoinbaseInstrumentClassifier {
    private static final Logger logger = Logger.getLogger(CoinbaseInstrumentClassifier.class.getName());

    /**
         * Classify a Coinbase product ID into the appropriate InstrumentType and BrokerVenue.
         * Product ID examples:
         * - BTC-USD (spot)
         * - BTC-PERP-INTX (international perpetual)
         * - BTC (US futures)
         */
        public record Classification(InstrumentType instrumentType, BrokerVenue venue, String baseAsset, String quoteAsset,
                                     String settlementAsset) {
    }
    
    public Classification classify(String productId, Map<String, Object> productMetadata) {
        // Determine venue and type from product ID patterns
        logger.info(productMetadata.toString());
        if (productId.contains("-PERP-")) {
            // International perpetual: BTC-PERP-INTX
            return classifyPerpetual(productId);
        } else if (productId.matches("[A-Z]+$")) {
            // US futures: BTC, ES, NQ (no hyphens, uppercase only)
            return classifyFutures(productId);
        } else if (productId.contains("-")) {
            // Spot: BTC-USD, ETH-USDT
            return classifySpot(productId);
        } else {
            logger.warning("Unknown Coinbase product format: %s".formatted(productId));
            return new Classification(InstrumentType.UNKNOWN, BrokerVenue.UNKNOWN,
                    productId, "", "");
        }
    }
    
    private Classification classifySpot(String productId) {
        String[] parts = productId.split("-");
        if (parts.length < 2) {
            return new Classification(InstrumentType.UNKNOWN, BrokerVenue.COINBASE_SPOT,
                    parts[0], "", "");
        }
        
        String baseAsset = parts[0];
        String quoteAsset = parts[1];
        
        // Determine asset class from base asset
        InstrumentType instrumentType = switch (baseAsset.toUpperCase()) {
            case "BTC", "ETH", "SOL", "XRP", "ADA", "DOGE", "MATIC", "LINK", "ARB", "OP" ->
                    InstrumentType.CRYPTO_SPOT;
            case "GLD", "SLV", "GC", "SI" -> InstrumentType.METAL_SPOT;
            case "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "AMD" ->
                    InstrumentType.STOCK_SPOT;
            default -> {
                if (isCryptoAsset(baseAsset)) {
                    yield InstrumentType.CRYPTO_SPOT;
                } else if (isMetalAsset(baseAsset)) {
                    yield InstrumentType.METAL_SPOT;
                } else {
                    yield InstrumentType.UNKNOWN;
                }
            }
        };
        
        return new Classification(instrumentType, BrokerVenue.COINBASE_SPOT,
                baseAsset, quoteAsset, quoteAsset);
    }
    
    private Classification classifyFutures(String productId) {
        // US futures: single symbol like BTC, ES, NQ

        
        InstrumentType instrumentType = switch (productId.toUpperCase()) {
            case "BTC", "ETH", "SOL", "XRP" -> InstrumentType.CRYPTO_FUTURE;
            case "ES", "NQ", "MES", "MNQ" -> InstrumentType.INDEX_FUTURE;
            case "GC", "SI" -> InstrumentType.METAL_FUTURE;
            default -> InstrumentType.UNKNOWN;
        };
        
        return new Classification(instrumentType, BrokerVenue.COINBASE_US_FUTURES,
                productId, "USD", "USD");
    }
    
    private Classification classifyPerpetual(String productId) {
        // International perpetual: BTC-PERP-INTX, ETH-PERP-INTX
        String[] parts = productId.split("-");
        String baseAsset = parts[0];
        
        InstrumentType instrumentType = switch (baseAsset.toUpperCase()) {
            case "BTC", "ETH", "SOL", "XRP" -> InstrumentType.CRYPTO_PERPETUAL;
            case "ES", "NQ" -> InstrumentType.INDEX_PERPETUAL;
            case "GC", "SI" -> InstrumentType.METAL_PERPETUAL;
            default -> InstrumentType.UNKNOWN;
        };
        
        return new Classification(instrumentType, BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS,
                baseAsset, "USD", "USD");
    }
    
    private boolean isCryptoAsset(@NotNull String symbol) {
        return symbol.matches("^[A-Z]{2,10}$") && !isMetalAsset(symbol) && !symbol.matches("^[A-Z]$");
    }
    
    @Contract(pure = true)
    private boolean isMetalAsset(String symbol) {
        return symbol.matches("^(GC|SI|GLD|SLV|AU|AG)$");
    }
}
