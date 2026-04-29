package org.investpro.investpro;

import javafx.application.Application;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

public final class InvestProLauncher {
    private static final Logger logger = LoggerFactory.getLogger(InvestProLauncher.class);

    private InvestProLauncher() {


    }

  public   static void main(String[] args) {
        Properties launchProperties = JavaFxRuntimeBootstrap.loadLaunchProperties();
        JavaFxRuntimeBootstrap.configurePrism(launchProperties);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        logger.info("InvestPro started at {}", LocalDateTime.now().format(formatter));
        Application.launch(InvestPro.class, args);
    }
}
