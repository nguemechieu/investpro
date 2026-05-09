package org.investpro.licensing;

/**
 * Types of licenses available for InvestPro.
 *
 * @author NOEL NGUEMECHIEU
 */
public enum LicenseType {
    /**
     * Free trial license with limited features
     */
    TRIAL("Trial", "Limited time evaluation license", 7),

    /**
     * Standard license for individual traders
     */
    STANDARD("Standard", "Individual trader license", null),

    /**
     * Premium license with advanced features
     */
    PREMIUM("Premium", "Advanced features for professional traders", null),

    /**
     * Enterprise license for teams and institutions
     */
    ENTERPRISE("Enterprise", "Team/institution license", null),

    /**
     * Development license for testing
     */
    DEVELOPMENT("Development", "Development and testing license", 90);

    private final String displayName;
    private final String description;
    private final Integer trialDays; // null = not a trial, Integer = trial duration

    LicenseType(String displayName, String description, Integer trialDays) {
        this.displayName = displayName;
        this.description = description;
        this.trialDays = trialDays;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public Integer getTrialDays() {
        return trialDays;
    }

    public boolean isTrial() {
        return trialDays != null;
    }
}
