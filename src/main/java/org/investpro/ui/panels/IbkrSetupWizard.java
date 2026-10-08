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
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.ibkr.IbkrConnectionDiagnosticsService;
import org.investpro.exchange.ibkr.IbkrConnectionMode;
import org.investpro.exchange.ibkr.IbkrConnectionProfile;
import org.investpro.exchange.ibkr.IbkrConnectionService;
import org.investpro.exchange.ibkr.IbkrFeatureAvailabilityService;
import org.investpro.exchange.ibkr.IbkrLocalServiceDetector;
import org.investpro.exchange.ibkr.IbkrSessionState;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
@Slf4j
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
    private final Spinner<Integer> portSpinner = new Spinner<>(1, 65535, IbkrConnectionProfile.TWS_LIVE_PORT);
    private final Spinner<Integer> clientIdSpinner = new Spinner<>(1, 9999, 1);
    private final CheckBox autoDetectCheck = new CheckBox("Auto-detect local service");
    private final ListView<String> detectionList = new ListView<>();
    private final ListView<String> diagnosticsList = new ListView<>();
    private final ListView<String> featureList = new ListView<>();
    private final Label statusLabel = new Label("Not connected");
    private final Label accountLabel = new Label();
    private final Label marketDataLabel = new Label();
    private final Button connectButton = new Button("Connect");
    private final Button disconnectButton = new Button("Disconnect");
    private javafx.scene.control.TitledPane setupPane;

    private IbkrConnectionProfile profile;
    private IbkrSessionState sessionState;
    private boolean applyingProfile;

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

        sessionState = connectionService.getSessionState();
        profile = sessionState.connectionSuccessful()
                ? new IbkrConnectionProfile(sessionState.mode(), sessionState.host(), sessionState.port(),
                    sessionState.clientId(), sessionState.paper(), false, null, sessionState.connectedAt())
                : loadProfile();
        buildUi();
        applyProfile(profile);
        refreshSessionSummary();
    }

    private void buildUi() {
        Label title = new Label("IBKR Account & Connection");
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        Label requirement = new Label(
                "Interactive Brokers requires an active IBKR session through TWS, IB Gateway, or Client Portal Gateway. "
                        + "InvestPro does not store your IBKR password.");
        requirement.setWrapText(true);

        modeSelector.getItems().setAll("TWS / IB Gateway", "Client Portal Gateway", "Not sure / Auto-detect",
                "Future Cloud/OAuth");
        modeSelector.setMaxWidth(Double.MAX_VALUE);
        modeSelector.setOnAction(event -> {
            if (applyingProfile) return;
            profile = currentProfile().withMode(modeFromSelection());
            applyProfile(profile);
            refreshDetection();
            refreshDiagnostics();
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
        form.addRow(5, new Label("Execution"), new Label("Live gateway (paper simulation is local)"));
        form.addRow(6, new Label("Detection"), autoDetectCheck);
        GridPane.setHgrow(modeSelector, Priority.ALWAYS);
        GridPane.setHgrow(connectionNameField, Priority.ALWAYS);
        GridPane.setHgrow(hostField, Priority.ALWAYS);

        HBox actions = getActions();

        detectionList.setPrefHeight(110);
        diagnosticsList.setPrefHeight(190);
        featureList.setPrefHeight(110);

        VBox setup = new VBox(8,
                requirement,
                stepLabel("1. Choose connection mode"),
                form,
                stepLabel("2. Auto-detect local IBKR services"),
                detectionList,
                stepLabel("3. Diagnostics"),
                diagnosticsList,
                stepLabel("4. Feature availability"),
                featureList,
                setupActions);
        setupPane = new javafx.scene.control.TitledPane("Connection settings & diagnostics", setup);
        setupPane.setExpanded(!sessionState.connectionSuccessful());
        marketDataLabel.setWrapText(true);
        getChildren().addAll(title, statusLabel, accountLabel, marketDataLabel, actions, setupPane);
    }

    private @NonNull HBox getActions() {
        Button detectButton = new Button("Auto-detect");
        Button saveButton = new Button("Save profile");
        Button refreshButton = new Button("Refresh status");
        refreshButton.setOnAction(_ -> refreshSessionSummary());
        connectButton.setOnAction(event -> connect());
        saveButton.setOnAction(event -> saveProfile(currentProfile()));
        disconnectButton.setOnAction(event -> {
            connectionService.disconnect();
            sessionState = IbkrSessionState.disconnected(currentProfile(), "Disconnected.");
            refreshSessionSummary();
            notifySessionStateChanged();
        });
        HBox actions = new HBox(8, refreshButton, connectButton, disconnectButton);
        // Setup actions stay with the optional connection settings.
        detectButton.setOnAction(_ -> { refreshDetection(); refreshDiagnostics(); });
        setupActions = new HBox(8, detectButton, saveButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        return actions;
    }

    private @NonNull Label stepLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold; -fx-padding: 8 0 0 0;");
        return label;
    }

    private void connect() {
        if (connectionService.getSessionState().connectionSuccessful()) {
            refreshSessionSummary();
            return;
        }
        IbkrConnectionProfile selected = currentProfile();
        if (selected.mode() == IbkrConnectionMode.CLOUD_OAUTH_FUTURE) {
            statusLabel.setText("Future Cloud/OAuth is a placeholder. InvestPro will not collect IBKR credentials.");
            return;
        }

        connectButton.setDisable(true);
        disconnectButton.setDisable(true);
        statusLabel.setText("Connecting to IBKR gateway...");
        java.util.concurrent.CompletableFuture.supplyAsync(() -> connectionService.connect(selected))
                .whenComplete((state, error) -> javafx.application.Platform.runLater(() -> {
            sessionState = error == null ? state : IbkrSessionState.disconnected(selected, clearError(error));
            if (sessionState.connectionSuccessful()) {
                profile = selected.markSuccessful(Instant.now());
                saveProfile(profile);
            }
            refreshSessionSummary();
            if (error != null) statusLabel.setText(clearError(error));
            notifySessionStateChanged();
        }));
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

    private HBox setupActions;

    private void refreshSessionSummary() {
        sessionState = connectionService.getSessionState();
        boolean connected = sessionState.connectionSuccessful();
        statusLabel.setText(connected
                ? "Connected to " + sessionState.host() + ":" + sessionState.port()
                    + " · Client ID " + sessionState.clientId()
                : sessionState.message());
        accountLabel.setText("Accounts: " + (sessionState.managedAccounts().isEmpty()
                ? "Not available" : String.join(", ", sessionState.managedAccounts())));
        marketDataLabel.setText("Market-data subscriptions are separate from your gateway connection. "
                + "Availability depends on the instrument and your IBKR entitlements. "
                + "Delayed data is used when available; automated strategies require live quotes.");
        connectButton.setDisable(connected);
        disconnectButton.setDisable(!sessionState.socketConnected());
        if (connected) setupPane.setExpanded(false);
    }

    private IbkrConnectionProfile currentProfile() {
        return new IbkrConnectionProfile(
                modeFromSelection(),
                hostField.getText(),
                portSpinner.getValue(),
                clientIdSpinner.getValue(),
                false,
                autoDetectCheck.isSelected(),
                connectionNameField.getText(),
                profile == null ? null : profile.lastSuccessfulConnectionAt());
    }

    private void applyProfile(IbkrConnectionProfile selected) {
        if (selected == null) {
            selected = new IbkrConnectionProfile(IbkrConnectionMode.TWS_API, null, IbkrConnectionProfile.TWS_LIVE_PORT, 1, false, true, null, null);
        }
        connectionNameField.setText(selected.connectionName());
        hostField.setText(selected.host());
        portSpinner.getValueFactory().setValue(selected.port());
        clientIdSpinner.getValueFactory().setValue(selected.clientId());

        autoDetectCheck.setSelected(selected.autoDetect());

        applyingProfile = true;
        try { switch (selected.mode()) {
            case CLIENT_PORTAL_GATEWAY -> modeSelector.setValue("Client Portal Gateway");
            case CLOUD_OAUTH_FUTURE -> modeSelector.setValue("Future Cloud/OAuth");
            case TWS_API -> modeSelector.setValue(selected.autoDetect() ? "Not sure / Auto-detect" : "TWS / IB Gateway");
        } } finally { applyingProfile = false; }
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
                log.info("ERROR Ignored...skipping lastSuccessfulConnectionAt");
            }
        }
        return new IbkrConnectionProfile(
                mode,
                preferences.get("host", IbkrConnectionProfile.DEFAULT_HOST),
                preferences.getInt("port", IbkrConnectionProfile.defaultPort(mode, false)),
                preferences.getInt("clientId", 1),
                false,
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

    @Contract(pure = true)
    private @NonNull String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}
