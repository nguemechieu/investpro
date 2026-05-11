package org.investpro.exchange;

/**
 * Enumeration of all supported exchanges in InvestPro.
 * Provides display names and factory keys for creating exchange adapters.
 *
 * @author NOEL NGUEMECHIEU
 */
public enum SupportedExchange {
    BINANCE_US("Binance US", "binance-us"),
    COINBASE("Coinbase", "coinbase"),
    COINBASE_PRO("Coinbase Pro", "coinbase-pro"),
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
    STELLAR_NETWORK("Stellar Network", "stellar-network");

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
        for (SupportedExchange exchange : values()) {
            if (exchange.factoryKey.equalsIgnoreCase(factoryKey)) {
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
}
