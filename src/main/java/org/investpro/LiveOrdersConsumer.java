package org.investpro;

public interface LiveOrdersConsumer {
    void consume(LiveOrder liveOrder);

    void close();

}
