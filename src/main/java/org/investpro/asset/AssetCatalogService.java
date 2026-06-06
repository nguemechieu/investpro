package org.investpro.asset;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;
import org.investpro.models.trading.TradePair;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Slf4j
public final class AssetCatalogService {
    private final LocalAssetRepository repository;
    private final AssetRefreshScheduler refreshScheduler;

    public AssetCatalogService(LocalAssetRepository repository, AssetRefreshScheduler refreshScheduler) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.refreshScheduler = Objects.requireNonNull(refreshScheduler, "refreshScheduler must not be null");
    }

    public List<AssetCatalogEntry> loadAssets(ExchangeId exchangeId) {
        return repository.findByExchange(exchangeId).stream()
                .filter(asset -> asset.status() != AssetStatus.DELISTED)
                .toList();
    }

    public List<TradePair> loadMarketWatchPairs(ExchangeId exchangeId) {
        List<TradePair> pairs = new ArrayList<>();
        loadAssets(exchangeId).stream()
                .filter(asset -> asset.status() == AssetStatus.ACTIVE || asset.status() == AssetStatus.UNKNOWN)
                .forEach(asset -> addDisplayPairs(pairs, asset));
        return pairs.stream().filter(Objects::nonNull).distinct().toList();
    }

    public boolean isStaleOrMissing(ExchangeId exchangeId) {
        return refreshScheduler.isStaleOrMissing(exchangeId);
    }

    public CompletableFuture<AssetCatalogMergeResult> refreshIfStale(Exchange exchange) {
        ExchangeId exchangeId = exchangeId(exchange);
        return refreshScheduler.refreshIfStale(exchange, exchangeId);
    }

    public CompletableFuture<AssetCatalogMergeResult> refreshNow(Exchange exchange) {
        ExchangeId exchangeId = exchangeId(exchange);
        return refreshScheduler.refresh(exchange, exchangeId, true);
    }

    public void addManualAsset(AssetCatalogEntry asset) {
        repository.upsert(asset);
    }

    public static ExchangeId exchangeId(Exchange exchange) {
        if (exchange == null) {
            return ExchangeId.UNKNOWN;
        }
        ExchangeId fromId = ExchangeId.from(exchange.getExchangeId());
        if (fromId != ExchangeId.UNKNOWN) {
            return fromId;
        }
        return ExchangeId.from(exchange.getName() + " " + exchange.getDisplayName());
    }

    private void addDisplayPairs(List<TradePair> pairs, AssetCatalogEntry asset) {
        try {
            pairs.add(asset.toTradePair());
            if (asset.exchangeId() == ExchangeId.STELLAR && asset.reversedPairSupported()) {
                TradePair reversed = TradePair.of(asset.quoteAsset(), asset.baseAsset());
                reversed.setNativeSymbol(asset.quoteAsset() + "/" + asset.baseAsset());
                pairs.add(reversed);
            }
        } catch (RuntimeException | SQLException | ClassNotFoundException exception) {
            log.debug("Skipping local asset {}: {}", asset.id(), exception.getMessage());
        }
    }
}
