package org.investpro.ui.navigation;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import org.investpro.ui.Navigation;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class NavigationLayoutTest {
    @Test
    void narrowNavigationFitsAndDispatchesActions() throws Exception {
        new JFXPanel();
        CompletableFuture<Void> completed = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                Navigation navigation = new Navigation();
                var requests = new ArrayList<String>();
                navigation.setOnNavigationRequested(requests::add);
                Scene scene = new Scene(navigation, 320, 480);
                scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                navigation.applyCss();
                navigation.resize(320, 480);
                navigation.layout();
                ScrollPane scroll = (ScrollPane) navigation.lookup(".scroll-pane");
                assertNotNull(scroll);
                assertTrue(scroll.getViewportBounds().getHeight() > 0);
                assertTrue(scroll.getContent().getLayoutBounds().getHeight() > scroll.getViewportBounds().getHeight());
                for (var node : navigation.lookupAll(".trading-navigation-action")) {
                    Button button = (Button) node;
                    assertTrue(button.getGraphic().getLayoutBounds().getWidth() <= button.getWidth(),
                            "Navigation graphic overflows its button");
                    button.fire();
                }
                assertTrue(requests.contains("strategy-lab"));
                assertTrue(requests.contains("account-management"));
                completed.complete(null);
            } catch (Throwable error) {
                completed.completeExceptionally(error);
            }
        });
        completed.get(30, TimeUnit.SECONDS);
    }
}
