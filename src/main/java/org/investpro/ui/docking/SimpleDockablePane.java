package org.investpro.ui.docking;

import javafx.scene.Node;

import java.util.Objects;

/**
 * Lightweight DockablePane wrapper for existing JavaFX nodes.
 */
public final class SimpleDockablePane implements DockablePane {
    private final String id;
    private final String title;
    private final Node view;

    public SimpleDockablePane(String id, String title, Node view) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.view = Objects.requireNonNull(view, "view must not be null");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public Node getView() {
        return view;
    }
}
