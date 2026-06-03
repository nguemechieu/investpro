package org.investpro.ui.panels;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.investpro.exchange.blockchain.BlockchainTransactionResult;
import org.investpro.exchange.blockchain.execution.BlockchainTransactionRepository;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * UI panel for blockchain transaction monitoring across all chains.
 */
public class BlockchainTransactionsPanel extends VBox {

    private final BlockchainTransactionRepository repository;
    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private final ComboBox<String> statusFilter = new ComboBox<>();

    public BlockchainTransactionsPanel(BlockchainTransactionRepository repository) {
        this.repository = repository;
        setSpacing(10);
        setPadding(new Insets(12));

        Label title = new Label("Blockchain Transactions");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        statusFilter.getItems().setAll("All", "Pending", "Confirmed", "Failed", "Timeout");
        statusFilter.setValue("All");

        Button refresh = new Button("Refresh");
        refresh.setOnAction(ignored -> refresh());

        HBox controls = new HBox(8, new Label("Filter:"), statusFilter, refresh);

        TableView<Row> table = buildTable();
        table.setItems(rows);

        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(title, controls, table);

        statusFilter.valueProperty().addListener((ignored, oldValue, newValue) -> refresh());
        refresh();
    }

    private TableView<Row> buildTable() {
        TableView<Row> table = new TableView<>();

        TableColumn<Row, String> transactionId = new TableColumn<>("Transaction ID");
        transactionId.setCellValueFactory(new PropertyValueFactory<>("transactionId"));
        transactionId.setPrefWidth(220);

        TableColumn<Row, String> network = new TableColumn<>("Network");
        network.setCellValueFactory(new PropertyValueFactory<>("network"));

        TableColumn<Row, String> signature = new TableColumn<>("Signature");
        signature.setCellValueFactory(new PropertyValueFactory<>("signature"));
        signature.setPrefWidth(190);

        TableColumn<Row, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(new PropertyValueFactory<>("status"));

        TableColumn<Row, Number> confirmations = new TableColumn<>("Confirmations");
        confirmations.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().confirmations()));

        TableColumn<Row, String> fee = new TableColumn<>("Fee");
        fee.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fee()));

        TableColumn<Row, String> submitted = new TableColumn<>("Submitted");
        submitted.setCellValueFactory(new PropertyValueFactory<>("submitted"));
        submitted.setPrefWidth(160);

        TableColumn<Row, String> confirmed = new TableColumn<>("Confirmed");
        confirmed.setCellValueFactory(new PropertyValueFactory<>("confirmed"));
        confirmed.setPrefWidth(160);

        TableColumn<Row, String> error = new TableColumn<>("Error");
        error.setCellValueFactory(new PropertyValueFactory<>("error"));
        error.setPrefWidth(260);

        table.getColumns().add(transactionId);
        table.getColumns().add(network);
        table.getColumns().add(signature);
        table.getColumns().add(status);
        table.getColumns().add(confirmations);
        table.getColumns().add(fee);
        table.getColumns().add(submitted);
        table.getColumns().add(confirmed);
        table.getColumns().add(error);
        return table;
    }

    public final void refresh() {
        String filter = statusFilter.getValue();
        List<BlockchainTransactionResult> history = repository.history(500);

        rows.setAll(history.stream()
                .filter(result -> matchesFilter(result, filter))
                .map(Row::from)
                .toList());
    }

    private boolean matchesFilter(BlockchainTransactionResult result, String filter) {
        if (filter == null || "All".equalsIgnoreCase(filter)) {
            return true;
        }
        return switch (filter.toUpperCase()) {
            case "PENDING" -> result.outcome() == BlockchainTransactionResult.TransactionOutcome.PENDING;
            case "CONFIRMED" -> result.outcome() == BlockchainTransactionResult.TransactionOutcome.CONFIRMED;
            case "FAILED" -> result.outcome() == BlockchainTransactionResult.TransactionOutcome.FAILED
                    || result.outcome() == BlockchainTransactionResult.TransactionOutcome.SIMULATION_FAILED;
            case "TIMEOUT" -> result.outcome() == BlockchainTransactionResult.TransactionOutcome.TIMEOUT;
            default -> true;
        };
    }

    public record Row(
            String transactionId,
            String network,
            String signature,
            String status,
            int confirmations,
            String fee,
            String submitted,
            String confirmed,
            String error) {

        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

        static Row from(BlockchainTransactionResult result) {
            String feeText = result.feeUnitsConsumed() == null ? "" : String.valueOf(result.feeUnitsConsumed());
            String submittedText = result.submittedAt() == null ? "" : FORMATTER.format(result.submittedAt());
            String confirmedText = result.confirmedAt() == null ? "" : FORMATTER.format(result.confirmedAt());
            String errorText = result.errorMessage() == null ? "" : result.errorMessage();

            return new Row(
                    result.transactionId(),
                    result.networkId(),
                    result.signature() == null ? "" : result.signature(),
                    result.outcome().name(),
                    result.confirmationDepth(),
                    feeText,
                    submittedText,
                    confirmedText,
                    errorText);
        }
    }
}
