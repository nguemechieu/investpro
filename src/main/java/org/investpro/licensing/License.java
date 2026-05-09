package org.investpro.licensing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Set;

/**
 * Represents a license for InvestPro application.
 * Defines licensee, expiration, feature access, and validation.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Builder
@AllArgsConstructor
@ToString
public class License {
    /**
     * Unique license key/ID
     */
    @NotNull
    private final String licenseKey;

    /**
     * Name of the licensee/customer
     */
    @NotNull
    private final String licenseeName;

    /**
     * Email of the licensee for support
     */
    @NotNull
    private final String licenseeEmail;

    /**
     * License issuance date
     */
    @NotNull
    private final Instant issuedDate;

    /**
     * License expiration date (null = perpetual)
     */
    private final Instant expirationDate;

    /**
     * License type (TRIAL, STANDARD, PREMIUM, ENTERPRISE)
     */
    @NotNull
    @Builder.Default
    private final LicenseType licenseType = LicenseType.TRIAL;

    /**
     * Enabled features for this license
     */
    @NotNull
    @Builder.Default
    private final Set<String> features = Set.of();

    /**
     * Maximum number of simultaneous connections/sessions
     */
    @Builder.Default
    private final int maxConnections = 1;

    /**
     * Maximum number of concurrent trading strategies
     */
    @Builder.Default
    private final int maxStrategies = 1;

    /**
     * Whether this license is currently active/valid
     */
    @Builder.Default
    private final boolean active = true;

    /**
     * License activation code (for verification)
     */
    private final String activationCode;

    /**
     * Custom metadata about the license
     */
    @Builder.Default
    private final String metadata = "";

    /**
     * Check if license is expired
     */
    public boolean isExpired() {
        return expirationDate != null && Instant.now().isAfter(expirationDate);
    }

    /**
     * Check if license is valid (active and not expired)
     */
    public boolean isValid() {
        return active && !isExpired();
    }

    /**
     * Check if feature is enabled in this license
     */
    public boolean hasFeature(String featureName) {
        return features.contains(featureName);
    }

    /**
     * Get days until expiration (-1 if perpetual, 0 if expired)
     */
    public long getDaysUntilExpiration() {
        if (expirationDate == null) {
            return Long.MAX_VALUE; // Perpetual
        }
        long daysUntilExpiration = java.time.temporal.ChronoUnit.DAYS.between(Instant.now(), expirationDate);
        return Math.max(daysUntilExpiration, 0);
    }
}
