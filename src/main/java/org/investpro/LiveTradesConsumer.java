package org.investpro;

import java.util.List;

/**
 * @author Michael Ennen
 */
public interface LiveTradesConsumer {
    void acceptTrades(List<Trade> trades);
}
