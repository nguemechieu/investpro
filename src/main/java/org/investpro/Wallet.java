package org.investpro;

import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.SQLException;
import java.text.ParseException;

public class Wallet extends Stage {
    public Wallet(@NotNull Exchange exchange) {
        super();

        GridPane gridPane = new GridPane();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new javafx.geometry.Insets(10, 10, 10, 10));
        gridPane.setAlignment(javafx.geometry.Pos.CENTER);

        gridPane.add(new Label("Name: " + exchange.getName()), 0, 0);
        try {
            gridPane.add(new Label("Symbol: " + exchange.getTradePair()), 0, 1);
        } catch (IOException | InterruptedException | ParseException | URISyntaxException | SQLException |
                 ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        gridPane.add(new Label("Volume: " + exchange.getVolume()), 0, 3);
        gridPane.add(new Label("Balance: " + exchange.getBalance()), 0, 4);
        gridPane.add(new Label("Available : " + exchange.getAvailable()), 0, 5);
        gridPane.add(new Label("Pending : " + exchange.getPending()), 0, 6);
        gridPane.add(new Label("Total : " + exchange.getTotal()), 0, 7);
        gridPane.add(new Label("Fee : " + exchange.getFee()), 0, 8);
        gridPane.add(new Label("Total : " + exchange.getTotal()), 0, 9);
        gridPane.add(new Label("Deposit : " + exchange.getDeposit()), 0, 10);
        gridPane.add(new Label("Withdraw : " + exchange.getWithdraw()), 0, 11);


        Spinner<Double> spinner=new Spinner<>();
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(
                0.0,
                1000000.0
        ));
        gridPane.add(spinner, 5, 8);
        Button depositBtn = new Button("Deposit");
        depositBtn.setOnAction(e -> exchange.deposit(spinner.getValue()));
        gridPane.add(depositBtn, 0, 8);
        Button withdrawBtn = new Button("Withdraw");
        withdrawBtn.setOnAction(e -> exchange.withdraw(spinner.getValue()));

        gridPane.add(withdrawBtn, 10, 8);
        setScene(gridPane.getScene());
        setResizable(true);
        setTitle("Wallet");
        show();


    }
}
