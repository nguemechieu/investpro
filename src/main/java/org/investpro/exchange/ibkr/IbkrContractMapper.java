package org.investpro.exchange.ibkr;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.MARKET_TYPES;
import org.investpro.utils.ORDER_TYPES;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

/**
 * Maps InvestPro symbols/trade pairs into IBKR contract definitions.
 */
public final class IbkrContractMapper {

    public IbkrContract toContract(@NotNull TradePair pair, MARKET_TYPES marketTypeHint) {
        Objects.requireNonNull(pair, "pair must not be null");

        String symbol = pair.getBaseCurrency().getCode().toUpperCase(Locale.ROOT);
        String currency = pair.getCounterCurrency().getCode().toUpperCase(Locale.ROOT);

        if (marketTypeHint != MARKET_TYPES.FUTURES && looksLikeForex(pair)) {
            return new IbkrContract(symbol, "CASH", "IDEALPRO", currency, null, null, null, null);
        }

        if (marketTypeHint == MARKET_TYPES.FUTURES) {
            throw new IllegalArgumentException("Select a broker-resolved futures contract with its actual expiry and multiplier.");
        }

        return new IbkrContract(symbol, "STK", "SMART", currency, null, null, null, null);
    }

    public IbkrContract toContract(@NotNull TradePair pair, ORDER_TYPES orderTypeHint) {
        Objects.requireNonNull(pair, "pair must not be null");
        return toContract(pair, MARKET_TYPES.STOCKS);
    }

    public IbkrContract toContract(@NotNull TradePair pair) {
        return toContract(pair, MARKET_TYPES.STOCKS);
    }

    public boolean supports(@NotNull TradePair pair, MARKET_TYPES marketTypeHint) {
        IbkrContract contract = toContract(pair, marketTypeHint);
        return switch (contract.secType()) {
            case "STK", "CASH", "FUT", "OPT" -> true;
            default -> false;
        };
    }

    private boolean looksLikeForex(TradePair pair) {
        String base = pair.getBaseCurrency().getCode().toUpperCase(Locale.ROOT);
        String quote = pair.getCounterCurrency().getCode().toUpperCase(Locale.ROOT);
        return isFxCode(base) && isFxCode(quote);
    }

    private boolean isFxCode(String code) {
        return java.util.Set.of("USD", "EUR", "GBP", "JPY", "CHF", "CAD", "AUD", "NZD", "HKD", "SGD", "CNH", "SEK", "NOK", "DKK", "MXN", "ZAR", "PLN", "HUF", "CZK", "ILS", "TRY", "KRW", "INR", "BRL").contains(code);
    }

    public record IbkrContract(
            String symbol,
            String secType,
            String exchange,
            String currency,
            String expiry,
            Double strike,
            String right,
            String multiplier) {
    }
}
