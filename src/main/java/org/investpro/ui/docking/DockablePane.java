package org.investpro.ui.docking;

import javafx.scene.Node;

import java.util.Map;

/**
 * Contract for panes managed by the docking system.
 */
public interface DockablePane {
    String id();

    String title();

    Node view();

    default void onAttach() {
    }

    default void onDetach() {
    }

    default Map<String, String> saveState() {
        return Map.of();
    }

    default void restoreState(Map<String, String> state) {
    }
}
