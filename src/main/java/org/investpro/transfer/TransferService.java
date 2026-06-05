package org.investpro.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TransferService {

    private final Map<String, TransferProvider> providers = new LinkedHashMap<>();

    public TransferService() {
        registerDefaults();
    }

    public Map<String, TransferProvider> providers() {
        return providers;
    }

    public List<String> providerNames() {
        return new ArrayList<>(providers.keySet());
    }

    public TransferProvider provider(String name) {
        return providers.get(name);
    }

    public void registerProvider(String name, TransferProvider provider) {
        if (name != null && !name.isBlank() && provider != null) {
            providers.put(name, provider);
        }
    }

    private void registerDefaults() {
        registerProvider("Interactive Brokers", defaultProvider("USD", "EUR", "GBP"));
        registerProvider("OANDA", defaultProvider("USD", "EUR", "JPY"));
        registerProvider("Binance", defaultProvider("USD", "USDT", "BTC", "ETH", "SOL"));
        registerProvider("Coinbase", defaultProvider("USD", "USDC", "BTC", "ETH"));
        registerProvider("Kraken", defaultProvider("USD", "EUR", "USDT", "BTC"));
        registerProvider("Alpaca", defaultProvider("USD"));
        registerProvider("Tradier", defaultProvider("USD"));
    }

    private TransferProvider defaultProvider(String... currencies) {
        return new TransferProvider() {
            private final Map<String, BigDecimal> balances = bootstrapBalances(currencies);
            private final List<String> supportedCurrencies = List.of(currencies);
            private final Map<String, TransferStatus> statuses = new LinkedHashMap<>();

            @Override
            public Map<String, BigDecimal> getBalances() {
                return balances;
            }

            @Override
            public List<String> getSupportedCurrencies() {
                return supportedCurrencies;
            }

            @Override
            public BigDecimal estimateFee(TransferRequest request) {
                boolean internal = request.fromProvider().equals(request.toProvider());
                boolean stablecoin = "USDT".equalsIgnoreCase(request.currency())
                        || "USDC".equalsIgnoreCase(request.currency());
                return new TransferFeeCalculator().calculate(request, internal, stablecoin);
            }

            @Override
            public TransferValidator.ValidationOutcome validateTransfer(TransferRequest request) {
                return new TransferValidator.ValidationOutcome(true, List.of(), List.of());
            }

            @Override
            public TransferResult executeTransfer(TransferRequest request) {
                String transactionId = "TR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                BigDecimal fee = estimateFee(request);
                BigDecimal netAmount = request.amount().subtract(fee);
                balances.computeIfPresent(request.currency(), (k, value) -> value.subtract(request.amount().add(fee)));
                TransferResult result = new TransferResult(transactionId, request, TransferStatus.PENDING,
                        "Queued for execution", fee, netAmount, estimateArrival(request));
                statuses.put(transactionId, TransferStatus.PENDING);
                return result;
            }

            @Override
            public TransferStatus getTransferStatus(String transactionId) {
                return statuses.getOrDefault(transactionId, TransferStatus.PENDING);
            }

            private String estimateArrival(TransferRequest request) {
                if (request.fromProvider().equals(request.toProvider())) {
                    return "Instant";
                }
                if ("CRYPTO".equalsIgnoreCase(request.network())) {
                    return "5-20 min";
                }
                return "T+0 to T+1";
            }

            private Map<String, BigDecimal> bootstrapBalances(String[] supported) {
                Map<String, BigDecimal> map = new LinkedHashMap<>();
                for (String currency : supported) {
                    map.put(currency, currency.equalsIgnoreCase("USD")
                            ? new BigDecimal("25430.15")
                            : new BigDecimal("120.00"));
                }
                return map;
            }
        };
    }

    public TransferRequest request(String fromProvider,
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
}
