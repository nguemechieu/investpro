package org.investpro.investpro;

public final class Utils {

    // Constants used for precision handling
    public static final int MAX_ALLOWED_PRECISION = 16; // Adjust as needed
    // Maximum value for a long divided by 10
    public static final long MAX_LONG_DIVIDED_BY_10 = Long.MAX_VALUE / 10;
    public static final int MAX_LONG_LENGTH = Long.toString(MAX_LONG_DIVIDED_BY_10).length() - 1;
    public static long[] MULTIPLIERS;


    static {
        MULTIPLIERS = new long[MAX_ALLOWED_PRECISION + 1];
        MULTIPLIERS[0] = 1;
        for (int i = 1; i <= MAX_ALLOWED_PRECISION; i++) {
            MULTIPLIERS[i] = MULTIPLIERS[i - 1] * 10;
        }
    }

    // Prevent instantiation
    private Utils() {
    }

    public static void checkPrecision(int precision) {
        if (precision < 0 || precision > MAX_ALLOWED_PRECISION) {
            throw new IllegalArgumentException("Precision out of bounds: %d".formatted(precision));
        }
    }
}
