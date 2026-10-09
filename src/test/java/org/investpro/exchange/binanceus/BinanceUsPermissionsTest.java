package org.investpro.exchange.binanceus;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.investpro.models.trading.TradePair;
import org.investpro.trading.tradability.SymbolTradability;
import org.investpro.trading.tradability.TradabilityStatus;
import com.fasterxml.jackson.databind.JsonNode;
import static org.mockito.Mockito.*;

class BinanceUsPermissionsTest {
    private final ObjectMapper json = new ObjectMapper();

    private SymbolTradability map(String symbol, String account) throws Exception {
        var exchange = mock(BinanceUs.class);
        when(exchange.canSubmitLiveOrders()).thenReturn(true);
        when(exchange.getExchangeId()).thenReturn("binance-us");
        var pair = mock(TradePair.class);
        var method = BinanceUs.class.getDeclaredMethod("mapBinanceUsTradability", TradePair.class, JsonNode.class, JsonNode.class);
        method.setAccessible(true);
        return (SymbolTradability) method.invoke(exchange, pair,
                symbol == null ? null : json.readTree(symbol), account == null ? null : json.readTree(account));
    }

    @Test void advertisedSpotMarketIsVisibleButLiveTradingRequiresAccountPermission() throws Exception {
        String symbol = """
                {"symbol":"BTCUSDT","status":"TRADING","isSpotTradingAllowed":true,
                 "permissionSets":[["SPOT"]],"orderTypes":["LIMIT","MARKET"],"filters":[]}
                """;
        var unknown = map(symbol, null);
        assertTrue(unknown.canBeDisplayedInMarketWatch());
        assertFalse(unknown.liveTradingAllowed());
        var permitted = map(symbol, "{\"canTrade\":true,\"permissions\":[\"SPOT\"]}");
        assertTrue(permitted.isFullyTradable());
        assertTrue(permitted.marketOrderAllowed());
        var readOnly = map(symbol, "{\"canTrade\":false,\"permissions\":[\"SPOT\"]}");
        assertEquals(TradabilityStatus.PERMISSION_DENIED, readOnly.status());
        assertTrue(readOnly.canBeDisplayedInMarketWatch());
        assertFalse(readOnly.orderSubmissionAllowed());
    }

    @Test void haltedAndNonSpotSymbolsCannotSubmitOrders() throws Exception {
        String account = "{\"canTrade\":true,\"permissions\":[\"SPOT\"]}";
        var halted = map("{\"status\":\"HALT\",\"isSpotTradingAllowed\":true}", account);
        assertEquals(TradabilityStatus.HALTED, halted.status());
        assertFalse(halted.orderSubmissionAllowed());
        var nonSpot = map("{\"status\":\"TRADING\",\"isSpotTradingAllowed\":false}", account);
        assertEquals(TradabilityStatus.UNSUPPORTED_PRODUCT_TYPE, nonSpot.status());
        assertFalse(nonSpot.orderSubmissionAllowed());
    }

    @Test void upgradedPermissionSetsRequireEveryGroupButAllowAlternatives() throws Exception {
        var symbol = json.readTree("""
                {"permissions":[],"permissionSets":[["SPOT"],["TRD_GRP_004","TRD_GRP_005"]]}
                """);
        assertFalse(BinanceUsPermissions.allows(symbol,
                json.readTree("{\"canTrade\":true,\"permissions\":[\"SPOT\"]}")));
        assertTrue(BinanceUsPermissions.allows(symbol,
                json.readTree("{\"canTrade\":true,\"permissions\":[\"SPOT\",\"TRD_GRP_005\"]}")));
    }

    @Test void readOnlyAndUnknownAccountsCannotTradeAdvertisedSymbols() throws Exception {
        var symbol = json.readTree("{\"permissionSets\":[[\"SPOT\"]]}");
        assertFalse(BinanceUsPermissions.allows(symbol, null));
        assertFalse(BinanceUsPermissions.allows(symbol,
                json.readTree("{\"canTrade\":false,\"permissions\":[\"SPOT\"]}")));
        assertFalse(BinanceUsPermissions.allows(symbol, json.readTree("{}")));
    }

    @Test void legacyPermissionsRemainSupportedWithoutTreatingEmptyPermissionsAsDenial() throws Exception {
        var account = json.readTree("{\"canTrade\":true,\"permissions\":[\"SPOT\"]}");
        assertTrue(BinanceUsPermissions.allows(json.readTree("{\"permissions\":[\"SPOT\",\"MARGIN\"]}"), account));
        assertTrue(BinanceUsPermissions.allows(json.readTree("{\"permissions\":[]}"), account));
        assertFalse(BinanceUsPermissions.allows(json.readTree("{\"permissions\":[\"MARGIN\"]}"), account));
    }
}
