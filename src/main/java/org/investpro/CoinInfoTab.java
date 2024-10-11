package org.investpro;

import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;

public class CoinInfoTab extends Tab {


    public CoinInfoTab() {
        // Add TableView to display currency info
        TableView<CoinInfo> tableView = new TableView<>();

        // Define columns
        TableColumn<CoinInfo, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));

        TableColumn<CoinInfo, String> symbolColumn = new TableColumn<>("Symbol");
        symbolColumn.setCellValueFactory(new PropertyValueFactory<>("symbol"));

        TableColumn<CoinInfo, String> nameColumn = new TableColumn<>("Name");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));

        TableColumn<CoinInfo, Double> priceColumn = new TableColumn<>("Current Price");
        priceColumn.setCellValueFactory(new PropertyValueFactory<>("current_price"));

        TableColumn<CoinInfo, Long> marketCapColumn = new TableColumn<>("Market Cap");
        marketCapColumn.setCellValueFactory(new PropertyValueFactory<>("market_cap"));

        TableColumn<CoinInfo, Double> marketCapRankColumn = new TableColumn<>("Market Cap Rank");
        marketCapRankColumn.setCellValueFactory(new PropertyValueFactory<>("market_cap_rank"));

        TableColumn<CoinInfo, String> lastUpdatedColumn = new TableColumn<>("Last Updated");
        lastUpdatedColumn.setCellValueFactory(new PropertyValueFactory<>("last_updated"));

        // Add columns to the table
        tableView.getColumns().add(idColumn);
        tableView.getColumns().add(symbolColumn);
        tableView.getColumns().add(nameColumn);
        tableView.getColumns().add(priceColumn);
        tableView.getColumns().add(marketCapColumn);
        tableView.getColumns().add(marketCapRankColumn);
        tableView.getColumns().add(lastUpdatedColumn);

        // Set the table to grow with the window
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        // Add the TableView to the VBox
        VBox vBox = new VBox(tableView);

        // Add VBox to Tab
        setContent(vBox);


        // Show the Scene


        // Simulate adding data (in a real app, you can dynamically update this from an API)
        addSampleData(tableView);

    }

    // A method to add sample data
    private void addSampleData(TableView<CoinInfo> tableView) {
        // Normally, you would load this from a live API, but we'll add static data for now

        tableView.getItems().addAll(CurrencyDataProvider.getCoinInfoList());
    }


}