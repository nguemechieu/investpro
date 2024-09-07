package org.investpro;


import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropertyLoader {

    public static @NotNull Properties loadProperties(String fileName) {
        Properties properties = new Properties();
        try (InputStream input = PropertyLoader.class.getClassLoader().getResourceAsStream(fileName)) {
            if (input == null) {
                throw new IllegalArgumentException(STR."Sorry, unable to find \{fileName}");
            }
            // Load the properties file
            properties.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return properties;
    }
}
