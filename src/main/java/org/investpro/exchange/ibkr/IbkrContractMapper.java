package org.investpro.exchange.ibkr;

import org.investpro.models.trading.TradePair;
import org.investpro.utils.MARKET_TYPES;
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

        if (looksLikeForex(pair)) {
            return new IbkrContract(symbol, "CASH", "IDEALPRO", currency, null, null, null, null);
        }

        if (marketTypeHint == MARKET_TYPES.FUTURES) {
            return new IbkrContract(symbol, "FUT", "GLOBEX", currency, defaultFutureExpiry(), null, null, "1");
        }

        if (marketTypeHint == MARKET_TYPES.STOP_LIMIT || symbol.length() > 5) {
            return new IbkrContract(symbol, "OPT", "SMART", currency, defaultOptionExpiry(), 100.0, "C", "100");
        }

        return new IbkrContract(symbol, "STK", "SMART", currency, null, null, null, null);
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
        return code.length() == 3;
    }

    private String defaultFutureExpiry() {
        return "202612";
    }

    private String defaultOptionExpiry() {
        return "20261220";
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
