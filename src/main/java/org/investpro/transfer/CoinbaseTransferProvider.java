package org.investpro.transfer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.exchange.Coinbase;
import org.investpro.models.Account;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class CoinbaseTransferProvider implements TransferProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

        if (hints.cdpRequested()) {
            List<String> errors = validateCdpTransferHints(request, hints);
            return new TransferValidator.ValidationOutcome(errors.isEmpty(), errors, List.of());
        }

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
            if (hints.cdpRequested()) {
                response = coinbase.requestPlatformTransfer(
                        buildCdpSource(request, hints),
                        buildCdpTarget(request, hints),
                        request.amount(),
                        firstNonBlank(hints.asset(), request.currency()),
                        hints.execute(),
                        hints.validateOnly(),
                        hints.amountType(),
                        hints.idempotencyKey(),
                        metadataFor(request, hints)).join();
            } else if ("deposit".equals(action)) {
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
        String sourceAccountId = "";
        String targetAccountId = "";
        String sourceAsset = "";
        String targetAsset = "";
        String asset = "";
        String email = "";
        String amountType = "";
        String idempotencyKey = "";
        String reference = "";
        boolean cdpRequested = false;
        boolean execute = true;
        boolean validateOnly = false;

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
                    case "cdp", "platform_transfer", "cdp_transfer" -> cdpRequested = Boolean.parseBoolean(value);
                    case "funding_action", "action", "type" -> action = value.toLowerCase(Locale.ROOT);
                    case "payment_method_id", "paymentmethodid", "payment_method", "bank_account", "debit_card" ->
                        paymentMethodId = value;
                    case "crypto_address", "address", "wallet_address" -> cryptoAddress = value;
                    case "network", "chain" -> network = value;
                    case "destination_tag", "memo", "tag" -> destinationTag = value;
                    case "source_account_id", "source_account", "account_id", "from_account_id" -> sourceAccountId = value;
                    case "target_account_id", "target_account", "to_account_id", "custodial_account_id" ->
                        targetAccountId = value;
                    case "source_asset" -> sourceAsset = value;
                    case "target_asset" -> targetAsset = value;
                    case "asset" -> asset = value;
                    case "email", "recipient_email", "target_email" -> email = value;
                    case "amount_type", "amounttype" -> amountType = value;
                    case "execute" -> execute = Boolean.parseBoolean(value);
                    case "validate_only", "validateonly" -> validateOnly = Boolean.parseBoolean(value);
                    case "idempotency_key", "idempotencykey" -> idempotencyKey = value;
                    case "reference", "invoice_id", "invoiceid" -> reference = value;
                    default -> {
                    }
                }
            }
        }

        if (!sourceAccountId.isBlank() || !targetAccountId.isBlank() || !email.isBlank()) {
            cdpRequested = true;
        }

        return new RoutingHints(
                action,
                paymentMethodId,
                cryptoAddress,
                network,
                destinationTag,
                sourceAccountId,
                targetAccountId,
                sourceAsset,
                targetAsset,
                asset,
                email,
                amountType,
                idempotencyKey,
                reference,
                cdpRequested,
                execute,
                validateOnly);
    }

    private List<String> validateCdpTransferHints(TransferRequest request, RoutingHints hints) {
        List<String> errors = new java.util.ArrayList<>();
        if (hints.validateOnly() && hints.execute()) {
            errors.add("CDP transfer validateOnly and execute cannot both be true.");
        }
        boolean paymentMethodSource = hints.sourceAccountId().isBlank() && !hints.paymentMethodId().isBlank();
        boolean paymentMethodTarget = !hints.sourceAccountId().isBlank()
                && hints.targetAccountId().isBlank()
                && hints.cryptoAddress().isBlank()
                && hints.email().isBlank()
                && !hints.paymentMethodId().isBlank();

        if (hints.sourceAccountId().isBlank() && hints.paymentMethodId().isBlank()) {
            errors.add("CDP transfer requires source_account_id or payment_method_id.");
        }
        if (paymentMethodSource && hints.targetAccountId().isBlank()) {
            errors.add("CDP payment-method deposits require target_account_id.");
        }
        if (hints.targetAccountId().isBlank() && hints.cryptoAddress().isBlank() && hints.email().isBlank()
                && !paymentMethodTarget) {
            errors.add("CDP transfer requires target_account_id, crypto_address, email, or payment_method_id target.");
        }
        if (!hints.cryptoAddress().isBlank() && hints.network().isBlank()) {
            errors.add("CDP onchain transfers require network.");
        }
        if (firstNonBlank(hints.asset(), request.currency()).isBlank()) {
            errors.add("CDP transfer asset is required.");
        }
        return List.copyOf(errors);
    }

    private JsonNode buildCdpSource(TransferRequest request, RoutingHints hints) {
        var source = OBJECT_MAPPER.createObjectNode();
        if (!hints.sourceAccountId().isBlank()) {
            source.put("accountId", hints.sourceAccountId());
            source.put("asset", firstNonBlank(hints.sourceAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
            return source;
        }
        source.put("paymentMethodId", hints.paymentMethodId());
        source.put("asset", firstNonBlank(hints.sourceAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
        return source;
    }

    private JsonNode buildCdpTarget(TransferRequest request, RoutingHints hints) {
        var target = OBJECT_MAPPER.createObjectNode();
        if (!hints.targetAccountId().isBlank()) {
            target.put("accountId", hints.targetAccountId());
            target.put("asset", firstNonBlank(hints.targetAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
            return target;
        }
        if (!hints.cryptoAddress().isBlank()) {
            target.put("address", hints.cryptoAddress());
            target.put("network", hints.network().toLowerCase(Locale.ROOT));
            target.put("asset", firstNonBlank(hints.targetAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
            return target;
        }
        if (!hints.email().isBlank()) {
            target.put("email", hints.email());
            target.put("asset", firstNonBlank(hints.targetAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
            return target;
        }
        target.put("paymentMethodId", hints.paymentMethodId());
        target.put("asset", firstNonBlank(hints.targetAsset(), hints.asset(), request.currency()).toLowerCase(Locale.ROOT));
        return target;
    }

    private Map<String, String> metadataFor(TransferRequest request, RoutingHints hints) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (!hints.reference().isBlank()) {
            metadata.put("reference", hints.reference());
        }
        if (!request.toProvider().isBlank()) {
            metadata.put("toProvider", request.toProvider());
        }
        if (!request.toAccount().isBlank()) {
            metadata.put("toAccount", request.toAccount());
        }
        return metadata;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private record RoutingHints(
            String action,
            String paymentMethodId,
            String cryptoAddress,
            String network,
            String destinationTag,
            String sourceAccountId,
            String targetAccountId,
            String sourceAsset,
            String targetAsset,
            String asset,
            String email,
            String amountType,
            String idempotencyKey,
            String reference,
            boolean cdpRequested,
            boolean execute,
            boolean validateOnly) {
    }
}
