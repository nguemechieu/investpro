package org.investpro.models.trading;

import javafx.util.Pair;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.investpro.enums.LiquidityProfile;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyRegistry;
import org.investpro.models.currency.CurrencyNotFoundException;
import org.investpro.models.currency.CurrencyType;
import org.investpro.models.currency.FiatCurrency;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.investpro.market.InstrumentTradingSession;
import org.investpro.enums.TradingSessionStatus;

import java.time.ZonedDateTime;

/**
 * Represents a tradable market pair such as:
 * BTC/USD, EUR/USD, ETH/USDT.
 * <p>
 * This class is intentionally a model only.
 * It should not fetch data from exchanges directly.
 * <p>
 * Exchange adapters should update bid/ask/last/volume/change fields.
 */

@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Slf4j
@Data
public class TradePair extends Pair<Currency, Currency> {
    private final Currency baseCurrency;
    private final Currency counterCurrency;

    private long id;

    private double bid;
    private double ask;
    private double last;
    private double volume;
    private double changePercent;
    private double high24h;
    private double low24h;

    private Instant updatedAt;
    private String nativeSymbol;
    private String displaySymbol;
    private String underlyingCode;
    private String expiryCode;
    private LocalDate contractExpiryDate;
    private TradeSymbolKind symbolKind = TradeSymbolKind.PAIR;
    private MarketType marketType = MarketType.SPOT;
    private TradingEnvironment tradingEnvironment = TradingEnvironment.UNKNOWN;
    private String productVenue;
    private String exchangeId;

    private static final DateTimeFormatter CDE_EXPIRY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyy")
            .toFormatter(Locale.US);

    public TradePair(
            @NotNull Currency baseCurrency,
            @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        super(
                Objects.requireNonNull(baseCurrency, "baseCurrency must not be null"),
                Objects.requireNonNull(counterCurrency, "counterCurrency must not be null"));

        validateCurrencies(baseCurrency, counterCurrency);

        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;
        this.updatedAt = Instant.now();

        log.debug("TradePair created: {}", this);
    }

    public TradePair(
            @NotNull String baseCurrency,
            @NotNull String counterCurrency) throws SQLException, ClassNotFoundException {
        this(
                CurrencyRegistry.global().findOrUnknown(normalizeCode(baseCurrency)),
                CurrencyRegistry.global().findOrUnknown(normalizeCode(counterCurrency)));
    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(
            String baseCurrencyCode,
            String counterCurrencyCode) throws SQLException, ClassNotFoundException {
        return new TradePair(baseCurrencyCode, counterCurrencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(
            @NotNull Currency baseCurrency,
            @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        return new TradePair(baseCurrency, counterCurrency);
    }

    @Contract("_ -> new")
    public static @NotNull TradePair of(
            @NotNull Pair<Currency, Currency> currencyPair) throws SQLException, ClassNotFoundException {
        Objects.requireNonNull(currencyPair, "currencyPair must not be null");
        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
    }

    public static <T extends Currency, V extends Currency> @NotNull TradePair parse(
            String symbol,
            @NotNull String separator,
            Pair<Class<T>, Class<V>> pairType) throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        Objects.requireNonNull(symbol, "tradePair must not be null");
        Objects.requireNonNull(separator, "separator must not be null");
        Objects.requireNonNull(pairType, "pairType must not be null");
        Objects.requireNonNull(pairType.getKey(), "first member of pairType must not be null");

        String text = symbol.trim().toUpperCase();

        if (text.isBlank()) {
            throw new IllegalArgumentException("tradePair must not be blank");
        }

        String[] parts = splitPair(text, separator);

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "Invalid trade pair '%s'. Expected format BASE%sQUOTE."
                            .formatted(symbol, separator));
        }

        String baseCode = normalizeCode(parts[0]);
        String counterCode = normalizeCode(parts[1]);

        Currency base = CurrencyRegistry.global().findOrUnknown(baseCode);
        Currency counter = CurrencyRegistry.global().findOrUnknown(counterCode);

        validateExpectedCurrencyType(base, baseCode, pairType.getKey(), true);

        if (pairType.getValue() != null) {
            validateExpectedCurrencyType(counter, counterCode, pairType.getValue(), false);
        } else {
            validateAnyKnownCurrency(counter, counterCode);
        }

        assert base != null;
        assert counter != null;
        TradePair pair = new TradePair(base, counter);
        pair.setNativeSymbol(text);
        return pair;
    }

    public static @NotNull TradePair fromSymbol(String symbol) throws SQLException, ClassNotFoundException {
        Objects.requireNonNull(symbol, "symbol must not be null");
        String normalized = symbol.trim().toUpperCase(Locale.ROOT);
        if (isDerivativeProductSymbol(normalized)) {
            return fromNativeProductSymbol(normalized);
        }

        String[] parts = splitAnyPair(normalized);
        String baseCode = normalizeCode(parts[0]);
        String counterCode = normalizeCode(parts[1]);

        Currency base = CurrencyRegistry.global().findOrUnknown(baseCode);
        Currency counter = CurrencyRegistry.global().findOrUnknown(counterCode);
        TradePair pair = new TradePair(base, counter);
        pair.setNativeSymbol(symbol);
        return pair;
    }

    public static @NotNull TradePair fromNativeProductSymbol(String symbol) throws SQLException, ClassNotFoundException {
        Objects.requireNonNull(symbol, "symbol must not be null");
        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }

        String[] segments = normalized.split("-");
        String underlying = normalizeCode(segments.length == 0 ? normalized : segments[0]);
        String safeQuote = "USD";

        Currency base = CurrencyRegistry.global().findOrUnknown(underlying);
        Currency counter = CurrencyRegistry.global().findOrUnknown(safeQuote);
        TradePair pair = new TradePair(base, counter);
        pair.setNativeSymbol(normalized);
        pair.setDisplaySymbol(normalized);
        pair.setUnderlyingCode(underlying);
        pair.setExchangeId("coinbase");

        if (isPerpetualProductSymbol(normalized)) {
            pair.setSymbolKind(TradeSymbolKind.PERPETUAL_CONTRACT);
            pair.setContractType(ContractType.PERPETUAL);
            pair.setAssetClass(AssetClass.DERIVATIVE);
            pair.setMarketType(MarketType.PERPETUAL);
            pair.setProductVenue("COINBASE_DERIVATIVES");
            return pair;
        }

        pair.setSymbolKind(TradeSymbolKind.DERIVATIVE_CONTRACT);
        pair.setContractType(ContractType.FUTURE);
        pair.setAssetClass(AssetClass.DERIVATIVE);
        pair.setMarketType(MarketType.FUTURE);
        pair.setProductVenue("COINBASE_DERIVATIVES");

        String expiry = expirySegment(normalized);
        pair.setExpiryCode(expiry);
        pair.setContractExpiryDate(parseExpiryDate(expiry));
        return pair;
    }

    public static boolean isDerivativeProductSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
        return normalized.endsWith("-CDE")
                || normalized.contains("-PERP")
                || normalized.matches(".*(?:^|-)\\d{2}[A-Z]{3}\\d{2}(?:-|$).*");
    }

    public static boolean isPerpetualProductSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return false;
        }
        String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
        return normalized.endsWith("-PERP") || normalized.contains("-PERP-");
    }

    private static String expirySegment(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        for (String part : symbol.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-').split("-")) {
            if (part.matches("\\d{2}[A-Z]{3}\\d{2}")) {
                return part;
            }
        }
        return "";
    }

    private static LocalDate parseExpiryDate(String expiry) {
        if (expiry == null || expiry.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(expiry, CDE_EXPIRY_FORMATTER);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String[] splitAnyPair(String symbol) {
        if (symbol.contains("-")) {
            return symbol.split("-", 2);
        }
        if (symbol.contains("_")) {
            return symbol.split("_", 2);
        }
        if (symbol.contains("/")) {
            return symbol.split("/", 2);
        }
        if (symbol.contains(":")) {
            return symbol.split(":", 2);
        }

        if (symbol.length() >= 6) {
            return new String[] { symbol.substring(0, 3), symbol.substring(3) };
        }

        return new String[] { symbol, "USD" };
    }

    private static String @NotNull [] splitPair(String tradePair, @NotNull String separator) {
        if (separator.isEmpty()) {
            if (tradePair.length() < 6) {
                throw new IllegalArgumentException(
                        "Cannot parse compact trade pair shorter than 6 characters: %s".formatted(tradePair));
            }

            return new String[] {
                    tradePair.substring(0, 3),
                    tradePair.substring(3)
            };
        }

        return tradePair.split(Pattern.quote(separator));
    }

    private static void validateExpectedCurrencyType(
            Currency currency,
            String code,
            Class<? extends Currency> expectedType,
            boolean base) {
        if (expectedType == null) {
            try {
                validateAnyKnownCurrency(currency, code);
            } catch (CurrencyNotFoundException e) {
                throw new RuntimeException(e);
            }
            return;
        }

        if (FiatCurrency.class.equals(expectedType)) {
            if (!(currency instanceof FiatCurrency) || currency.getCurrencyType() == CurrencyType.UNKNOWN) {
                try {
                    throw new CurrencyNotFoundException(CurrencyType.FIAT, code);
                } catch (CurrencyNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            return;
        }

        if (CryptoCurrency.class.equals(expectedType)) {
            if (!(currency instanceof CryptoCurrency) || currency.getCurrencyType() == CurrencyType.UNKNOWN) {
                try {
                    throw new CurrencyNotFoundException(CurrencyType.CRYPTO, code);
                } catch (CurrencyNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
            return;
        }

        throw new IllegalArgumentException(
                "%s currency type must be FiatCurrency.class, CryptoCurrency.class, or null, but was: %s"
                        .formatted(base ? "Base" : "Counter", expectedType));
    }

    private static void validateAnyKnownCurrency(
            Currency currency,
            String code) throws CurrencyNotFoundException {
        if (currency == null
                || currency == Currency.NULL_FIAT_CURRENCY
                || currency == Currency.NULL_CRYPTO_CURRENCY
                || currency.getCurrencyType() == CurrencyType.UNKNOWN) {
            throw new CurrencyNotFoundException(CurrencyType.FIAT, code);
        }
    }

    private static void validateCurrencies(
            Currency baseCurrency,
            Currency counterCurrency) {
        String baseCode = codeOf(baseCurrency);
        String counterCode = codeOf(counterCurrency);

        if (baseCode.isBlank() || counterCode.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency codes must be non-empty, but were '%s' and '%s'"
                            .formatted(baseCode, counterCode));
        }

        // Reject null/placeholder currency codes (XXX, ¤¤¤)
        if (baseCode.equals("XXX") || baseCode.equals("¤¤¤")) {
            throw new IllegalArgumentException(
                    "Base currency code '%s' is a reserved null currency code and cannot be traded"
                            .formatted(baseCode));
        }

        if (counterCode.equals("XXX") || counterCode.equals("¤¤¤")) {
            throw new IllegalArgumentException(
                    "Counter currency code '%s' is a reserved null currency code and cannot be traded"
                            .formatted(counterCode));
        }

        if (baseCode.equalsIgnoreCase(counterCode)) {
            throw new IllegalArgumentException(
                    "baseCurrency and counterCurrency must be different: %s".formatted(baseCode));
        }
    }

    private static @NotNull String normalizeCode(String code) {
        if (code == null) {
            return "";
        }

        return code.trim().toUpperCase();
    }

    private static @NotNull String codeOf(Currency currency) {
        if (currency == null || currency.getCode() == null) {
            return "";
        }

        return currency.getCode().trim().toUpperCase();
    }

    public String toString(@NotNull Character separator) {
        if (isNativeProductSymbol()) {
            return displaySymbol();
        }

        String baseCode = codeOf(baseCurrency);
        String counterCode = codeOf(counterCurrency);

        return switch (separator) {
            case '_' -> "%s_%s".formatted(baseCode, counterCode);
            case '-' -> "%s-%s".formatted(baseCode, counterCode);
            case '/' -> "%s/%s".formatted(baseCode, counterCode);
            default -> "%s%s%s".formatted(baseCode, separator, counterCode);
        };
    }

    public String toCompactSymbol() {
        if (isNativeProductSymbol()) {
            return displaySymbol();
        }
        return "%s%s".formatted(codeOf(baseCurrency), codeOf(counterCurrency));
    }

    public String toDashSymbol() {
        return toString('-');
    }

    public String toSlashSymbol() {
        return toString('/');
    }

    public String toUnderscoreSymbol() {
        return toString('_');
    }

    public String getSymbol() {
        return toSlashSymbol();
    }

    public boolean isNativeProductSymbol() {
        return symbolKind == TradeSymbolKind.NATIVE_PRODUCT
                || symbolKind == TradeSymbolKind.DERIVATIVE_CONTRACT
                || symbolKind == TradeSymbolKind.PERPETUAL_CONTRACT
                || isDerivativeProductSymbol(nativeSymbol);
    }

    public boolean isDerivativeContract() {
        return symbolKind == TradeSymbolKind.DERIVATIVE_CONTRACT
                || contractType == ContractType.FUTURE
                || isExpiringContract();
    }

    public boolean isPerpetual() {
        return symbolKind == TradeSymbolKind.PERPETUAL_CONTRACT
                || contractType == ContractType.PERPETUAL
                || isPerpetualProductSymbol(nativeSymbol);
    }

    public boolean isExpiringContract() {
        return expiryCode != null && !expiryCode.isBlank();
    }

    public boolean hasExpired() {
        return contractExpiryDate != null && contractExpiryDate.isBefore(LocalDate.now(ZoneOffset.UTC));
    }

    public boolean isActiveContract() {
        return !hasExpired();
    }

    public String nativeSymbol() {
        if (nativeSymbol != null && !nativeSymbol.isBlank()) {
            return nativeSymbol.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
        }
        return toDashSymbol();
    }

    public String displaySymbol() {
        if (displaySymbol != null && !displaySymbol.isBlank()) {
            return displaySymbol.trim().toUpperCase(Locale.ROOT);
        }
        if (nativeSymbol != null && !nativeSymbol.isBlank()) {
            return nativeSymbol.trim().toUpperCase(Locale.ROOT);
        }
        return toString('/');
    }

    public String underlyingCode() {
        if (underlyingCode != null && !underlyingCode.isBlank()) {
            return underlyingCode.trim().toUpperCase(Locale.ROOT);
        }
        return getBaseCode();
    }

    public String expiryCode() {
        return expiryCode == null ? "" : expiryCode.trim().toUpperCase(Locale.ROOT);
    }

    public String toExchangeSymbol(String exchangeId) {
        if (isNativeProductSymbol()) {
            return nativeSymbol();
        }
        String normalizedExchange = exchangeId == null ? "" : exchangeId.trim().toLowerCase(Locale.ROOT);
        if ("coinbase".equals(normalizedExchange) || "coinbase_derivatives".equals(normalizedExchange)) {
            return nativeSymbol();
        }
        return toDashSymbol();
    }

    public String toDisplayString() {
        return displaySymbol();
    }

    public LocalDate contractExpiryDate() {
        return contractExpiryDate;
    }

    public boolean canDisplayMarketData() {
        return true;
    }

    public boolean canSubmitOrders() {
        return !isNativeProductSymbol();
    }

    public boolean canBotTrade() {
        return canSubmitOrders();
    }

    public String getBaseCode() {
        return codeOf(baseCurrency);
    }

    public String getCounterCode() {
        return codeOf(counterCurrency);
    }

    public double getMidPrice() {
        if (bid > 0 && ask > 0) {
            return (bid + ask) / 2.0;
        }

        if (last > 0) {
            return last;
        }

        return Math.max(bid, ask);
    }

    /**
     * Get the last traded price. Falls back to mid-price if last price is not
     * available.
     */
    public double getLastPrice() {
        return last > 0 ? last : getMidPrice();
    }

    public double getSpread() {
        if (bid <= 0 || ask <= 0) {
            return 0.0;
        }

        return Math.abs(ask - bid);
    }

    public boolean hasQuote() {
        return bid > 0 || ask > 0 || last > 0;
    }

    public void updateQuote(double bid, double ask) {
        this.bid = sanitizeMarketValue(bid);
        this.ask = sanitizeMarketValue(ask);

        if (this.bid > 0 && this.ask > 0) {
            this.last = getMidPrice();
        }

        this.updatedAt = Instant.now();
    }

    public void updateTicker(
            double bid,
            double ask,
            double last,
            double volume,
            double changePercent) {
        this.bid = sanitizeMarketValue(bid);
        this.ask = sanitizeMarketValue(ask);
        this.last = sanitizeMarketValue(last);
        this.volume = sanitizeMarketValue(volume);
        this.changePercent = sanitizeFinite(changePercent);
        this.updatedAt = Instant.now();
    }

    public void updateTicker(
            double bid,
            double ask,
            double last,
            double volume,
            double changePercent,
            double high24h,
            double low24h) {
        updateTicker(bid, ask, last, volume, changePercent);
        this.high24h = sanitizeMarketValue(high24h);
        this.low24h = sanitizeMarketValue(low24h);
        this.updatedAt = Instant.now();
    }

    private double sanitizeMarketValue(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0.0;
        }

        return value;
    }

    private double sanitizeFinite(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }

        return value;
    }

    @Override
    public String toString() {
        return toSlashSymbol();
    }

    public void setId(long id) {
        this.id = Math.max(0, id);
    }

    public void setBid(double bid) {
        this.bid = sanitizeMarketValue(bid);
        this.updatedAt = Instant.now();
    }

    public void setAsk(double ask) {
        this.ask = sanitizeMarketValue(ask);
        this.updatedAt = Instant.now();
    }

    public void setLast(double last) {
        this.last = sanitizeMarketValue(last);
        this.updatedAt = Instant.now();
    }

    public void setVolume(double volume) {
        this.volume = sanitizeMarketValue(volume);
        this.updatedAt = Instant.now();
    }

    public void setChangePercent(double changePercent) {
        this.changePercent = sanitizeFinite(changePercent);
        this.updatedAt = Instant.now();
    }

    /**
     * Compatibility alias for older UI code.
     */
    public int getChange() {
        return (int) Math.round(changePercent);
    }

    public void setHigh24h(double high24h) {
        this.high24h = sanitizeMarketValue(high24h);
        this.updatedAt = Instant.now();
    }

    public void setLow24h(double low24h) {
        this.low24h = sanitizeMarketValue(low24h);
        this.updatedAt = Instant.now();
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    /**
     * Deprecated: TradePair should not fetch ticker data.
     * Use Exchange#getLivePrice(...) or Exchange#fetchTicker(...).
     */
    @Deprecated
    public List<Ticker> getTicker() {
        return List.of();
    }

    /**
     * Deprecated: TradePair should not fetch order book data.
     * Use Exchange#fetchOrderBook(...).
     */
    @Deprecated
    public List<OrderBook> getOrderBook() {
        return List.of();
    }

    public void setChange(double change) {
        this.changePercent = sanitizeFinite(change);
    }

    /**
     * Real asset class for this pair (set by InstrumentMetadataService or exchange
     * adapter).
     * Defaults to DERIVATIVE if not set.
     */
    private AssetClass assetClass = AssetClass.DERIVATIVE;

    /**
     * Real contract type for this pair (set by InstrumentMetadataService or
     * exchange adapter).
     * Defaults to SPOT if not set.
     */
    private ContractType contractType = ContractType.SPOT;

    private LiquidityProfile liquidityProfile = LiquidityProfile.NORMAL;

    public boolean isTradableByLiquidity() {
        return liquidityProfile != null && liquidityProfile.isTradable();
    }

    public double liquidityAdjustedSize(double requestedSize) {
        return requestedSize
                * Objects.requireNonNullElse(liquidityProfile, LiquidityProfile.NORMAL).getSizeMultiplier();
    }

    private InstrumentTradingSession tradingSession;

    public TradingSessionStatus getTradingSessionStatus() {
        if (tradingSession == null) {
            return isCryptoPair() ? TradingSessionStatus.OPEN : TradingSessionStatus.UNKNOWN;
        }

        return tradingSession.getStatus(ZonedDateTime.now());
    }

    private boolean isCryptoPair() {
        return baseCurrency instanceof CryptoCurrency
                || counterCurrency instanceof CryptoCurrency;
    }

    /**
     * Check if instrument is tradable during its market session.
     * Returns true if no session is defined (assume tradable).
     */
    public boolean isTradableNow() {
        if (isCryptoPair() || tradingSession == null) {
            return true; // Assume tradable if no session rules defined
        }

        return tradingSession.isTradableNow(ZonedDateTime.now());
    }

    /**
     * Set the asset class for this pair.
     * Used by InstrumentMetadataService to enrich data.
     */
    public void setAssetClass(AssetClass assetClass) {
        this.assetClass = assetClass != null ? assetClass : AssetClass.DERIVATIVE;
    }

    /**
     * Set the contract type for this pair.
     * Used by InstrumentMetadataService to enrich data.
     */
    public void setContractType(ContractType contractType) {
        this.contractType = contractType != null ? contractType : ContractType.SPOT;
    }

}
