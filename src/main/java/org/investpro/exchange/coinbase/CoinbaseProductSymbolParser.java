package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.models.currency.CurrencyRegistry;
import org.investpro.models.market.MarketType;
import org.investpro.models.trading.TradePair;
import org.investpro.models.trading.TradeSymbolKind;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;

public final class CoinbaseProductSymbolParser {

    public static final String COINBASE_DERIVATIVES_VENUE = "COINBASE_DERIVATIVES";

    private static final DateTimeFormatter CDE_EXPIRY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("ddMMMyy")
            .toFormatter(Locale.US);

    public boolean isCdeFuture(String productId) {
        String normalized = normalize(productId);
        return normalized.endsWith("-CDE") || !expiryCode(normalized).isBlank();
    }

    public boolean isPerpetual(String productId) {
        return TradePair.isPerpetualProductSymbol(productId);
    }

    public boolean isPerpetual(String productId, JsonNode metadata) {
        JsonNode details = metadata == null ? null : metadata.path("future_product_details");
        return isPerpetual(productId)
                || "PERPETUAL".equalsIgnoreCase(firstText(metadata, "product_type"))
                || "PERPETUAL".equalsIgnoreCase(firstNonBlank(firstText(metadata, "contract_expiry_type"),
                        firstText(details, "contract_expiry_type")))
                || (normalize(productId).endsWith("-CDE") && !firstNonBlank(firstText(details, "funding_rate"),
                        firstText(details == null ? null : details.path("perpetual_details"), "funding_rate")).isBlank());
    }

    public boolean isNativeDerivativeSymbol(String productId) {
        String normalized = normalize(productId);
        return isCdeFuture(normalized) || isPerpetual(normalized);
    }

    public TradePair parseProduct(String productId, JsonNode metadata) throws SQLException, ClassNotFoundException {
        String nativeSymbol = normalize(firstNonBlank(productId, firstText(metadata, "product_id", "id", "symbol")));
        if (nativeSymbol.isBlank()) {
            throw new IllegalArgumentException("Coinbase product id must not be blank");
        }

        JsonNode details = metadata == null ? null : metadata.path("future_product_details");
        String productType = firstText(metadata, "product_type").toUpperCase(Locale.ROOT);
        boolean perpetual = isPerpetual(nativeSymbol, metadata);
        if (isNativeDerivativeSymbol(nativeSymbol) || "FUTURE".equals(productType) || "OPTION".equals(productType) || perpetual) {
            TradePair pair = TradePair.fromNativeProductSymbol(nativeSymbol);
            pair.setDisplaySymbol(nativeSymbol);
            pair.setNativeSymbol(nativeSymbol);
            pair.setExchangeId("coinbase");
            pair.setProductVenue(routingVenue(nativeSymbol, metadata));
            pair.setUnderlyingCode(firstNonBlank(
                    realCurrency(firstText(metadata, "base_currency_id", "base_currency", "base_currency_code")),
                    realCurrency(firstText(metadata == null ? null : metadata.path("future_product_details"),
                            "contract_root_unit", "underlying_asset", "base_asset")),
                    underlying(nativeSymbol)));

            String expiry = expiryCode(nativeSymbol);
            if (!expiry.isBlank()) {
                pair.setExpiryCode(expiry);
                pair.setContractExpiryDate(parseCdeExpiry(nativeSymbol).orElse(null));
            }

            if ("OPTION".equals(productType)) {
                pair.setSymbolKind(TradeSymbolKind.DERIVATIVE_CONTRACT);
                pair.setContractType(ContractType.OPTION);
                pair.setMarketType(MarketType.DERIVATIVES);
            } else if (perpetual) {
                pair.setSymbolKind(TradeSymbolKind.PERPETUAL_CONTRACT);
                pair.setContractType(ContractType.PERPETUAL);
                pair.setMarketType(MarketType.PERPETUAL);
            } else {
                pair.setSymbolKind(TradeSymbolKind.DERIVATIVE_CONTRACT);
                pair.setContractType(ContractType.FUTURE);
                pair.setMarketType(MarketType.FUTURE);
            }
            pair.setAssetClass(AssetClass.DERIVATIVE);
            return pair;
        }

        String base = realCurrency(firstText(metadata, "base_currency_id", "base_currency", "base_currency_code"));
        String quote = realCurrency(firstText(metadata, "quote_currency_id", "quote_currency", "quote_currency_code"));
        TradePair pair = TradePair.fromSymbol(!base.isBlank() && !quote.isBlank()
                ? base + "-" + quote
                : nativeSymbol);
        pair.setNativeSymbol(nativeSymbol);
        pair.setDisplaySymbol(base.isBlank() || quote.isBlank() ? nativeSymbol : base + "/" + quote);
        pair.setSymbolKind(TradeSymbolKind.PAIR);
        pair.setContractType(ContractType.SPOT);
        pair.setAssetClass(AssetClass.CRYPTO_ASSET);
        pair.setMarketType(MarketType.SPOT);
        pair.setExchangeId("coinbase");
        return pair;
    }

    /** Preserve explicit exchange metadata. A perpetual contract is not necessarily a US CDE product. */
    public static String routingVenue(String symbol, JsonNode metadata) {
        String venue = firstText(metadata, "product_venue");
        if (!venue.isBlank() && !venue.startsWith("UNKNOWN")) return venue;
        if (normalize(symbol).endsWith("-INTX")) return "INTX";
        if (normalize(symbol).endsWith("-CDE")) return COINBASE_DERIVATIVES_VENUE;
        return firstText(metadata == null ? null : metadata.path("future_product_details"), "venue");
    }

    public Optional<LocalDate> parseCdeExpiry(String productId) {
        String expiry = expiryCode(productId);
        if (expiry.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(expiry, CDE_EXPIRY_FORMATTER));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    public static String underlying(String productId) {
        String normalized = normalize(productId);
        int dash = normalized.indexOf('-');
        return dash > 0 ? normalized.substring(0, dash) : normalized;
    }

    public static String expiryCode(String productId) {
        String normalized = normalize(productId);
        if (normalized.isBlank()) {
            return "";
        }
        for (String part : normalized.split("-")) {
            if (part.matches("\\d{2}[A-Z]{3}\\d{2}")) {
                return part;
            }
        }
        return "";
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace('/', '-').replace('_', '-');
    }

    private static String realCurrency(String value) {
        String normalized = normalize(value);
        return CurrencyRegistry.isNonCurrencyToken(normalized) ? "" : normalized;
    }

    private static String firstText(JsonNode node, String... names) {
        if (node == null || node.isMissingNode() || names == null) {
            return "";
        }
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText("").trim();
            }
        }
        return "";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
