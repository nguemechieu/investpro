package org.investpro.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

final class JavaFxRuntimeBootstrap {
    private static final Logger logger = LoggerFactory.getLogger(JavaFxRuntimeBootstrap.class);

    private JavaFxRuntimeBootstrap() {
    }

    static Properties loadLaunchProperties() {
        Path configFile = AppFiles.ensureConfigFile("config.properties");
        return AppFiles.loadProperties(configFile, "config.properties");
    }

    static void configurePrism(Properties properties) {
        String prismOrder = System.getProperty("prism.order");
        if (prismOrder == null || prismOrder.isBlank()) {
            String configuredOrder = properties.getProperty("javafx_prism_order", "").trim();
            if (!configuredOrder.isEmpty()) {
                prismOrder = configuredOrder;
                System.setProperty("prism.order", prismOrder);
            } else if (System.getProperty("os.name", "")
                    .toLowerCase(Locale.ROOT)
                    .contains("win")) {
                prismOrder = "sw";
                System.setProperty("prism.order", prismOrder);
            }
        }

        if (System.getProperty("prism.verbose") == null) {
            String prismVerbose = properties.getProperty("javafx_prism_verbose", "").trim();
            if (!prismVerbose.isEmpty()) {
                System.setProperty("prism.verbose", prismVerbose);
            }
        }

        logger.info("JavaFX Prism renderer order: {}", System.getProperty("prism.order", "<default>"));
    }
}
