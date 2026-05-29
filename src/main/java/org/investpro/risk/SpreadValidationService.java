package org.investpro.risk;

import org.investpro.decision.AssetMarketType;
import org.investpro.models.trading.Ticker;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates market spread by asset type.
 */
public class SpreadValidationService {

    /** 0.5 pip = 0.00005 in major FX quote terms (not five pips). */
    private static final BigDecimal FOREX_MAX_SPREAD_PIPS = new BigDecimal("0.5");
    private static final BigDecimal CRYPTO_SPOT_MAX_SPREAD_PERCENT = new BigDecimal("1.0");
    private static final BigDecimal CRYPTO_DERIV_MAX_SPREAD_PERCENT = new BigDecimal("1.5");
    private static final BigDecimal EQUITY_MAX_SPREAD_PERCENT = new BigDecimal("0.5");
    private static final BigDecimal EQUITY_DERIV_MAX_SPREAD_PERCENT = new BigDecimal("0.8");

    @NotNull
    public SpreadValidationResult validate(@NotNull Ticker ticker, @NotNull AssetMarketType assetType) {
        BigDecimal bid = BigDecimal.valueOf(ticker.getBidPrice());
        BigDecimal ask = BigDecimal.valueOf(ticker.getAskPrice());

        if (bid.signum() <= 0 || ask.signum() <= 0 || ask.compareTo(bid) <= 0) {
            return new SpreadValidationResult(
                    false,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    "Spread cannot be calculated from invalid bid/ask",
                    List.of("Ticker must be validated before spread validation"));
        }

        BigDecimal spread = ask.subtract(bid);
        BigDecimal mid = ask.add(bid).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
        BigDecimal spreadPercent = spread.divide(mid, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        List<String> warnings = new ArrayList<>();
        BigDecimal maxSpreadPercent;

        switch (assetType) {
            case FOREX -> {
                BigDecimal pips = spread.multiply(BigDecimal.valueOf(10_000));
                boolean ok = pips.compareTo(FOREX_MAX_SPREAD_PIPS) <= 0;
                return new SpreadValidationResult(
                        ok,
                        spread,
                        spreadPercent,
                        "Spread " + pips + " pips vs max " + FOREX_MAX_SPREAD_PIPS + " pips",
                        List.copyOf(warnings));
            }
            case CRYPTO_SPOT -> maxSpreadPercent = CRYPTO_SPOT_MAX_SPREAD_PERCENT;
            case CRYPTO_DERIVATIVES -> maxSpreadPercent = CRYPTO_DERIV_MAX_SPREAD_PERCENT;
            case EQUITIES -> maxSpreadPercent = EQUITY_MAX_SPREAD_PERCENT;
            case EQUITY_DERIVATIVES -> maxSpreadPercent = EQUITY_DERIV_MAX_SPREAD_PERCENT;
            default -> {
                warnings.add("Unknown asset type; spread threshold is permissive fallback");
                maxSpreadPercent = BigDecimal.valueOf(5.0);
            }
        }

        boolean acceptable = spreadPercent.compareTo(maxSpreadPercent) <= 0;
        String reason = "Spread " + spreadPercent.setScale(4, RoundingMode.HALF_UP)
                + "% vs max " + maxSpreadPercent + "%";
        return new SpreadValidationResult(acceptable, spread, spreadPercent, reason, List.copyOf(warnings));
    }
}
