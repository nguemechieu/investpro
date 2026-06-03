package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrConnectionManager;
import org.investpro.exchange.ibkr.IbkrExchange;

public class IbkrConnectionPanel extends VBox {

    private final Label endpointLabel = new Label("Endpoint: -");
    private final Label statusLabel = new Label("Status: Disconnected");
    private final Label heartbeatLabel = new Label("Heartbeat: -");

    public IbkrConnectionPanel(IbkrExchange exchange) {
        super(8);
        setPadding(new Insets(14));

        Button connectPaper = new Button("Connect Paper");
        Button connectLive = new Button("Connect Live");
        Button reconnect = new Button("Reconnect");
        Button disconnect = new Button("Disconnect");
        Button refresh = new Button("Refresh Health");

        connectPaper.setOnAction(event -> {
            exchange.setUserSelectedTradingMode("PAPER");
            exchange.connect();
            refresh(exchange);
        });

        connectLive.setOnAction(event -> {
            exchange.setUserSelectedTradingMode("LIVE");
            exchange.connect();
            refresh(exchange);
        });

        reconnect.setOnAction(event -> {
            exchange.reconnect();
            refresh(exchange);
        });

        disconnect.setOnAction(event -> {
            exchange.disconnect();
            refresh(exchange);
        });

        refresh.setOnAction(event -> refresh(exchange));

        getChildren().addAll(
                new Label("IBKR Connection Panel"),
                endpointLabel,
                statusLabel,
                heartbeatLabel,
                connectPaper,
                connectLive,
                reconnect,
                disconnect,
                refresh);

        refresh(exchange);
    }

    private void refresh(IbkrExchange exchange) {
        IbkrConnectionManager.ConnectionHealth health = exchange.connectionHealth();
        endpointLabel.setText("Endpoint: " + exchange.getConnectionManager().getHost() + ":"
                + exchange.getConnectionManager().getPort());
        statusLabel.setText("Status: " + (health.connected() ? "Connected" : "Disconnected")
                + ", MarketData=" + health.marketDataAvailable()
                + ", Reconnects=" + health.reconnectAttempts());
        heartbeatLabel.setText("Heartbeat latency=" + health.latencyMs() + "ms, stale=" + health.heartbeatStale());
    }
}
