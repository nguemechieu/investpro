package org.investpro;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Wallet extends AnchorPane {

    private final ListView<String> transactionHistoryView;
    private final List<String> transactionHistory;
    private final DecimalFormat df = new DecimalFormat("#,###.00");
    private final Label balanceLabel= new Label();
    private double balanceBTC; // Bitcoin balance
    private static final String walletAddress = "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa"; // Example Bitcoin wallet address



    public Wallet(@NotNull CompletableFuture<List<Account>> dataAccount) throws ExecutionException, InterruptedException {

        setPadding(new Insets(20));



        // Wallet Address Section
        Label walletInfoLabel = new Label("Wallet Information");

        Label walletAddressLabel = new Label("Wallet Address: %s".formatted(walletAddress));
        walletAddressLabel.setStyle("-fx-text-fill: white;");

           Canvas canvas = new Canvas();
            canvas.setWidth(600);
            canvas.setHeight(400);
            canvas.getGraphicsContext2D().setFill(Color.web("#212121"));
            canvas.getGraphicsContext2D().fillRect(0, 0, 600, 400);
            canvas.getGraphicsContext2D().strokeText("Welcome to InvestPro", 10, 30);
            canvas.getGraphicsContext2D().strokeText("InvestPro", 10, 60);


        getChildren().add(canvas);
        // Buttons for Deposit and Withdraw
        Button depositButton = new Button("Deposit BTC");
        Button withdrawButton = new Button("Withdraw BTC");

        depositButton.setOnAction(e -> handleTransaction("Deposit", balanceLabel));
        withdrawButton.setOnAction(e -> handleTransaction("Withdraw", balanceLabel));

        // Layout for buttons
        HBox buttonBox = new HBox(10, depositButton, withdrawButton);
        buttonBox.setPadding(new Insets(20, 0, 0, 0));

        // Transaction History Section
        transactionHistory = new ArrayList<>();
        transactionHistoryView = new ListView<>();
        transactionHistoryView.setPrefHeight(150);

        Label transactionHistoryLabel = new Label("Transaction History:");
        transactionHistoryLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: white;");

        // Grid Layout for Account Information
        GridPane gridPane = new GridPane();
        gridPane.setVgap(10);
        gridPane.setHgap(10);
        gridPane.add(walletInfoLabel, 0, 0);
        gridPane.add(walletAddressLabel, 0, 1);
        gridPane.add(balanceLabel, 0, 2);
        gridPane.add(buttonBox, 0, 3);
        gridPane.add(transactionHistoryLabel, 0, 4);
        gridPane.add(transactionHistoryView, 0, 5);

        getChildren().add(gridPane);

        // Load account data after future completes
        dataAccount.thenAccept(accounts -> Platform.runLater(() -> updateAccountData(accounts, balanceLabel)));
    }

    /**
     * Update the wallet with account data once it's available.
     *
     * @param accounts List of Account objects fetched from the API
     * @param balanceLabel Label to update with account balance
     */
    private void updateAccountData(List<Account> accounts, Label balanceLabel) {
        // Assuming the first account in the list is the primary one
        if (!accounts.isEmpty()) {
            Account primaryAccount = accounts.get(0);
            balanceBTC = primaryAccount.getBalance();
            balanceLabel.setText("Balance: $" + df.format(balanceBTC));
        } else {
            balanceLabel.setText("Balance: 0.00 BTC");
        }
    }

    /**
     * Handle Deposit or Withdraw Transaction
     *
     * @param transactionType "Deposit" or "Withdraw"
     * @param balanceLabel Label to update with new balance
     */
    private void handleTransaction(String transactionType, Label balanceLabel) {
        Stage transactionStage = new Stage();
        transactionStage.setTitle(transactionType + " BTC");

        Label transactionLabel = new Label(transactionType + " Amount (BTC):");
        TextField transactionField = new TextField();

        Button submitButton = new Button("Submit");
        submitButton.setOnAction(e -> {
            try {
                double amount = Double.parseDouble(transactionField.getText());
                if (transactionType.equals("Deposit")) {
                    depositBTC(amount, balanceLabel);
                } else {
                    withdrawBTC(amount, balanceLabel);
                }
                transactionStage.close();
            } catch (NumberFormatException ex) {
                showError("Invalid amount. Please enter a valid number.");
            }
        });

        GridPane transactionPane = new GridPane();
        transactionPane.setPadding(new Insets(20));
        transactionPane.setVgap(10);
        transactionPane.setHgap(10);
        transactionPane.add(transactionLabel, 0, 0);
        transactionPane.add(transactionField, 1, 0);
        transactionPane.add(submitButton, 1, 1);

        Scene transactionScene = new Scene(transactionPane, 300, 150);
        transactionStage.setScene(transactionScene);
        transactionStage.show();
    }

    /**
     * Deposit BTC into the wallet.
     *
     * @param amount Amount to deposit in BTC
     * @param balanceLabel Label to update with new balance
     */
    private void depositBTC(double amount, Label balanceLabel) {
        if (amount <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }
        balanceBTC += amount;
        updateBalanceLabel(balanceLabel);
        addTransactionHistory("Deposited " + df.format(amount) + " BTC");
    }

    /**
     * Withdraw BTC from the wallet.
     *
     * @param amount Amount to withdraw in BTC
     * @param balanceLabel Label to update with new balance
     */
    private void withdrawBTC(double amount, Label balanceLabel) {
        if (amount <= 0) {
            showError("Amount must be greater than zero.");
            return;
        }
        if (amount > balanceBTC) {
            showError("Insufficient balance.");
            return;
        }
        balanceBTC -= amount;
        updateBalanceLabel(balanceLabel);
        addTransactionHistory("Withdrew " + df.format(amount) + " BTC");
    }

    /**
     * Update the balance label with the latest balance.
     *
     * @param balanceLabel The label to update
     */
    private void updateBalanceLabel(Label balanceLabel) {
        balanceLabel.setText("Balance: " + df.format(balanceBTC) + " BTC");
    }

    /**
     * Add a transaction to the transaction history.
     *
     * @param transaction The transaction description
     */
    private void addTransactionHistory(String transaction) {
        transactionHistory.add(transaction);
        transactionHistoryView.getItems().setAll(transactionHistory);
    }

    /**
     * Show an error message in a popup alert.
     *
     * @param message The error message to display
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
