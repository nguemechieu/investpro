package org.investpro.ui.ibkr;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextInputDialog;
import org.investpro.exchange.ibkr.IbkrContractCandidate;
import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.exchange.ibkr.IbkrResolvedContract;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class IbkrContractSearchController {

    private final IbkrExchange ibkrExchange;
    private final Consumer<TradePair> addToMarketWatch;

    public IbkrContractSearchController(IbkrExchange ibkrExchange, Consumer<TradePair> addToMarketWatch) {
        this.ibkrExchange = ibkrExchange;
        this.addToMarketWatch = addToMarketWatch;
    }

    public void showSearchDialog() {
        TextInputDialog input = new TextInputDialog();
        input.setTitle("IBKR Contract Search");
        input.setHeaderText("Search IBKR contracts");
        input.setContentText("Symbol");
        input.showAndWait()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .ifPresent(this::searchAndResolve);
    }

    public void searchAndResolve(String query) {
        if (ibkrExchange == null) {
            showError("IBKR session is not connected.");
            return;
        }

        ibkrExchange.searchContracts(query)
                .thenCompose(candidates -> selectCandidate(candidates)
                        .map(ibkrExchange::resolveContract)
                        .orElseGet(() -> CompletableFuture.failedFuture(
                                new IllegalStateException("Contract is ambiguous."))))
                .thenAccept(contract -> Platform.runLater(() -> addResolvedContract(contract)))
                .exceptionally(error -> {
                    Platform.runLater(() -> showError(cleanMessage(error)));
                    return null;
                });
    }

    private Optional<IbkrContractCandidate> selectCandidate(List<IbkrContractCandidate> candidates) {
        CompletableFuture<Optional<IbkrContractCandidate>> selected = new CompletableFuture<>();
        Platform.runLater(() -> selected.complete(IbkrContractSelectionDialog.show(candidates)));
        return selected.join();
    }

    private void addResolvedContract(IbkrResolvedContract contract) {
        try {
            TradePair pair = TradePair.fromSymbol(contract.symbol() + "_" + contract.currency());
            pair.setNativeSymbol(contract.userFriendlySymbol());
            addToMarketWatch.accept(pair);
            showInfo("Resolved " + contract.userFriendlySymbol() + " as IBKR conId " + contract.conId() + ".");
        } catch (SQLException | ClassNotFoundException exception) {
            showError("Resolved contract was saved, but MarketWatch could not create a display symbol: "
                    + exception.getMessage());
        }
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("IBKR Contract Resolved");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("IBKR Contract Search");
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank() ? "API is not ready." : message);
        alert.showAndWait();
    }

    private String cleanMessage(Throwable error) {
        Throwable current = error;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null || current.getMessage() == null ? "API is not ready." : current.getMessage();
    }
}
