package org.investpro.models.trading;

import lombok.Data;
import org.investpro.utils.Side;

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
    private Instant timestamp;

    private String positionId;
    private boolean open;

    private double leverage;

    private double stopLoss;
    private double takeProfit;

    public Position(TradePair tradePair) {
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair cannot be null");
        this.positionId = UUID.randomUUID().toString();
        this.openTime = Instant.now();
        this.timestamp = this.openTime;
        this.open = true;
        this.leverage = 1.0;
    }

    public Position(TradePair tradePair, Side side, double quantity, double entryPrice) {
        this(tradePair);

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
        updateUnrealizedPnl();
    }

    public void updateCurrentPrice(double currentPrice) {
        if (currentPrice <= 0) {
            throw new IllegalArgumentException("currentPrice must be greater than 0");
        }

        this.currentPrice = currentPrice;
        this.timestamp = Instant.now();
        updateUnrealizedPnl();
    }

    public void updateUnrealizedPnl() {
        if (side == null || quantity <= 0 || entryPrice <= 0 || currentPrice <= 0) {
            this.unrealizedPnl = 0;
            return;
        }

        if (side == Side.BUY) {
            this.unrealizedPnl = (currentPrice - entryPrice) * quantity;
        } else {
            this.unrealizedPnl = (entryPrice - currentPrice) * quantity;
        }
    }

    public double getEntryValue() {
        return quantity * entryPrice;
    }

    public double getCurrentValue() {
        return quantity * currentPrice;
    }

    public double getMarginUsed() {
        if (leverage <= 0) {
            return getEntryValue();
        }

        return getEntryValue() / leverage;
    }

    public double getReturnPercentage() {
        double entryValue = getEntryValue();

        if (entryValue <= 0) {
            return 0;
        }

        if (side == Side.BUY) {
            return ((getCurrentValue() - entryValue) / entryValue) * 100.0;
        }

        if (side == Side.SELL) {
            return ((entryValue - getCurrentValue()) / entryValue) * 100.0;
        }

        return 0;
    }

    public double getLeveragedReturnPercentage() {
        double marginUsed = getMarginUsed();

        if (marginUsed <= 0) {
            return 0;
        }

        return (unrealizedPnl / marginUsed) * 100.0;
    }

    public double getTotalPnl() {
        return realizedPnl + unrealizedPnl;
    }

    public boolean isBuy() {
        return side == Side.BUY;
    }

    public boolean isSell() {
        return side == Side.SELL;
    }

    public boolean hasStopLoss() {
        return stopLoss > 0;
    }

    public boolean hasTakeProfit() {
        return takeProfit > 0;
    }

    public double getDistanceToStopLossPercentage() {
        if (!hasStopLoss() || currentPrice <= 0) {
            return 0;
        }

        return Math.abs(currentPrice - stopLoss) / currentPrice * 100.0;
    }

    public double getDistanceToTakeProfitPercentage() {
        if (!hasTakeProfit() || currentPrice <= 0) {
            return 0;
        }

        return Math.abs(takeProfit - currentPrice) / currentPrice * 100.0;
    }

    public boolean isStopLossHit() {
        if (!hasStopLoss() || currentPrice <= 0 || side == null) {
            return false;
        }

        if (isBuy()) {
            return currentPrice <= stopLoss;
        }

        return currentPrice >= stopLoss;
    }

    public boolean isTakeProfitHit() {
        if (!hasTakeProfit() || currentPrice <= 0 || side == null) {
            return false;
        }

        if (isBuy()) {
            return currentPrice >= takeProfit;
        }

        return currentPrice <= takeProfit;
    }

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

    public void close() {
        updateUnrealizedPnl();

        this.realizedPnl += this.unrealizedPnl;
        this.unrealizedPnl = 0;
        this.open = false;
        this.closeTime = Instant.now();
        this.timestamp = this.closeTime;
    }

    public void close(double exitPrice) {
        updateCurrentPrice(exitPrice);
        close();
    }

    public void setIsOpen(boolean open) {
        this.open = open;

        if (!open && this.closeTime == null) {
            this.closeTime = Instant.now();
            this.timestamp = this.closeTime;
        }
    }

    public boolean getIsOpen() {
        return open;
    }

    public String getSymbol() {
        return tradePair == null ? "" : tradePair.toString('/');
    }

    @Override
    public String toString() {
        return "Position{" +
                "positionId='" + positionId + '\'' +
                ", symbol=" + getSymbol() +
                ", side=" + side +
                ", quantity=" + quantity +
                ", entryPrice=" + entryPrice +
                ", currentPrice=" + currentPrice +
                ", unrealizedPnl=" + unrealizedPnl +
                ", realizedPnl=" + realizedPnl +
                ", returnPct=" + String.format("%.2f%%", getReturnPercentage()) +
                ", leveragedReturnPct=" + String.format("%.2f%%", getLeveragedReturnPercentage()) +
                ", leverage=" + leverage +
                ", stopLoss=" + stopLoss +
                ", takeProfit=" + takeProfit +
                ", open=" + open +
                '}';
    }
}