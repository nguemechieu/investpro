package org.investpro.transfer;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class TransferController {

    private final TransferManager manager;
    private final Consumer<String> notifier;

    private final ObservableList<String> fromAccounts = FXCollections.observableArrayList();
    private final ObservableList<String> toAccounts = FXCollections.observableArrayList();
    private final ObservableList<String> currencies = FXCollections.observableArrayList();

    private final SimpleStringProperty availableBalance = new SimpleStringProperty("$0.00");
    private final SimpleStringProperty estimatedFees = new SimpleStringProperty("$0.00");
    private final SimpleStringProperty estimatedArrival = new SimpleStringProperty("Instant");
    private final SimpleStringProperty transferMessage = new SimpleStringProperty("");
    private final SimpleObjectProperty<TransferManager.Preview> lastPreview = new SimpleObjectProperty<>();

    public TransferController(TransferManager manager, Consumer<String> notifier) {
        this.manager = manager;
        this.notifier = notifier == null ? ignored -> {
        } : notifier;
    }

    public void initialize(ComboBox<String> fromAccountBox,
            ComboBox<String> toAccountBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            TextArea notesField,
            Spinner<Integer> prioritySpinner,
            Label availableBalanceLabel,
            Label feeLabel,
            Label arrivalLabel,
            Label messageLabel,
            ProgressIndicator progressIndicator,
            TableView<TransferResult> historyTable,
            javafx.scene.control.Button previewButton,
            javafx.scene.control.Button executeButton) {

        fromAccountBox.setItems(fromAccounts);
        toAccountBox.setItems(toAccounts);
        currencyBox.setItems(currencies);
        historyTable.setItems(manager.historyService().history());
        manager.statusMonitor().tickWithCallback(result -> Platform.runLater(() -> {
            historyTable.refresh();
            if (result != null) {
                notifier.accept("Transfer status update: " + result.getTransactionId() + " -> " + result.getStatus());
            }
        }));

        availableBalanceLabel.textProperty().bind(availableBalance);
        feeLabel.textProperty().bind(estimatedFees);
        arrivalLabel.textProperty().bind(estimatedArrival);
        messageLabel.textProperty().bind(transferMessage);

        fromAccounts.setAll(buildAccounts());
        toAccounts.setAll(buildAccounts());
        if (!fromAccounts.isEmpty()) {
            fromAccountBox.getSelectionModel().select(0);
        }
        if (toAccounts.size() > 1) {
            toAccountBox.getSelectionModel().select(1);
        }

        refreshCurrencies(fromAccountBox.getValue(), currencyBox);
        updateBalance(fromAccountBox.getValue(), currencyBox.getValue());

        fromAccountBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            refreshCurrencies(newVal, currencyBox);
            updateBalance(newVal, currencyBox.getValue());
            recalculateEstimate(fromAccountBox, toAccountBox, currencyBox, amountField, notesField, prioritySpinner);
        });
        toAccountBox.valueProperty().addListener((obs, oldVal, newVal) -> recalculateEstimate(fromAccountBox,
                toAccountBox, currencyBox, amountField, notesField, prioritySpinner));
        currencyBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateBalance(fromAccountBox.getValue(), newVal);
            recalculateEstimate(fromAccountBox, toAccountBox, currencyBox, amountField, notesField, prioritySpinner);
        });
        amountField.textProperty().addListener((obs, oldVal, newVal) -> recalculateEstimate(fromAccountBox,
                toAccountBox, currencyBox, amountField, notesField, prioritySpinner));
        notesField.textProperty().addListener((obs, oldVal, newVal) -> recalculateEstimate(fromAccountBox,
                toAccountBox, currencyBox, amountField, notesField, prioritySpinner));

        previewButton.disableProperty().bind(Bindings.createBooleanBinding(() -> canPreview(fromAccountBox,
                        toAccountBox, currencyBox, amountField),
                fromAccountBox.valueProperty(), toAccountBox.valueProperty(), currencyBox.valueProperty(),
                amountField.textProperty()));

        executeButton.disableProperty().bind(Bindings.createBooleanBinding(() -> {
            TransferManager.Preview preview = lastPreview.get();
            return preview == null || !preview.validation().valid();
        }, lastPreview));

        previewButton.setOnAction(event -> previewTransfer(fromAccountBox, toAccountBox, currencyBox, amountField,
                notesField, prioritySpinner));
        executeButton.setOnAction(event -> executeTransfer(fromAccountBox, toAccountBox, currencyBox, amountField,
                notesField, prioritySpinner, progressIndicator));
    }

    private void previewTransfer(ComboBox<String> fromAccountBox,
            ComboBox<String> toAccountBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            TextArea notesField,
            Spinner<Integer> prioritySpinner) {
        TransferRequest request = request(fromAccountBox.getValue(), toAccountBox.getValue(), currencyBox.getValue(),
                amountField.getText(), notesField.getText(), prioritySpinner.getValue());
        TransferManager.Preview preview = manager.preview(request);
        lastPreview.set(preview);

        estimatedFees.set(money(preview.fee()));
        estimatedArrival.set(preview.estimatedArrival());

        if (!preview.validation().valid()) {
            transferMessage.set(String.join(" | ", preview.validation().errors()));
            return;
        }

        transferMessage.set("Preview ready: net " + money(preview.netAmount()));
    }

    private void executeTransfer(ComboBox<String> fromAccountBox,
            ComboBox<String> toAccountBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            TextArea notesField,
            Spinner<Integer> prioritySpinner,
            ProgressIndicator progressIndicator) {
        TransferRequest request = request(fromAccountBox.getValue(), toAccountBox.getValue(), currencyBox.getValue(),
                amountField.getText(), notesField.getText(), prioritySpinner.getValue());

        TransferManager.Preview preview = manager.preview(request);
        if (!preview.validation().valid()) {
            transferMessage.set("Execution blocked: " + String.join("; ", preview.validation().errors()));
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Transfer Summary");
        confirm.setHeaderText("Confirm transfer");
        confirm.setContentText("From: " + request.fromProvider() + " / " + request.fromAccount() + "\n"
                + "To: " + request.toProvider() + " / " + request.toAccount() + "\n"
                + "Currency: " + request.currency() + "\n"
                + "Amount: " + money(request.amount()) + "\n"
                + "Fees: " + money(preview.fee()) + "\n"
                + "Net Amount: " + money(preview.netAmount()) + "\n"
                + "Estimated Arrival: " + preview.estimatedArrival());

        Optional<ButtonType> decision = confirm.showAndWait();
        if (decision.isEmpty() || decision.get() != ButtonType.OK) {
            transferMessage.set("Transfer cancelled before execution.");
            return;
        }

        progressIndicator.setVisible(true);
        progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        TransferResult result = manager.execute(request);

        transferMessage.set("Transfer " + result.getTransactionId() + " is " + result.getStatus().name());
        notifier.accept("Transfer event: " + result.getTransactionId() + " -> " + result.getStatus());

        Platform.runLater(() -> {
            progressIndicator.setVisible(false);
            progressIndicator.setProgress(result.getProgress());
        });
    }

    public String formatDate(TransferResult result) {
        if (result == null || result.getCreatedAt() == null) {
            return "";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(result.getCreatedAt());
    }

    public String money(BigDecimal value) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(value == null ? BigDecimal.ZERO : value);
    }

    private boolean canPreview(ComboBox<String> fromAccountBox,
            ComboBox<String> toAccountBox,
            ComboBox<String> currencyBox,
            TextField amountField) {
        return fromAccountBox.getValue() == null
                || toAccountBox.getValue() == null
                || currencyBox.getValue() == null
                || parseAmount(amountField.getText()).compareTo(BigDecimal.ZERO) <= 0;
    }

    private TransferRequest request(String fromAccountValue,
            String toAccountValue,
            String currency,
            String amountText,
            String notes,
            Integer priority) {
        AccountRef from = parseAccount(fromAccountValue);
        AccountRef to = parseAccount(toAccountValue);
        return manager.createRequest(
                from.provider(),
                from.account(),
                to.provider(),
                to.account(),
                currency == null ? "USD" : currency,
                parseAmount(amountText),
                notes,
                chooseNetwork(currency),
                priority == null ? 3 : priority);
    }

    private void refreshCurrencies(String fromAccountValue, ComboBox<String> currencyBox) {
        AccountRef from = parseAccount(fromAccountValue);
        String currentCurrency = currencyBox.getValue();
        currencies.setAll(manager.currencies(from.provider()));
        if (currentCurrency != null && currencies.contains(currentCurrency)) {
            currencyBox.getSelectionModel().select(currentCurrency);
        } else if (!currencies.isEmpty()) {
            currencyBox.getSelectionModel().selectFirst();
        } else {
            currencyBox.getSelectionModel().clearSelection();
        }
    }

    private void updateBalance(String fromAccountValue, String currency) {
        AccountRef from = parseAccount(fromAccountValue);
        Map<String, BigDecimal> balances = manager.balances(from.provider());
        BigDecimal balance = BigDecimal.ZERO;

        if (balances != null && !balances.isEmpty()) {
            if (currency != null && !currency.isBlank()) {
                BigDecimal selected = balances.get(currency);
                if (selected != null) {
                    balance = selected;
                }
            } else {
                BigDecimal usd = balances.get("USD");
                balance = Objects.requireNonNullElseGet(usd, () -> balances.values().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(BigDecimal.ZERO));
            }
        }

        availableBalance.set(money(balance));
    }

    private void recalculateEstimate(ComboBox<String> fromAccountBox,
            ComboBox<String> toAccountBox,
            ComboBox<String> currencyBox,
            TextField amountField,
            TextArea notesField,
            Spinner<Integer> prioritySpinner) {
        if (canPreview(fromAccountBox, toAccountBox, currencyBox, amountField)) {
            estimatedFees.set("$0.00");
            estimatedArrival.set("Instant");
            return;
        }
        TransferRequest request = request(fromAccountBox.getValue(), toAccountBox.getValue(), currencyBox.getValue(),
                amountField.getText(), notesField.getText(), prioritySpinner.getValue());
        TransferManager.Preview preview = manager.preview(request);
        lastPreview.set(preview);
        estimatedFees.set(money(preview.fee()));
        estimatedArrival.set(preview.estimatedArrival());
    }

    private ObservableList<String> buildAccounts() {
        ObservableList<String> values = FXCollections.observableArrayList();
        for (String provider : manager.providers()) {
            values.add(provider + " Spot");
            values.add(provider + " Margin");
            values.add(provider + " Cash");
            values.add(provider + " Wallet");
        }
        return values;
    }

    private BigDecimal parseAmount(String text) {
        try {
            return text == null || text.isBlank() ? BigDecimal.ZERO : new BigDecimal(text.trim());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String chooseNetwork(String currency) {
        if (currency == null) {
            return "FIAT";
        }
        if ("USDT".equalsIgnoreCase(currency)
                || "USDC".equalsIgnoreCase(currency)
                || "BTC".equalsIgnoreCase(currency)
                || "ETH".equalsIgnoreCase(currency)
                || "SOL".equalsIgnoreCase(currency)) {
            return "CRYPTO";
        }
        return "FIAT";
    }

    private AccountRef parseAccount(String value) {
        if (value == null || value.isBlank()) {
            return new AccountRef("", "");
        }
        int index = value.indexOf(' ');
        if (index <= 0) {
            return new AccountRef(value.trim(), "Spot");
        }
        return new AccountRef(value.substring(0, index).trim(), value.substring(index + 1).trim());
    }

    private record AccountRef(String provider, String account) {
    }
}
