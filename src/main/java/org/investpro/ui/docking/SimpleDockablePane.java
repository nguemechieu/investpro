package org.investpro.ui.docking;

import javafx.scene.Node;

import java.util.Objects;

/**
 * Lightweight DockablePane wrapper for existing JavaFX nodes.
 */
public record SimpleDockablePane(String id, String title, Node view) implements DockablePane {
    public SimpleDockablePane(String id, String title, Node view) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.view = Objects.requireNonNull(view, "view must not be null");
    }
}
