package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.models.trading.OpenOrder;

public class IbkrOpenOrdersPanel extends VBox {

    private final TableView<OpenOrder> table = new TableView<>();

    public IbkrOpenOrdersPanel(IbkrExchange exchange) {
        super(8);
        setPadding(new Insets(14));

        TableColumn<OpenOrder, String> idColumn = new TableColumn<>("Order ID");
        idColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(cell.getValue().getOrderId()));

        TableColumn<OpenOrder, String> symbolColumn = new TableColumn<>("Symbol");
        symbolColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getTradePair() == null ? "" : cell.getValue().getTradePair().toString('/')));

        TableColumn<OpenOrder, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleStringProperty(String.valueOf(cell.getValue().getOrderType())));

        TableColumn<OpenOrder, Number> sizeColumn = new TableColumn<>("Size");
        sizeColumn
                .setCellValueFactory(cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getSize()));

        TableColumn<OpenOrder, Number> priceColumn = new TableColumn<>("Price");
        priceColumn.setCellValueFactory(
                cell -> new javafx.beans.property.SimpleDoubleProperty(cell.getValue().getPrice()));

        table.getColumns().add(idColumn);
        table.getColumns().add(symbolColumn);
        table.getColumns().add(typeColumn);
        table.getColumns().add(sizeColumn);
        table.getColumns().add(priceColumn);

        Button refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh(exchange));

        getChildren().addAll(table, refresh);
        refresh(exchange);
    }

    private void refresh(IbkrExchange exchange) {
        table.getItems().setAll(exchange.getOrderService().fetchAllOpenOrders());
    }
}
