package org.investpro.asset;

import org.investpro.models.trading.OpenOrder;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TradabilityServiceTest {
    @Test
    void blocksWhenLiveExchangeStatusDisallowsSubmission() throws Exception {
        TradabilityService service = new TradabilityService(new InMemoryLocalAssetRepository());
        OrderTradabilityDecision decision = service.validateOrder(
                request("BTC/USD", OpenOrder.OrderType.MARKET, "1"),
                live(false, true, true),
                true);

        assertFalse(decision.allowed());
    }

    @Test
    void blocksMissingStellarTrustline() {
        InMemoryLocalAssetRepository repository = new InMemoryLocalAssetRepository();
        repository.upsert(new AssetCatalogEntry(
                null, ExchangeId.STELLAR, "USDC/XLM", "USDC/XLM", "USDC", "XLM",
                AssetType.STELLAR_ASSET, AssetStatus.ACTIVE, TradabilityStatus.TRUSTLINE_REQUIRED,
                false, false, true, false, true, true, null, null, null, null,
                null, null, "GISSUER", "example.com", true, false, false, false,
                true, true, Instant.now(), Instant.now(), "{}"));

        TradabilityService service = new TradabilityService(repository);
        OrderTradabilityDecision decision = service.validateOrder(
                new OrderTradabilityRequest(ExchangeId.STELLAR, "USDC/XLM", OpenOrder.OrderType.LIMIT,
                        BigDecimal.ONE, true, true, true),
                live(true, false, true),
                false);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("trustline"));
    }

    @Test
    void blocksUnsupportedOrderType() {
        TradabilityService service = new TradabilityService(new InMemoryLocalAssetRepository());
        OrderTradabilityDecision decision = service.validateOrder(
                request("BTC/USD", OpenOrder.OrderType.MARKET, "1"),
                live(true, false, true),
                true);

        assertFalse(decision.allowed());
        assertTrue(decision.reason().contains("order type"));
    }

    private OrderTradabilityRequest request(String symbol, OpenOrder.OrderType type, String quantity) {
        return new OrderTradabilityRequest(ExchangeId.COINBASE, symbol, type, new BigDecimal(quantity), true, true, true);
    }

    private SymbolTradability live(boolean orderAllowed, boolean marketAllowed, boolean limitAllowed) {
        try {
            return new SymbolTradability(
                    "coinbase",
                    TradePair.fromSymbol("BTC-USD"),
                    "BTC-USD",
                    org.investpro.trading.tradability.TradabilityStatus.FULLY_TRADABLE,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    orderAllowed,
                    marketAllowed,
                    limitAllowed,
                    false,
                    false,
                    false,
                    false,
                    orderAllowed ? "ok" : "blocked",
                    Instant.now(),
                    Map.of());
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
