package org.investpro.utils;

import javafx.scene.text.Font;

import java.util.List;
import java.util.Locale;

/**
 * Static utility methods that make working with JavaFX more pleasant.
 */
public class FXUtils {
    private static final String MONOSPACED_FONT = getMonospacedFontHelper();
    private static final String OS_NAME = System.getProperty("os.name").toLowerCase(Locale.US);
    private static final boolean IS_WINDOWS = OS_NAME.contains("windows");
    private static final boolean IS_LINUX = OS_NAME.contains("nux");
    private static final boolean IS_MAC = OS_NAME.contains("mac");

    private FXUtils() {
    }

    private static String getMonospacedFontHelper() {
        String monospacedFont;
        List<String> families = Font.getFamilies();
        if (isWindows()) {
            if (families.contains("Consolas")) {
                monospacedFont = "Consolas";
            } else {
                monospacedFont = "Monospace";
            }
        } else if (isMac()) {
            if (families.contains("Menlo")) {
                monospacedFont = "Menlo";
            } else if (families.contains("Source Code Pro")) {
                monospacedFont = "Source Code Pro";
            } else {
                monospacedFont = "Monaco";
            }
        } else {
            // Linux
            if (families.contains("DejaVu Sans Mono")) {
                monospacedFont = "DejaVu Sans Mono";
            } else if (families.contains("Source Code Pro")) {
                monospacedFont = "Source Code Pro";
            } else if (families.contains("Droid Sans Mono")) {
                monospacedFont = "Droid Sans Mono";
            } else {
                monospacedFont = "Monospace";
            }
        }

        return monospacedFont;
    }

    public static String getMonospacedFont() {
        return MONOSPACED_FONT;
    }


    /**
     * @return true if the JVM we are running on is on Apple's Mac OS X, false otherwise
     */
    public static boolean isMac() {
        return IS_MAC;
    }

    /**
     * @return true if the JVM we are running on is on Microsoft Windows, false otherwise
     */
    public static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * @return true if the JVM we are running on is Linux, false otherwise.
     */
    public static boolean isLinux() {
        return IS_LINUX;
    }
}
