package org.investpro.terminal.stellar;

import java.time.Instant;

public record StellarIssuerProfile(
        String issuer,
        String homeDomain,
        boolean verifiedDomain,
        boolean trusted,
        String qualityReason,
        Instant checkedAt
) {
    public StellarIssuerProfile {
        issuer = issuer == null ? "" : issuer.trim();
        homeDomain = homeDomain == null ? "" : homeDomain.trim();
        qualityReason = qualityReason == null ? "" : qualityReason.trim();
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        if (!issuer.isBlank() && !issuer.startsWith("G")) {
            throw new IllegalArgumentException("Stellar issuer must be a public G... key");
        }
    }
}
