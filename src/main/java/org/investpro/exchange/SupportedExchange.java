package org.investpro.exchange;

import java.util.Locale;

/**
 * Enumeration of all supported exchanges in InvestPro.
 * Provides display names and factory keys for creating exchange adapters.
 *
 * @author NOEL NGUEMECHIEU
 */
public enum SupportedExchange {
    BINANCE_US("Binance US", "binance-us"),
    COINBASE("Coinbase", "coinbase"),
    OANDA("OANDA", "oanda"),
    IG("IG", "ig"),
    KRAKEN("Kraken", "kraken"),
    BITTREX("Bittrex", "bittrex"),
    BITFINEX("Bitfinex", "bitfinex"),
    BITFINEX_US("Bitfinex US", "bitfinex-us"),
    BITMEX("BitMEX", "bitmex"),
    KUCOIN("KuCoin", "kucoin"),
    KUCOIN_US("KuCoin US", "kucoin-us"),
    BITSTAMP("Bitstamp", "bitstamp"),
    POLONIEX("Poloniex", "poloniex"),
    BINANCE("Binance", "binance"),
    ALPACA("Alpaca", "alpaca"),
    INTERACTIVE_BROKERS("Interactive Brokers", "interactive-brokers"),
    STELLAR_NETWORK("Stellar Network", "stellar-network"),
    SOLANA_NETWORK("Solana Network", "solana-network");

    private final String displayName;
    private final String factoryKey;

    SupportedExchange(String displayName, String factoryKey) {
        this.displayName = displayName;
        this.factoryKey = factoryKey;
    }

    /**
     * Get the user-friendly display name for this exchange.
     * 
     * @return display name suitable for UI labels
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the factory key used to instantiate this exchange.
     * 
     * @return key for ExchangeFactory.create()
     */
    public String getFactoryKey() {
        return factoryKey;
    }

    /**
     * Get the enum value from a factory key.
     * 
     * @param factoryKey the key used in ExchangeFactory
     * @return the matching SupportedExchange enum value
     * @throws IllegalArgumentException if no matching exchange found
     */
    public static SupportedExchange fromFactoryKey(String factoryKey) {
        String normalized = normalizeFactoryKey(factoryKey);
        for (SupportedExchange exchange : values()) {
            if (normalizeFactoryKey(exchange.factoryKey).equals(normalized)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange: " + factoryKey);
    }

    /**
     * Get the enum value from a display name.
     * 
     * @param displayName the UI-friendly display name
     * @return the matching SupportedExchange enum value
     * @throws IllegalArgumentException if no matching exchange found
     */
    public static SupportedExchange fromDisplayName(String displayName) {
        for (SupportedExchange exchange : values()) {
            if (exchange.displayName.equalsIgnoreCase(displayName)) {
                return exchange;
            }
        }
        throw new IllegalArgumentException("Unknown exchange: " + displayName);
    }

    private static String normalizeFactoryKey(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
