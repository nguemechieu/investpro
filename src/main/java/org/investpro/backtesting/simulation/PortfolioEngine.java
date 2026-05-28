package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestConfig;
import org.investpro.backtesting.BacktestResult;

/**
 * Maintains cash, positions, and trade records without scanning trade history.
 */
public final class PortfolioEngine {
    private final BacktestConfig config;
    private final PositionManager positionManager = new PositionManager();
    private BacktestResult.TradeRecord activeTrade;
    private double cash;

    public PortfolioEngine(BacktestConfig config) {
        this.config = config;
        reset();
    }

    public void reset() {
        cash = config.getInitialBalance();
        activeTrade = null;
        positionManager.reset();
    }

    public BacktestResult.TradeRecord open(ExecutionFill fill) {
        Position position = positionManager.activePosition();
        position.open(fill.quantity(), fill.price(), Math.max(1, config.getLeverageRatio()));
        cash -= fill.quantity() * fill.price() + fill.commission();

        activeTrade = new BacktestResult.TradeRecord(fill.candleIndex(), fill.price(), fill.quantity());
        activeTrade.setEntrySignal(fill.reason() == null ? "BUY" : fill.reason());
        activeTrade.setFee(fill.commission());
        return activeTrade;
    }

    public BacktestResult.TradeRecord close(ExecutionFill fill) {
        if (activeTrade == null || !positionManager.hasOpenPosition()) {
            return null;
        }
        Position position = positionManager.activePosition();
        double exitedQuantity = position.scaleOut(fill.quantity());
        double proceeds = exitedQuantity * fill.price() - fill.commission();
        double costBasis = activeTrade.getEntryPrice() * exitedQuantity;
        double profit = proceeds - costBasis;

        activeTrade.setExitTime(fill.candleIndex());
        activeTrade.setExitPrice(fill.price());
        activeTrade.setExitSignal(fill.reason() == null ? "SELL" : fill.reason());
        activeTrade.setProfit(profit);
        activeTrade.setProfitPercent(costBasis > 0.0 ? profit / costBasis * 100.0 : 0.0);
        activeTrade.setFee(activeTrade.getFee() + fill.commission());
        cash += proceeds;

        BacktestResult.TradeRecord closed = activeTrade;
        if (!positionManager.hasOpenPosition()) {
            activeTrade = null;
        }
        return closed;
    }

    public boolean hasPosition() {
        return positionManager.hasOpenPosition();
    }

    public double totalEquity(double currentPrice) {
        return cash + openQuantity() * currentPrice;
    }

    public double cash() {
        return cash;
    }

    public double openQuantity() {
        return positionManager.activePosition().quantity();
    }

    public double totalExposure() {
        Position position = positionManager.activePosition();
        return position.quantity() * position.averageEntryPrice();
    }

    public BacktestResult.TradeRecord activeTrade() {
        return activeTrade;
    }
}
