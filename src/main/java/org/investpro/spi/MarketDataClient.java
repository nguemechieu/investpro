package org.investpro.spi;

public interface MarketDataClient extends AutoCloseable {
    String id();

    @Override
    default void close() {
        // Optional for lightweight providers.
    }
}
