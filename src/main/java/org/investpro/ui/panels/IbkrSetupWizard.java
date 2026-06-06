package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrConnectionDiagnosticsService;
import org.investpro.exchange.ibkr.IbkrConnectionMode;
import org.investpro.exchange.ibkr.IbkrConnectionProfile;
import org.investpro.exchange.ibkr.IbkrConnectionService;
import org.investpro.exchange.ibkr.IbkrFeatureAvailabilityService;
import org.investpro.exchange.ibkr.IbkrLocalServiceDetector;
import org.investpro.exchange.ibkr.IbkrSessionState;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

public class IbkrSetupWizard extends VBox {

    private static final String PREF_NODE = "org.investpro.ibkr.connection";

    private final IbkrConnectionService connectionService;
    private final IbkrLocalServiceDetector detector;
    private final IbkrConnectionDiagnosticsService diagnosticsService;
    private final IbkrFeatureAvailabilityService featureAvailabilityService = new IbkrFeatureAvailabilityService();
    private final Preferences preferences = Preferences.userRoot().node(PREF_NODE);
    private final Runnable sessionStateChanged;

    private final ComboBox<String> modeSelector = new ComboBox<>();
    private final TextField connectionNameField = new TextField();
    private final TextField hostField = new TextField();
    private final Spinner<Integer> portSpinner = new Spinner<>(1, 65535, IbkrConnectionProfile.TWS_PAPER_PORT);
    private final Spinner<Integer> clientIdSpinner = new Spinner<>(1, 9999, 1);
    private final CheckBox paperCheck = new CheckBox("Paper trading");
    private final CheckBox autoDetectCheck = new CheckBox("Auto-detect local service");
    private final ListView<String> detectionList = new ListView<>();
    private final ListView<String> diagnosticsList = new ListView<>();
    private final ListView<String> featureList = new ListView<>();
    private final Label statusLabel = new Label("Not connected");

    private IbkrConnectionProfile profile;
    private IbkrSessionState sessionState;

    public IbkrSetupWizard(
            IbkrConnectionService connectionService,
            IbkrLocalServiceDetector detector,
            IbkrConnectionDiagnosticsService diagnosticsService) {
        this(connectionService, detector, diagnosticsService, null);
    }

    public IbkrSetupWizard(
            IbkrConnectionService connectionService,
            IbkrLocalServiceDetector detector,
            IbkrConnectionDiagnosticsService diagnosticsService,
            Runnable sessionStateChanged) {
        super(12);
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService must not be null");
        this.detector = detector == null ? new IbkrLocalServiceDetector() : detector;
        this.diagnosticsService = diagnosticsService == null
                ? new IbkrConnectionDiagnosticsService(this.detector)
                : diagnosticsService;
        this.sessionStateChanged = sessionStateChanged;

        setPadding(new Insets(16));
        setPrefWidth(680);

        profile = loadProfile();
        sessionState = IbkrSessionState.disconnected(profile, "No IBKR session has been started yet.");
        buildUi();
        applyProfile(profile);
        refreshDetection();
        refreshDiagnostics();
    }

    private void buildUi() {
        Label title = new Label("IBKR Gateway Setup");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label requirement = new Label(
                "Interactive Brokers requires an active IBKR session through TWS, IB Gateway, or Client Portal Gateway. "
                        + "InvestPro does not store your IBKR password.");
        requirement.setWrapText(true);

        modeSelector.getItems().setAll("TWS / IB Gateway", "Client Portal Gateway", "Not sure / Auto-detect",
                "Future Cloud/OAuth");
        modeSelector.setMaxWidth(Double.MAX_VALUE);
        modeSelector.setOnAction(event -> {
            profile = currentProfile().withMode(modeFromSelection());
            applyProfile(profile);
            refreshDetection();
            refreshDiagnostics();
        });

        paperCheck.setSelected(true);
        autoDetectCheck.setSelected(true);
        paperCheck.setOnAction(event -> {
            profile = currentProfile().withPaper(paperCheck.isSelected());
            applyProfile(profile);
            refreshDetection();
        });
        autoDetectCheck.setOnAction(event -> refreshDetection());

        portSpinner.setEditable(true);
        clientIdSpinner.setEditable(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, new Label("Mode"), modeSelector);
        form.addRow(1, new Label("Connection name"), connectionNameField);
        form.addRow(2, new Label("Host"), hostField);
        form.addRow(3, new Label("Port"), portSpinner);
        form.addRow(4, new Label("Client ID"), clientIdSpinner);
        form.addRow(5, new Label("Environment"), paperCheck);
        form.addRow(6, new Label("Detection"), autoDetectCheck);
        GridPane.setHgrow(modeSelector, Priority.ALWAYS);
        GridPane.setHgrow(connectionNameField, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);

        Button detectButton = new Button("Auto-detect");
        Button connectButton = new Button("Connect");
        Button saveButton = new Button("Save profile");
        Button disconnectButton = new Button("Disconnect");
        detectButton.setOnAction(event -> refreshDetection());
        connectButton.setOnAction(event -> connect());
        saveButton.setOnAction(event -> saveProfile(currentProfile()));
        disconnectButton.setOnAction(event -> {
            connectionService.disconnect();
            sessionState = IbkrSessionState.disconnected(currentProfile(), "Disconnected.");
            refreshDiagnostics();
            notifySessionStateChanged();
        });
        HBox actions = new HBox(8, detectButton, connectButton, saveButton, disconnectButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        detectionList.setPrefHeight(110);
        diagnosticsList.setPrefHeight(190);
        featureList.setPrefHeight(110);

        getChildren().addAll(
                title,
                requirement,
                stepLabel("1. Choose connection mode"),
                form,
                stepLabel("2. Auto-detect local IBKR services"),
                detectionList,
                stepLabel("3. Diagnostics"),
                diagnosticsList,
                stepLabel("4. Feature availability"),
                featureList,
                statusLabel,
                actions);
    }

    private Label stepLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        return label;
    }

    private void connect() {
        IbkrConnectionProfile selected = currentProfile();
        if (selected.mode() == IbkrConnectionMode.CLOUD_OAUTH_FUTURE) {
            statusLabel.setText("Future Cloud/OAuth is a placeholder. InvestPro will not collect IBKR credentials.");
            return;
        }

        try {
            sessionState = connectionService.connect(selected);
            if (sessionState.connectionSuccessful()) {
                selected = selected.markSuccessful(Instant.now());
                saveProfile(selected);
                profile = selected;
            }
            statusLabel.setText(sessionState.message());
        } catch (Exception exception) {
            sessionState = IbkrSessionState.disconnected(selected, clearError(exception));
            statusLabel.setText(sessionState.message());
        }
        refreshDiagnostics();
        notifySessionStateChanged();
    }

    private void refreshDetection() {
        IbkrConnectionProfile selected = currentProfile();
        List<String> rows = detector.detect(selected).stream()
                .map(result -> "%s:%d - %s - %s".formatted(
                        result.host(),
                        result.port(),
                        result.label(),
                        result.reachable() ? "reachable" : "not reachable"))
                .toList();
        detectionList.getItems().setAll(rows);
    }

    private void refreshDiagnostics() {
        IbkrConnectionProfile selected = currentProfile();
        diagnosticsList.getItems().setAll(diagnosticsService.diagnose(selected, sessionState).stream()
                .map(item -> "%s %s - %s".formatted(item.passed() ? "[OK]" : "[ ]", item.name(), item.message()))
                .toList());

        IbkrFeatureAvailabilityService.FeatureAvailability availability =
                featureAvailabilityService.evaluate(sessionState);
        featureList.getItems().setAll(
                "Account balance available: " + yesNo(availability.accountBalanceAvailable()),
                "Positions available: " + yesNo(availability.positionsAvailable()),
                "Top-of-book available: " + yesNo(availability.topOfBookAvailable()),
                "Orderbook available: " + yesNo(availability.orderbookAvailable()),
                "Trading enabled: " + yesNo(availability.tradingEnabled()));
    }

    private IbkrConnectionProfile currentProfile() {
        return new IbkrConnectionProfile(
                modeFromSelection(),
                hostField.getText(),
                portSpinner.getValue(),
                clientIdSpinner.getValue(),
                paperCheck.isSelected(),
                autoDetectCheck.isSelected(),
                connectionNameField.getText(),
                profile == null ? null : profile.lastSuccessfulConnectionAt());
    }

    private void applyProfile(IbkrConnectionProfile selected) {
        if (selected == null) {
            selected = IbkrConnectionProfile.twsPaper();
        }
        connectionNameField.setText(selected.connectionName());
        hostField.setText(selected.host());
        portSpinner.getValueFactory().setValue(selected.port());
        clientIdSpinner.getValueFactory().setValue(selected.clientId());
        paperCheck.setSelected(selected.paper());
        autoDetectCheck.setSelected(selected.autoDetect());

        switch (selected.mode()) {
            case CLIENT_PORTAL_GATEWAY -> modeSelector.setValue("Client Portal Gateway");
            case CLOUD_OAUTH_FUTURE -> modeSelector.setValue("Future Cloud/OAuth");
            case TWS_API -> modeSelector.setValue(selected.autoDetect() ? "Not sure / Auto-detect" : "TWS / IB Gateway");
        }
    }

    private IbkrConnectionMode modeFromSelection() {
        String value = modeSelector.getValue();
        if ("Client Portal Gateway".equals(value)) {
            return IbkrConnectionMode.CLIENT_PORTAL_GATEWAY;
        }
        if ("Future Cloud/OAuth".equals(value)) {
            return IbkrConnectionMode.CLOUD_OAUTH_FUTURE;
        }
        return IbkrConnectionMode.TWS_API;
    }

    private IbkrConnectionProfile loadProfile() {
        IbkrConnectionMode mode = IbkrConnectionMode.valueOf(
                preferences.get("mode", IbkrConnectionMode.TWS_API.name()));
        Instant lastSuccess = null;
        String lastSuccessText = preferences.get("lastSuccessfulConnectionAt", "");
        if (!lastSuccessText.isBlank()) {
            try {
                lastSuccess = Instant.parse(lastSuccessText);
            } catch (Exception ignored) {
                lastSuccess = null;
            }
        }
        return new IbkrConnectionProfile(
                mode,
                preferences.get("host", IbkrConnectionProfile.DEFAULT_HOST),
                preferences.getInt("port", IbkrConnectionProfile.defaultPort(mode, true)),
                preferences.getInt("clientId", 1),
                preferences.getBoolean("paper", true),
                preferences.getBoolean("autoDetect", true),
                preferences.get("connectionName", ""),
                lastSuccess);
    }

    private void saveProfile(IbkrConnectionProfile selected) {
        preferences.put("mode", selected.mode().name());
        preferences.put("host", selected.host());
        preferences.putInt("port", selected.port());
        preferences.putInt("clientId", selected.clientId());
        preferences.putBoolean("paper", selected.paper());
        preferences.putBoolean("autoDetect", selected.autoDetect());
        preferences.put("connectionName", selected.connectionName());
        preferences.put("lastSuccessfulConnectionAt",
                selected.lastSuccessfulConnectionAt() == null ? "" : selected.lastSuccessfulConnectionAt().toString());
        statusLabel.setText("IBKR connection profile saved. No IBKR username or password was stored.");
    }

    private void notifySessionStateChanged() {
        if (sessionStateChanged != null) {
            sessionStateChanged.run();
        }
    }

    private String clearError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? "" : current.getMessage();
        if (message == null || message.isBlank()) {
            return "IBKR connection failed.";
        }
        return message;
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
