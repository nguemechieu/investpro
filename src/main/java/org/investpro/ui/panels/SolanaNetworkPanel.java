package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.investpro.exchange.*;
import org.investpro.exchange.SolanaBalanceService.SolanaAccountSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * JavaFX panel that displays Solana wallet information, token balances,
 * and transaction history.
 *
 * <p>All Solana network calls run on background threads via
 * {@link CompletableFuture}. UI updates are dispatched to the JavaFX
 * Application Thread via {@link Platform#runLater(Runnable)}.
 * The panel NEVER blocks the UI thread.
 *
 * <p>The panel is lazily initialised: Solana is only contacted when
 * the user provides a wallet address and clicks "Refresh".
 */
public class SolanaNetworkPanel extends VBox {

    private static final Logger log = LoggerFactory.getLogger(SolanaNetworkPanel.class);

    // ── Status indicator ─────────────────────────────────────────────────────
    private final Circle       statusDot     = new Circle(6);
    private final Label        statusLabel   = new Label("Disconnected");
    private final Label        networkLabel  = new Label("–");

    // ── Wallet ───────────────────────────────────────────────────────────────
    private final TextField    addressField  = new TextField();
    private final Label        solBalance    = new Label("–");

    // ── Token table ──────────────────────────────────────────────────────────
    private final TableView<SolanaTokenBalance> tokenTable = new TableView<>();

    // ── Transaction table ────────────────────────────────────────────────────
    private final TableView<SolanaTransaction> txTable = new TableView<>();

    // ── Controls ─────────────────────────────────────────────────────────────
    private final Button refreshBtn  = new Button("⟳ Refresh");
    private final Button connectBtn  = new Button("Connect");
    private final Label  errorLabel  = new Label();

    private SolanaNetworkAdapter adapter;

    // ── Constructor ───────────────────────────────────────────────────────────

    public SolanaNetworkPanel() {
        super(12);
        setPadding(new Insets(16));
        getStyleClass().add("solana-panel");

        buildUI();
        setDisconnectedState();
    }

    // ── UI construction ───────────────────────────────────────────────────────

    private void buildUI() {
        // ── Header ────────────────────────────────────────────────────────────
        statusDot.setFill(Color.GRAY);
        HBox statusRow = new HBox(8, statusDot, statusLabel,
                new Label(" | Network:"), networkLabel);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        // ── Wallet address input ──────────────────────────────────────────────
        addressField.setPromptText("Enter Solana wallet address (base-58)…");
        addressField.setPrefWidth(480);
        HBox walletRow = new HBox(8, new Label("Wallet:"), addressField,
                connectBtn, refreshBtn);
        walletRow.setAlignment(Pos.CENTER_LEFT);

        // ── SOL balance ───────────────────────────────────────────────────────
        solBalance.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        HBox balanceRow = new HBox(8, new Label("SOL Balance:"), solBalance);
        balanceRow.setAlignment(Pos.CENTER_LEFT);

        // ── Token table ───────────────────────────────────────────────────────
        TableColumn<SolanaTokenBalance, String> mintCol   = new TableColumn<>("Mint");
        mintCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().mint()));
        mintCol.setPrefWidth(320);

        TableColumn<SolanaTokenBalance, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().symbol()));
        symbolCol.setPrefWidth(80);

        TableColumn<SolanaTokenBalance, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().amount() == null ? "0"
                                : c.getValue().amount().toPlainString()));
        amountCol.setPrefWidth(160);

        tokenTable.getColumns().addAll(mintCol, symbolCol, amountCol);
        tokenTable.setPlaceholder(new Label("No SPL tokens found"));
        tokenTable.setPrefHeight(180);

        // ── Transaction table ─────────────────────────────────────────────────
        TableColumn<SolanaTransaction, String> sigCol    = new TableColumn<>("Signature");
        sigCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().shortSignature()));
        sigCol.setPrefWidth(200);

        TableColumn<SolanaTransaction, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(c.getValue().status()));
        statusCol.setPrefWidth(90);

        TableColumn<SolanaTransaction, String> slotCol   = new TableColumn<>("Slot");
        slotCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        String.valueOf(c.getValue().slot())));
        slotCol.setPrefWidth(120);

        TableColumn<SolanaTransaction, String> feeCol    = new TableColumn<>("Fee (SOL)");
        feeCol.setCellValueFactory(c ->
                new javafx.beans.property.SimpleStringProperty(
                        c.getValue().feeSol() == null ? "–"
                                : c.getValue().feeSol().toPlainString()));
        feeCol.setPrefWidth(100);

        txTable.getColumns().addAll(sigCol, statusCol, slotCol, feeCol);
        txTable.setPlaceholder(new Label("No transactions loaded"));
        txTable.setPrefHeight(200);

        // ── Error label ───────────────────────────────────────────────────────
        errorLabel.setTextFill(Color.RED);
        errorLabel.setWrapText(true);

        // ── Wire buttons ──────────────────────────────────────────────────────
        connectBtn.setOnAction(ignored -> onConnect());
        refreshBtn.setOnAction(ignored -> onRefresh());
        refreshBtn.setDisable(true);

        // ── Layout ────────────────────────────────────────────────────────────
        getChildren().addAll(
                new Label("Solana Network"),
                new Separator(),
                statusRow,
                walletRow,
                balanceRow,
                new Label("SPL Token Balances"),
                tokenTable,
                new Label("Recent Transactions"),
                txTable,
                errorLabel
        );
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void onConnect() {
        clearError();
        statusLabel.setText("Connecting…");
        statusDot.setFill(Color.ORANGE);

        CompletableFuture.supplyAsync(() -> {
            try {
                return SolanaNetworkAdapter.create();
            } catch (SolanaException.SolanaDisabledException ex) {
                throw new RuntimeException("Solana is disabled in config.properties\n" +
                        "Set solana.enabled=true to activate.", ex);
            }
        }).thenCompose(adp -> {
            this.adapter = adp;
            return adp.connect();
        }).whenComplete((slot, ex) -> Platform.runLater(() -> {
            if (ex == null) {
                setConnectedState(slot);
            } else {
                setDisconnectedState();
                showError(ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            }
        }));
    }

    private void onRefresh() {
        if (adapter == null) return;
        String address = addressField.getText().trim();
        if (address.isBlank()) {
            showError("Please enter a wallet address.");
            return;
        }
        if (!adapter.getWalletService().validateAddress(address)) {
            showError("Invalid Solana address format.");
            return;
        }
        clearError();
        refreshBtn.setDisable(true);

        adapter.refreshBalances(address)
                .thenCompose(snapshot -> adapter.getRecentTransactions(address, 20)
                        .thenApply(txs -> new RefreshResult(snapshot, txs)))
                .whenComplete((result, ex) -> Platform.runLater(() -> {
                    refreshBtn.setDisable(false);
                    if (ex != null) {
                        showError("Refresh failed: " + ex.getMessage());
                        return;
                    }
                    solBalance.setText(formatSol(result.snapshot().solBalance()));
                    tokenTable.getItems().setAll(result.snapshot().tokens());
                    txTable.getItems().setAll(result.transactions());
                }));
    }

    // ── State helpers ─────────────────────────────────────────────────────────

    private void setConnectedState(long slot) {
        statusDot.setFill(Color.LIMEGREEN);
        statusLabel.setText("Connected (slot " + slot + ")");
        networkLabel.setText(adapter.getConfig().network().toUpperCase());
        refreshBtn.setDisable(false);
    }

    private void setDisconnectedState() {
        statusDot.setFill(Color.GRAY);
        statusLabel.setText("Disconnected");
        networkLabel.setText("–");
        refreshBtn.setDisable(true);
    }

    private void showError(String message) {
        errorLabel.setText("⚠ " + message);
        log.warn("SolanaNetworkPanel: {}", message);
    }

    private void clearError() {
        errorLabel.setText("");
    }

    private static String formatSol(BigDecimal sol) {
        return sol == null ? "–" : sol.toPlainString() + " SOL";
    }

    /** Combines snapshot + transactions for a single-future composition. */
    private record RefreshResult(
            SolanaAccountSnapshot snapshot,
            List<SolanaTransaction> transactions
    ) {}
}
