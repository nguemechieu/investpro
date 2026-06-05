package org.investpro.broker.ibkr;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.models.Account;
import org.investpro.models.trading.Order;
import org.investpro.models.trading.Position;
import org.investpro.models.trading.Ticker;

/**
 * Bridges IBKR callback updates into JavaFX observable collections and
 * EventBus.
 */
public class IBKREventBridge {

    private static final String SOURCE = "IBKRBroker";

    private final EventBusManager eventBusManager = EventBusManager.getInstance();
    private final ObservableList<Position> positions = FXCollections.observableArrayList();
    private final ObservableList<Order> orders = FXCollections.observableArrayList();
    private final ObservableList<Ticker> tickers = FXCollections.observableArrayList();

    public IBKREventBridge() {
        eventBusManager.start();
    }

    public ObservableList<Position> positions() {
        return positions;
    }

    public ObservableList<Order> orders() {
        return orders;
    }

    public ObservableList<Ticker> tickers() {
        return tickers;
    }

    public void onAccount(Account account) {
        publish(AgentEvent.ACCOUNT_UPDATE, account);
    }

    public void onPositions(java.util.List<Position> latest) {
        if (latest == null) {
            return;
        }
        runOnFxThread(() -> {
            positions.setAll(latest);
            publish(AgentEvent.POSITION_UPDATE, latest);
        });
    }

    public void onOrders(java.util.List<Order> latest) {
        if (latest == null) {
            return;
        }
        runOnFxThread(() -> {
            orders.setAll(latest);
            publish(AgentEvent.ORDER_UPDATE, latest);
        });
    }

    public void onTicker(Ticker ticker) {
        if (ticker == null) {
            return;
        }
        runOnFxThread(() -> {
            int existingIndex = tickers.indexOf(ticker);
            if (existingIndex >= 0) {
                tickers.set(existingIndex, ticker);
            } else {
                tickers.add(ticker);
            }
            publish(AgentEvent.MARKET_TICK, ticker);
        });
    }

    private void publish(String type, Object payload) {
        eventBusManager.publish(AgentEvent.of(type, SOURCE, payload));
    }

    private void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        Platform.runLater(runnable);
    }
}
