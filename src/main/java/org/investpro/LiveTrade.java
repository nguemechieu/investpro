package org.investpro;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LiveTrade {
    private final LiveTradesConsumer consumer;
    private Trade trade;


    public LiveTrade(Trade trade, LiveTradesConsumer consumer) {
        this.trade = trade;
        this.consumer = consumer;
    }



}
