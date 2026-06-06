package org.investpro.terminal.stellar;

public record StellarAssetMetadata(
        String code,
        String issuer,
        String homeDomain,
        boolean nativeAsset,
        boolean verifiedDomain,
        boolean trustlineRequired,
        StellarIssuerProfile issuerProfile
) {
    public StellarAssetMetadata {
        code = code == null ? "" : code.trim().toUpperCase();
        issuer = issuer == null ? "" : issuer.trim();
        homeDomain = homeDomain == null ? "" : homeDomain.trim();
        if ("XLM".equals(code)) {
            nativeAsset = true;
            issuer = "";
            trustlineRequired = false;
        }
        if (!nativeAsset && issuer.isBlank()) {
            throw new IllegalArgumentException("Non-native Stellar asset metadata requires an issuer");
        }
    }

    public String canonicalKey() {
        return nativeAsset ? "XLM:native" : code + ":" + issuer;
    }
}
