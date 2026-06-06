package org.investpro.ui.tradingdesk.panels;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.investpro.exchange.Exchange;
import org.investpro.ui.tradingdesk.TradingDeskContext;
import org.investpro.ui.tradingdesk.TradingDeskState;
import org.investpro.ui.tradingdesk.services.TradingDeskFundingService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

public final class DepositsWithdrawalsPanel extends VBox {

    private static final DateTimeFormatter REQUEST_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TradingDeskContext context;
    private final TradingDeskState state;
    private final TradingDeskFundingService fundingService;
    private final Consumer<String> journal;
    private final ObservableList<TransferFundingRow> fundingRows = FXCollections.observableArrayList();

    public DepositsWithdrawalsPanel(
            TradingDeskContext context,
            TradingDeskState state,
            Consumer<String> journal) {
        this.context = context;
        this.state = state;
        this.fundingService = context.fundingService();
        this.journal = journal == null ? ignored -> {
        } : journal;

        build();
    }

    private void build() {
        Label title = new Label("Deposits & Withdrawals");
        title.getStyleClass().add("terminal-window-title");

        Label badge = new Label("LIVE FUNDING");
        badge.getStyleClass().add("danger-badge");

        Label subtitle = new Label(
                "Submit live funding requests for connected exchanges that expose deposit/withdraw APIs. "
                        + "Cross-exchange movement is crypto-only and should be executed from Transfer Funds.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("muted-text");

        HBox titleBar = new HBox(10, title, badge);
        titleBar.setAlignment(Pos.CENTER_LEFT);

        Label safetyNotice = new Label(
                "Funding operations may move real money or crypto. Verify provider, currency, destination, "
                        + "network, and amount before submitting.");
        safetyNotice.setWrapText(true);
        safetyNotice.getStyleClass().add("warning-text");

        VBox safetyCard = new VBox(6, new Label("Safety Notice"), safetyNotice);
        safetyCard.getStyleClass().addAll("pro-panel", "warning-panel");
        safetyCard.setPadding(new Insets(12));

        TableView<TransferFundingRow> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setItems(fundingRows);
        table.setPlaceholder(new Label("No funding requests submitted in this session."));
        table.getColumns().setAll(
                tableColumn("Date", TransferFundingRow::date, 130),
                tableColumn("Provider", TransferFundingRow::provider, 150),
                tableColumn("Type", TransferFundingRow::type, 110),
                tableColumn("Currency", TransferFundingRow::currency, 90),
                tableColumn("Amount", TransferFundingRow::amount, 120),
                tableColumn("Status", TransferFundingRow::status, 110),
                tableColumn("Reference", TransferFundingRow::reference, 220));
        table.getStyleClass().add("terminal-table");

        ComboBox<String> actionType = new ComboBox<>(FXCollections.observableArrayList("Deposit", "Withdrawal"));
        actionType.getSelectionModel().selectFirst();
        actionType.setMaxWidth(Double.MAX_VALUE);

        ObservableList<String> fundingProviders = FXCollections.observableArrayList(
                fundingService.fundingCapableProviders());
        ComboBox<String> providerBox = new ComboBox<>(fundingProviders);
        providerBox.setPromptText("Select provider");
        providerBox.setMaxWidth(Double.MAX_VALUE);
        if (!fundingProviders.isEmpty()) {
            providerBox.getSelectionModel().selectFirst();
        }

        ComboBox<String> currencyBox = new ComboBox<>(FXCollections.observableArrayList(
                fundingService.fundingCurrencies()));
        currencyBox.setPromptText("Currency");
        currencyBox.setMaxWidth(Double.MAX_VALUE);
        if (currencyBox.getItems().contains("USD")) {
            currencyBox.getSelectionModel().select("USD");
        } else if (!currencyBox.getItems().isEmpty()) {
            currencyBox.getSelectionModel().selectFirst();
        }

        TextField amountField = new TextField();
        amountField.setPromptText("Amount, e.g. 100.00");
        amountField.setMaxWidth(Double.MAX_VALUE);

        TextField destinationField = new TextField();
        destinationField.setPromptText("bank_account:<id>, debit_card:<id>, payment_method_id, or wallet address");
        destinationField.setMaxWidth(Double.MAX_VALUE);

        TextField networkField = new TextField();
        networkField.setPromptText("Network optional, e.g. ERC20, SOL, Stellar");
        networkField.setMaxWidth(Double.MAX_VALUE);

        CheckBox confirmationBox = new CheckBox("I verified the provider, destination, network, currency, and amount.");
        confirmationBox.getStyleClass().add("terminal-check-box");

        Label statusLabel = new Label(fundingProviders.isEmpty()
                ? "No connected exchange currently exposes live deposit/withdraw operations."
                : "Ready. Complete the form and confirm details before submitting.");
        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("muted-text");
        state.setStatusMessage(statusLabel.getText());

        Label destinationLabel = new Label("Destination");
        Label networkLabel = new Label("Network");

        actionType.valueProperty().addListener((obs, oldValue, newValue) -> {
            String action = Optional.ofNullable(newValue).orElse("Deposit");
            if ("Deposit".equalsIgnoreCase(action)) {
                destinationLabel.setText("Funding Source");
                destinationField.setPromptText("bank_account:<id>, debit_card:<id>, or payment_method_id");
                networkLabel.setText("Network");
                networkField.setPromptText("Optional for fiat. Required for some crypto deposits.");
            } else {
                destinationLabel.setText("Destination");
                destinationField.setPromptText("payment_method_id for fiat or wallet address for crypto");
                networkLabel.setText("Network");
                networkField.setPromptText("Required for crypto withdrawals, e.g. ERC20, SOL, Stellar");
            }
        });

        Button submitButton = new Button("Submit Funding Request");
        submitButton.getStyleClass().addAll("primary-button", "danger-action-button");
        submitButton.setMaxWidth(Double.MAX_VALUE);

        BooleanBinding formInvalid = Bindings.createBooleanBinding(
                () -> fundingProviders.isEmpty()
                        || providerBox.getValue() == null
                        || providerBox.getValue().isBlank()
                        || currencyBox.getValue() == null
                        || currencyBox.getValue().isBlank()
                        || amountField.getText() == null
                        || amountField.getText().isBlank()
                        || !confirmationBox.isSelected(),
                providerBox.valueProperty(),
                currencyBox.valueProperty(),
                amountField.textProperty(),
                confirmationBox.selectedProperty());
        submitButton.disableProperty().bind(formInvalid);

        GridPane actionForm = createActionForm(
                actionType,
                providerBox,
                currencyBox,
                amountField,
                destinationLabel,
                destinationField,
                networkLabel,
                networkField,
                confirmationBox,
                statusLabel,
                submitButton);

        VBox formCard = new VBox(12, sectionTitle("New Funding Request"), actionForm);
        formCard.getStyleClass().add("pro-panel");
        formCard.setPadding(new Insets(14));

        VBox historyCard = new VBox(10, sectionTitle("Funding Request History"), table);
        historyCard.getStyleClass().add("pro-panel");
        historyCard.setPadding(new Insets(14));
        VBox.setVgrow(table, Priority.ALWAYS);

        submitButton.setOnAction(event -> submitFundingRequest(
                actionType,
                providerBox,
                currencyBox,
                amountField,
                destinationField,
                networkField,
                confirmationBox,
                statusLabel,
                submitButton,
                formInvalid));

        VBox header = new VBox(6, titleBar, subtitle);
        header.getStyleClass().add("terminal-header");

        getChildren().setAll(header, safetyCard, formCard, historyCard);
        setSpacing(12);
        setPadding(new Insets(16));
        getStyleClass().add("terminal-window-content");
        VBox.setVgrow(historyCard, Priority.ALWAYS);
    }

    private GridPane createActionForm(
            ComboBox<String> actionType,
            ComboBox<String> providerBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            Label destinationLabel,
            TextField destinationField,
            Label networkLabel,
            TextField networkField,
            CheckBox confirmationBox,
            Label statusLabel,
            Button submitButton) {
        GridPane actionForm = new GridPane();
        actionForm.setHgap(12);
        actionForm.setVgap(10);
        actionForm.getStyleClass().add("funding-form");

        int row = 0;
        actionForm.add(formLabel("Action"), 0, row);
        actionForm.add(actionType, 1, row);
        actionForm.add(formLabel("Provider"), 2, row);
        actionForm.add(providerBox, 3, row);

        row++;
        actionForm.add(formLabel("Currency"), 0, row);
        actionForm.add(currencyBox, 1, row);
        actionForm.add(formLabel("Amount"), 2, row);
        actionForm.add(amountField, 3, row);

        row++;
        actionForm.add(formLabel(destinationLabel.getText()), 0, row);
        actionForm.add(destinationField, 1, row, 3, 1);

        row++;
        actionForm.add(formLabel(networkLabel.getText()), 0, row);
        actionForm.add(networkField, 1, row, 3, 1);

        row++;
        actionForm.add(confirmationBox, 0, row, 4, 1);

        row++;
        actionForm.add(statusLabel, 0, row, 3, 1);
        actionForm.add(submitButton, 3, row);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setHgrow(i % 2 == 1 ? Priority.ALWAYS : Priority.NEVER);
            column.setMinWidth(i % 2 == 1 ? 180 : 90);
            actionForm.getColumnConstraints().add(column);
        }

        GridPane.setHgrow(actionType, Priority.ALWAYS);
        GridPane.setHgrow(providerBox, Priority.ALWAYS);
        GridPane.setHgrow(currencyBox, Priority.ALWAYS);
        GridPane.setHgrow(amountField, Priority.ALWAYS);
        GridPane.setHgrow(destinationField, Priority.ALWAYS);
        GridPane.setHgrow(networkField, Priority.ALWAYS);

        return actionForm;
    }

    private void submitFundingRequest(
            ComboBox<String> actionType,
            ComboBox<String> providerBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            TextField destinationField,
            TextField networkField,
            CheckBox confirmationBox,
            Label statusLabel,
            Button submitButton,
            BooleanBinding formInvalid) {
        String type = Optional.ofNullable(actionType.getValue()).orElse("Deposit");
        String provider = safe(providerBox.getValue());
        String currency = safe(currencyBox.getValue()).toUpperCase(Locale.ROOT);
        String destination = safe(destinationField.getText());
        String network = safe(networkField.getText());

        TradingDeskFundingService.FundingValidation validation = fundingService.validateFundingRequest(
                type,
                provider,
                currency,
                amountField.getText(),
                destination,
                network);

        if (!validation.valid()) {
            updateStatus(statusLabel, validation.message());
            return;
        }

        Exchange providerExchange = fundingService.resolveFundingExchange(provider).orElse(null);
        if (providerExchange == null) {
            updateStatus(statusLabel, "Selected provider is not connected.");
            return;
        }

        BigDecimal amount = validation.amount();
        String timestamp = LocalDateTime.now().format(REQUEST_TIME_FORMAT);
        String pendingReference = "PENDING-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        String amountDisplay = fundingService.money(amount.doubleValue());

        TransferFundingRow pending = new TransferFundingRow(
                timestamp,
                provider,
                type,
                currency,
                amountDisplay,
                "Pending",
                pendingReference);

        fundingRows.add(0, pending);

        submitButton.disableProperty().unbind();
        submitButton.setDisable(true);
        updateStatus(statusLabel, "Submitting " + type.toLowerCase(Locale.ROOT) + " request...");
        journal.accept("Funding request submitted: " + type + " " + amountDisplay + " " + currency + " via " + provider);

        CompletableFuture<String> future = fundingService.executeFundingRequest(
                providerExchange,
                type,
                currency,
                amount,
                destination,
                network);

        future.whenComplete((response, throwable) -> runOnFx(() -> {
            int pendingIndex = fundingRows.indexOf(pending);

            if (throwable != null) {
                String message = fundingService.rootMessage(throwable);

                TransferFundingRow failed = new TransferFundingRow(
                        timestamp,
                        provider,
                        type,
                        currency,
                        amountDisplay,
                        "Failed",
                        pendingReference);

                if (pendingIndex >= 0) {
                    fundingRows.set(pendingIndex, failed);
                }

                updateStatus(statusLabel, "Failed: " + message);
                journal.accept("Funding request failed: " + message);

                submitButton.disableProperty().bind(formInvalid);
                return;
            }

            String reference = fundingService.extractFundingReference(response, pendingReference);

            TransferFundingRow completed = new TransferFundingRow(
                    timestamp,
                    provider,
                    type,
                    currency,
                    amountDisplay,
                    "Submitted",
                    reference);

            if (pendingIndex >= 0) {
                fundingRows.set(pendingIndex, completed);
            }

            amountField.clear();
            destinationField.clear();
            networkField.clear();
            confirmationBox.setSelected(false);

            updateStatus(statusLabel, "Request submitted successfully. Ref: " + reference);
            journal.accept("Funding request submitted successfully: " + reference);

            submitButton.disableProperty().bind(formInvalid);
        }));
    }

    private void updateStatus(Label statusLabel, String message) {
        statusLabel.setText(message);
        state.setStatusMessage(message);
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private <T> TableColumn<T, String> tableColumn(String title, Function<T, String> mapper, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue() == null ? "" : safe(mapper.apply(cell.getValue()))));
        column.setPrefWidth(width);
        return column;
    }

    private void runOnFx(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
        } else {
            Platform.runLater(runnable);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private record TransferFundingRow(
            String date,
            String provider,
            String type,
            String currency,
            String amount,
            String status,
            String reference) {
    }
}
