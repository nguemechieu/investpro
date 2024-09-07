package org.investpro;

public class LiveTrade {
    private final LiveTradesConsumer consumer;
    private Trade trade;


    public LiveTrade(Trade trade, LiveTradesConsumer consumer) {
        this.trade = trade;
        this.consumer = consumer;
    }

    public Trade getTrade() {
        return trade;
    }

    public void setTrade(Trade trade) {
        this.trade = trade;
    }

    public LiveTradesConsumer getConsumer() {
        return consumer;
    }
}
