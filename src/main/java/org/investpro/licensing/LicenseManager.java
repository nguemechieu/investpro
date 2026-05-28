package org.investpro.licensing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.prefs.Preferences;

/**
 * Manages application licensing, validation, and persistence.
 * <p>
 * License keys are local, signed payloads in the form:
 * {@code INVESTPRO-V1.<base64url payload>.<base64url hmac>}.
 */
@Getter
@Setter
@Slf4j
public final class LicenseManager {
    private static final String LICENSE_PREFS_KEY = "investpro.license";
    private static final Path DEFAULT_LICENSE_FILE = Path.of(
            System.getProperty("user.home"),
            ".investpro",
            "license.dat");
    private static final String KEY_PREFIX = "INVESTPRO-V1";
    private static final String SIGNING_ALGORITHM = "HmacSHA256";
    private static final byte[] SIGNING_SECRET =
            "investpro-local-license-signing-v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private static final Map<LicenseType, LicenseEntitlement> ENTITLEMENTS = createEntitlements();

    private final Object licenseLock = new Object();
    private final Preferences preferences;
    private final Path licenseFile;
    private final String preferencesKey;
    private volatile License currentLicense;
    private SystemCore systemCore;

    public LicenseManager(SystemCore systemCore) {
        this(systemCore, Preferences.userNodeForPackage(LicenseManager.class), DEFAULT_LICENSE_FILE, LICENSE_PREFS_KEY);
    }

    LicenseManager(SystemCore systemCore, Preferences preferences, Path licenseFile, String preferencesKey) {
        this.systemCore = systemCore;
        this.preferences = Objects.requireNonNull(preferences, "preferences cannot be null");
        this.licenseFile = Objects.requireNonNull(licenseFile, "licenseFile cannot be null");
        this.preferencesKey = Objects.requireNonNull(preferencesKey, "preferencesKey cannot be null");
    }

    /**
     * Load the saved license, or create a default trial license on first run.
     */
    public void initialize() {
        synchronized (licenseLock) {
            License loaded = loadLicenseFromPreferences();
            if (loaded == null) {
                loaded = loadLicenseFromFile();
            }

            if (loaded != null) {
                currentLicense = loaded;
                if (loaded.isValid()) {
                    log.info("Loaded {} license for: {}", loaded.getLicenseType(), loaded.getLicenseeName());
                } else {
                    log.warn("Loaded inactive or expired {} license for: {}", loaded.getLicenseType(), loaded.getLicenseeName());
                }
                return;
            }

            currentLicense = createDefaultTrialLicense();
            saveLicense(currentLicense);
            log.info("Created default trial license");
        }
    }

    /**
     * Get the currently active license record.
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
     * Activate a signed InvestPro license key.
     */
    public boolean activateLicense(@NotNull String licenseKey) {
        Objects.requireNonNull(licenseKey, "licenseKey cannot be null");
        try {
            License license = parseLicenseKey(licenseKey);
            if (!license.isValid()) {
                log.warn("Invalid or expired license key for: {}", license.getLicenseeName());
                return false;
            }

            synchronized (licenseLock) {
                currentLicense = license;
                saveLicense(license);
            }

            log.info("License activated for: {}", license.getLicenseeName());
            return true;
        } catch (Exception exception) {
            log.warn("Failed to activate license: {}", exception.getMessage());
            return false;
        }
    }

    /**
     * Check if a feature is available in the current license.
     */
    public boolean isFeatureEnabled(@NotNull String featureName) {
        Objects.requireNonNull(featureName, "featureName cannot be null");
        License license = getCurrentLicense();
        return license.isValid() && license.hasFeature(featureName.trim().toUpperCase());
    }

    /**
     * Check if the current license is valid.
     */
    public boolean isLicenseValid() {
        return getCurrentLicense().isValid();
    }

    /**
     * Get license status information.
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
     * Create a signed activation key for a license.
     */
    @NotNull
    public String generateLicenseKey(@NotNull License license) {
        Objects.requireNonNull(license, "license cannot be null");
        try {
            License normalized = normalizeLicense(license);
            String payload = OBJECT_MAPPER.writeValueAsString(toPayload(normalized));
            String encodedPayload = URL_ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
            String encodedSignature = URL_ENCODER.encodeToString(sign(encodedPayload));
            return KEY_PREFIX + "." + encodedPayload + "." + encodedSignature;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate license key", exception);
        }
    }

    /**
     * Convenience factory for local/admin license provisioning.
     */
    @NotNull
    public License createLicense(
            @NotNull LicenseType licenseType,
            @NotNull String licenseeName,
            @NotNull String licenseeEmail,
            Instant expirationDate) {
        Objects.requireNonNull(licenseType, "licenseType cannot be null");
        LicenseEntitlement entitlement = ENTITLEMENTS.get(licenseType);
        return License.builder()
                .licenseKey(generateUniqueLicenseKey())
                .licenseeName(requireText(licenseeName, "licenseeName"))
                .licenseeEmail(requireText(licenseeEmail, "licenseeEmail"))
                .licenseType(licenseType)
                .issuedDate(Instant.now())
                .expirationDate(expirationDate)
                .features(entitlement.features())
                .maxConnections(entitlement.maxConnections())
                .maxStrategies(entitlement.maxStrategies())
                .active(true)
                .build();
    }

    private void saveLicense(@NotNull License license) {
        try {
            String signedLicense = generateLicenseKey(license);
            preferences.put(preferencesKey, signedLicense);
            preferences.flush();

            Files.createDirectories(licenseFile.getParent());
            Files.writeString(licenseFile, signedLicense, StandardCharsets.UTF_8);
        } catch (Exception exception) {
            log.warn("Failed to persist license", exception);
        }
    }

    private  License loadLicenseFromPreferences() {
        try {
            String savedLicense = preferences.get(preferencesKey, null);
            return parseSavedLicense(savedLicense);
        } catch (Exception exception) {
            log.warn("Failed to load license from preferences", exception);
            return null;
        }
    }

    private License loadLicenseFromFile() {
        try {
            if (!Files.exists(licenseFile)) {
                return null;
            }
            return parseSavedLicense(Files.readString(licenseFile, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            log.warn("Failed to load license from file", exception);
            return null;
        }
    }

    private License parseSavedLicense(String savedLicense) {
        if (savedLicense == null || savedLicense.isBlank()) {
            return null;
        }

        try {
            return parseLicenseKey(savedLicense.trim());
        } catch (Exception exception) {
            log.warn("Ignoring unreadable saved license: {}", exception.getMessage());
            return null;
        }
    }

    private License createDefaultTrialLicense() {
        LicenseType type = LicenseType.TRIAL;
        LicenseEntitlement entitlement = ENTITLEMENTS.get(type);
        Instant now = Instant.now();
        return License.builder()
                .licenseKey(generateUniqueLicenseKey())
                .licenseeName("Trial User")
                .licenseeEmail("trial@investpro.local")
                .licenseType(type)
                .issuedDate(now)
                .expirationDate(now.plus(type.getTrialDays(), ChronoUnit.DAYS))
                .features(entitlement.features())
                .maxConnections(entitlement.maxConnections())
                .maxStrategies(entitlement.maxStrategies())
                .active(true)
                .metadata("auto-created trial")
                .build();
    }

    private License parseLicenseKey(@NotNull String licenseKey) throws Exception {
        String[] parts = licenseKey.trim().split("\\.");
        if (parts.length != 3 || !KEY_PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("License key must use the " + KEY_PREFIX + " format");
        }

        String encodedPayload = parts[1];
        byte[] expectedSignature = sign(encodedPayload);
        byte[] actualSignature = URL_DECODER.decode(parts[2]);
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            throw new IllegalArgumentException("License signature is invalid");
        }

        String payloadJson = new String(URL_DECODER.decode(encodedPayload), StandardCharsets.UTF_8);
        LicensePayload payload = OBJECT_MAPPER.readValue(payloadJson, LicensePayload.class);
        return normalizeLicense(fromPayload(payload, licenseKey));
    }

    private License normalizeLicense(@NotNull License license) {
        LicenseType type = license.getLicenseType();
        LicenseEntitlement entitlement = ENTITLEMENTS.get(type);
        Set<String> features = license.getFeatures().isEmpty()
                ? entitlement.features()
                : normalizeFeatures(license.getFeatures());

        return License.builder()
                .licenseKey(requireText(license.getLicenseKey(), "licenseKey"))
                .licenseeName(requireText(license.getLicenseeName(), "licenseeName"))
                .licenseeEmail(requireText(license.getLicenseeEmail(), "licenseeEmail"))
                .licenseType(type)
                .issuedDate(license.getIssuedDate())
                .expirationDate(license.getExpirationDate())
                .features(features)
                .maxConnections(Math.max(1, license.getMaxConnections()))
                .maxStrategies(Math.max(1, license.getMaxStrategies()))
                .active(license.isActive())
                .activationCode(license.getActivationCode())
                .metadata(license.getMetadata() == null ? "" : license.getMetadata())
                .build();
    }

    private static LicensePayload toPayload(License license) {
        LicensePayload payload = new LicensePayload();
        payload.licenseKey = license.getLicenseKey();
        payload.licenseeName = license.getLicenseeName();
        payload.licenseeEmail = license.getLicenseeEmail();
        payload.issuedDate = license.getIssuedDate();
        payload.expirationDate = license.getExpirationDate();
        payload.licenseType = license.getLicenseType();
        payload.features = normalizeFeatures(license.getFeatures());
        payload.maxConnections = license.getMaxConnections();
        payload.maxStrategies = license.getMaxStrategies();
        payload.active = license.isActive();
        payload.metadata = license.getMetadata();
        return payload;
    }

    private static License fromPayload(LicensePayload payload, String activationCode) {
        if (payload == null) {
            throw new IllegalArgumentException("License payload is empty");
        }

        return License.builder()
                .licenseKey(payload.licenseKey)
                .licenseeName(payload.licenseeName)
                .licenseeEmail(payload.licenseeEmail)
                .issuedDate(payload.issuedDate)
                .expirationDate(payload.expirationDate)
                .licenseType(payload.licenseType)
                .features(payload.features == null ? Set.of() : payload.features)
                .maxConnections(payload.maxConnections)
                .maxStrategies(payload.maxStrategies)
                .active(payload.active)
                .activationCode(activationCode)
                .metadata(payload.metadata == null ? "" : payload.metadata)
                .build();
    }

    private static byte[] sign(String encodedPayload) throws Exception {
        Mac mac = Mac.getInstance(SIGNING_ALGORITHM);
        mac.init(new SecretKeySpec(SIGNING_SECRET, SIGNING_ALGORITHM));
        return mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8));
    }

    private static String generateUniqueLicenseKey() {
        byte[] randomBytes = new byte[12];
        RANDOM.nextBytes(randomBytes);
        return "INVEST-" + URL_ENCODER.encodeToString(randomBytes);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim();
    }

    private static Set<String> normalizeFeatures(Set<String> features) {
        if (features == null || features.isEmpty()) {
            return Set.of();
        }

        Set<String> normalized = new LinkedHashSet<>();
        for (String feature : features) {
            if (feature != null && !feature.isBlank()) {
                normalized.add(feature.trim().toUpperCase());
            }
        }
        return Set.copyOf(normalized);
    }

    private static Map<LicenseType, LicenseEntitlement> createEntitlements() {
        Map<LicenseType, LicenseEntitlement> entitlements = new EnumMap<>(LicenseType.class);
        entitlements.put(LicenseType.TRIAL, new LicenseEntitlement(
                Set.of("BASIC_TRADING", "PAPER_TRADING", "MARKET_DATA", "SINGLE_STRATEGY"),
                1,
                1));
        entitlements.put(LicenseType.STANDARD, new LicenseEntitlement(
                Set.of("BASIC_TRADING", "PAPER_TRADING", "MARKET_DATA", "SINGLE_STRATEGY", "RISK_MANAGEMENT"),
                2,
                2));
        entitlements.put(LicenseType.PREMIUM, new LicenseEntitlement(
                Set.of("BASIC_TRADING", "ADVANCED_TRADING", "PAPER_TRADING", "MARKET_DATA",
                        "MULTIPLE_STRATEGIES", "RISK_MANAGEMENT", "AI_SIGNALS", "TELEGRAM_ALERTS"),
                3,
                5));
        entitlements.put(LicenseType.ENTERPRISE, new LicenseEntitlement(
                Set.of("BASIC_TRADING", "ADVANCED_TRADING", "PAPER_TRADING", "MARKET_DATA",
                        "MULTIPLE_STRATEGIES", "RISK_MANAGEMENT", "AI_SIGNALS", "TELEGRAM_ALERTS",
                        "TEAM_MANAGEMENT", "UNLIMITED_CONNECTIONS"),
                25,
                50));
        entitlements.put(LicenseType.DEVELOPMENT, new LicenseEntitlement(
                Set.of("BASIC_TRADING", "ADVANCED_TRADING", "PAPER_TRADING", "MARKET_DATA",
                        "MULTIPLE_STRATEGIES", "RISK_MANAGEMENT", "AI_SIGNALS", "TELEGRAM_ALERTS",
                        "DEVELOPER_TOOLS"),
                10,
                20));
        return Map.copyOf(entitlements);
    }

    private record LicenseEntitlement(Set<String> features, int maxConnections, int maxStrategies) {
        private LicenseEntitlement {
            features = normalizeFeatures(features);
        }
    }

    private static final class LicensePayload {
        public String licenseKey;
        public String licenseeName;
        public String licenseeEmail;
        public Instant issuedDate;
        public Instant expirationDate;
        public LicenseType licenseType;
        public Set<String> features;
        public int maxConnections;
        public int maxStrategies;
        public boolean active;
        public String metadata;
    }
}
