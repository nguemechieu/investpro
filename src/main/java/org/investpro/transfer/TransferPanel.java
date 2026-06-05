package org.investpro.transfer;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class TransferPanel extends BorderPane {

    private final TransferController controller;

    public TransferPanel(Consumer<String> notifier) {
        TransferService transferService = new TransferService();
        TransferValidator transferValidator = new TransferValidator();
        TransferFeeCalculator feeCalculator = new TransferFeeCalculator();
        TransferHistoryService historyService = new TransferHistoryService();
        TransferStatusMonitor statusMonitor = new TransferStatusMonitor();

        TransferManager transferManager = new TransferManager(
                transferService,
                transferValidator,
                feeCalculator,
                historyService,
                statusMonitor);

        this.controller = new TransferController(transferManager, notifier);
        setCenter(buildUi());
        getStyleClass().addAll("transfer-funds-panel", "pro-panel");
    }

    private VBox buildUi() {
        Label title = new Label("Transfer Funds");
        title.getStyleClass().add("transfer-funds-title");

        ComboBox<String> fromAccount = new ComboBox<>();
        ComboBox<String> toAccount = new ComboBox<>();
        ComboBox<String> currency = new ComboBox<>();
        TextField amount = new TextField();
        amount.setPromptText("Enter amount");
        TextArea notes = new TextArea();
        notes.setPromptText("Optional notes / memo / internal ticket");
        notes.setPrefRowCount(2);
        Spinner<Integer> priority = new Spinner<>();
        priority.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 5, 3));

        Label availableBalance = metricLabel();
        Label estimatedFees = metricLabel();
        Label estimatedArrival = metricLabel();
        Label transferMessage = new Label();
        transferMessage.getStyleClass().add("transfer-funds-message");

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setVisible(false);
        progressIndicator.setPrefSize(22, 22);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.addRow(0, formLabel("From Account"), fromAccount);
        form.addRow(1, formLabel("To Account"), toAccount);
        form.addRow(2, formLabel("Currency"), currency);
        form.addRow(3, formLabel("Amount"), amount);
        form.addRow(4, formLabel("Available Balance"), availableBalance);
        form.addRow(5, formLabel("Estimated Fees"), estimatedFees);
        form.addRow(6, formLabel("Estimated Arrival"), estimatedArrival);
        form.addRow(7, formLabel("Priority"), priority);
        form.addRow(8, formLabel("Notes"), notes);

        fromAccount.setMaxWidth(Double.MAX_VALUE);
        toAccount.setMaxWidth(Double.MAX_VALUE);
        currency.setMaxWidth(Double.MAX_VALUE);
        amount.setMaxWidth(Double.MAX_VALUE);
        notes.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(fromAccount, Priority.ALWAYS);
        GridPane.setHgrow(toAccount, Priority.ALWAYS);
        GridPane.setHgrow(currency, Priority.ALWAYS);
        GridPane.setHgrow(amount, Priority.ALWAYS);
        GridPane.setHgrow(notes, Priority.ALWAYS);

        Button previewButton = new Button("Preview Transfer");
        Button executeButton = new Button("Execute Transfer");
        Button cancelButton = new Button("Cancel");

        previewButton.getStyleClass().add("transfer-funds-preview-button");
        executeButton.getStyleClass().add("transfer-funds-execute-button");
        cancelButton.getStyleClass().add("transfer-funds-cancel-button");

        cancelButton.setOnAction(event -> {
            amount.clear();
            notes.clear();
            transferMessage.setText("Transfer input reset.");
        });

        HBox actions = new HBox(10, previewButton, executeButton, cancelButton, progressIndicator);
        actions.setAlignment(Pos.CENTER_LEFT);

        TableView<TransferResult> historyTable = new TableView<>();
        historyTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        historyTable.getColumns().addAll(
                column("Date", result -> controller.formatDate(result)),
                column("Source",
                        result -> result.getRequest().fromProvider() + " " + result.getRequest().fromAccount()),
                column("Destination",
                        result -> result.getRequest().toProvider() + " " + result.getRequest().toAccount()),
                column("Currency", result -> result.getRequest().currency()),
                column("Amount", result -> controller.money(result.getRequest().amount())),
                column("Fee", result -> controller.money(result.getFee())),
                column("Status", result -> result.getStatus().name()),
                column("Transaction ID", TransferResult::getTransactionId));

        Label historyTitle = new Label("Transfer History");
        historyTitle.getStyleClass().add("transfer-funds-section-title");

        VBox historyBox = new VBox(8, historyTitle, historyTable);
        VBox.setVgrow(historyTable, Priority.ALWAYS);

        HBox header = new HBox(title, new Region());
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(12, header, form, actions, transferMessage, historyBox);
        root.setPadding(new Insets(16));
        root.getStyleClass().add("transfer-funds-root");
        VBox.setVgrow(historyBox, Priority.ALWAYS);

        controller.initialize(fromAccount, toAccount, currency, amount, notes, priority, availableBalance,
                estimatedFees, estimatedArrival, transferMessage, progressIndicator, historyTable, previewButton,
                executeButton);

        return root;
    }

    private TableColumn<TransferResult, String> column(String title,
            java.util.function.Function<TransferResult, String> resolver) {
        TableColumn<TransferResult, String> col = new TableColumn<>(title);
        col.setCellValueFactory(row -> new ReadOnlyStringWrapper(resolver.apply(row.getValue())));
        return col;
    }

    private Label formLabel(String text) {
        Label label = new Label(text + ":");
        label.getStyleClass().add("transfer-funds-label");
        return label;
    }

    private Label metricLabel() {
        Label label = new Label("--");
        label.getStyleClass().add("transfer-funds-metric");
        return label;
    }
}
