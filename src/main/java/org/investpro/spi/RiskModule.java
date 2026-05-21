package org.investpro.spi;

public interface RiskModule {
    String id();

    default String displayName() {
        return id();
    }
}
