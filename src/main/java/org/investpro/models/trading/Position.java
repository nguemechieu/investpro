package org.investpro.models.trading;

import lombok.Data;
import  org.investpro.utils.Side;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a trading position in a specific trading pair.
 *
 * Tracks:
 * - side
 * - quantity
 * - entry/current price
 * - realized/unrealized P&L
 * - stop loss / take profit
 * - leverage
 * - open/closed state
 */
@Data
public class Position {

    private TradePair tradePair;
    private Side side; // BUY = long, SELL = short

    private double quantity;
    private double entryPrice;
    private double currentPrice;

    private double unrealizedPnl;
    private double realizedPnl;

    private Instant openTime;
    private Instant closeTime;

    private String positionId;
    private boolean open;

    private double leverage;

    private double stopLoss;
    private double takeProfit;

    public Position() {
        this.positionId = UUID.randomUUID().toString();
        this.openTime = Instant.now();
        this.open = true;
        this.leverage = 1.0;
    }

    public Position(TradePair tradePair, Side side, double quantity, double entryPrice) {
        this();

        this.tradePair = Objects.requireNonNull(tradePair, "tradePair cannot be null");
        this.side = Objects.requireNonNull(side, "side cannot be null");

        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }

        if (entryPrice <= 0) {
            throw new IllegalArgumentException("entryPrice must be greater than 0");
        }

        this.quantity = quantity;
        this.entryPrice = entryPrice;
        this.currentPrice = entryPrice;
    }

    /**
     * Updates the current market price and recalculates unrealized P&L.
     */
    public void updateCurrentPrice(double currentPrice) {
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("currentPrice must be greater than 0");
        }

        this.currentPrice = currentPrice;
        updateUnrealizedPnl();
    }

    /**
     * Calculates unrealized P&L based on current price.
     */
    public void updateUnrealizedPnl() {
        if (side == null) {
            this.unrealizedPnl = 0;
            return;
        }

        if (side == Side.BUY) {
            this.unrealizedPnl = (currentPrice - entryPrice) * quantity;
        } else {
            this.unrealizedPnl = (entryPrice - currentPrice) * quantity;
        }
    }

    /**
     * Entry notional value.
     */
    public double getEntryValue() {
        return quantity * entryPrice;
    }

    /**
     * Current notional value.
     */
    public double getCurrentValue() {
        return quantity * currentPrice;
    }

    /**
     * Margin used by the position.
     *
     * Example:
     * position value = $10,000
     * leverage = 10x
     * margin used = $1,000
     */
    public double getMarginUsed() {
        if (leverage <= 0) {
            return getEntryValue();
        }

        return getEntryValue() / leverage;
    }

    /**
     * Percentage return based on price movement.
     *
     * This is unleveraged price return.
     */
    public double getReturnPercentage() {
        double entryValue = getEntryValue();

        if (entryValue == 0) {
            return 0;
        }

        if (side == Side.BUY) {
            return ((getCurrentValue() - entryValue) / entryValue) * 100.0;
        }

        return ((entryValue - getCurrentValue()) / entryValue) * 100.0;
    }

    /**
     * Leveraged return percentage based on margin used.
     */
    public double getLeveragedReturnPercentage() {
        double marginUsed = getMarginUsed();

        if (marginUsed == 0) {
            return 0;
        }

        return (unrealizedPnl / marginUsed) * 100.0;
    }

    /**
     * Total P&L including realized and unrealized.
     */
    public double getTotalPnl() {
        return realizedPnl + unrealizedPnl;
    }

    /**
     * Returns true when this is a BUY/LONG position.
     */
    public boolean isBuy() {
        return side == Side.BUY;
    }

    /**
     * Returns true when this is a SELL/SHORT position.
     */
    public boolean isSell() {
        return side == Side.SELL;
    }

    /**
     * Returns true if a stop loss has been configured.
     */
    public boolean hasStopLoss() {
        return !(stopLoss > 0);
    }

    /**
     * Returns true if a take profit has been configured.
     */
    public boolean hasTakeProfit() {
        return !(takeProfit > 0);
    }

    /**
     * Distance from current price to stop loss as a percentage.
     */
    public double getDistanceToStopLossPercentage() {
        if (hasStopLoss() || currentPrice <= 0) {
            return 0;
        }

        return Math.abs(currentPrice - stopLoss) / currentPrice * 100.0;
    }

    /**
     * Distance from current price to take profit as a percentage.
     */
    public double getDistanceToTakeProfitPercentage() {
        if (hasTakeProfit() || currentPrice <= 0) {
            return 0;
        }

        return Math.abs(takeProfit - currentPrice) / currentPrice * 100.0;
    }

    /**
     * Checks whether stop loss is hit at current price.
     */
    public boolean isStopLossHit() {
        if (hasStopLoss()) {
            return false;
        }

        if (isBuy()) {
            return currentPrice <= stopLoss;
        }

        return currentPrice >= stopLoss;
    }

    /**
     * Checks whether take profit is hit at current price.
     */
    public boolean isTakeProfitHit() {
        if (hasTakeProfit()) {
            return false;
        }

        if (isBuy()) {
            return currentPrice >= takeProfit;
        }

        return currentPrice <= takeProfit;
    }

    /**
     * Position age in minutes.
     */
    public long getAgeMinutes() {
        if (openTime == null) {
            return 0;
        }

        Instant end = open ? Instant.now() : closeTime;

        if (end == null) {
            end = Instant.now();
        }

        return Duration.between(openTime, end).toMinutes();
    }

    /**
     * Close this position and lock current unrealized P&L into realized P&L.
     */
    public void close() {
        updateUnrealizedPnl();

        this.realizedPnl += this.unrealizedPnl;
        this.unrealizedPnl = 0;
        this.open = false;
        this.closeTime = Instant.now();
    }

    /**
     * Compatibility setter for older code that calls setIsOpen(...).
     */
    public void setIsOpen(boolean open) {
        this.open = open;

        if (!open && this.closeTime == null) {
            this.closeTime = Instant.now();
        }
    }

    /**
     * Compatibility getter for older code that calls getIsOpen().
     */
    public boolean getIsOpen() {
        return open;
    }

    @Override
    public String toString() {
        return "Position{positionId='%s', tradePair=%s, side=%s, quantity=%s, entryPrice=%s, currentPrice=%s, unrealizedPnl=%s, realizedPnl=%s, returnPct=%s, leveragedReturnPct=%s, leverage=%s, stopLoss=%s, takeProfit=%s, open=%s}".formatted(positionId, tradePair, side, quantity, entryPrice, currentPrice, unrealizedPnl, realizedPnl, String.format("%.2f%%", getReturnPercentage()), String.format("%.2f%%", getLeveragedReturnPercentage()), leverage, stopLoss, takeProfit, open);
    }

    public String getSymbol() {
        return  tradePair.toString('/');
    }

    // Explicit getters (Lombok @Data not being invoked)
    public TradePair getTradePair() {
        return tradePair;
    }

    public double getQuantity() {
        return quantity;
    }
}