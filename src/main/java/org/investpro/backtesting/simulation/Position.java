package org.investpro.backtesting.simulation;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Mutable simulation position. Designed for future pooling and multi-position
 * support.
 */
@Getter
@Setter
@ToString
@Slf4j
public final class Position {
    private double quantity;
    private double averageEntryPrice;
    private double stopLoss;
    private double takeProfit;
    private double trailingStop;
    private double leverage = 1.0;

    public void open(double quantity, double price, double leverage) {
        this.quantity = quantity;
        this.averageEntryPrice = price;
        this.leverage = Math.max(1.0, leverage);
    }

    public void scaleIn(double additionalQuantity, double price) {
        if (additionalQuantity <= 0.0) {
            return;
        }
        double totalCost = averageEntryPrice * quantity + price * additionalQuantity;
        quantity += additionalQuantity;
        averageEntryPrice = totalCost / quantity;
    }

    public double scaleOut(double quantityToExit) {
        double exited = Math.min(quantity, Math.max(0.0, quantityToExit));
        quantity -= exited;
        if (quantity <= 0.000000001) {
            reset();
        }
        return exited;
    }

    public void reset() {
        quantity = 0.0;
        averageEntryPrice = 0.0;
        stopLoss = 0.0;
        takeProfit = 0.0;
        trailingStop = 0.0;
        leverage = 1.0;
    }

    public boolean open() {
        return quantity > 0.0;
    }

    public double quantity() {
        return quantity;
    }

    public double averageEntryPrice() {
        return averageEntryPrice;
    }

    public double leverage() {
        return leverage;
    }
}
