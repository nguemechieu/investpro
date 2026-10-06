package org.investpro.activities;

import org.investpro.activities.binanceus.BinanceUsActivityService;
import org.investpro.activities.coinbase.CoinbaseActivityService;
import org.investpro.activities.oanda.OandaActivityService;
import org.investpro.exchange.binanceus.BinanceUs;
import org.investpro.exchange.coinbase.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.oanda.Oanda;

import java.util.Optional;

public final class ExchangeActivityServiceFactory {
    private ExchangeActivityServiceFactory() {
    }

    public static Optional<ExchangeActivityService> create(
            Exchange exchange,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        if (exchange instanceof Oanda oanda) {
            return Optional.of(new OandaActivityService(oanda, activityRepository, checkpointRepository, projectionService));
        }
        if (exchange instanceof Coinbase coinbase) {
            return Optional.of(new CoinbaseActivityService(coinbase, activityRepository, checkpointRepository, projectionService));
        }
        if (exchange instanceof BinanceUs binanceUs) {
            return Optional.of(new BinanceUsActivityService(binanceUs, activityRepository, checkpointRepository, projectionService));
        }
        return Optional.empty();
    }
}
