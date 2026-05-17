package org.investpro.models.trading;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Records the fee paid on a single completed trade.
 * <p>
 * Every order fill that touches an exchange produces a {@code TradeFee}.
 * Fees are denominated in the exchange's native fee currency (e.g. BNB on
 * Binance, USD on OANDA, ETH on Coinbase) and expressed as an absolute amount —
 * not a percentage. Use {@link ExchangeFeeSchedule} to compute expected fees
 * before execution.
 */
@Data
@Builder
public class TradeFee {

    /** Human-readable exchange name (e.g. "Binance", "Coinbase", "OANDA"). */
    private String exchange;

    /** The trade pair the fee was incurred on. */
    private TradePair tradePair;

    /** Exchange-assigned order / fill ID for traceability. */
    private String orderId;

    /** Fee amount (absolute, not a rate). */
    private double amount;

    /** ISO 4217 code or crypto symbol of the fee currency (e.g. "USD", "BNB"). */
    private String feeCurrency;

    /** Whether the order was a maker (limit) or taker (market) order. */
    private FeeType feeType;

    /** When the fill / settlement occurred on the exchange. */
    private Instant timestamp;

    /** Notional trade value in quote currency — used for rate verification. */
    private double notionalValue;

    /** Database row ID (0 = not yet persisted). */
    private long id;

    public enum FeeType {
        MAKER,
        TAKER,
        /** Used when the exchange does not distinguish (e.g. spread-based venues). */
        UNKNOWN
    }

    /**
     * Effective fee rate = fee amount / notional value.
     * Returns 0 when notional is zero to avoid division by zero.
     */
    public double effectiveRate() {
        return notionalValue > 0 ? amount / notionalValue : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "TradeFee[exchange=%s, pair=%s, orderId=%s, amount=%.8f %s, type=%s, rate=%.4f%%, ts=%s]",
                exchange, tradePair, orderId, amount, feeCurrency,
                feeType, effectiveRate() * 100, timestamp);
    }
}
