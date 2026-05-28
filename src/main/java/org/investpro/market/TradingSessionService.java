package org.investpro.market;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.AssetClass;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.ZonedDateTime;

/**
 * Determines trading session status for instruments.
 * Supports:
 * - Crypto: always tradable (24/7)
 * - Forex: 5 days, overlapping sessions (Asia, Europe, US, Pacific)
 * - Stocks: market hours per venue (NYSE 9:30-16:00 EST, etc.)
 * - Futures: variable by contract
 * - CFDs: broker-dependent hours
 * <p>
 * Integrates with InstrumentMetadata.tradingSession for detailed rules.
 */
@Slf4j
public record TradingSessionService(InstrumentRegistry registry) {

    public TradingSessionService(@NotNull InstrumentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Determine if an instrument is tradable right now.
     */
    public boolean isTradableNow(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            log.warn("Instrument not found in registry: {}", tradePair);
            return false;
        }

        return isTradableNow(metadata);
    }

    /**
     * Determine if an instrument is tradable right now (with metadata).
     */
    public boolean isTradableNow(@NotNull InstrumentMetadata metadata) {
        // Quick check: asset class
        if (metadata.getAssetClass() == AssetClass.CRYPTO_ASSET) {
            return true; // Crypto is 24/7
        }

        // Deferred to session handler if available
        if (metadata.getTradingSession() != null) {
            ZonedDateTime now = ZonedDateTime.now();
            return metadata.getTradingSession().isTradableNow(now);
        }

        // Default: tradable if session is unknown
        return true;
    }

    /**
     * Get trading session status for an instrument.
     */
    @NotNull
    public TradingSessionStatus getSessionStatus(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            log.warn("Instrument not found in registry: {}", tradePair);
            return TradingSessionStatus.UNKNOWN;
        }

        return getSessionStatus(metadata);
    }

    /**
     * Get trading session status for an instrument (with metadata).
     */
    @NotNull
    public TradingSessionStatus getSessionStatus(@NotNull InstrumentMetadata metadata) {
        // Quick return for crypto
        if (metadata.getAssetClass() == AssetClass.CRYPTO_ASSET) {
            return TradingSessionStatus.OPEN;
        }

        // Deferred to session handler if available
        if (metadata.getTradingSession() != null) {
            ZonedDateTime now = ZonedDateTime.now();
            return metadata.getTradingSession().getStatus(now);
        }

        return TradingSessionStatus.UNKNOWN;
    }

    /**
     * Get next session opening time for an instrument.
     */
    public ZonedDateTime getNextOpenTime(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            log.warn("Instrument not found in registry: {}", tradePair);
            return ZonedDateTime.now().plusDays(1);
        }

        // For now, just return tomorrow (simplified)
        // A more sophisticated implementation would calculate based on trading session
        // rules
        return ZonedDateTime.now()
                .plusDays(1)
                .withHour(0)
                .withMinute(0)
                .withSecond(0);
    }

    /**
     * Get next session closing time for an instrument.
     */
    public ZonedDateTime getNextCloseTime(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            log.warn("Instrument not found in registry: {}", tradePair);
            return ZonedDateTime.now().plusDays(1);
        }

        // For now, just return end of day (simplified)
        return ZonedDateTime.now()
                .withHour(23)
                .withMinute(59)
                .withSecond(59);
    }

    /**
     * Get time until next trading opportunity.
     */
    public long millisUntilNextOpen(@NotNull TradePair tradePair) {
        ZonedDateTime nextOpen = getNextOpenTime(tradePair);
        return Math.max(0, nextOpen.toInstant().toEpochMilli() - System.currentTimeMillis());
    }

    /**
     * Determine if instrument is tradable during a specific time window.
     */
    public boolean isTradableDuring(@NotNull TradePair tradePair, @NotNull ZonedDateTime dateTime) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            return false;
        }

        if (metadata.getAssetClass() == AssetClass.CRYPTO_ASSET) {
            return true;
        }

        if (metadata.getTradingSession() != null) {
            return metadata.getTradingSession().isTradableNow(dateTime);
        }

        return true;
    }

    /**
     * Get typical trading hours description (human-readable).
     */
    @NotNull
    public String getTradingHoursDescription(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            return "Unknown";
        }

        if (metadata.getAssetClass() == AssetClass.CRYPTO_ASSET) {
            return "24/7";
        }

        if (metadata.getTradingSession() != null) {
            return metadata.getTradingSession().getNotes() != null
                    ? metadata.getTradingSession().getNotes()
                    : "Variable hours";
        }

        return "Unknown";
    }

    /**
     * Check if Friday/Monday might have special hours (weekend gap).
     */
    public boolean hasWeekendGap(@NotNull TradePair tradePair) {
        InstrumentMetadata metadata = registry.get(tradePair).orElse(null);
        if (metadata == null) {
            return false;
        }

        // Crypto: no weekend gap (trades 24/7)
        if (metadata.getAssetClass() == AssetClass.CRYPTO_ASSET) {
            return false;
        }

        // Forex and stocks typically have weekend gaps
        // This is a simplified check - could be more sophisticated
        return metadata.getAssetClass() == AssetClass.DERIVATIVE;
    }
}
