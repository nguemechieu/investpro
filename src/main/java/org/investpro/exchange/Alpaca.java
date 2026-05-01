package org.investpro.exchange;

import org.investpro.utils.MARKET_TYPES;

import java.util.List;

public class Alpaca extends BrokerExchangeAdapter {

    public Alpaca(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
    }

    @Override
    public String getName() {
        return "ALPACA";
    }

    @Override
    public String getExchangeId() {
        return "alpaca";
    }

    @Override
    public String getDisplayName() {
        return "Alpaca";
    }

    @Override
    public boolean isPaperTrading() {
        return true;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS);
    }
}
