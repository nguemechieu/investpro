package org.investpro.exchange;

import org.investpro.utils.MARKET_TYPES;

import java.util.List;

public class InteractiveBrokers extends BrokerExchangeAdapter {

    public InteractiveBrokers(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
    }

    @Override
    public String getName() {
        return "INTERACTIVE BROKERS";
    }

    @Override
    public String getExchangeId() {
        return "interactive_brokers";
    }

    @Override
    public String getDisplayName() {
        return "Interactive Brokers";
    }

    @Override
    public boolean isPaperTrading() {
        return true;
    }

    @Override
    public List<MARKET_TYPES> getSupportedMarketTypes() {
        return List.of(MARKET_TYPES.STOCKS, MARKET_TYPES.FOREX);
    }

    @Override
    public boolean supportsForex() {
        return true;
    }
}
