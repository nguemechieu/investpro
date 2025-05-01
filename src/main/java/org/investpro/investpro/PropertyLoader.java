package org.investpro.investpro;


import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


public class PropertyLoader {

    private static final Logger logger = LoggerFactory.getLogger(PropertyLoader.class);

    public static @NotNull Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        try (InputStream input = PropertyLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new IllegalArgumentException("Sorry, unable to find " + fileName);
            }
            // Load the properties file
            properties.load(input);
        } catch (IOException ex) {
            logger.error("Error loading properties file", ex);
        }
        return properties;
    }
}
