package org.investpro.models.trading;

/**
 * Canonical fee schedules for all exchanges supported by InvestPro.
 * <p>
 * Rates are the <em>standard</em> (non-VIP, non-discount) published rates.
 * Volume-based tier discounts and token-payment discounts (e.g. BNB on Binance)
 * are not modelled here but can be configured at runtime via
 * {@link #withMakerRate} / {@link #withTakerRate}.
 * <p>
 * Sources (as of May 2026):<br>
 * Binance: binance.com/en/fee/schedule<br>
 * Coinbase: coinbase.com/advanced-trade/fees<br>
 * OANDA: oanda.com/us-en/trading/spreads-and-fees/<br>
 * Interactive Brokers: interactivebrokers.com/en/trading/stocks-commissions.php<br>
 * Alpaca: alpaca.markets/fees<br>
 * Bitfinex: bitfinex.com/fees<br>
 * Stellar DEX: stellar.org/developers/guides/concepts/fees.html
 */
public enum ExchangeFeeSchedule {

    BINANCE(
            "Binance",
            0.0010,   // maker 0.10 %
            0.0010,   // taker 0.10 %
            "BNB",    // native fee currency (BNB discount available)
            FeeModel.PERCENTAGE
    ),

    BINANCE_US(
            "BinanceUs",
            0.0010,
            0.0010,
            "USD",
            FeeModel.PERCENTAGE
    ),

    COINBASE(
            "Coinbase",
            0.0040,   // maker 0.40 %
            0.0060,   // taker 0.60 %
            "USD",
            FeeModel.PERCENTAGE
    ),

    /**
     * OANDA charges via spread; explicit commission only on certain account types.
     * Standard retail: 0 fixed commission, spread is the cost.
     * Raw spread account: USD 5 per 100 k notional (0.005 %).
     * We model the raw-spread commission here; for spread-only accounts set both to 0.
     */
    OANDA(
            "Oanda",
            0.00005,  // ~USD 5 / 100 k notional
            0.00005,
            "USD",
            FeeModel.PERCENTAGE
    ),

    /**
     * Interactive Brokers — US equities (tiered pricing, ≤ 300 k shares/month).
     * USD 0.005 per share, min USD 1.00 per order.
     * Crypto: 0.12 % – 0.18 % depending on volume.
     * We store the per-share stock rate; crypto overrides should be applied at runtime.
     */
    INTERACTIVE_BROKERS(
            "InteractiveBrokers",
            0.0050,   // USD 0.005 per share (stored as rate; absolute calc done in service)
            0.0050,
            "USD",
            FeeModel.PER_UNIT   // use perUnitRate for stock trades
    ),

    /**
     * Alpaca — US stocks: $0 commission (zero-commission). Crypto: maker 0.15 %, taker 0.25 %.
     * Stock rate stored as 0; crypto accounts should use the crypto rate override.
     */
    ALPACA(
            "Alpaca",
            0.0000,   // stocks: zero commission
            0.0000,
            "USD",
            FeeModel.PERCENTAGE
    ),

    ALPACA_CRYPTO(
            "Alpaca",
            0.0015,   // crypto maker 0.15 %
            0.0025,   // crypto taker 0.25 %
            "USD",
            FeeModel.PERCENTAGE
    ),

    BITFINEX(
            "Bitfinex",
            0.0010,   // maker 0.10 %
            0.0020,   // taker 0.20 %
            "USD",
            FeeModel.PERCENTAGE
    ),

    /**
     * Stellar DEX — network fee is 0.00001 XLM (base fee) per operation.
     * For swap-style trades, the effective cost is typically < 0.01 % of notional.
     * We model 0.10 % as a conservative estimate of the path-payment cost.
     */
    STELLAR(
            "StellarNetwork",
            0.0010,
            0.0010,
            "XLM",
            FeeModel.PERCENTAGE
    );

    // -------------------------------------------------------------------------

    private final String exchangeName;
    private double makerRate;
    private double takerRate;
    private final String defaultFeeCurrency;
    private final FeeModel feeModel;

    ExchangeFeeSchedule(String exchangeName, double makerRate, double takerRate,
                        String defaultFeeCurrency, FeeModel feeModel) {
        this.exchangeName = exchangeName;
        this.makerRate = makerRate;
        this.takerRate = takerRate;
        this.defaultFeeCurrency = defaultFeeCurrency;
        this.feeModel = feeModel;
    }

    public String getExchangeName()        { return exchangeName; }
    public double getMakerRate()           { return makerRate; }
    public double getTakerRate()           { return takerRate; }
    public String getDefaultFeeCurrency()  { return defaultFeeCurrency; }
    public FeeModel getFeeModel()          { return feeModel; }

    /** Runtime override — e.g. after fetching VIP tier from the exchange. */
    public ExchangeFeeSchedule withMakerRate(double rate) { this.makerRate = rate; return this; }
    public ExchangeFeeSchedule withTakerRate(double rate) { this.takerRate = rate; return this; }

    /**
     * Compute the expected fee for a trade.
     *
     * @param notional   trade value in quote currency
     * @param units      number of units / shares (only used for PER_UNIT model)
     * @param feeType    MAKER or TAKER
     * @return absolute fee amount in {@link #defaultFeeCurrency}
     */
    public double computeFee(double notional, double units, TradeFee.FeeType feeType) {
        double rate = (feeType == TradeFee.FeeType.MAKER) ? makerRate : takerRate;
        return switch (feeModel) {
            case PERCENTAGE -> notional * rate;
            case PER_UNIT   -> Math.max(1.0, units * rate);  // IB min $1.00
            case FLAT       -> rate;
        };
    }

    /** Convenience overload — defaults to TAKER (market order). */
    public double computeFee(double notional, double units) {
        return computeFee(notional, units, TradeFee.FeeType.TAKER);
    }

    /**
     * Resolve the schedule by exchange adapter class simple name.
     * Returns {@code null} if no match is found.
     */
    public static ExchangeFeeSchedule forExchange(String exchangeName) {
        if (exchangeName == null) return null;
        String lower = exchangeName.toLowerCase();
        for (ExchangeFeeSchedule s : values()) {
            if (s.exchangeName.toLowerCase().equals(lower)) return s;
        }
        // Fuzzy: partial match
        for (ExchangeFeeSchedule s : values()) {
            if (lower.contains(s.exchangeName.toLowerCase()) ||
                s.exchangeName.toLowerCase().contains(lower)) {
                return s;
            }
        }
        return null;
    }

    public enum FeeModel {
        /** Fee = notional * rate (most crypto/forex venues). */
        PERCENTAGE,
        /** Fee = units * rate, min applies (IB equities). */
        PER_UNIT,
        /** Fixed fee per order regardless of size. */
        FLAT
    }
}
