package org.investpro.terminal.licensing;

public final class DefaultFeatureGateService implements FeatureGateService {

    private final LicenseService licenseService;

    public DefaultFeatureGateService(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    @Override
    public boolean isEnabled(String featureKey) {
        LicenseStatus status = licenseService == null ? new LicenseStatus(LicenseTier.FREE, true, null, "default") : licenseService.currentStatus();
        if (!status.active()) {
            return false;
        }
        return switch (status.tier()) {
            case FREE -> !"liveTrading".equalsIgnoreCase(featureKey) && !"ai".equalsIgnoreCase(featureKey);
            case STARTER, PRO, ELITE, BUSINESS, ENTERPRISE -> true;
        };
    }

    @Override
    public int limit(String featureKey, int defaultLimit) {
        LicenseStatus status = licenseService == null ? new LicenseStatus(LicenseTier.FREE, true, null, "default") : licenseService.currentStatus();
        if ("maxWatchlistSymbols".equalsIgnoreCase(featureKey) && status.tier() == LicenseTier.FREE) {
            return Math.min(defaultLimit, 25);
        }
        if ("maxBrokers".equalsIgnoreCase(featureKey) && status.tier() == LicenseTier.FREE) {
            return Math.min(defaultLimit, 1);
        }
        return defaultLimit;
    }
}
