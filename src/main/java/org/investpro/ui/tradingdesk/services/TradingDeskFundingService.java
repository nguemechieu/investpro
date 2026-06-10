package org.investpro.ui.tradingdesk.services;

import org.investpro.exchange.binance.Binance;
import org.investpro.exchange.bitfinex.Bitfinex;
import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;
import org.investpro.transfer.FundingDestinationParser;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public final class TradingDeskFundingService {

    private final Supplier<Exchange> activeExchangeSupplier;
    private final Supplier<Collection<Exchange>> connectedFundingExchangesSupplier;
    private final Supplier<Collection<TradePair>> symbolUniverseSupplier;

    public TradingDeskFundingService(
            Supplier<Exchange> activeExchangeSupplier,
            Supplier<Collection<Exchange>> connectedFundingExchangesSupplier,
            Supplier<Collection<TradePair>> symbolUniverseSupplier) {
        this.activeExchangeSupplier = Objects.requireNonNull(
                activeExchangeSupplier,
                "activeExchangeSupplier must not be null");
        this.connectedFundingExchangesSupplier = Objects.requireNonNull(
                connectedFundingExchangesSupplier,
                "connectedFundingExchangesSupplier must not be null");
        this.symbolUniverseSupplier = Objects.requireNonNull(
                symbolUniverseSupplier,
                "symbolUniverseSupplier must not be null");
    }

    public List<Exchange> connectedFundingExchanges() {
        LinkedHashMap<String, Exchange> providers = new LinkedHashMap<>();
        Collection<Exchange> exchanges = connectedFundingExchangesSupplier.get();
        if (exchanges != null) {
            for (Exchange provider : exchanges) {
                addFundingProvider(providers, provider);
            }
        }
        addFundingProvider(providers, activeExchangeSupplier.get());
        return List.copyOf(providers.values());
    }

    public List<String> fundingCapableProviders() {
        return connectedFundingExchanges().stream()
                .map(this::displayName)
                .filter(value -> !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public Optional<Exchange> resolveFundingExchange(String providerName) {
        String normalizedProvider = normalizeExchangeName(providerName);
        if (normalizedProvider.isBlank()) {
            return Optional.empty();
        }
        return connectedFundingExchanges().stream()
                .filter(provider -> normalizedProvider.equals(normalizeExchangeName(displayName(provider))))
                .findFirst();
    }

    public List<String> fundingCurrencies() {
        LinkedHashSet<String> currencies = new LinkedHashSet<>();
        currencies.add("USD");
        currencies.add("USDC");
        currencies.add("USDT");
        currencies.add("BTC");
        currencies.add("ETH");
        currencies.add("SOL");
        currencies.add("XLM");

        Collection<TradePair> symbols = symbolUniverseSupplier.get();
        if (symbols != null) {
            symbols.stream()
                    .filter(Objects::nonNull)
                    .map(TradePair::getBaseCurrency)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .forEach(currencies::add);

            symbols.stream()
                    .filter(Objects::nonNull)
                    .map(TradePair::getCounterCurrency)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .map(value -> value.toUpperCase(Locale.ROOT))
                    .forEach(currencies::add);
        }

        return new ArrayList<>(currencies);
    }

    public FundingValidation validateFundingRequest(
            String type,
            String provider,
            String currency,
            String amountText,
            String destination,
            String network) {
        if (provider == null || provider.isBlank()) {
            return FundingValidation.invalid("Select a provider that supports funding operations.");
        }

        if (currency == null || currency.isBlank()) {
            return FundingValidation.invalid("Select a currency.");
        }

        BigDecimal amount;
        try {
            amount = new BigDecimal(safe(amountText));
        } catch (Exception exception) {
            return FundingValidation.invalid("Invalid amount.");
        }

        if (amount.signum() <= 0) {
            return FundingValidation.invalid("Amount must be greater than zero.");
        }

        if (amount.scale() > 8) {
            return FundingValidation.invalid("Amount has too many decimal places.");
        }

        boolean withdrawal = "Withdrawal".equalsIgnoreCase(type);
        boolean cryptoLike = isCryptoFundingCurrency(currency);

        if (withdrawal && cryptoLike && safe(destination).isBlank()) {
            return FundingValidation.invalid("Crypto withdrawal requires a destination wallet address.");
        }

        if (withdrawal && cryptoLike && safe(network).isBlank()) {
            return FundingValidation.invalid("Crypto withdrawal requires a network.");
        }

        if (withdrawal && !cryptoLike && safe(destination).isBlank()) {
            return FundingValidation.invalid("Fiat withdrawal requires a payment method or bank destination.");
        }

        return FundingValidation.valid(amount);
    }

    public CompletableFuture<String> executeFundingRequest(
            Exchange providerExchange,
            String action,
            String currency,
            BigDecimal amount,
            String destination,
            String network) {
        if (providerExchange instanceof Coinbase coinbase) {
            return executeCoinbaseFundingRequest(coinbase, action, currency, amount, safe(destination), safe(network));
        }

        if (providerExchange instanceof Binance binance) {
            if ("Deposit".equalsIgnoreCase(action)) {
                return CompletableFuture.failedFuture(
                        new UnsupportedOperationException("Binance adapter supports withdrawals only in this build."));
            }
            if (!isCryptoCurrencyCode(currency)) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Binance withdrawals require a crypto currency."));
            }
            if (safe(destination).isBlank()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Binance withdrawal requires a destination wallet address."));
            }
            return binance.requestWithdrawalToCryptoAddress(amount, currency, destination, network, null);
        }

        if (providerExchange instanceof Bitfinex bitfinex) {
            if ("Deposit".equalsIgnoreCase(action)) {
                return CompletableFuture.failedFuture(
                        new UnsupportedOperationException("Bitfinex adapter supports withdrawals only in this build."));
            }
            if (!isCryptoCurrencyCode(currency)) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Bitfinex withdrawals require a crypto currency."));
            }
            if (safe(destination).isBlank()) {
                return CompletableFuture.failedFuture(
                        new IllegalArgumentException("Bitfinex withdrawal requires a destination wallet address."));
            }
            return bitfinex.requestWithdrawalToCryptoAddress(amount, currency, destination, network, null);
        }

        String name = providerExchange == null ? "Selected provider" : displayName(providerExchange);
        return CompletableFuture.failedFuture(new UnsupportedOperationException(
                name + " does not expose live funding APIs in this adapter."));
    }

    public String extractFundingReference(String responseBody, String fallback) {
        if (responseBody == null || responseBody.isBlank()) {
            return fallback;
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

        return fallback;
    }

    public String money(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    public String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                return current.getMessage();
            }
            current = current.getCause();
        }
        return "Unknown error";
    }

    private CompletableFuture<String> executeCoinbaseFundingRequest(
            Coinbase coinbase,
            String action,
            String currency,
            BigDecimal amount,
            String destination,
            String network) {
        if (coinbase == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Coinbase exchange is unavailable."));
        }

        boolean isDeposit = "Deposit".equalsIgnoreCase(action);
        boolean cryptoRoute = isCryptoCurrencyCode(currency) || (!destination.isBlank() && destination.length() >= 24);

        if (isDeposit) {
            if (isCryptoCurrencyCode(currency)) {
                return CompletableFuture.failedFuture(new UnsupportedOperationException(
                        "Crypto deposits cannot be pulled by API. Generate a deposit address on the destination exchange and withdraw from the source exchange to that address."));
            }
            String paymentMethodId = FundingDestinationParser.parsePaymentMethodId(destination);
            if (paymentMethodId.isBlank()) {
                return CompletableFuture.failedFuture(new IllegalArgumentException(
                        "Deposit requires payment_method_id, bank_account:<id>, or debit_card:<id> in Destination."));
            }
            return coinbase.requestDepositFromPaymentMethod(amount, currency, paymentMethodId);
        }

        if (destination.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Withdrawal requires a payment method ID or crypto address in Destination."));
        }

        if (cryptoRoute) {
            return coinbase.requestWithdrawalToCryptoAddress(amount, currency, destination, network, null);
        }

        String paymentMethodId = FundingDestinationParser.parsePaymentMethodId(destination);
        if (paymentMethodId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Fiat withdrawal requires payment_method_id in Destination."));
        }
        return coinbase.requestWithdrawalToPaymentMethod(amount, currency, paymentMethodId);
    }

    private void addFundingProvider(Map<String, Exchange> providers, Exchange provider) {
        if (!supportsFundingOperations(provider)) {
            return;
        }
        providers.putIfAbsent(normalizeExchangeName(displayName(provider)), provider);
    }

    private boolean supportsFundingOperations(Exchange providerExchange) {
        return providerExchange instanceof Coinbase
                || providerExchange instanceof Binance
                || providerExchange instanceof Bitfinex;
    }

    private boolean isCryptoFundingCurrency(String currency) {
        if (currency == null) {
            return false;
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (isCryptoCurrencyCode(normalized)) {
            return true;
        }
        Collection<TradePair> symbols = symbolUniverseSupplier.get();
        if (symbols == null) {
            return false;
        }
        return symbols.stream()
                .filter(Objects::nonNull)
                .anyMatch(pair -> normalized.equalsIgnoreCase(String.valueOf(pair.getBaseCurrency()))
                        || normalized.equalsIgnoreCase(String.valueOf(pair.getCounterCurrency())));
    }

    private boolean isCryptoCurrencyCode(String currency) {
        if (currency == null) {
            return false;
        }
        return switch (currency.toUpperCase(Locale.ROOT)) {
            case "BTC", "ETH", "SOL", "USDC", "USDT", "XRP", "LTC", "ADA", "DOGE" -> true;
            default -> false;
        };
    }

    private String displayName(Exchange provider) {
        if (provider == null) {
            return "";
        }
        return firstNonBlank(
                provider.getDisplayName(),
                provider.getName(),
                provider.getExchangeId(),
                provider.getClass().getSimpleName());
    }

    private String normalizeExchangeName(String value) {
        return safe(value).toUpperCase(Locale.ROOT).replace('-', ' ').replace('_', ' ').trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String safeValue = safe(value);
            if (!safeValue.isBlank()) {
                return safeValue;
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record FundingValidation(boolean valid, String message, BigDecimal amount) {
        public static FundingValidation valid(BigDecimal amount) {
            return new FundingValidation(true, "", amount);
        }

        public static FundingValidation invalid(String message) {
            return new FundingValidation(false, message, BigDecimal.ZERO);
        }
    }
}
