package org.investpro.terminal.stellar;

public record StellarTrustlineRequirement(
        String assetCode,
        String issuer,
        boolean required,
        boolean present,
        String reason
) {
    public StellarTrustlineRequirement {
        assetCode = assetCode == null ? "" : assetCode.trim().toUpperCase();
        issuer = issuer == null ? "" : issuer.trim();
        reason = reason == null ? "" : reason.trim();
    }
}
