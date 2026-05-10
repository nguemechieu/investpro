package org.investpro.models.trading;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.LiquidityProfile;
import org.investpro.market.InstrumentTradingSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Immutable metadata for a tradable instrument.
 * Contains contract specifications, trading rules, and session information.
 * Sourced from exchange adapters and enriched by InstrumentMetadataService.
 */
@Builder
@Getter
@ToString
public class InstrumentMetadata implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The instrument identity.
     */
    @NotNull
    private final TradePair tradePair;

    /**
     * Broker or exchange name (e.g., "Binance", "OANDA", "Interactive Brokers").
     */
    @NotNull
    private final String broker;

    /**
     * Trading venue (e.g., "SPOT", "FUTURES", "FOREX", "EQUITIES").
     */
    @NotNull
    private final String venue;

    /**
     * Exchange-specific symbol (e.g., "BTCUSDT" on Binance vs "BTC/USD" on OANDA).
     */
    @NotNull
    private final String exchangeSymbol;

    /**
     * Asset class: CRYPTO, FOREX, STOCK, ETF, INDEX, COMMODITY, FUTURE, PERPETUAL,
     * OPTION, CFD.
     */
    @NotNull
    private final AssetClass assetClass;

    /**
     * Contract type: SPOT, MARGIN, FUTURES, PERPETUAL, OPTION, CFD.
     */
    @NotNull
    private final ContractType contractType;

    /**
     * Minimum order size for this instrument.
     */
    private final double minOrderSize;

    /**
     * Maximum order size for this instrument.
     */
    private final double maxOrderSize;

    /**
     * Price increment (e.g., 0.01 for BTC/USD means prices are in cents).
     */
    private final double tickSize;

    /**
     * Lot size: minimum order quantity unit.
     */
    private final double lotSize;

    /**
     * Pip size (mainly for forex: 0.0001 for most pairs, 0.01 for JPY pairs).
     */
    private final double pipSize;

    /**
     * Contract size (e.g., 100 for standard forex lots, 1 for crypto).
     */
    private final double contractSize;

    /**
     * Currency used for margin requirements (e.g., "USD" or "USDT").
     */
    @NotNull
    private final String marginCurrency;

    /**
     * Maximum leverage allowed (e.g., 10.0 for 10:1 leverage).
     */
    private final double leverageLimit;

    /**
     * Whether shorting is allowed.
     */
    private final boolean shortable;

    /**
     * Whether the instrument is currently tradable.
     */
    private final boolean tradable;

    /**
     * Liquidity profile: ILLIQUID, LOW, NORMAL, HIGH, ULTRA_HIGH.
     */
    @NotNull
    @Builder.Default
    private final LiquidityProfile liquidityProfile = LiquidityProfile.NORMAL;

    /**
     * Trading session information (e.g., NYSE hours, forex sessions, crypto 24/7).
     */
    @Nullable
    private final InstrumentTradingSession tradingSession;

    /**
     * Verify metadata consistency and completeness.
     */
    public boolean isComplete() {
        return tradePair != null
                && !broker.isBlank()
                && !venue.isBlank()
                && !exchangeSymbol.isBlank()
                && assetClass != null
                && contractType != null
                && minOrderSize > 0
                && maxOrderSize >= minOrderSize
                && tickSize > 0
                && lotSize > 0
                && pipSize > 0
                && contractSize > 0
                && !marginCurrency.isBlank()
                && leverageLimit > 0
                && liquidityProfile != null;
    }

    /**
     * Calculate order quantity in base currency units given a nominal size.
     */
    public double calculateOrderQuantity(double nominalSize) {
        return Math.max(minOrderSize, nominalSize * lotSize);
    }

    /**
     * Calculate pip value in margin currency.
     */
    public double pipValue(double quantity, double price) {
        return quantity * pipSize * price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InstrumentMetadata that = (InstrumentMetadata) o;
        return Objects.equals(tradePair, that.tradePair)
                && Objects.equals(broker, that.broker)
                && Objects.equals(venue, that.venue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, broker, venue);
    }
}
