package org.investpro.licensing;

import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

/**
 * License status information for display and monitoring.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter
@Builder
public class LicenseStatus {
    /**
     * Whether the current license is valid
     */
    private final boolean isValid;

    /**
     * Type of the current license
     */
    @NotNull
    private final LicenseType licenseType;

    /**
     * Name of the licensee
     */
    @NotNull
    private final String licensee;

    /**
     * Get the licensee name
     */
    public String getLicenseeName() {
        return licensee;
    }

    /**
     * Days remaining until expiration (-1 for perpetual)
     */
    private final long daysUntilExpiration;

    /**
     * Whether the license has expired
     */
    private final boolean isExpired;

    /**
     * Whether this is a trial license
     */
    private final boolean isTrial;

    /**
     * Maximum concurrent connections allowed
     */
    private final int maxConnections;

    /**
     * Maximum concurrent strategies allowed
     */
    private final int maxStrategies;

    /**
     * Get human-readable status
     */
    public String getStatusText() {
        if (isExpired) {
            return "EXPIRED";
        }
        if (!isValid) {
            return "INVALID";
        }
        if (isTrial) {
            return "TRIAL (" + daysUntilExpiration + " days)";
        }
        return licenseType.getDisplayName().toUpperCase();
    }

    /**
     * Get status color for UI display
     */
    public String getStatusColor() {
        if (isExpired) {
            return "#ef4444"; // Red
        }
        if (!isValid) {
            return "#ef4444"; // Red
        }
        if (isTrial && daysUntilExpiration < 3) {
            return "#f59e0b"; // Amber
        }
        return "#10b981"; // Green
    }
}
