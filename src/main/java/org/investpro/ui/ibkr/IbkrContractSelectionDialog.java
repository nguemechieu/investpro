package org.investpro.ui.ibkr;

import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrContractCandidate;

import java.util.List;
import java.util.Optional;

public final class IbkrContractSelectionDialog {

    private IbkrContractSelectionDialog() {
    }

    public static Optional<IbkrContractCandidate> show(List<IbkrContractCandidate> candidates) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("IBKR contract selection dialog must be shown on the JavaFX Application Thread.");
        }
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }
        if (candidates.size() == 1) {
            return Optional.of(candidates.getFirst());
        }

        Dialog<IbkrContractCandidate> dialog = new Dialog<>();
        dialog.setTitle("IBKR Contract Selection");
        dialog.setHeaderText("Multiple contracts found; please choose one.");

        TableView<IbkrContractCandidate> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPrefSize(780, 360);

        TableColumn<IbkrContractCandidate, String> symbol = column("Symbol", c -> c.symbol());
        TableColumn<IbkrContractCandidate, String> type = column("Type", c -> c.secType());
        TableColumn<IbkrContractCandidate, String> exchange = column("Exchange", c -> c.exchange());
        TableColumn<IbkrContractCandidate, String> currency = column("Currency", c -> c.currency());
        TableColumn<IbkrContractCandidate, String> expiry = column("Expiry", c -> c.lastTradeDateOrContractMonth());
        TableColumn<IbkrContractCandidate, String> name = column("Name", c -> c.description());
        table.getColumns().setAll(symbol, type, exchange, currency, expiry, name);
        table.getItems().setAll(candidates);
        table.getSelectionModel().selectFirst();

        ButtonType resolveButton = new ButtonType("Resolve", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(resolveButton, ButtonType.CANCEL);
        dialog.getDialogPane().setContent(new VBox(8,
                new Label("IBKR instruments are identified by contract/conId, so this choice is saved locally."),
                table));
        dialog.setResultConverter(button -> button == resolveButton
                ? table.getSelectionModel().getSelectedItem()
                : null);
        return dialog.showAndWait();
    }

    private static TableColumn<IbkrContractCandidate, String> column(String title,
            java.util.function.Function<IbkrContractCandidate, String> value) {
        TableColumn<IbkrContractCandidate, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                value.apply(cell.getValue())));
        return column;
    }
}
