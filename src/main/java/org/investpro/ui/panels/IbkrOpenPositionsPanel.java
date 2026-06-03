package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.models.trading.Position;

public class IbkrOpenPositionsPanel extends VBox {

    private final TableView<Position> table = new TableView<>();

    public IbkrOpenPositionsPanel(IbkrExchange exchange) {
        super(8);
        setPadding(new Insets(14));

        TableColumn<Position, String> symbolColumn = new TableColumn<>("Symbol");
        symbolColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getSymbol()));

        TableColumn<Position, String> sideColumn = new TableColumn<>("Side");
        sideColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cell.getValue().getSide())));

        TableColumn<Position, Number> qtyColumn = new TableColumn<>("Quantity");
        qtyColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getQuantity()));

        TableColumn<Position, Number> pnlColumn = new TableColumn<>("Unrealized PnL");
        pnlColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getUnrealizedPnl()));

        table.getColumns().add(symbolColumn);
        table.getColumns().add(sideColumn);
        table.getColumns().add(qtyColumn);
        table.getColumns().add(pnlColumn);

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh(exchange));

        getChildren().addAll(table, refresh);
        refresh(exchange);
    }

    private void refresh(IbkrExchange exchange) {
        table.getItems().setAll(exchange.getPositionService().fetchAll());
    }
}
