package org.investpro.exchange.stellar;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
@Getter
@Setter
@ToString
@Slf4j
public final class StellarTrustedAssetRegistry {

    private static final String MAINNET_USDC_ISSUER = "GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN";
    private static final String TESTNET_USDC_ISSUER = "GBBD47IF6LWK7P7MDEVSCWR7DPUWV3NY3DTQEVFL4NAT4AQH3ZLLFLA5";


    private final boolean testnet;
    private final Map<String, StellarAssetIdentity> assetsByCanonicalKey = new LinkedHashMap<>();

    public StellarTrustedAssetRegistry(boolean testnet) {
        this.testnet = testnet;
        loadBuiltIns();
    }

    public synchronized Optional<StellarAssetIdentity> resolve(String code) {
        String normalized = normalizeCode(code);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        if ("XLM".equals(normalized)) {
            return Optional.of(nativeXlm());
        }

        return assetsByCanonicalKey.values().stream()
                .filter(asset -> asset.code().equalsIgnoreCase(normalized))
                .filter(asset -> asset.trusted() || asset.userAdded())
                .findFirst();
    }

    public synchronized Optional<StellarAssetIdentity> resolve(String code, String issuer) {
        String normalized = normalizeCode(code);
        String normalizedIssuer = issuer == null ? "" : issuer.trim();
        if ("XLM".equals(normalized)) {
            return Optional.of(nativeXlm());
        }
        if (normalized.isBlank() || normalizedIssuer.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(assetsByCanonicalKey.get(canonicalKey(normalized, normalizedIssuer)));
    }

    public synchronized List<StellarAssetIdentity> allAssets() {
        return List.copyOf(assetsByCanonicalKey.values());
    }

    public synchronized void addUserTrustlineAsset(String code, String issuer, String homeDomain) {
        String normalized = normalizeCode(code);
        String normalizedIssuer = issuer == null ? "" : issuer.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Stellar asset code must not be blank");
        }
        if ("XLM".equals(normalized)) {
            put(nativeXlm());
            return;
        }
        if (normalizedIssuer.isBlank() || !normalizedIssuer.startsWith("G")) {
            throw new IllegalArgumentException("Stellar asset issuer must start with G");
        }

        StellarAssetIdentity asset = new StellarAssetIdentity(
                normalized,
                normalizedIssuer,
                false,
                homeDomain,
                false,
                true,
                "user-trustline");
        put(asset);
        log.info("stellar.trustline.user.added code={} issuer={} homeDomain={}",
                asset.code(), asset.issuer(), asset.homeDomain());
    }

    public synchronized boolean isTrusted(String code, String issuer) {
        return resolve(code, issuer)
                .map(StellarAssetIdentity::trusted)
                .orElse(false);
    }

    private void loadBuiltIns() {
        put(nativeXlm());
        put(new StellarAssetIdentity(
                "USDC",
                testnet ? TESTNET_USDC_ISSUER : MAINNET_USDC_ISSUER,
                false,
                "circle.com",
                true,
                false,
                testnet ? "built-in-testnet" : "built-in-mainnet"));
        if (!testnet) {
            put(new StellarAssetIdentity(
                    "USDC",
                    MAINNET_USDC_ISSUER,
                    false,
                    "circle.com",
                    true,
                    false,
                    "built-in-mainnet"));
        }
    }

    private void put(StellarAssetIdentity asset) {
        assetsByCanonicalKey.put(asset.canonicalKey(), asset);
    }

    private static StellarAssetIdentity nativeXlm() {
        return new StellarAssetIdentity("XLM", "", true, "stellar.org", true, false, "native");
    }

    private static String canonicalKey(String code, String issuer) {
        return code.toUpperCase(Locale.ROOT) + ":" + issuer;
    }

    private static String normalizeCode(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
