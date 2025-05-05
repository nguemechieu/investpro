package org.investpro.investpro.services;

import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.components.OandaCandleDataSupplier;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.util.Objects;


public record OandaCandleService(@NotNull String accountId, @NotNull String apiSecret, @NotNull HttpClient client,
                                 int max_candle) {


    private static final Logger logger = LoggerFactory.getLogger(OandaCandleService.class);

    public OandaCandleService {
        logger.info(OandaCandleService.class.getName());
    }

    @Contract("_, _ -> new")
    public @NotNull CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        Objects.requireNonNull(tradePair);
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair);
    }




}
