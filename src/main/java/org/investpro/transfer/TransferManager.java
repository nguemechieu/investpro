package org.investpro.transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransferManager {

    private final TransferService transferService;
    private final TransferValidator validator;
    private final TransferFeeCalculator feeCalculator;
    private final TransferHistoryService historyService;
    private final TransferStatusMonitor statusMonitor;
    private final Map<String, Boolean> kycByProvider = new LinkedHashMap<>();

    public TransferManager(TransferService transferService,
            TransferValidator validator,
            TransferFeeCalculator feeCalculator,
            TransferHistoryService historyService,
            TransferStatusMonitor statusMonitor) {
        this.transferService = transferService;
        this.validator = validator;
        this.feeCalculator = feeCalculator;
        this.historyService = historyService;
        this.statusMonitor = statusMonitor;

        for (String provider : transferService.providerNames()) {
            kycByProvider.put(provider, Boolean.TRUE);
        }
    }

    public Preview preview(TransferRequest request) {
        boolean kycVerified = kycByProvider.getOrDefault(request.fromProvider(), Boolean.FALSE);
        TransferValidator.ValidationOutcome validation = validator.validate(request, transferService.providers(),
                feeCalculator, kycVerified);
        TransferProvider sourceProvider = transferService.provider(request.fromProvider());
        if (sourceProvider != null) {
            validation = merge(validation, sourceProvider.validateTransfer(request));
        }
        BigDecimal fee = feeCalculator.calculate(request,
                request.fromProvider().equals(request.toProvider()),
                "USDT".equalsIgnoreCase(request.currency()) || "USDC".equalsIgnoreCase(request.currency()));
        BigDecimal net = request.amount().subtract(fee).setScale(2, RoundingMode.HALF_UP);

        String eta = request.fromProvider().equals(request.toProvider())
                ? "Instant"
                : "CRYPTO".equalsIgnoreCase(request.network()) ? "5-20 min" : "T+0 to T+1";

        return new Preview(validation, fee, net, eta);
    }

    private TransferValidator.ValidationOutcome merge(
            TransferValidator.ValidationOutcome first,
            TransferValidator.ValidationOutcome second) {
        if (second == null) {
            return first;
        }
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (first != null) {
            errors.addAll(first.errors());
            warnings.addAll(first.warnings());
        }
        errors.addAll(second.errors());
        warnings.addAll(second.warnings());
        return new TransferValidator.ValidationOutcome(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    public TransferResult execute(TransferRequest request) {
        Preview preview = preview(request);
        if (!preview.validation().valid()) {
            return new TransferResult(
                    "TR-REJECTED",
                    request,
                    TransferStatus.FAILED,
                    String.join("; ", preview.validation().errors()),
                    preview.fee(),
                    preview.netAmount(),
                    preview.estimatedArrival());
        }

        TransferProvider provider = transferService.provider(request.fromProvider());
        if (provider == null) {
            return new TransferResult("TR-UNKNOWN", request, TransferStatus.FAILED, "Provider unavailable",
                    preview.fee(), preview.netAmount(), preview.estimatedArrival());
        }

        TransferResult result = provider.executeTransfer(request);
        historyService.add(result);
        statusMonitor.track(result);
        return result;
    }

    public List<String> providers() {
        return transferService.providerNames();
    }

    public TransferRequest createRequest(String fromProvider,
            String fromAccount,
            String toProvider,
            String toAccount,
            String currency,
            BigDecimal amount,
            String notes,
            String network,
            int priority) {
        return new TransferRequest(fromProvider, fromAccount, toProvider, toAccount, currency, amount, notes,
                network, priority, Instant.now());
    }

    public Map<String, BigDecimal> balances(String provider) {
        TransferProvider p = transferService.provider(provider);
        return p == null ? Map.of() : p.getBalances();
    }

    public List<String> currencies(String provider) {
        TransferProvider p = transferService.provider(provider);
        return p == null ? List.of("USD") : p.getSupportedCurrencies();
    }

    public TransferHistoryService historyService() {
        return historyService;
    }

    public TransferStatusMonitor statusMonitor() {
        return statusMonitor;
    }

    public record Preview(
            TransferValidator.ValidationOutcome validation,
            BigDecimal fee,
            BigDecimal netAmount,
            String estimatedArrival) {
    }
}
