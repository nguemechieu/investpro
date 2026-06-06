package org.investpro.asset;

import org.investpro.exchange.Exchange;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface ExchangeAssetDiscoveryService {
    CompletableFuture<List<AssetCatalogEntry>> discover(Exchange exchange, ExchangeId exchangeId);
}
