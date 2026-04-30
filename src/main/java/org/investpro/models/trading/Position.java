package org.investpro.models.trading;

import lombok.Data;


import org.investpro.utils.Side;
import java.time.Instant;

/**
 * Represents a trading position (holding) in a specific trading pair.
 * Tracks quantity, entry price, current price, and P&L.
 */
@Data
public class Position {
    private TradePair tradePair;
    private Side side;  // LONG or SHORT
    private double quantity;
    private double entryPrice;
    private double currentPrice;
    private double unrealizedPnl;
    private double realizedPnl;
    private Instant openTime;
    private String positionId;
    private boolean isOpen;
    private double leverage;

    public Position() {}

    public Position(TradePair tradePair, Side side, double quantity, double entryPrice) {
        this.tradePair = tradePair;
        this.side = side;
        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.currentPrice = entryPrice;
        this.openTime = Instant.now();
        this.isOpen = true;
        this.leverage = 1.0;
    }

    /**
     * Calculate unrealized P&L based on current price
     */
    public void updateUnrealizedPnl() {

        double quantityDouble = quantity;
        double entryDouble = entryPrice;
        double currentDouble = currentPrice;

        double pnl;
        if (side == Side.BUY) {
            pnl = (currentDouble - entryDouble) * quantityDouble;
        } else {
            pnl = (entryDouble - currentDouble) * quantityDouble;
        }

        this.unrealizedPnl =pnl;
    }

    /**
     * Get the entry cost basis
     */
    public double getEntryValue() {

        return quantity * entryPrice;
    }

    /**
     * Get the current market value
     */
    public double getCurrentValue() {
        return quantity * currentPrice;
    }

    /**
     * Get the percentage return on this position
     */
    public double getReturnPercentage() {
        double entryValue = getEntryValue();
        if (entryValue == 0) {
            return 0;
        }

        if (side == Side.BUY) {
            return ((getCurrentValue() - entryValue) / entryValue) * 100;
        } else {
            return ((entryValue - getCurrentValue()) / entryValue) * 100;
        }
    }

    /**
     * Close this position
     */
    public void close() {
        this.isOpen = false;
    }

    @Override
    public String toString() {
        return "Position{tradePair=%s, side=%s, quantity=%s, entryPrice=%s, currentPrice=%s, unrealizedPnl=%s, returnPct=%s}".formatted(tradePair, side, quantity, entryPrice, currentPrice, unrealizedPnl, String.format("%.2f%%", getReturnPercentage()));
    }

    public void setIsOpen(boolean b) {
        this.isOpen = b;
    }

}
