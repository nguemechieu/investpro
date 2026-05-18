package org.investpro;

import javafx.application.Application;

/**
 * JVM entry point used by IDE run configurations.
 *
 * Running a class that directly extends {@link Application} can fail on
 * classpath launches with "JavaFX runtime components are missing".
 * This launcher avoids that bootstrap path.
 */
public final class InvestProLauncher {

    private InvestProLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(InvestPro.class, args);
    }
}
