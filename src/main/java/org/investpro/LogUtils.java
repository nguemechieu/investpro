package org.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class LogUtils {

    // Create a static logger instance
    private static final Logger logger = LoggerFactory.getLogger(LogUtils.class);

    public static void setupLogger(String logFilePath) {
        try {
            // Create a FileHandler to log to the specified file
            FileHandler fileHandler = new FileHandler(logFilePath, true); // 'true' for appending logs to file

            // Set a simple formatter (you can also use XMLFormatter or custom ones)
            SimpleFormatter formatter = new SimpleFormatter();
            fileHandler.setFormatter(formatter);


            // Optionally, set the logging level (INFO, WARNING, SEVERE, etc.)
            logger.isEnabledForLevel(Level.intToLevel(0)); // Log everything

        } catch (IOException e) {
            logger.error(STR."Failed to set up logger: \{e.getMessage()}");
        }
    }

    // You can use this function to log messages
    public static void logInfo(String message) {
        logger.info(message);
    }

    public static void logWarning(String message) {
        logger.warn(message);
    }

    public static void logSevere(String message) {
        logger.error(message);
    }
}
