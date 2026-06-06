package org.investpro.terminal.domain;

import java.util.Locale;

public record Asset(
        String code,
        String displayName,
        AssetClass assetClass,
        String issuer,
        String homeDomain
) {
    public Asset {
        code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        displayName = displayName == null || displayName.isBlank() ? code : displayName.trim();
        assetClass = assetClass == null ? AssetClass.UNKNOWN : assetClass;
        issuer = issuer == null ? "" : issuer.trim();
        homeDomain = homeDomain == null ? "" : homeDomain.trim();
        if (code.isBlank()) {
            throw new IllegalArgumentException("asset code is required");
        }
    }

    public String canonicalKey() {
        return issuer.isBlank() ? code : code + ":" + issuer;
    }
}
