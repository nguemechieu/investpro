package org.investpro.risk;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Service for managing Behaviour Guard settings.
 * <p>
 * Handles persistence, loading, and validation of guard configuration.
 * Uses Java Preferences API for system-independent storage.
 */
@Slf4j
public class BehaviourGuardService {

    private static final String PREFS_NODE = "org/investpro/behaviour_guard";
    private static final String GUARD_ENABLED = "guard_enabled";
    private static final String DRAWDOWN_ENABLED = "drawdown_enabled";
    private static final String MAX_DRAWDOWN = "max_drawdown";
    private static final String EQUITY_ENABLED = "equity_enabled";
    private static final String MIN_EQUITY = "min_equity";
    private static final String WIN_LIMIT_ENABLED = "win_limit_enabled";
    private static final String MAX_WINS = "max_wins";
    private static final String LOSS_LIMIT_ENABLED = "loss_limit_enabled";
    private static final String MAX_LOSSES = "max_losses";
    private static final String HOURS_ENABLED = "hours_enabled";
    private static final String START_TIME = "start_time";
    private static final String END_TIME = "end_time";
    private static final String VOL_ENABLED = "vol_enabled";
    private static final String MAX_VOL = "max_vol";
    private static final String VOL_SOURCE = "vol_source";
    private static final String NOTES = "notes";

    private static BehaviourGuardService instance;
    private final Preferences prefs;
    private BehaviourGuardConfig currentConfig;

    public BehaviourGuardService() {
        this.prefs = Preferences.userRoot().node(PREFS_NODE);
        loadConfig();
    }

    public static synchronized BehaviourGuardService getInstance() {
        if (instance == null) {
            instance = new BehaviourGuardService();
        }
        return instance;
    }

    /**
     * Load configuration from preferences
     */
    public void loadConfig() {
        try {
            currentConfig = BehaviourGuardConfig.builder()
                    .guardEnabled(prefs.getBoolean(GUARD_ENABLED, true))
                    .drawdownProtectionEnabled(prefs.getBoolean(DRAWDOWN_ENABLED, true))
                    .maxDrawdownPercent(prefs.getDouble(MAX_DRAWDOWN, 5.0))
                    .equityGuardEnabled(prefs.getBoolean(EQUITY_ENABLED, true))
                    .minEquityThreshold(prefs.getDouble(MIN_EQUITY, 1000.0))
                    .winStreakLimitEnabled(prefs.getBoolean(WIN_LIMIT_ENABLED, true))
                    .maxConsecutiveWins(prefs.getInt(MAX_WINS, 10))
                    .lossStreakLimitEnabled(prefs.getBoolean(LOSS_LIMIT_ENABLED, true))
                    .maxConsecutiveLosses(prefs.getInt(MAX_LOSSES, 5))
                    .tradingHoursEnabled(prefs.getBoolean(HOURS_ENABLED, false))
                    .tradingStartTime(prefs.get(START_TIME, "00:00"))
                    .tradingEndTime(prefs.get(END_TIME, "23:59"))
                    .volatilityFilterEnabled(prefs.getBoolean(VOL_ENABLED, true))
                    .maxVolatilityPercent(prefs.getDouble(MAX_VOL, 50.0))
                    .volatilitySource(prefs.get(VOL_SOURCE, "ATR"))
                    .notes(prefs.get(NOTES, ""))
                    .build();

            log.info("Loaded behaviour guard config: {}", currentConfig);
        } catch (Exception e) {
            log.warn("Failed to load behaviour guard config, using defaults", e);
            currentConfig = BehaviourGuardConfig.defaults();
        }
    }

    /**
     * Save configuration to preferences
     */
    public void saveConfig(BehaviourGuardConfig config) {
        try {
            prefs.putBoolean(GUARD_ENABLED, config.getGuardEnabled());
            prefs.putBoolean(DRAWDOWN_ENABLED, config.getDrawdownProtectionEnabled());
            prefs.putDouble(MAX_DRAWDOWN, config.getMaxDrawdownPercent());
            prefs.putBoolean(EQUITY_ENABLED, config.getEquityGuardEnabled());
            prefs.putDouble(MIN_EQUITY, config.getMinEquityThreshold());
            prefs.putBoolean(WIN_LIMIT_ENABLED, config.getWinStreakLimitEnabled());
            prefs.putInt(MAX_WINS, config.getMaxConsecutiveWins());
            prefs.putBoolean(LOSS_LIMIT_ENABLED, config.getLossStreakLimitEnabled());
            prefs.putInt(MAX_LOSSES, config.getMaxConsecutiveLosses());
            prefs.putBoolean(HOURS_ENABLED, config.getTradingHoursEnabled());
            prefs.put(START_TIME, config.getTradingStartTime());
            prefs.put(END_TIME, config.getTradingEndTime());
            prefs.putBoolean(VOL_ENABLED, config.getVolatilityFilterEnabled());
            prefs.putDouble(MAX_VOL, config.getMaxVolatilityPercent());
            prefs.put(VOL_SOURCE, config.getVolatilitySource());
            prefs.put(NOTES, config.getNotes() != null ? config.getNotes() : "");

            prefs.flush();
            currentConfig = config;
            log.info("Saved behaviour guard config: {}", config);
        } catch (Exception e) {
            log.error("Failed to save behaviour guard config", e);
        }
    }

    /**
     * Get current configuration
     */
    public BehaviourGuardConfig getConfig() {
        return currentConfig;
    }

    /**
     * Reset to defaults
     */
    public void resetDefaults() {
        try {
            prefs.clear();
            currentConfig = BehaviourGuardConfig.defaults();
            log.info("Reset behaviour guard to defaults");
        } catch (Exception e) {
            log.error("Failed to reset behaviour guard", e);
        }
    }

    /**
     * Validate configuration
     */
    public BehaviourGuardValidation validate(BehaviourGuardConfig config) {
        BehaviourGuardValidation validation = new BehaviourGuardValidation();

        // Check drawdown
        if (config.getDrawdownProtectionEnabled() && config.getMaxDrawdownPercent() <= 0) {
            validation.addError("Drawdown protection enabled but max drawdown is not positive");
        }
        if (config.getMaxDrawdownPercent() > 100) {
            validation.addError("Max drawdown cannot exceed 100%");
        }

        // Check equity
        if (config.getEquityGuardEnabled() && config.getMinEquityThreshold() <= 0) {
            validation.addError("Equity guard enabled but minimum equity is not positive");
        }

        // Check win/loss streaks
        if (config.getWinStreakLimitEnabled() && config.getMaxConsecutiveWins() <= 0) {
            validation.addError("Win streak limit enabled but max wins is not positive");
        }
        if (config.getLossStreakLimitEnabled() && config.getMaxConsecutiveLosses() <= 0) {
            validation.addError("Loss streak limit enabled but max losses is not positive");
        }

        // Check trading hours
        if (config.getTradingHoursEnabled()) {
            try {
                java.time.LocalTime.parse(config.getTradingStartTime());
                java.time.LocalTime.parse(config.getTradingEndTime());
            } catch (Exception e) {
                validation.addError("Invalid trading hours format. Use HH:MM");
            }
        }

        // Check volatility
        if (config.getVolatilityFilterEnabled() && config.getMaxVolatilityPercent() <= 0) {
            validation.addError("Volatility filter enabled but max volatility is not positive");
        }
        if (config.getMaxVolatilityPercent() > 100) {
            validation.addError("Max volatility cannot exceed 100%");
        }

        return validation;
    }

    /**
     * Validation result wrapper
     */
    public static class BehaviourGuardValidation {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public void addError(String error) {
            errors.add(error);
            log.warn("Guard validation error: {}", error);
        }

        public void addWarning(String warning) {
            warnings.add(warning);
            log.warn("Guard validation warning: {}", warning);
        }

        public boolean isValid() {
            return errors.isEmpty();
        }

        public java.util.List<String> getErrors() {
            return new java.util.ArrayList<>(errors);
        }

        public java.util.List<String> getWarnings() {
            return new java.util.ArrayList<>(warnings);
        }

        @Override
        public String toString() {
            if (isValid()) {
                return "BehaviourGuardValidation{valid=true}";
            }
            return String.format("BehaviourGuardValidation{errors=%d, warnings=%d}", errors.size(), warnings.size());
        }
    }
}
