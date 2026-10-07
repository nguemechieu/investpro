package org.investpro.models.market;

/** Market structure; asset class describes the underlying, independently of this category. */
public enum MarketCategory {
    SPOT("Spot"), DERIVATIVES("Derivatives"), UNKNOWN("Unknown");
    private final String label;
    MarketCategory(String label) { this.label = label; }
    @Override public String toString() { return label; }
}
