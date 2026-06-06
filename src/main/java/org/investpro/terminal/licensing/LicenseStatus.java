package org.investpro.terminal.licensing;

import java.time.Instant;

public record LicenseStatus(
        LicenseTier tier,
        boolean active,
        Instant checkedAt,
        String reason
) {
    public LicenseStatus {
        tier = tier == null ? LicenseTier.FREE : tier;
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
        reason = reason == null ? "" : reason.trim();
    }
}
