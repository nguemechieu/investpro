package org.investpro.activity;

import org.investpro.activity.binanceus.BinanceUsActivityService;
import org.investpro.activity.coinbase.CoinbaseActivityService;
import org.investpro.activity.oanda.OandaActivityService;
import org.investpro.exchange.BinanceUs;
import org.investpro.exchange.Coinbase;
import org.investpro.exchange.Exchange;
import org.investpro.exchange.Oanda;

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
