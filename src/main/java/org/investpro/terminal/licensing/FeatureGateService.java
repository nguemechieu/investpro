package org.investpro.terminal.licensing;

public interface FeatureGateService {
    boolean isEnabled(String featureKey);

    int limit(String featureKey, int defaultLimit);
}
