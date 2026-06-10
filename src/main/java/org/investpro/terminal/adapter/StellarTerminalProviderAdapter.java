package org.investpro.terminal.adapter;

import org.investpro.exchange.PairQuality;
import org.investpro.exchange.stellar.StellarAssetIdentity;
import org.investpro.exchange.stellar.StellarNetwork;
import org.investpro.exchange.stellar.StellarPairIdentity;
import org.investpro.models.trading.TradePair;
import org.investpro.terminal.domain.Asset;
import org.investpro.terminal.domain.AssetClass;
import org.investpro.terminal.domain.Instrument;
import org.investpro.terminal.domain.TradingStatus;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StellarTerminalProviderAdapter extends ExchangeTerminalProviderAdapter {

    private final StellarNetwork stellarNetwork;

    public StellarTerminalProviderAdapter(StellarNetwork stellarNetwork) {
        super(stellarNetwork);
        this.stellarNetwork = stellarNetwork;
    }

    @Override
    public Set<AssetClass> supportedAssetClasses() {
        return Set.of(AssetClass.CRYPTO_STELLAR);
    }

    @Override
    protected Instrument instrumentForPair(TradePair pair) {
        Optional<StellarPairIdentity> resolvedPair = stellarNetwork.resolvePairIdentity(pair);
        Optional<PairQuality> quality = resolvedPair.flatMap(stellarNetwork::evaluatePairWithInversion);

        if (resolvedPair.isEmpty()) {
            return super.instrumentForPair(pair);
        }

        StellarPairIdentity identity = resolvedPair.get();
        Asset baseAsset = stellarAsset(identity.base());
        Asset quoteAsset = stellarAsset(identity.quote());
        Map<String, Object> metadata = stellarMetadata(pair, identity, quality);
        boolean tradable = quality.map(PairQuality::tradeable).orElse(false);

        return new Instrument(
                TerminalExchangeMapper.instrumentId(providerId(), pair),
                baseAsset,
                quoteAsset,
                identity.displaySymbol(),
                AssetClass.CRYPTO_STELLAR,
                exchange.getDisplayName(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                tradable ? TradingStatus.ACTIVE : TradingStatus.VIEW_ONLY,
                false,
                false,
                true,
                true,
                metadata);
    }

    @Override
    protected Map<String, Object> instrumentMetadata(TradePair pair) {
        Optional<StellarPairIdentity> resolvedPair = stellarNetwork.resolvePairIdentity(pair);
        return resolvedPair
                .map(identity -> stellarMetadata(pair, identity, stellarNetwork.evaluatePairWithInversion(identity)))
                .orElseGet(() -> super.instrumentMetadata(pair));
    }

    private Asset stellarAsset(StellarAssetIdentity asset) {
        return new Asset(
                asset.code(),
                asset.code(),
                AssetClass.CRYPTO_STELLAR,
                asset.issuer(),
                asset.homeDomain());
    }

    private Map<String, Object> stellarMetadata(
            TradePair pair,
            StellarPairIdentity identity,
            Optional<PairQuality> quality
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>(TerminalExchangeMapper.baseMetadata(pair, providerId()));
        metadata.put("stellar.baseIssuer", identity.base().issuer());
        metadata.put("stellar.quoteIssuer", identity.quote().issuer());
        metadata.put("stellar.baseHomeDomain", identity.base().homeDomain());
        metadata.put("stellar.quoteHomeDomain", identity.quote().homeDomain());
        metadata.put("stellar.baseCanonicalKey", identity.base().canonicalKey());
        metadata.put("stellar.quoteCanonicalKey", identity.quote().canonicalKey());
        metadata.put("stellar.canonicalPairKey", identity.canonicalKey());
        metadata.put("stellar.baseTrusted", identity.base().trusted());
        metadata.put("stellar.quoteTrusted", identity.quote().trusted());
        metadata.put("stellar.baseTrustlineRequired", !identity.base().isNative());
        metadata.put("stellar.quoteTrustlineRequired", !identity.quote().isNative());
        metadata.put("stellar.issuerAware", true);
        metadata.put("stellar.marketWatchEligible", true);

        quality.ifPresent(pairQuality -> {
            metadata.put("stellar.tradeable", pairQuality.tradeable());
            metadata.put("stellar.usingInvertedOrderBook", pairQuality.inverted());
            metadata.put("stellar.bestBid", pairQuality.bestBid());
            metadata.put("stellar.bestAsk", pairQuality.bestAsk());
            metadata.put("stellar.spreadPercent", pairQuality.spreadPercent());
            metadata.put("stellar.bidDepth", pairQuality.bidDepth());
            metadata.put("stellar.askDepth", pairQuality.askDepth());
            metadata.put("stellar.liquidityReason", pairQuality.reason());
        });

        return metadata;
    }
}
