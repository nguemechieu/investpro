package org.investpro.asset;

import org.investpro.models.trading.OpenOrder;
import org.investpro.trading.tradability.SymbolTradability;

import java.math.BigDecimal;
import java.util.Optional;

public final class TradabilityService {
    private final LocalAssetRepository repository;

    public TradabilityService(LocalAssetRepository repository) {
        this.repository = repository;
    }

    public OrderTradabilityDecision validateOrder(
            OrderTradabilityRequest request,
            SymbolTradability liveStatus,
            boolean stellarTrustlineExists) {
        if (request == null) {
            return OrderTradabilityDecision.block("Order request is missing");
        }
        if (!request.exchangeConnected()) {
            return OrderTradabilityDecision.block("Exchange connection is not active");
        }
        if (!request.sessionOpen()) {
            return OrderTradabilityDecision.block("Exchange session is closed for order submission");
        }
        if (liveStatus == null) {
            return OrderTradabilityDecision.block("Live tradability check did not return a result");
        }
        if (!liveStatus.orderSubmissionAllowed()) {
            return OrderTradabilityDecision.block(liveStatus.reason().isBlank()
                    ? "Live exchange status does not allow order submission"
                    : liveStatus.reason());
        }
        if (!isOrderTypeAllowed(request.orderType(), liveStatus)) {
            return OrderTradabilityDecision.block("Requested order type is not supported for this symbol");
        }

        Optional<AssetCatalogEntry> localAsset = repository.findById(
                AssetCatalogEntry.canonicalId(request.exchangeId(), request.symbol(), "", "", ""));
        if (localAsset.isEmpty()) {
            localAsset = repository.findByExchange(request.exchangeId()).stream()
                    .filter(asset -> asset.symbol().equalsIgnoreCase(request.symbol()))
                    .findFirst();
        }

        if (localAsset.isPresent()) {
            OrderTradabilityDecision localDecision = validateLocalConstraints(request, localAsset.get(), stellarTrustlineExists);
            if (!localDecision.allowed()) {
                return localDecision;
            }
        }
        return OrderTradabilityDecision.allow();
    }

    private OrderTradabilityDecision validateLocalConstraints(
            OrderTradabilityRequest request,
            AssetCatalogEntry asset,
            boolean stellarTrustlineExists) {
        if (asset.status() == AssetStatus.INACTIVE || asset.status() == AssetStatus.DELISTED) {
            return OrderTradabilityDecision.block("Local asset catalog marks this symbol as " + asset.status());
        }
        if (request.liveMode() && !asset.supportsLiveTrading()) {
            return OrderTradabilityDecision.block("Symbol does not support live trading in the local catalog");
        }
        if (!request.liveMode() && !asset.supportsPaperTrading()) {
            return OrderTradabilityDecision.block("Symbol does not support paper trading in the local catalog");
        }
        if (asset.requiresTrustline() && !stellarTrustlineExists) {
            return OrderTradabilityDecision.block("Stellar trustline is required before trading this asset");
        }
        if (asset.minOrderSize() != null && request.quantity().compareTo(asset.minOrderSize()) < 0) {
            return OrderTradabilityDecision.block("Order quantity is below the minimum size");
        }
        if (asset.maxOrderSize() != null && request.quantity().compareTo(asset.maxOrderSize()) > 0) {
            return OrderTradabilityDecision.block("Order quantity is above the maximum size");
        }
        if (asset.quantityIncrement() != null && asset.quantityIncrement().compareTo(BigDecimal.ZERO) > 0
                && request.quantity().remainder(asset.quantityIncrement()).compareTo(BigDecimal.ZERO) != 0) {
            return OrderTradabilityDecision.block("Order quantity does not match the required increment");
        }
        return OrderTradabilityDecision.allow();
    }

    private boolean isOrderTypeAllowed(OpenOrder.OrderType orderType, SymbolTradability status) {
        if (orderType == null) {
            return false;
        }
        return switch (orderType) {
            case MARKET -> status.marketOrderAllowed();
            case LIMIT -> status.limitOrderAllowed();
            case STOP_LOSS, TAKE_PROFIT, STOP_LIMIT, TRAILING_STOP -> status.stopOrderAllowed();
        };
    }
}
