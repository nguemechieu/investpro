package org.investpro.licensing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void initializesTrialLicenseAndPersistsIt() {
        LicenseManager manager = newManager("trial");

        manager.initialize();

        LicenseStatus status = manager.getStatus();
        assertTrue(status.isValid());
        assertTrue(status.isTrial());
        assertEquals(LicenseType.TRIAL, status.getLicenseType());
        assertTrue(manager.isFeatureEnabled("paper_trading"));
        assertFalse(manager.isFeatureEnabled("AI_SIGNALS"));
    }

    @Test
    void generatedLicenseKeyActivatesAndReloads() {
        LicenseManager issuer = newManager("issuer");
        License license = issuer.createLicense(
                LicenseType.PREMIUM,
                "Premium Trader",
                "trader@example.com",
                Instant.now().plus(30, ChronoUnit.DAYS));
        String licenseKey = issuer.generateLicenseKey(license);

        LicenseManager manager = newManager("premium");
        assertTrue(manager.activateLicense(licenseKey));

        assertEquals("Premium Trader", manager.getStatus().getLicenseeName());
        assertEquals(LicenseType.PREMIUM, manager.getStatus().getLicenseType());
        assertTrue(manager.isFeatureEnabled("ai_signals"));

        LicenseManager reloaded = newManager("premium");
        reloaded.initialize();

        assertEquals("Premium Trader", reloaded.getStatus().getLicenseeName());
        assertEquals(LicenseType.PREMIUM, reloaded.getStatus().getLicenseType());
        assertTrue(reloaded.isFeatureEnabled("TELEGRAM_ALERTS"));
    }

    @Test
    void rejectsTamperedLicenseKey() {
        LicenseManager issuer = newManager("tamper-issuer");
        License license = issuer.createLicense(
                LicenseType.STANDARD,
                "Standard Trader",
                "standard@example.com",
                Instant.now().plus(30, ChronoUnit.DAYS));
        String licenseKey = issuer.generateLicenseKey(license);
        String tampered = licenseKey.substring(0, licenseKey.length() - 2) + "xx";

        LicenseManager manager = newManager("tamper");

        assertFalse(manager.activateLicense(tampered));
    }

    @Test
    void rejectsExpiredLicenseKey() {
        LicenseManager issuer = newManager("expired-issuer");
        License license = issuer.createLicense(
                LicenseType.STANDARD,
                "Expired Trader",
                "expired@example.com",
                Instant.now().minus(1, ChronoUnit.DAYS));
        String licenseKey = issuer.generateLicenseKey(license);

        LicenseManager manager = newManager("expired");

        assertFalse(manager.activateLicense(licenseKey));
    }

    private LicenseManager newManager(String name) {
        Preferences preferences = Preferences.userRoot().node("/org/investpro/tests/license/" + name);
        try {
            preferences.clear();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        return new LicenseManager(
                null,
                preferences,
                tempDir.resolve(name).resolve("license.dat"),
                "license");
    }
}
