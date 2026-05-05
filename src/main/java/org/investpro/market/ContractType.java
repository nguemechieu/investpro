package org.investpro.market;

/**
 * Contract type for financial instruments.
 */
public enum ContractType {
    SPOT("Spot Trading"),
    FUTURE("Future"),
    PERPETUAL("Perpetual Swap"),
    OPTION("Option"),
    MARGIN("Margin Trading");

    private final String displayName;

    ContractType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
