package org.investpro.transfer;

import org.investpro.exchange.Coinbase;
import org.investpro.models.Account;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class CoinbaseTransferProvider implements TransferProvider {

    private final Coinbase coinbase;
    private final Map<String, TransferStatus> statuses = new LinkedHashMap<>();

    CoinbaseTransferProvider(Coinbase coinbase) {
        this.coinbase = coinbase;
    }

    @Override
    public Map<String, BigDecimal> getBalances() {
        try {
            Account account = coinbase.getUserAccountDetails();
            Map<String, Double> source = account == null ? Map.of() : account.getAvailableBalances();
            Map<String, BigDecimal> balances = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : source.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    balances.put(entry.getKey().toUpperCase(Locale.ROOT), BigDecimal.valueOf(entry.getValue()));
                }
            }
            return balances;
        } catch (Exception exception) {
            return Map.of();
        }
    }

    @Override
    public List<String> getSupportedCurrencies() {
        Map<String, BigDecimal> balances = getBalances();
        if (!balances.isEmpty()) {
            return List.copyOf(balances.keySet());
        }
        return List.of("USD", "USDC", "BTC", "ETH", "SOL");
    }

    @Override
    public BigDecimal estimateFee(TransferRequest request) {
        if (request == null || request.amount() == null) {
            return BigDecimal.ZERO;
        }
        if (isCryptoCurrency(request.currency())) {
            return request.amount().multiply(new BigDecimal("0.0010"));
        }
        return request.amount().multiply(new BigDecimal("0.0005"));
    }

    @Override
    public TransferValidator.ValidationOutcome validateTransfer(TransferRequest request) {
        if (request == null) {
            return new TransferValidator.ValidationOutcome(false, List.of("Transfer request is missing."), List.of());
        }

        if (coinbase == null) {
            return new TransferValidator.ValidationOutcome(false, List.of("Coinbase exchange is unavailable."),
                    List.of());
        }

        if (coinbase.isPaperTrading()) {
            return new TransferValidator.ValidationOutcome(false,
                    List.of("Coinbase paper mode cannot execute live transfers."),
                    List.of());
        }

        if (request.amount() == null || request.amount().signum() <= 0) {
            return new TransferValidator.ValidationOutcome(false, List.of("Amount must be greater than zero."),
                    List.of());
        }

        RoutingHints hints = parseRoutingHints(request.notes());
        String action = hints.action();

        if ("deposit".equals(action)) {
            if (hints.paymentMethodId().isBlank()) {
                return new TransferValidator.ValidationOutcome(false,
                        List.of("For Coinbase deposits, add payment_method_id=<id> in Notes."),
                        List.of());
            }
            return new TransferValidator.ValidationOutcome(true, List.of(), List.of());
        }

        if (isCryptoCurrency(request.currency())) {
            if (hints.cryptoAddress().isBlank()) {
                return new TransferValidator.ValidationOutcome(false,
                        List.of("For crypto withdrawals, add crypto_address=<address> in Notes."),
                        List.of());
            }
            return new TransferValidator.ValidationOutcome(true, List.of(), List.of());
        }

        if (hints.paymentMethodId().isBlank()) {
            return new TransferValidator.ValidationOutcome(false,
                    List.of("For fiat withdrawals, add payment_method_id=<id> in Notes."),
                    List.of());
        }

        return new TransferValidator.ValidationOutcome(true, List.of(), List.of());
    }

    @Override
    public TransferResult executeTransfer(TransferRequest request) {
        TransferValidator.ValidationOutcome validation = validateTransfer(request);
        BigDecimal fee = estimateFee(request);
        BigDecimal netAmount = request.amount().subtract(fee);

        if (!validation.valid()) {
            return new TransferResult("CB-REJECTED", request, TransferStatus.FAILED,
                    String.join("; ", validation.errors()), fee, netAmount, "Rejected");
        }

        RoutingHints hints = parseRoutingHints(request.notes());
        String action = hints.action();

        try {
            String response;
            if ("deposit".equals(action)) {
                response = coinbase.requestDepositFromPaymentMethod(
                        request.amount(),
                        request.currency(),
                        hints.paymentMethodId()).join();
            } else if (isCryptoCurrency(request.currency())) {
                response = coinbase.requestWithdrawalToCryptoAddress(
                        request.amount(),
                        request.currency(),
                        hints.cryptoAddress(),
                        hints.network(),
                        hints.destinationTag()).join();
            } else {
                response = coinbase.requestWithdrawalToPaymentMethod(
                        request.amount(),
                        request.currency(),
                        hints.paymentMethodId()).join();
            }

            String transactionId = extractReference(response);
            statuses.put(transactionId, TransferStatus.PENDING);
            return new TransferResult(
                    transactionId,
                    request,
                    TransferStatus.PENDING,
                    "Submitted to Coinbase",
                    fee,
                    netAmount,
                    "Pending Coinbase settlement");
        } catch (Exception exception) {
            String message = exception.getMessage() == null ? "Coinbase transfer failed" : exception.getMessage();
            return new TransferResult(
                    "CB-FAILED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT),
                    request,
                    TransferStatus.FAILED,
                    message,
                    fee,
                    netAmount,
                    "Failed");
        }
    }

    @Override
    public TransferStatus getTransferStatus(String transactionId) {
        return statuses.getOrDefault(transactionId, TransferStatus.PENDING);
    }

    private boolean isCryptoCurrency(String currency) {
        if (currency == null) {
            return false;
        }
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "BTC", "ETH", "SOL", "USDC", "USDT", "XRP", "LTC", "ADA", "DOGE" -> true;
            default -> false;
        };
    }

    private String extractReference(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }

        for (String key : List.of("id", "transfer_id", "transaction_id", "withdrawal_id", "deposit_id")) {
            String needle = "\"" + key + "\"";
            int keyIndex = responseBody.indexOf(needle);
            if (keyIndex < 0) {
                continue;
            }
            int colonIndex = responseBody.indexOf(':', keyIndex + needle.length());
            if (colonIndex < 0) {
                continue;
            }
            int firstQuote = responseBody.indexOf('"', colonIndex + 1);
            if (firstQuote < 0) {
                continue;
            }
            int secondQuote = responseBody.indexOf('"', firstQuote + 1);
            if (secondQuote < 0) {
                continue;
            }
            String value = responseBody.substring(firstQuote + 1, secondQuote).trim();
            if (!value.isBlank()) {
                return value;
            }
        }

        return "CB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private RoutingHints parseRoutingHints(String notes) {
        String action = "withdraw";
        String paymentMethodId = "";
        String cryptoAddress = "";
        String network = "";
        String destinationTag = "";

        if (notes != null && !notes.isBlank()) {
            String[] segments = notes.split("[;\\n]");
            for (String segment : segments) {
                if (segment == null || segment.isBlank()) {
                    continue;
                }
                String[] pair = segment.split("=", 2);
                if (pair.length != 2) {
                    continue;
                }
                String key = pair[0].trim().toLowerCase(Locale.ROOT);
                String value = pair[1].trim();
                switch (key) {
                    case "funding_action", "action", "type" -> action = value.toLowerCase(Locale.ROOT);
                    case "payment_method_id", "paymentmethodid", "payment_method", "bank_account", "debit_card" ->
                        paymentMethodId = value;
                    case "crypto_address", "address", "wallet_address" -> cryptoAddress = value;
                    case "network", "chain" -> network = value;
                    case "destination_tag", "memo", "tag" -> destinationTag = value;
                    default -> {
                    }
                }
            }
        }

        return new RoutingHints(action, paymentMethodId, cryptoAddress, network, destinationTag);
    }

    private record RoutingHints(
            String action,
            String paymentMethodId,
            String cryptoAddress,
            String network,
            String destinationTag) {
    }
}
