package org.investpro.exchange.stellar;

import org.jspecify.annotations.NonNull;

import java.util.Locale;

public record StellarAssetIdentity(
        String code,
        String issuer,
        boolean nativeAsset,
        String homeDomain,
        boolean trusted,
        boolean userAdded,
        String source
) {
    public StellarAssetIdentity {
        code = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        issuer = issuer == null ? "" : issuer.trim();
        homeDomain = homeDomain == null ? "" : homeDomain.trim();
        source = source == null ? "" : source.trim();

        if ("XLM".equalsIgnoreCase(code)) {
            nativeAsset = true;
            issuer = "";
        } else if (!nativeAsset && issuer.isBlank()) {
            throw new IllegalArgumentException("Non-native Stellar assets require an issuer");
        }
    }

    public boolean isNative() {
        return nativeAsset || "XLM".equalsIgnoreCase(code);
    }

    public @NonNull String canonicalKey() {
        return isNative()
                ? "XLM:native"
                : code.toUpperCase(Locale.ROOT) + ":" + issuer;
    }
}
