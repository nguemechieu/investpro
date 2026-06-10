package org.investpro.transfer;

import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.binance.Binance;
import org.investpro.exchange.bitfinex.Bitfinex;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransferService {

    private final Map<String, TransferProvider> providers = new LinkedHashMap<>();

    public TransferService() {
        this((Exchange) null);
    }

    public TransferService(Exchange activeExchange) {
        if (activeExchange == null) {
            return;
        }

        registerExchange(activeExchange);
    }

    public TransferService(Collection<? extends Exchange> exchanges) {
        if (exchanges == null || exchanges.isEmpty()) {
            return;
        }
        exchanges.stream()
                .filter(exchange -> exchange != null)
                .forEach(this::registerExchange);
    }

    private void registerExchange(Exchange activeExchange) {
        String providerName = providerName(activeExchange);
        if (activeExchange instanceof Coinbase coinbase) {
            registerProvider(providerName, new CoinbaseTransferProvider(coinbase));
        } else if (activeExchange instanceof Binance binance) {
            registerProvider(providerName, new BinanceTransferProvider(binance));
        } else if (activeExchange instanceof Bitfinex bitfinex) {
            registerProvider(providerName, new BitfinexTransferProvider(bitfinex));
        } else {
            registerProvider(providerName, unsupportedProvider(providerName));
        }
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

    private TransferProvider unsupportedProvider(String providerName) {
        return new TransferProvider() {
            @Override
            public Map<String, BigDecimal> getBalances() {
                return Map.of("USD", BigDecimal.ZERO, "USDC", BigDecimal.ZERO, "BTC", BigDecimal.ZERO,
                        "ETH", BigDecimal.ZERO, "SOL", BigDecimal.ZERO);
            }

            @Override
            public List<String> getSupportedCurrencies() {
                return List.of("USD", "USDC", "BTC", "ETH", "SOL");
            }

            @Override
            public BigDecimal estimateFee(TransferRequest request) {
                return BigDecimal.ZERO;
            }

            @Override
            public TransferValidator.ValidationOutcome validateTransfer(TransferRequest request) {
                return new TransferValidator.ValidationOutcome(false,
                        List.of(providerName + " does not expose a live transfer API in this adapter."),
                        List.of());
            }

            @Override
            public TransferResult executeTransfer(TransferRequest request) {
                return new TransferResult(
                        "TR-UNSUPPORTED",
                        request,
                        TransferStatus.FAILED,
                        providerName + " does not expose a live transfer API in this adapter.",
                        BigDecimal.ZERO,
                        request.amount(),
                        "Unavailable");
            }

            @Override
            public TransferStatus getTransferStatus(String transactionId) {
                return TransferStatus.FAILED;
            }
        };
    }

    private String providerName(Exchange exchange) {
        String display = exchange.getDisplayName();
        if (display != null && !display.isBlank()) {
            return display.trim();
        }
        String name = exchange.getName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        return exchange.getClass().getSimpleName();
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
