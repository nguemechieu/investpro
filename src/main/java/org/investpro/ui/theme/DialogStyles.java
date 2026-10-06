package org.investpro.ui.theme;

import javafx.scene.control.Dialog;
import java.net.URL;

/** Dialog panes form a separate CSS tree and need their own theme lookup scope. */
public final class DialogStyles {
    private DialogStyles() {}

    public static void apply(Dialog<?> dialog) {
        var pane = dialog.getDialogPane();
        if (!pane.getStyleClass().contains("root")) {
            pane.getStyleClass().add("root");
        }
        for (String resource : new String[]{"/css/components.css", "/css/app.css"}) {
            URL url = DialogStyles.class.getResource(resource);
            if (url != null && !pane.getStylesheets().contains(url.toExternalForm())) {
                pane.getStylesheets().add(url.toExternalForm());
            }
        }
    }
}
