package org.investpro;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;

/**
 * AccountAnchor is a class that displays detailed account information on a graphical canvas.
 * It pulls account data from the provided Exchange object and presents it in a well-formatted manner.
 */
public class AccountAnchor extends AnchorPane {

    public AccountAnchor(@NotNull Exchange exchange) throws IOException, NoSuchAlgorithmException, InvalidKeyException, InterruptedException {

        // Fetch the account details from the exchange
        Account account = exchange.getAccounts().getFirst();

        // Create a canvas to display the account details
        Canvas canvas = new Canvas(1500, 700);

        // Positioning the canvas within the anchor pane
        AnchorPane.setTopAnchor(canvas, 0.0);
        AnchorPane.setLeftAnchor(canvas, 0.0);
        AnchorPane.setRightAnchor(canvas, 0.0);
        AnchorPane.setBottomAnchor(canvas, 0.0);

        // Retrieve the graphics context to draw on the canvas
        GraphicsContext context = canvas.getGraphicsContext2D();

        // Draw the background and border
        context.setFill(Color.BLACK);
        context.fillRoundRect(20, 20, canvas.getWidth() - 40, canvas.getHeight() - 40, 10, 10);

        context.setStroke(Color.GREEN);
        context.strokeRoundRect(20, 20, canvas.getWidth() - 40, canvas.getHeight() - 40, 10, 10);

        // Set text color for account details
        context.setStroke(Color.GOLD);

        // Display account details with clear formatting
        context.strokeText("================================================================= Account Details =======================================================", 40, 40);

        context.strokeText("Account ID: " + account.getAccount_id(), 40, 70);
        context.strokeText("Account Balance: $" + String.format("%.2f", account.balance.getFree()), 40, 90);
        context.strokeText("Total Deposits: $" + String.format("%.2f", account.getTotalDeposits()), 40, 110);
        context.strokeText("Total Withdrawals: $" + String.format("%.2f", account.getTotalWithdrawals()), 40, 130);
        context.strokeText("Last Deposit Date: " + account.getLastDepositDate(), 40, 150);
        context.strokeText("Last Withdrawal Date: " + account.getLastWithdrawalDate(), 40, 170);
        context.strokeText("Last Update: " + Date.from(Instant.ofEpochMilli(account.getUpdateTime())), 40, 190);
        context.strokeText("Assets: " + account.balance.getAsset(), 40, 200);

        context.strokeText("Profit and Loss Details", 40, 220);
        context.strokeText("Total Loss: $" + String.format("%.2f", account.getTotalLoss()), 40, 240);
        context.strokeText("Total Gain: $" + String.format("%.2f", account.getTotalProfit()), 40, 260);
        context.strokeText("Profitability: " + String.format("%.2f%%", account.getProfitability()), 40, 280);

        context.strokeText("================================================ Trading Activity ==============================================================", 40, 310);
        context.strokeText("Total Trades: " + account.getTotalTrades(), 40, 330);
        context.strokeText("Net Balance: $" + String.format("%.2f", account.getNetBalance()), 40, 350);
        context.strokeText("Open Positions: " + account.getOpenPositions(), 40, 370);
        context.strokeText("Closed Positions: " + account.getClosedPositions(), 40, 390);
        context.strokeText("Open PnL: $" + String.format("%.2f", account.getOpenPnL()), 40, 410);
        context.strokeText("Closed PnL: $" + String.format("%.2f", account.getClosedPnL()), 40, 430);
        context.strokeText("Trading Status: " + account.getTradingStatus(), 40, 450);

        // Add the canvas to the AccountAnchor pane
        getChildren().add(canvas);

        // Set the maximum size for the AccountAnchor pane
        setMaxWidth(1540);
        setMaxHeight(750);
    }
}
