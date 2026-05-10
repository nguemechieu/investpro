package org.investpro.enums;

/**
 * Asset class enumeration for grouping financial instruments by risk/exposure
 * characteristics.
 * Used for portfolio risk calculation, diversification, and asset allocation.
 */
public enum AssetClass {
    /** Cryptocurrencies (BTC, ETH, XLM, SOL, etc.) */
    CRYPTO_ASSET("Cryptocurrency Asset"),

    /** Fiat and stablecoins (USD, EUR, USDT, USDC) */
    FIAT_CURRENCY("Fiat Currency"),

    /** Individual stocks */
    EQUITY("Equity Stock"),

    /** Stock indices and index funds */
    EQUITY_INDEX("Equity Index"),

    /** Physical commodities and commodity futures */
    COMMODITY("Commodity"),

    /** Bonds and fixed income securities */
    FIXED_INCOME("Fixed Income"),

    /** Derivatives (futures, options, perpetuals) */
    DERIVATIVE("Derivative"),

    /** Cash and cash equivalents */
    CASH("Cash"),

    /** Synthetic or programmable assets */
    SYNTHETIC("Synthetic"),

    /** Unknown asset class */
    UNKNOWN("Unknown"), CRYPTO("Crypto Currency");

    private final String displayName;

    AssetClass(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Get human-readable display name.
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Check if this asset class carries market risk (not cash-like).
     */
    public boolean isRiskAsset() {
        return this != CASH &&
                this != FIAT_CURRENCY &&
                this != UNKNOWN;
    }

    /**
     * Check if this asset class is cash or cash-equivalent.
     */
    public boolean isCashLike() {
        return this == CASH || this == FIAT_CURRENCY;
    }

    /**
     * Parse AssetClass from string value.
     */
    public static AssetClass parse(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return AssetClass.valueOf(value.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
