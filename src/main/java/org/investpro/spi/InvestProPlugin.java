package org.investpro.spi;

public interface InvestProPlugin {
    String id();

    String displayName();

    String version();

    boolean enabledByDefault();
}
