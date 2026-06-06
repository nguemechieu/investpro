package org.investpro.ui.theme;

import org.investpro.models.market.AssetClass;
import org.investpro.models.market.ContractType;
import org.investpro.models.market.InstrumentType;
import org.investpro.models.market.LeverageMode;
import org.investpro.models.market.MarketType;
import org.investpro.models.market.ProductVenue;
import org.investpro.models.market.TradingEnvironment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketConfigurationTest {

    @Test
    void tradingModeNormalizesLiveValues() {
        MarketConfiguration configuration = configuration("LIVE TRADING");

        assertEquals("LIVE", configuration.tradingMode());
        assertEquals("LIVE", configuration.selectedTradingMode());
        assertFalse(configuration.isPaperTrading());
    }

    @Test
    void tradingModeDefaultsToPaper() {
        MarketConfiguration configuration = configuration(null);

        assertEquals("PAPER", configuration.tradingMode());
        assertEquals("PAPER", configuration.selectedTradingMode());
        assertTrue(configuration.isPaperTrading());
    }

    @Test
    void marketTypeNormalizesDisplayLabels() {
        assertEquals(MarketType.SPOT, configuration("Crypto Spot", "US", "coinbase", "PAPER").normalizedMarketType());
        assertEquals(MarketType.DERIVATIVES, configuration("Perpetuals", "International", "coinbase", "PAPER").normalizedMarketType());
        assertEquals(MarketType.DERIVATIVES, configuration("Futures", "US Derivatives", "coinbase", "PAPER").normalizedMarketType());
        assertEquals(MarketType.FOREX, configuration("Forex", "Global", "oanda", "PAPER").normalizedMarketType());
        assertEquals(MarketType.SECURITIES, configuration("Stocks", "US", "alpaca", "PAPER").normalizedMarketType());
    }

    @Test
    void assetClassAndContractTypeNormalizeDisplayLabels() {
        MarketConfiguration spot = configuration("Crypto Spot", "US", "coinbase", "PAPER");
        MarketConfiguration perp = configuration("Perpetuals", "International", "coinbase", "PAPER");
        MarketConfiguration stock = configuration("Stocks", "US", "alpaca", "PAPER");

        assertEquals(AssetClass.CRYPTO, spot.normalizedAssetClass());
        assertEquals(ContractType.CASH, spot.normalizedContractType());
        assertEquals(AssetClass.CRYPTO, perp.normalizedAssetClass());
        assertEquals(ContractType.PERPETUAL, perp.normalizedContractType());
        assertEquals(AssetClass.EQUITY, stock.normalizedAssetClass());
        assertEquals(ContractType.CASH, stock.normalizedContractType());
        assertEquals(InstrumentType.SPOT, spot.normalizedInstrumentType());
        assertEquals(InstrumentType.PERPETUAL, perp.normalizedInstrumentType());
        assertEquals(InstrumentType.STOCK, stock.normalizedInstrumentType());
        assertEquals(LeverageMode.NONE, spot.normalizedLeverageMode());
        assertEquals(LeverageMode.DERIVATIVE_LEVERAGE, perp.normalizedLeverageMode());
    }

    @Test
    void coinbaseVenueFollowsMarketAndVenueSelection() {
        assertEquals(ProductVenue.COINBASE_ADVANCED,
                configuration("Crypto Spot", "Spot", "coinbase", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.COINBASE_INTERNATIONAL,
                configuration("Perpetuals", "International", "coinbase", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.COINBASE_DERIVATIVES,
                configuration("Futures", "US Derivatives", "coinbase", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.COINBASE_DERIVATIVES,
                configuration("Indices", "US", "coinbase", "PAPER").normalizedVenue());
    }

    @Test
    void brokerVenueUsesExchangeAndTradingMode() {
        assertEquals(ProductVenue.OANDA,
                configuration("Forex", "Global", "oanda", "PAPER TRADING").normalizedVenue());
        assertEquals(ProductVenue.OANDA,
                configuration("Forex", "Global", "oanda", "LIVE").normalizedVenue());
        assertEquals(ProductVenue.IBKR_IDEALPRO,
                configuration("Forex", "Global", "interactive-brokers", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.IBKR_CME,
                configuration("Futures", "US", "interactive-brokers", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.ALPACA_EQUITIES,
                configuration("Stocks", "US", "alpaca", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.ALPACA_CRYPTO,
                configuration("Crypto Spot", "US", "alpaca", "LIVE").normalizedVenue());
    }

    @Test
    void onboardingSupportedExchangesDoNotFallBackToUnknownVenue() {
        assertEquals(ProductVenue.BINANCE_SPOT,
                configuration("Crypto Spot", "Global", "binance", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.BINANCE_US_SPOT,
                configuration("Crypto Spot", "US", "binance-us", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.KRAKEN_SPOT,
                configuration("Crypto Spot", "Global", "kraken", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.BITFINEX_US_SPOT,
                configuration("Crypto Spot", "US", "bitfinex-us", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.BITMEX_DERIVATIVES,
                configuration("Perpetuals", "Global", "bitmex", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.KUCOIN_US_SPOT,
                configuration("Crypto Spot", "US", "kucoin-us", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.BITSTAMP_SPOT,
                configuration("Crypto Spot", "Global", "bitstamp", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.POLONIEX_SPOT,
                configuration("Crypto Spot", "Global", "poloniex", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.BITTREX_SPOT,
                configuration("Crypto Spot", "Global", "bittrex", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.IG,
                configuration("CFD", "Global", "ig", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.SCHWAB_EQUITIES,
                configuration("Stocks", "US", "schwab", "PAPER").normalizedVenue());
    }

    @Test
    void directVenueAliasesFromSavedOnboardingPreferencesAreAccepted() {
        assertEquals(ProductVenue.COINBASE_INTERNATIONAL,
                configuration("Perpetuals", "coinbase-international", "coinbase", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.IBKR_CME,
                configuration("Futures", "ibkr-cme", "interactive-brokers", "PAPER").normalizedVenue());
        assertEquals(ProductVenue.ALPACA_EQUITIES,
                configuration("Stocks", "alpaca-equities", "alpaca", "PAPER").normalizedVenue());
    }

    @Test
    void tradingEnvironmentNormalizesSeparatelyFromVenue() {
        assertEquals(TradingEnvironment.LIVE, configuration("LIVE").normalizedEnvironment());
        assertEquals(TradingEnvironment.PAPER, configuration("PAPER TRADING").normalizedEnvironment());
        assertEquals(TradingEnvironment.PRACTICE, configuration("PRACTICE").normalizedEnvironment());
        assertEquals(TradingEnvironment.SANDBOX, configuration("SANDBOX").normalizedEnvironment());
        assertEquals(TradingEnvironment.TESTNET, configuration("TESTNET").normalizedEnvironment());
        assertEquals(TradingEnvironment.BACKTEST, configuration("BACKTEST").normalizedEnvironment());
        assertEquals(TradingEnvironment.SIMULATION, configuration("SIMULATION").normalizedEnvironment());
    }

    private MarketConfiguration configuration(String tradingMode) {
        return configuration("Crypto Spot", "US", "coinbase", tradingMode);
    }

    private MarketConfiguration configuration(String marketType, String venue, String exchange, String tradingMode) {
        return new MarketConfiguration(
                "user",
                marketType,
                venue,
                exchange,
                "",
                "",
                "",
                "",
                "",
                null,
                null,
                tradingMode);
    }
}
