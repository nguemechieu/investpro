package org.investpro.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.enums.LiquidityProfile;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Service for enriching TradePair with detailed InstrumentMetadata.
 * Uses free/broker providers and enriched metadata from external sources.
 * Avoids hard-coding provider logic in UI panels.
 */
@Slf4j
public class InstrumentMetadataService {

    private final InstrumentRegistry registry;

    public InstrumentMetadataService(@NotNull InstrumentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Enrich a TradePair with metadata (synchronous).
     */
    @NotNull
    public InstrumentMetadata enrich(@NotNull TradePair tradePair, @NotNull String broker) {
        var existing = registry.get(tradePair);
        if (existing.isPresent()) {
            return existing.get();
        }

        AssetClass assetClass = inferAssetClass(tradePair, broker);
        InstrumentMetadata metadata = InstrumentMetadata.builder()
                .tradePair(tradePair)
                .broker(broker)
                .venue(inferVenue(broker, assetClass))
                .exchangeSymbol(tradePair.toCompactSymbol())
                .assetClass(assetClass)
                .contractType(ContractType.SPOT)
                .minOrderSize(assetClass == AssetClass.CRYPTO_ASSET ? 0.001 : 1.0)
                .maxOrderSize(assetClass == AssetClass.CRYPTO_ASSET ? 1_000_000 : 100_000)
                .tickSize(0.01)
                .lotSize(1.0)
                .pipSize(assetClass == AssetClass.DERIVATIVE ? 0.0001 : 0.01)
                .contractSize(assetClass == AssetClass.DERIVATIVE ? 100_000 : 1.0)
                .marginCurrency(tradePair.getCounterCode())
                .leverageLimit(1.0)
                .shortable(true)
                .tradable(true)
                .liquidityProfile(LiquidityProfile.NORMAL)
                .build();

        log.info("Enriched metadata for {}: assetClass={}", tradePair, assetClass);
        return metadata;
    }

    /**
     * Enrich a TradePair asynchronously.
     */
    @NotNull
    public CompletableFuture<InstrumentMetadata> enrichAsync(
            @NotNull TradePair tradePair,
            @NotNull String broker) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return enrich(tradePair, broker);
            } catch (Exception e) {
                log.error("Failed to enrich metadata for {}", tradePair, e);
                return createFallback(tradePair, broker);
            }
        });
    }

    /**
     * Create fallback metadata when enrichment fails.
     */
    @NotNull
    private InstrumentMetadata createFallback(@NotNull TradePair tradePair, @NotNull String broker) {
        return InstrumentMetadata.builder()
                .tradePair(tradePair)
                .broker(broker)
                .venue("UNKNOWN")
                .exchangeSymbol(tradePair.toCompactSymbol())
                .assetClass(AssetClass.DERIVATIVE)
                .contractType(ContractType.SPOT)
                .minOrderSize(0.01)
                .maxOrderSize(100_000)
                .tickSize(0.01)
                .lotSize(1.0)
                .pipSize(0.0001)
                .contractSize(100_000)
                .marginCurrency(tradePair.getCounterCode())
                .leverageLimit(1.0)
                .shortable(true)
                .tradable(true)
                .liquidityProfile(LiquidityProfile.NORMAL)
                .build();
    }

    /**
     * Infer asset class from pair naming conventions.
     */
    @NotNull
    private AssetClass inferAssetClass(@NotNull TradePair tradePair, @NotNull String broker) {
        if (isAlwaysOpenCryptoBroker(broker)) {
            return AssetClass.CRYPTO_ASSET;
        }

        String baseCode = tradePair.getBaseCode();
        if (isKnownCrypto(baseCode)) {
            return AssetClass.CRYPTO_ASSET;
        }
        if (isKnownFiat(baseCode) && isKnownFiat(tradePair.getCounterCode())) {
            return AssetClass.DERIVATIVE;
        }
        return AssetClass.EQUITY;
    }

    /**
     * Infer venue from asset class.
     */
    @NotNull
    private String inferVenue(@NotNull String broker, @NotNull AssetClass assetClass) {
        return switch (assetClass) {
            case CRYPTO_ASSET -> "SPOT";
            case DERIVATIVE, FIAT_CURRENCY -> "FOREX";
            case EQUITY, EQUITY_INDEX -> "EQUITIES";
            case COMMODITY -> "COMMODITIES";
            case FIXED_INCOME -> "BONDS";

            default -> "UNKNOWN";
        };
    }

    private boolean isKnownCrypto(@NotNull String code) {
        return code.matches("(BTC|ETH|BNB|SOL|XLM|XRP|ADA|DOGE|LTC|BCH|USDT|USDC|BUSD|DAI)");
    }

    private boolean isAlwaysOpenCryptoBroker(@NotNull String broker) {
        String normalized = broker.trim().toUpperCase();
        return normalized.contains("STELLAR") || normalized.contains("SOLANA");
    }

    private boolean isKnownFiat(@NotNull String code) {
        return code.matches("(USD|EUR|GBP|JPY|CHF|CAD|AUD|NZD|SGD|HKD|INR|ZAR|NOK|SEK|DKK|MXN|BRL)");
    }

    public void registerEnriched(@NotNull TradePair tradePair, @NotNull String broker) {
        InstrumentMetadata metadata = enrich(tradePair, broker);
        registry.register(metadata);
    }

    public void registerEnrichedAsync(
            @NotNull TradePair tradePair,
            @NotNull String broker,
            @NotNull Runnable onComplete) {
        enrichAsync(tradePair, broker).thenAccept(metadata -> {
            registry.register(metadata);
            onComplete.run();
        }).exceptionally(e -> {
            log.error("Failed to register enriched metadata for {}", tradePair, e);
            return null;
        });
    }
}
