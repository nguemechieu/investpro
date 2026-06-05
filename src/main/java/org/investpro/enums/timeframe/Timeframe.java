package org.investpro.enums.timeframe;

import lombok.Getter;

/**
 * Represents a trading timeframe (candle duration).
 * Supports standard forex/crypto/stock timeframes.
 */
@Getter
public enum Timeframe {
    M1("1m", 60, "1 Minute"),
    M3("3m", 180, "3 Minutes"),
    M5("5m", 300, "5 Minutes"),
    M15("15m", 900, "15 Minutes"),
    M30("30m", 1800, "30 Minutes"),
    H1("1h", 3600, "1 Hour"),
    H4("4h", 14400, "4 Hours"),
    H6("6h", 21600, "6 Hours"),
    H8("8h", 23200, "8 Hours"),
    D1("1d", 86400, "1 Day"),
    W1("1w", 604800, "1 Week"),
    MN("1M", 2592000, "1 Month");


    private final String code;
    private final int seconds;
    private final String displayName;

    Timeframe(String code, int seconds, String displayName) {
        this.code = code;
        this.seconds = seconds;
        this.displayName = displayName;
    }

    public static Timeframe fromCode(String code) {
        for (Timeframe tf : Timeframe.values()) {
            if (tf.code.equalsIgnoreCase(code)) {
                return tf;
            }
        }
        throw new IllegalArgumentException("Unknown timeframe code: " + code);
    }

    public static Timeframe fromSeconds(int seconds) {
        for (Timeframe tf : Timeframe.values()) {
            if (tf.seconds == seconds) {
                return tf;
            }
        }
        throw new IllegalArgumentException("Unknown timeframe for seconds: " + seconds);
    }

    public boolean isIntraday() {
        return this.ordinal() <= H4.ordinal();
    }

    public boolean isSwing() {
        return this.ordinal() >= H1.ordinal() && this.ordinal() <= D1.ordinal();
    }

    public boolean isLongTerm() {
        return this.ordinal() >= W1.ordinal();
    }
}
