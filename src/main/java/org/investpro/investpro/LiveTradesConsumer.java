package org.investpro.investpro;

import java.util.List;


public interface LiveTradesConsumer {
    void acceptTrades(List<Trade> trades);
}
