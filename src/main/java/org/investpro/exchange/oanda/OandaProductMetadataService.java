package org.investpro.exchange.oanda;

import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentMetadata;
import org.investpro.exchange.core.InstrumentType;
import org.investpro.exchange.core.MarketType;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * OandaProductMetadataService - Fetches and caches OANDA instrument metadata.
 * When an {@link OandaInstrumentSpec} is available (parsed from the instruments API),
 * its values take precedence over any hardcoded fallbacks.
 */
public class OandaProductMetadataService {
    private static final Logger logger = Logger.getLogger(OandaProductMetadataService.class.getName());
    private final Map<String, InstrumentMetadata> metadataCache;
    private final OandaInstrumentClassifier classifier;

    /** Optional supplier of live instrument specs; may be null if Oanda instance not yet wired. */
    private Function<String, OandaInstrumentSpec> specSupplier;

    public OandaProductMetadataService() {
        this.metadataCache = new HashMap<>();
        this.classifier = new OandaInstrumentClassifier();
    }

    /**
     * Wire a live spec supplier so that metadata derived from the instruments API is used.
     * Typically called from the Oanda exchange adapter after the spec cache is populated.
     *
     * @param specSupplier function that maps an OANDA instrument name -> its spec, or null if unknown
     */
    public void setSpecSupplier(Function<String, OandaInstrumentSpec> specSupplier) {
        this.specSupplier = specSupplier;
        metadataCache.clear(); // invalidate stale entries built without live data
    }

    /**
     * Get metadata for an instrument, fetching from API if not cached.
     */
    public InstrumentMetadata getMetadata(String instrumentName) {
        if (metadataCache.containsKey(instrumentName)) {
            return metadataCache.get(instrumentName);
        }

        InstrumentMetadata metadata = fetchFromApi(instrumentName);
        if (metadata != null) {
            metadataCache.put(instrumentName, metadata);
        }
        return metadata;
    }

    private InstrumentMetadata fetchFromApi(String instrumentName) {
        OandaInstrumentClassifier.Classification classification =
            classifier.classify(instrumentName, new HashMap<>());

        if (classification.instrumentType() == InstrumentType.UNKNOWN) {
            return null;
        }

        // Use live spec when available
        OandaInstrumentSpec spec = specSupplier != null ? specSupplier.apply(instrumentName) : null;

        BigDecimal priceIncrement  = spec != null ? spec.priceIncrement()    : fallbackPriceIncrement(instrumentName);
        BigDecimal minQuantity     = spec != null ? spec.minimumTradeSize()  : BigDecimal.ONE;
        BigDecimal maxLeverage     = spec != null ? spec.effectiveLeverage() : fallbackLeverage(classification.instrumentType());
        String     displayName     = spec != null ? spec.displayName()       : instrumentName;

        InstrumentMetadata meta = new InstrumentMetadata(
            "OANDA",
            instrumentName,
            displayName,
            BrokerVenue.OANDA_FX_CFD,
            classification.assetClass(),
            MarketType.CFD,
            classification.instrumentType(),
            classification.baseAsset(),
            classification.quoteAsset(),
            classification.settlementAsset(),
            priceIncrement,
            BigDecimal.ONE,   // quantity increment (always 1 unit for OANDA)
            minQuantity,
            BigDecimal.ZERO,  // no minimum notional enforced by OANDA
            maxLeverage,
            true,
            false
        );

        if (spec != null) {
            meta.putMetadata("pipLocation",                 spec.pipLocation());
            meta.putMetadata("displayPrecision",            spec.displayPrecision());
            meta.putMetadata("tradeUnitsPrecision",         spec.tradeUnitsPrecision());
            meta.putMetadata("marginRate",                  spec.marginRate());
            meta.putMetadata("maximumOrderUnits",           spec.maximumOrderUnits());
            meta.putMetadata("maximumPositionSize",         spec.maximumPositionSize());
            meta.putMetadata("maximumTrailingStopDistance", spec.maximumTrailingStopDistance());
            meta.putMetadata("minimumTrailingStopDistance", spec.minimumTrailingStopDistance());
            meta.putMetadata("instrumentType",              spec.type());
        }

        return meta;
    }

    private @NonNull BigDecimal fallbackPriceIncrement(@NonNull String instrumentName) {
        if (instrumentName.startsWith("X")) {
            return BigDecimal.valueOf(0.01);      // Metals
        } else if (instrumentName.contains("_USD")) {
            return BigDecimal.valueOf(0.0001);    // USD pairs (4-decimal pip)
        } else {
            return BigDecimal.valueOf(0.00001);   // Other FX (5-decimal)
        }
    }

    @Contract(pure = true)
    private BigDecimal fallbackLeverage(@NonNull InstrumentType instrumentType) {
        return switch (instrumentType) {
            case FOREX_CFD -> BigDecimal.valueOf(50);
            case METAL_CFD, INDEX_CFD, STOCK_CFD -> BigDecimal.valueOf(20);
            case COMMODITY_CFD -> BigDecimal.valueOf(10);
            default -> BigDecimal.ONE;
        };
    }

    public void clearCache() {
        metadataCache.clear();
    }
}
