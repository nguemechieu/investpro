package org.investpro.exchange.oanda;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Immutable specification for an OANDA instrument, populated directly from the
 * /v3/accounts/{id}/instruments API response.
 *
 * <pre>
 * {
 *   "displayName":                 "CAD/SGD",
 *   "displayPrecision":            5,
 *   "marginRate":                  "0.05",
 *   "maximumOrderUnits":           "100000000",
 *   "maximumPositionSize":         "0",
 *   "maximumTrailingStopDistance": "1.00000",
 *   "minimumTradeSize":            "1",
 *   "minimumTrailingStopDistance": "0.00050",
 *   "name":                        "CAD_SGD",
 *   "pipLocation":                 -4,
 *   "tradeUnitsPrecision":         0,
 *   "type":                        "CURRENCY"
 * }
 * </pre>
 */
public record OandaInstrumentSpec(
        /** Human-readable name, e.g. "CAD/SGD". */
        String displayName,

        /** Number of decimal places used when displaying prices, e.g. 5. */
        int displayPrecision,

        /** Required margin ratio (e.g. 0.05 = 5 % margin → 20× leverage). */
        BigDecimal marginRate,

        /** Maximum units per single order (0 = unlimited). */
        BigDecimal maximumOrderUnits,

        /** Maximum open position in units (0 = unlimited). */
        BigDecimal maximumPositionSize,

        /** Maximum allowed trailing-stop distance in price units. */
        BigDecimal maximumTrailingStopDistance,

        /** Minimum units for a single trade (e.g. 1). */
        BigDecimal minimumTradeSize,

        /** Minimum allowed trailing-stop distance in price units. */
        BigDecimal minimumTrailingStopDistance,

        /** OANDA instrument name, e.g. "CAD_SGD". */
        String name,

        /** Exponent for 1 pip: pipValue = 10^pipLocation (e.g. -4 → 0.0001). */
        int pipLocation,

        /** Decimal places for trade units; 0 means integer units only. */
        int tradeUnitsPrecision,

        /** Instrument category: "CURRENCY", "CFD", "METAL". */
        String type
) {

    /** One pip expressed as a price increment (10^pipLocation). */
    public BigDecimal pipValue() {
        return BigDecimal.ONE.scaleByPowerOfTen(pipLocation);
    }

    /**
     * Effective leverage derived from marginRate.
     * Returns 1/marginRate, or 1 if marginRate is zero/null.
     */
    public BigDecimal effectiveLeverage() {
        if (marginRate == null || marginRate.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.ONE.divide(marginRate, MathContext.DECIMAL64);
    }

    /**
     * Returns the price increment (smallest meaningful price unit).
     * OANDA prices are quoted to {@code displayPrecision} decimal places.
     */
    public BigDecimal priceIncrement() {
        return BigDecimal.ONE.scaleByPowerOfTen(-displayPrecision);
    }

    /** Whether order units must be whole numbers (tradeUnitsPrecision == 0). */
    public boolean requiresIntegerUnits() {
        return tradeUnitsPrecision == 0;
    }

    /**
     * Parse an {@link OandaInstrumentSpec} from the raw JSON node fields.
     * All numeric fields accept both string and number representations.
     */
    public static OandaInstrumentSpec from(
            String name,
            String displayName,
            int displayPrecision,
            String marginRate,
            String maximumOrderUnits,
            String maximumPositionSize,
            String maximumTrailingStopDistance,
            String minimumTradeSize,
            String minimumTrailingStopDistance,
            int pipLocation,
            int tradeUnitsPrecision,
            String type) {

        return new OandaInstrumentSpec(
                displayName,
                displayPrecision,
                parseSafe(marginRate, BigDecimal.valueOf(0.02)),
                parseSafe(maximumOrderUnits, BigDecimal.valueOf(100_000_000)),
                parseSafe(maximumPositionSize, BigDecimal.ZERO),
                parseSafe(maximumTrailingStopDistance, BigDecimal.ONE),
                parseSafe(minimumTradeSize, BigDecimal.ONE),
                parseSafe(minimumTrailingStopDistance, BigDecimal.valueOf(0.0005)),
                name,
                pipLocation,
                tradeUnitsPrecision,
                type == null || type.isBlank() ? "CURRENCY" : type
        );
    }

    private static BigDecimal parseSafe(String value, BigDecimal fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
