package org.investpro.licensing;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Manages application licensing, validation, and persistence.
 * Handles license activation, verification, and feature gating.
 *
 * @author NOEL NGUEMECHIEU
 */
@Getter

@Setter
@Slf4j
public final class LicenseManager {
    private static final String LICENSE_PREFS_KEY = "investpro.license";
    private static final String LICENSE_FILE_PATH = "~/.investpro/license.dat";
    private static final Preferences PREFS = Preferences.userNodeForPackage(LicenseManager.class);

    private volatile License currentLicense;
    private final Object licenseLock = new Object();
    private  SystemCore systemCore;

    public LicenseManager(SystemCore systemCore) {

        this .systemCore=systemCore;
    }

    /**
     * Load or create a default trial license
     */
    public void initialize() {
        synchronized (licenseLock) {
            // Try to load from preferences
            License loaded = loadLicenseFromPreferences();
            if (loaded != null && loaded.isValid()) {
                currentLicense = loaded;
                log.info("Loaded valid license for: {}", loaded.getLicenseeName());
               // systemCore.authorize_license();
                return;
            }

            // Try to load from file
            loaded = loadLicenseFromFile();
            if (loaded != null && loaded.isValid()) {
                currentLicense = loaded;
                log.info("Loaded valid license from file for: {}", loaded.getLicenseeName());
                return;
            }

            // Create default trial license
            currentLicense = createDefaultTrialLicense();
            saveLicense(currentLicense);
            log.info("Created default trial license");
        }
    }

    /**
     * Get the currently active license
     */
    @NotNull
    public License getCurrentLicense() {
        synchronized (licenseLock) {
            if (currentLicense == null) {
                initialize();
            }
            return currentLicense;
        }
    }

    /**
     * Activate a new license
     */
    public boolean activateLicense(@NotNull String licenseKey) {
        Objects.requireNonNull(licenseKey, "licenseKey cannot be null");
        try {
            // In a real system, this would validate the license key with a server
            // For now, we'll create a license from the key
            License license = parseLicenseKey(licenseKey);
            if (license.isValid()) {
                synchronized (licenseLock) {
                    currentLicense = license;
                    saveLicense(license);
                    log.info("License activated for: {}", license.getLicenseeName());
                    return true;
                }
            }
            log.warn("Invalid or expired license key");
            return false;
        } catch (Exception e) {
            log.error("Failed to activate license", e);
            return false;
        }
    }

    /**
     * Check if a feature is available in the current license
     */
    public boolean isFeatureEnabled(@NotNull String featureName) {
        Objects.requireNonNull(featureName, "featureName cannot be null");
        License license = getCurrentLicense();
        return license.isValid() && license.hasFeature(featureName);
    }

    /**
     * Check if the current license is valid
     */
    public boolean isLicenseValid() {
        return getCurrentLicense().isValid();
    }

    /**
     * Get license status information
     */
    @NotNull
    public LicenseStatus getStatus() {
        License license = getCurrentLicense();
        return LicenseStatus.builder()
                .isValid(license.isValid())
                .licenseType(license.getLicenseType())
                .licensee(license.getLicenseeName())
                .daysUntilExpiration(license.getDaysUntilExpiration())
                .isExpired(license.isExpired())
                .isTrial(license.getLicenseType().isTrial())
                .maxConnections(license.getMaxConnections())
                .maxStrategies(license.getMaxStrategies())
                .build();
    }

    /**
     * Create license key for the given license (for sharing)
     */
    @NotNull
    public String generateLicenseKey(@NotNull License license) {
        Objects.requireNonNull(license, "license cannot be null");
        try {
            String licenseData = String.format("%s|%s|%d|%d",
                    license.getLicenseKey(),
                    license.getLicenseeName(),
                    license.getIssuedDate().toEpochMilli(),
                    license.getExpirationDate() != null ? license.getExpirationDate().toEpochMilli() : 0);

            byte[] signature = generateSignature(licenseData);
            String encoded = Base64.getEncoder().encodeToString(signature);
            return license.getLicenseKey() + "|" + encoded;
        } catch (Exception e) {
            log.error("Failed to generate license key", e);
            return license.getLicenseKey();
        }
    }

    /**
     * Save current license to persistent storage
     */
    private void saveLicense(@NotNull License license) {
        try {
            String licenseJson = serializeLicense(license);
            PREFS.put(LICENSE_PREFS_KEY, licenseJson);

            // Also try to save to file
            Path licenseFile = Paths.get(LICENSE_FILE_PATH.replace("~", System.getProperty("user.home")));
            Files.createDirectories(licenseFile.getParent());
            Files.writeString(licenseFile, licenseJson);
        } catch (Exception e) {
            log.warn("Failed to save license to file", e);
            // Preferences fallback is still in place
        }
    }

    /**
     * Load license from preferences
     */
    private License loadLicenseFromPreferences() {
        try {
            String licenseJson = PREFS.get(LICENSE_PREFS_KEY, null);
            if (licenseJson != null && !licenseJson.isBlank()) {
                return deserializeLicense(licenseJson);
            }
        } catch (Exception e) {
            log.warn("Failed to load license from preferences", e);
        }
        return null;
    }

    /**
     * Load license from file
     */
    private License loadLicenseFromFile() {
        try {
            Path licenseFile = Paths.get(LICENSE_FILE_PATH.replace("~", System.getProperty("user.home")));
            if (Files.exists(licenseFile)) {
                String licenseJson = Files.readString(licenseFile);
                return deserializeLicense(licenseJson);
            }
        } catch (Exception e) {
            log.warn("Failed to load license from file", e);
        }
        return null;
    }

    /**
     * Create default trial license
     */
    private License createDefaultTrialLicense() {
        return License.builder()
                .licenseKey(generateUniqueLicenseKey())
                .licenseeName("Trial User")
                .licenseeEmail("trial@investpro.local")
                .licenseType(LicenseType.TRIAL)
                .issuedDate(Instant.now())
                .expirationDate(Instant.now().plus(7, ChronoUnit.DAYS))
                .features(Set.of(
                        "BASIC_TRADING",
                        "PAPER_TRADING",
                        "MARKET_DATA",
                        "SINGLE_STRATEGY"))
                .maxConnections(1)
                .maxStrategies(1)
                .active(true)
                .build();
    }

    /**
     * Parse license key to extract license info
     */
    private License parseLicenseKey(@NotNull String licenseKey) {
        // In a real system, this would validate against a server
        // For now, we'll create a premium license from the key
        if (licenseKey.length() < 16) {
            throw new IllegalArgumentException("Invalid license key format");
        }

        return License.builder()
                .licenseKey(licenseKey)
                .licenseeName("Premium User")
                .licenseeEmail("premium@investpro.local")
                .licenseType(LicenseType.PREMIUM)
                .issuedDate(Instant.now())
                .expirationDate(Instant.now().plus(365, ChronoUnit.DAYS))
                .features(Set.of(
                        "BASIC_TRADING",
                        "ADVANCED_TRADING",
                        "PAPER_TRADING",
                        "MARKET_DATA",
                        "MULTIPLE_STRATEGIES",
                        "RISK_MANAGEMENT",
                        "AI_SIGNALS",
                        "TELEGRAM_ALERTS"))
                .maxConnections(3)
                .maxStrategies(5)
                .active(true)
                .activationCode(licenseKey)
                .build();
    }

    /**
     * Generate unique license key
     */
    private String generateUniqueLicenseKey() {
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[12];
        random.nextBytes(randomBytes);
        return "INVEST-" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generate cryptographic signature
     */
    private byte[] generateSignature(String data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update("investpro-secret-key".getBytes(StandardCharsets.UTF_8));
        digest.update(data.getBytes(StandardCharsets.UTF_8));
        return digest.digest();
    }

    /**
     * Serialize license to JSON-like format
     */
    private String serializeLicense(@NotNull License license) {
        return String.format(
                "{\"key\":\"%s\",\"licensee\":\"%s\",\"type\":\"%s\",\"expired\":%b}",
                license.getLicenseKey(),
                license.getLicenseeName(),
                license.getLicenseType(),
                license.isExpired());
    }

    /**
     * Deserialize license from JSON-like format
     */
    private License deserializeLicense(String json) {
        // Simple parsing - in production use proper JSON library
        try {
            if (json.contains("Trial")) {
                return createDefaultTrialLicense();
            }
            // Return default if parsing fails
            return createDefaultTrialLicense();
        } catch (Exception e) {
            log.error("Failed to deserialize license", e);
            return createDefaultTrialLicense();
        }
    }
}
