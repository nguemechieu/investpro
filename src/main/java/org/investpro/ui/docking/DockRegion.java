package org.investpro.ui.docking;

import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Docking zone container. Keeps pane instances and displays them as tabs.
 */
public final class DockRegion extends BorderPane {
    private static final String DRAG_PAYLOAD_PREFIX = "INVESTPRO_DOCK_PANE:";
    private final DockRegionType zone;
    private final TabPane tabPane = new TabPane();
    private final Map<String, DockablePane> panesById = new LinkedHashMap<>();
    private final Map<String, Tab> tabsById = new LinkedHashMap<>();
    private PaneMoveHandler paneMoveHandler;

    @FunctionalInterface
    public interface PaneMoveHandler {
        void requestMove(String paneId, DockRegionType targetRegion);
    }

    public DockRegion(DockRegionType zone) {
        this.zone = Objects.requireNonNull(zone, "zone must not be null");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        setCenter(tabPane);
        configureDropTargets();
    }

    public DockRegionType getZone() {
        return zone;
    }

    public boolean containsPane(String paneId) {
        return panesById.containsKey(paneId);
    }

    public void setPaneMoveHandler(PaneMoveHandler paneMoveHandler) {
        this.paneMoveHandler = paneMoveHandler;
    }

    public void addPane(DockablePane pane) {
        Objects.requireNonNull(pane, "pane must not be null");
        if (containsPane(pane.getId())) {
            return;
        }

        Tab tab = new Tab(pane.getTitle(), pane.getView());
        tab.setClosable(false);
        tab.setGraphic(createDraggableTabHeader(pane.getId(), pane.getTitle()));
        tab.setText(null);
        panesById.put(pane.getId(), pane);
        tabsById.put(pane.getId(), tab);
        tabPane.getTabs().add(tab);
        pane.onAttach();
    }

    public DockablePane removePane(String paneId) {
        Tab tab = tabsById.remove(paneId);
        DockablePane pane = panesById.remove(paneId);

        if (tab != null) {
            tabPane.getTabs().remove(tab);
        }
        if (pane != null) {
            pane.onDetach();
        }
        return pane;
    }

    private Label createDraggableTabHeader(String paneId, String title) {
        Label header = new Label(title);
        header.getStyleClass().add("dock-tab-header");
        header.setOnDragDetected(event -> {
            Dragboard dragboard = header.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(DRAG_PAYLOAD_PREFIX + paneId);
            dragboard.setContent(content);
            event.consume();
        });
        return header;
    }

    private void configureDropTargets() {
        tabPane.setOnDragOver(event -> {
            String paneId = extractPaneId(event.getDragboard());
            if (paneId != null && !containsPane(paneId)) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        tabPane.setOnDragEntered(event -> {
            String paneId = extractPaneId(event.getDragboard());
            if (paneId != null && !containsPane(paneId)) {
                tabPane.setStyle("-fx-border-color: -fx-accent; -fx-border-width: 2;");
            }
            event.consume();
        });

        tabPane.setOnDragExited(event -> {
            tabPane.setStyle("");
            event.consume();
        });

        tabPane.setOnDragDropped(event -> {
            String paneId = extractPaneId(event.getDragboard());
            boolean handled = false;
            if (paneId != null && paneMoveHandler != null && !containsPane(paneId)) {
                paneMoveHandler.requestMove(paneId, zone);
                handled = true;
            }
            event.setDropCompleted(handled);
            tabPane.setStyle("");
            event.consume();
        });
    }

    private String extractPaneId(Dragboard dragboard) {
        if (dragboard == null || !dragboard.hasString()) {
            return null;
        }
        String value = dragboard.getString();
        if (value == null || !value.startsWith(DRAG_PAYLOAD_PREFIX)) {
            return null;
        }
        String paneId = value.substring(DRAG_PAYLOAD_PREFIX.length());
        return paneId.isBlank() ? null : paneId;
    }
}
