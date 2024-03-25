package org.investpro;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Border;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import static org.investpro.Currency.db1;
import static org.investpro.TradePair.logger;

public class TradeStatistics extends Application {
    String tradePair;//"ICP (ICP)=GBP (GBP)";
    double price;//= 10.07;
    double amount;// = 1.003;
    String transactionType;//= "buy";
    int localId; //= 1971446;

    public TradeStatistics() {
        db1.create("CREATE TABLE IF NOT EXISTS trade_statistics (trade_pair VARCHAR(255), price, amount, transaction_type VARCHAR(255), local_id INT)");
    }

    public String getTradePair() {
        return tradePair;
    }

    public void setTradePair(String tradePair) {
        this.tradePair = tradePair;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public int getLocalId() {
        return localId;
    }

    public void setLocalId(int localId) {
        this.localId = localId;
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        display();
    }

    public void save(String tradePair, double price, double amount, String transactionType, int localId) {
        db1.create(STR."INSERT INTO trade_statistics (trade_pair, price, amount, transaction_type, local_id) VALUES ('\{tradePair}','\{price}','\{amount}','\{transactionType}','\{localId}')");
    }

    public String toString() {
        return String.format("TradeStatistics [tradePair = %s, price = %s, amount = %s, transactionType = %s, localId = %s]", tradePair, price, amount, transactionType, localId);
    }

    public void display() {

        Stage stage = new Stage();
        stage.setTitle("Trade Statistics");
        AnchorPane root = new AnchorPane();
        root.setPrefSize(500, 500);
        GridPane gridpane = new GridPane();
        gridpane.setHgap(10);
        gridpane.setVgap(10);
        ListView<TradeStatistics> view = new ListView<>();
        view.setOnMouseClicked(event -> {

            ResultSet stats = db1.Select("SELECT * FROM trade_statistics WHERE trade_pair = 'null'");
            if (stats != null) {
                try {
                    while (stats.next()) {
                        String tradePair = stats.getString("trade_pair");
                        double price = stats.getDouble("price");
                        double amount = stats.getDouble("amount");
                        String transactionType = stats.getString("transaction_type");
                        int localId = stats.getInt("local_id");
                        setAmount(amount);
                        setPrice(price);
                        setTradePair(tradePair);
                        setTransactionType(transactionType);
                        setLocalId(localId);
                        view.getItems().addAll(this);
                    }
                } catch (SQLException e) {
                    logger.error(e.getMessage());
                }

            }
        });
        view.prefWidthProperty().bind(gridpane.widthProperty());
        view.prefHeightProperty().bind(gridpane.heightProperty());

        gridpane.add(view, 0, 1);

        gridpane.add(new Label("Trade Statistics"), 0, 0);
        gridpane.add(new Label("Trade Pair"), 1, 0);
        gridpane.add(new Label("Price"), 2, 0);
        gridpane.add(new Label("Amount"), 3, 0);
        gridpane.add(new Label("Transaction Type"), 4, 0);
        gridpane.add(new Label("Local Id"), 5, 0);


        gridpane.setVgap(10);
        gridpane.setHgap(10);
        gridpane.setPadding(new Insets(10));
        gridpane.setAlignment(javafx.geometry.Pos.CENTER);
        gridpane.setMinWidth(400);
        gridpane.setMinHeight(400);
        root.setBorder(Border.stroke(Color.BLUE));
        root.getChildren().add(gridpane);

        stage.setScene(new Scene(root, 500, 500));
        stage.setResizable(true);
        stage.getIcons().add(
                new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream("/invest.png"))
                )
        );
        stage.show();


    }

    private @NotNull TradeStatistics getStats() {
        TradeStatistics stats = new TradeStatistics();


        try {
            db1.create(
                    "CREATE TABLE IF NOT EXISTS trade_statistics (trade_pair VARCHAR(255), price  REAL   " +

                            ", amount REAL , transaction_type VARCHAR(255), local_id INT)");


            ResultSet dat = db1.Select(
                    "SELECT trade_pair, price, amount, transaction_type, local_id FROM trade_statistics");

            while (dat.next()) {
                stats.setTradePair(dat.getString("trade_pair"));
                stats.setPrice(dat.getDouble("price"));
                stats.setAmount(dat.getDouble("amount"));
                stats.setTransactionType(dat.getString("transaction_type"));
                stats.setLocalId(dat.getInt("local_id"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        stats.setTradePair(tradePair);
        stats.setPrice(price);
        stats.setAmount(amount);
        stats.setTransactionType(transactionType);
        stats.setLocalId(localId);
        return stats;
    }

    public void accept(Trade trade) {
        db1.insert("INSERT INTO trade_statistics (trade_pair, price, amount, transaction_)");
    }
}
