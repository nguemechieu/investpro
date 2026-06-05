package org.investpro.ui.broker;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.investpro.broker.ibkr.IBKRBroker;
import org.investpro.broker.ibkr.IBKRConnectionConfig;
import org.investpro.broker.ibkr.IBKRConnectionMode;

/**
 * Professional IBKR connection dialog for TWS/IB Gateway sessions.
 */
public class IBKRConnectionDialog extends Stage {

    private final Label statusValue = new Label("Disconnected");
    private final ComboBox<IBKRConnectionMode> modeBox = new ComboBox<>();
    private final TextField hostField = new TextField();
    private final Spinner<Integer> paperPortSpinner = new Spinner<>(1, 65535, 4002);
    private final Spinner<Integer> livePortSpinner = new Spinner<>(1, 65535, 4001);
    private final Spinner<Integer> clientIdSpinner = new Spinner<>(0, Integer.MAX_VALUE, 1);

    public IBKRConnectionDialog(Window owner, IBKRBroker broker) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Interactive Brokers Connection");

        IBKRConnectionConfig initialConfig = broker.connectionManager().getConfig();
        modeBox.getItems().setAll(IBKRConnectionMode.PAPER, IBKRConnectionMode.LIVE);
        modeBox.setValue(initialConfig.mode());

        hostField.setText(initialConfig.host());
        paperPortSpinner.getValueFactory().setValue(initialConfig.paperPort());
        livePortSpinner.getValueFactory().setValue(initialConfig.livePort());
        clientIdSpinner.getValueFactory().setValue(initialConfig.clientId());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.addRow(0, new Label("Mode"), modeBox);
        grid.addRow(1, new Label("Host"), hostField);
        grid.addRow(2, new Label("Paper Port"), paperPortSpinner);
        grid.addRow(3, new Label("Live Port"), livePortSpinner);
        grid.addRow(4, new Label("Client ID"), clientIdSpinner);
        grid.addRow(5, new Label("Status"), statusValue);

        GridPane.setHgrow(modeBox, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);

        Button connectButton = new Button("Connect");
        Button disconnectButton = new Button("Disconnect");
        Button closeButton = new Button("Close");

        connectButton.setOnAction(event -> {
            broker.connectionManager().updateConfig(readConfig());
            broker.connect();
            refreshStatus(broker);
        });

        disconnectButton.setOnAction(event -> {
            broker.disconnect();
            refreshStatus(broker);
        });

        closeButton.setOnAction(event -> close());

        HBox actions = new HBox(10, connectButton, disconnectButton, closeButton);
        VBox root = new VBox(14, grid, actions);
        root.setPadding(new Insets(18));

        Scene scene = new Scene(root, 520, 320);
        setScene(scene);
        refreshStatus(broker);
    }

    private IBKRConnectionConfig readConfig() {
        return new IBKRConnectionConfig(
                hostField.getText() == null || hostField.getText().isBlank() ? "127.0.0.1" : hostField.getText().trim(),
                paperPortSpinner.getValue(),
                livePortSpinner.getValue(),
                clientIdSpinner.getValue(),
                5000L,
                true,
                modeBox.getValue() == null ? IBKRConnectionMode.PAPER : modeBox.getValue());
    }

    private void refreshStatus(IBKRBroker broker) {
        boolean connected = broker.connectionManager().isConnected();
        statusValue.setText(connected ? "Connected" : "Disconnected");
        statusValue.setStyle(connected ? "-fx-text-fill: #137333;" : "-fx-text-fill: #b3261e;");
    }
}
