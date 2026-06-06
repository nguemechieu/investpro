package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.JsonNode;
import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractExpiryType;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.LeverageMode;
import org.investpro.models.market.MarketInstrument;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.TradingEnvironment;
import org.investpro.models.market.UnderlyingType;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

public class CoinbaseMarketInstrumentMapper {

    private static final String COINBASE_DERIVATIVES_VENUE = CoinbaseProductSymbolParser.COINBASE_DERIVATIVES_VENUE;

    private final String exchangeId;
    private final Function<TradePair, SymbolTradability> tradabilityResolver;
    private final CoinbaseProductSymbolParser productSymbolParser = new CoinbaseProductSymbolParser();

    public CoinbaseMarketInstrumentMapper() {
        this("COINBASE", null);
    }

    public CoinbaseMarketInstrumentMapper(
            String exchangeId,
            Function<TradePair, SymbolTradability> tradabilityResolver) {
        this.exchangeId = exchangeId == null || exchangeId.isBlank() ? "COINBASE" : exchangeId;
        this.tradabilityResolver = tradabilityResolver;
    }

    public MarketInstrument map(JsonNode product) {
        if (product == null || product.isNull() || product.isMissingNode()) {
            return unknownInstrument();
        }

        JsonNode details = product.path("future_product_details");
        String nativeSymbol = firstText(product, "product_id", "id", "symbol");
        String productType = firstText(product, "product_type");
        boolean derivativeSymbol = isDerivativeProductId(nativeSymbol);
        String expiryTypeText = firstText(product, "contract_expiry_type");
        if (expiryTypeText.isBlank()) {
            expiryTypeText = firstText(details, "contract_expiry_type");
        }
        if (expiryTypeText.isBlank() && derivativeSymbol) {
            expiryTypeText = nativeSymbol.toUpperCase(Locale.ROOT).contains("-PERP")
                    ? "PERPETUAL"
                    : "EXPIRING";
        }
        String underlyingTypeText = firstText(product, "futures_underlying_type");
        if (underlyingTypeText.isBlank()) {
            underlyingTypeText = firstText(details, "futures_underlying_type", "underlying_type");
        }

        ContractExpiryType contractExpiryType = parseContractExpiryType(expiryTypeText);
        UnderlyingType underlyingType = parseUnderlyingType(underlyingTypeText);
        MarketType marketType = classifyMarketType(productType, expiryTypeText, underlyingTypeText);
        InstrumentType instrumentType = classifyInstrumentType(productType, expiryTypeText, underlyingTypeText);
        ContractType contractType = classifyContractType(productType, expiryTypeText);
        LeverageMode leverageMode = classifyLeverageMode(marketType, instrumentType, contractType);
        AssetClass assetClass = classifyAssetClass(productType, underlyingTypeText);

        String baseAsset = firstText(product, "base_currency_id", "base_currency", "base_currency_code", "base_name");
        if (baseAsset.isBlank()) {
            baseAsset = firstText(details, "contract_root_unit", "underlying_asset", "base_asset");
        }
        if (baseAsset.isBlank() && derivativeSymbol) {
            baseAsset = underlyingFromDerivativeProductId(nativeSymbol);
        }

        String quoteAsset = firstText(product, "quote_currency_id", "quote_currency", "quote_currency_code");
        if (quoteAsset.isBlank() && (marketType == MarketType.SPOT || contractType == ContractType.PERPETUAL)) {
            quoteAsset = "USD";
        }

        String displaySymbol = firstText(product, "display_name", "display_symbol");
        if (displaySymbol.isBlank()) {
            displaySymbol = firstText(details, "group_description", "contract_display_name");
        }
        if (displaySymbol.isBlank()) {
            displaySymbol = nativeSymbol;
        }

        TradePair tradePair = tryCreateTradePair(product, nativeSymbol, baseAsset, quoteAsset, contractType);
        SymbolTradability tradability = tradabilityResolver == null || tradePair == null
                ? null
                : tradabilityResolver.apply(tradePair);
        Instant contractExpiry = parseInstant(firstText(details, "contract_expiry"));
        if (contractExpiry == null && derivativeSymbol) {
            contractExpiry = parseCdeExpiry(nativeSymbol);
        }

        return new MarketInstrument(
                exchangeId,
                normalize(nativeSymbol),
                displaySymbol,
                tradePair,
                assetClass,
                marketType,
                instrumentType,
                leverageMode,
                contractType,
                normalize(productType),
                contractExpiryType,
                underlyingType,
                TradingEnvironment.UNKNOWN,
                parseRoutingExchange(product, derivativeSymbol),
                normalize(baseAsset),
                normalize(quoteAsset),
                firstText(product, "quote_currency_id", "quote_currency", "margin_asset", "settlement_currency"),
                normalize(firstNonBlank(baseAsset, firstText(details, "underlying_asset", "contract_root_unit"))),
                firstText(details, "contract_code"),
                firstText(details, "contract_size"),
                contractExpiry,
                leverageMode.isLeveraged(),
                contractExpiryType == ContractExpiryType.EXPIRING,
                marketType == MarketType.SPOT,
                contractType.isDerivative(),
                tradability,
                rawMetadata(product));
    }

    public List<MarketInstrument> mapAll(JsonNode productsRoot) {
        JsonNode products = productsRoot == null ? null
                : productsRoot.has("products") ? productsRoot.get("products") : productsRoot;
        if (products == null || !products.isArray()) {
            return List.of();
        }

        List<MarketInstrument> instruments = new ArrayList<>();
        for (JsonNode product : products) {
            instruments.add(map(product));
        }
        return instruments;
    }

    MarketType classifyMarketType(String productType, String contractExpiryType, String futuresUnderlyingType) {
        String product = normalize(productType);

        if ("SPOT".equals(product)) {
            return MarketType.SPOT;
        }
        if ("FUTURE".equals(product) || "PERPETUAL".equals(normalize(contractExpiryType))) {
            return MarketType.DERIVATIVES;
        }
        return MarketType.UNKNOWN;
    }

    InstrumentType classifyInstrumentType(String productType, String contractExpiryType, String futuresUnderlyingType) {
        String product = normalize(productType);
        String expiry = normalize(contractExpiryType);
        String underlying = normalize(futuresUnderlyingType);
        if ("SPOT".equals(product)) {
            return InstrumentType.SPOT;
        }
        if (!"FUTURE".equals(product)) {
            return InstrumentType.UNKNOWN;
        }
        if ("PERPETUAL".equals(expiry)) {
            return InstrumentType.PERPETUAL;
        }
        return switch (underlying) {
            case "INDEX" -> InstrumentType.INDEX;
            case "EQUITY" -> InstrumentType.STOCK;
            case "COMMODITY" -> InstrumentType.COMMODITY;
            case "FX" -> InstrumentType.FOREX;
            default -> InstrumentType.FUTURE;
        };
    }

    LeverageMode classifyLeverageMode(
            MarketType marketType,
            InstrumentType instrumentType,
            ContractType contractType) {
        if ((instrumentType != null && instrumentType.isDerivative())
                || (contractType != null && contractType.isDerivative())
                || marketType == MarketType.DERIVATIVES
                || marketType == MarketType.DERIVATIVE) {
            return LeverageMode.DERIVATIVE_LEVERAGE;
        }
        if (marketType == MarketType.MARGIN || contractType == ContractType.MARGIN) {
            return LeverageMode.MARGIN;
        }
        return LeverageMode.NONE;
    }

    ContractType classifyContractType(String productType, String contractExpiryType) {
        String product = normalize(productType);
        String expiry = normalize(contractExpiryType);
        if ("SPOT".equals(product)) {
            return ContractType.CASH;
        }
        if (!"FUTURE".equals(product)) {
            return ContractType.UNKNOWN;
        }
        return "PERPETUAL".equals(expiry) ? ContractType.PERPETUAL : ContractType.FUTURE;
    }

    AssetClass classifyAssetClass(String productType, String futuresUnderlyingType) {
        String product = normalize(productType);
        String underlying = normalize(futuresUnderlyingType);
        if ("SPOT".equals(product) || underlying.isBlank() || "SPOT".equals(underlying)) {
            return AssetClass.CRYPTO;
        }
        return switch (underlying) {
            case "INDEX" -> AssetClass.INDEX;
            case "EQUITY" -> AssetClass.EQUITY;
            case "COMMODITY" -> AssetClass.COMMODITY;
            case "FX" -> AssetClass.FIAT;
            default -> AssetClass.UNKNOWN;
        };
    }

    ContractExpiryType parseContractExpiryType(String value) {
        return switch (normalize(value)) {
            case "EXPIRING" -> ContractExpiryType.EXPIRING;
            case "PERPETUAL" -> ContractExpiryType.PERPETUAL;
            default -> ContractExpiryType.UNKNOWN;
        };
    }

    UnderlyingType parseUnderlyingType(String value) {
        return switch (normalize(value)) {
            case "SPOT" -> UnderlyingType.SPOT;
            case "INDEX" -> UnderlyingType.INDEX;
            case "EQUITY" -> UnderlyingType.EQUITY;
            case "COMMODITY" -> UnderlyingType.COMMODITY;
            case "FX" -> UnderlyingType.FX;
            default -> UnderlyingType.UNKNOWN;
        };
    }

    String parseRoutingExchange(JsonNode product) {
        return parseRoutingExchange(product, isDerivativeProductId(firstText(product, "product_id", "id", "symbol")));
    }

    String parseRoutingExchange(JsonNode product, boolean derivativeSymbol) {
        if (derivativeSymbol) {
            return COINBASE_DERIVATIVES_VENUE;
        }
        String venue = firstText(product.path("future_product_details"), "venue");
        if (!venue.isBlank()) {
            return venue.trim();
        }
        return "";
    }

    TradePair tryCreateTradePair(JsonNode product, String nativeSymbol, String base, String quote, ContractType contractType) {
        if (isDerivativeProductId(nativeSymbol)) {
            try {
                return productSymbolParser.parseProduct(nativeSymbol, product);
            } catch (SQLException | ClassNotFoundException | RuntimeException exception) {
                return null;
            }
        }
        if (base == null || base.isBlank() || quote == null || quote.isBlank()) {
            return null;
        }
        String baseCode = normalize(base);
        String quoteCode = normalize(quote);
        if (baseCode.equals(quoteCode)) {
            return null;
        }
        try {
            return productSymbolParser.parseProduct(firstNonBlank(nativeSymbol, baseCode + "-" + quoteCode), product);
        } catch (SQLException | ClassNotFoundException | RuntimeException exception) {
            return null;
        }
    }

    static boolean isDerivativeProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return false;
        }
        String normalized = normalize(productId).replace('/', '-').replace('_', '-');
        return normalized.endsWith("-CDE")
                || normalized.contains("-PERP")
                || Arrays.stream(normalized.split("-"))
                .anyMatch(part -> part.matches("\\d{2}[A-Z]{3}\\d{2}"));
    }

    static boolean isExpiringDerivativeProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return false;
        }
        String normalized = normalize(productId).replace('/', '-').replace('_', '-');
        return normalized.endsWith("-CDE")
                || Arrays.stream(normalized.split("-"))
                .anyMatch(part -> part.matches("\\d{2}[A-Z]{3}\\d{2}"));
    }

    static String underlyingFromDerivativeProductId(String productId) {
        if (productId == null || productId.isBlank()) {
            return "";
        }
        String normalized = normalize(productId).replace('/', '-').replace('_', '-');
        int dash = normalized.indexOf('-');
        return dash > 0 ? normalized.substring(0, dash) : normalized;
    }

    static Instant parseCdeExpiry(String productId) {
        if (productId == null || productId.isBlank()) {
            return null;
        }
        return new CoinbaseProductSymbolParser()
                .parseCdeExpiry(productId)
                .map(date -> date.atStartOfDay().toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private MarketInstrument unknownInstrument() {
        return new MarketInstrument(
                exchangeId,
                "",
                "",
                null,
                AssetClass.UNKNOWN,
                MarketType.UNKNOWN,
                InstrumentType.UNKNOWN,
                LeverageMode.UNKNOWN,
                ContractType.UNKNOWN,
                "",
                ContractExpiryType.UNKNOWN,
                UnderlyingType.UNKNOWN,
                TradingEnvironment.UNKNOWN,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                null,
                false,
                false,
                false,
                false,
                null,
                Map.of());
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

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception exception) {
            return null;
        }
    }

    private static Map<String, Object> rawMetadata(JsonNode node) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return metadata;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                continue;
            } else if (value.isValueNode()) {
                metadata.put(entry.getKey(), value.asText());
            } else {
                metadata.put(entry.getKey(), value.toString());
            }
        }
        return metadata;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
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
