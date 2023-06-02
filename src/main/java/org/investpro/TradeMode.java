package org.investpro;

public enum TradeMode {
    /**
     * Trade is disabled.
     */
    DISABLED,
    /**
     * Trade is enabled.
     */
    ENABLED_ONLY_IF_ENABLED, ENABLED;

    public boolean isEnabled() {

        return this == TradeMode.ENABLED;
    }

}
