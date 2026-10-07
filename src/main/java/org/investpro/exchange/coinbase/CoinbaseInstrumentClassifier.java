package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentType;
import org.investpro.models.market.MarketInstrument;
import java.util.Map;

/** Legacy metadata bridge using the same product classifications as the market catalog. */
public class CoinbaseInstrumentClassifier {
    private static final ObjectMapper JSON = new ObjectMapper();
    public record Classification(InstrumentType instrumentType, BrokerVenue venue, String baseAsset,
                                 String quoteAsset, String settlementAsset) { }

    public Classification classify(String productId, Map<String, Object> productMetadata) {
        ObjectNode product = JSON.valueToTree(productMetadata == null ? Map.of() : productMetadata);
        product.put("product_id", productId);
        CoinbaseProductSymbolParser parser = new CoinbaseProductSymbolParser();
        if (!product.has("product_type") && productId != null && !parser.isNativeDerivativeSymbol(productId)
                && productId.matches("[A-Za-z0-9]+-[A-Za-z0-9]+")) product.put("product_type", "SPOT");
        MarketInstrument instrument = new CoinbaseMarketInstrumentMapper().map(product);
        boolean future = instrument.isFuture(), perpetual = instrument.isPerpetual();
        InstrumentType type = switch (instrument.assetClass()) {
            case CRYPTO -> perpetual ? InstrumentType.CRYPTO_PERPETUAL : future ? InstrumentType.CRYPTO_FUTURE
                    : instrument.isSpot() ? InstrumentType.CRYPTO_SPOT : InstrumentType.UNKNOWN;
            case EQUITY -> perpetual ? InstrumentType.STOCK_PERPETUAL : future ? InstrumentType.STOCK_FUTURE : InstrumentType.STOCK_SPOT;
            case INDEX -> perpetual ? InstrumentType.INDEX_PERPETUAL : future ? InstrumentType.INDEX_FUTURE : InstrumentType.UNKNOWN;
            case METAL -> perpetual ? InstrumentType.METAL_PERPETUAL : future ? InstrumentType.METAL_FUTURE : InstrumentType.METAL_SPOT;
            case COMMODITY -> perpetual ? InstrumentType.COMMODITY_PERPETUAL : future ? InstrumentType.COMMODITY_FUTURE : InstrumentType.UNKNOWN;
            case FIAT -> instrument.isSpot() ? InstrumentType.FOREX_SPOT : InstrumentType.UNKNOWN;
            default -> InstrumentType.UNKNOWN;
        };
        String venue = instrument.routingExchange();
        BrokerVenue route = instrument.isSpot() ? BrokerVenue.COINBASE_SPOT
                : venue.equalsIgnoreCase("INTX") ? BrokerVenue.COINBASE_INTERNATIONAL_PERPETUALS
                : venue.equalsIgnoreCase("COINBASE_DERIVATIVES") || venue.equalsIgnoreCase("FCM")
                    || venue.equalsIgnoreCase("US Derivatives") ? BrokerVenue.COINBASE_US_FUTURES : BrokerVenue.UNKNOWN;
        return new Classification(type, route, instrument.baseAsset(), instrument.quoteAsset(), instrument.marginAsset());
    }
}