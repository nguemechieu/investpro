package org.investpro.ui.navigation;

import javafx.scene.Parent;

public interface Screen {

    Parent getView();

    default void onShow() {
        // Optional lifecycle hook.
    }

    default void onHide() {
        // Optional lifecycle hook.
    }
}
