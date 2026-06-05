package org.investpro.transfer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransferValidator {

    public record ValidationOutcome(boolean valid, List<String> errors, List<String> warnings) {
    }

    public ValidationOutcome validate(TransferRequest request,
            Map<String, TransferProvider> providers,
            TransferFeeCalculator feeCalculator,
            boolean kycVerified) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (request.fromProvider().isBlank() || request.toProvider().isBlank()) {
            errors.add("Source and destination providers are required.");
        }
        if (request.fromAccount().isBlank() || request.toAccount().isBlank()) {
            errors.add("Source and destination accounts are required.");
        }
        if (request.currency().isBlank()) {
            errors.add("Currency is required.");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            errors.add("Amount must be greater than zero.");
        }

        TransferProvider fromProvider = providers.get(request.fromProvider());
        TransferProvider toProvider = providers.get(request.toProvider());

        if (fromProvider == null) {
            errors.add("Source provider is unavailable.");
        }
        if (toProvider == null) {
            errors.add("Destination provider is unavailable.");
        }

        if (fromProvider != null && toProvider != null) {
            if (!fromProvider.getSupportedCurrencies().contains(request.currency())) {
                errors.add("Source provider does not support selected currency.");
            }
            if (!toProvider.getSupportedCurrencies().contains(request.currency())) {
                errors.add("Destination provider does not support selected currency.");
            }

            BigDecimal availableBalance = fromProvider.getBalances().getOrDefault(request.currency(), BigDecimal.ZERO);
            BigDecimal fee = feeCalculator.calculate(request, request.fromProvider().equals(request.toProvider()),
                    isStablecoin(request.currency()));
            BigDecimal total = request.amount().add(fee);
            if (availableBalance.compareTo(total) < 0) {
                errors.add("Insufficient balance for amount plus fees.");
            }

            if (request.amount().compareTo(new BigDecimal("1000000")) > 0) {
                errors.add("Transfer exceeds maximum transfer limit of 1,000,000.");
            }
            if (request.amount().compareTo(new BigDecimal("100000")) > 0 && !kycVerified) {
                errors.add("KYC verification required for high-value transfers.");
            }

            if (isRestrictedBrokerRoute(request.fromProvider(), request.toProvider())) {
                errors.add("Broker restrictions prevent this transfer route.");
            }

            if (!isNetworkCompatible(request)) {
                errors.add("Transfer network is not compatible with selected route/currency.");
            }

            if (request.fromProvider().equals(request.toProvider())
                    && request.fromAccount().equals(request.toAccount())) {
                warnings.add("Source and destination are the same account.");
            }
        }

        return new ValidationOutcome(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    private boolean isRestrictedBrokerRoute(String fromProvider, String toProvider) {
        return "OANDA".equalsIgnoreCase(fromProvider) && "BINANCE".equalsIgnoreCase(toProvider);
    }

    private boolean isNetworkCompatible(TransferRequest request) {
        if (request.network().isBlank()) {
            return true;
        }
        if ("CRYPTO".equalsIgnoreCase(request.network())) {
            return isStablecoin(request.currency()) || isCrypto(request.currency());
        }
        return true;
    }

    private boolean isStablecoin(String currency) {
        return "USDT".equalsIgnoreCase(currency) || "USDC".equalsIgnoreCase(currency);
    }

    private boolean isCrypto(String currency) {
        return "BTC".equalsIgnoreCase(currency)
                || "ETH".equalsIgnoreCase(currency)
                || "SOL".equalsIgnoreCase(currency);
    }
}
