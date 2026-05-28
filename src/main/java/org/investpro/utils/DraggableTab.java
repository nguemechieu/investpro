package org.investpro.utils;

import javafx.collections.ListChangeListener;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

import java.util.Set;

/**
 * A detachable and draggable JavaFX Tab.
 *
 * Features:
 * - Drag tab to reorder inside the same TabPane.
 * - Drag tab to another registered TabPane.
 * - Drag tab outside any registered TabPane to detach into a new window.
 * <p>
 * Notes:
 * - Works best when all tabs in the target pane are DraggableTab instances.
 * - Non-DraggableTab tabs are handled defensively and ignored for insertion
 * geometry.
 */
@Getter
@Setter
@Slf4j
public class DraggableTab extends Tab {

    private static final Set<TabPane> TAB_PANES = new HashSet<>();
    private static final Stage MARKER_STAGE = createMarkerStage();

    private final Label nameLabel;
    private final Stage dragStage;

    /**
     * Set whether this tab can detach into a separate window.
     * Defaults to true.
     */
    private boolean detachable = true;

    public DraggableTab(String text) {
        super();

        String safeText = text == null || text.isBlank() ? "Tab" : text.trim();

        this.nameLabel = new Label(safeText);
        this.dragStage = createDragStage(safeText);

        setText(null);
        setGraphic(nameLabel);
        setClosable(true);

        installDragHandlers();
        log.info(this.getClass().getSimpleName() + " created");
    }

    public DraggableTab(String text, Node content) {
        this(text);
        setContent(content);
    }

    /**
     * Register a TabPane as a drop target.
     */
    public static void registerTabPane(TabPane tabPane) {
        if (tabPane != null) {
            TAB_PANES.add(tabPane);
        }
    }

    /**
     * Remove a TabPane from known drop targets.
     */
    public static void unregisterTabPane(TabPane tabPane) {
        if (tabPane != null) {
            TAB_PANES.remove(tabPane);
        }
    }

    /**
     * Remove stale TabPane references with no Scene.
     */
    public static void pruneDetachedTabPanes() {
        TAB_PANES.removeIf(tabPane -> tabPane == null || tabPane.getScene() == null);
    }

    public String getTitle() {
        return nameLabel.getText();
    }

    public void setTitle(String title) {
        String safeTitle = title == null || title.isBlank() ? "Tab" : title.trim();
        nameLabel.setText(safeTitle);

        Scene scene = dragStage.getScene();
        if (scene != null && scene.getRoot() instanceof StackPane root && !root.getChildren().isEmpty()) {
            Node node = root.getChildren().get(0);
            if (node instanceof Text text) {
                text.setText(safeTitle);
            }
        }
    }

    private void installDragHandlers() {
        nameLabel.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        nameLabel.addEventHandler(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
    }

    private void handleMouseDragged(MouseEvent event) {
        TabPane currentPane = getTabPane();

        if (currentPane != null) {
            TAB_PANES.add(currentPane);
        }

        pruneDetachedTabPanes();

        dragStage.setWidth(Math.max(80, nameLabel.getWidth() + 18));
        dragStage.setHeight(Math.max(28, nameLabel.getHeight() + 12));
        dragStage.setX(event.getScreenX() + 6);
        dragStage.setY(event.getScreenY() + 6);

        if (!dragStage.isShowing()) {
            dragStage.show();
        }

        Point2D screenPoint = new Point2D(event.getScreenX(), event.getScreenY());
        InsertData data = getInsertData(screenPoint);

        if (data == null || data.insertPane() == null || data.insertPane().getTabs().isEmpty()) {
            MARKER_STAGE.hide();
            return;
        }

        showInsertionMarker(data);
    }

    private void handleMouseReleased(MouseEvent event) {
        MARKER_STAGE.hide();
        dragStage.hide();

        if (event.isStillSincePress()) {
            return;
        }

        TabPane oldTabPane = getTabPane();

        if (oldTabPane == null) {
            return;
        }

        int oldIndex = oldTabPane.getTabs().indexOf(this);
        TAB_PANES.add(oldTabPane);

        Point2D screenPoint = new Point2D(event.getScreenX(), event.getScreenY());
        InsertData insertData = getInsertData(screenPoint);

        if (insertData != null && insertData.insertPane() != null) {
            moveToPane(oldTabPane, oldIndex, insertData);
            return;
        }

        if (detachable) {
            detachToNewWindow(event, oldTabPane);
        }
    }

    private void showInsertionMarker(@NotNull InsertData data) {
        TabPane pane = data.insertPane();

        if (pane.getTabs().isEmpty()) {
            MARKER_STAGE.hide();
            return;
        }

        int index = data.index();
        boolean end = false;

        if (index >= pane.getTabs().size()) {
            end = true;
            index = pane.getTabs().size() - 1;
        }

        if (index < 0) {
            MARKER_STAGE.hide();
            return;
        }

        Rectangle2D rect = getAbsoluteRect(pane.getTabs().get(index));

        if (rect == null) {
            MARKER_STAGE.hide();
            return;
        }

        if (end) {
            MARKER_STAGE.setX(rect.getMaxX() + 10);
        } else {
            MARKER_STAGE.setX(rect.getMinX());
        }

        MARKER_STAGE.setY(rect.getMaxY() + 8);

        if (!MARKER_STAGE.isShowing()) {
            MARKER_STAGE.show();
        }
    }

    private void moveToPane(
            @NotNull TabPane oldTabPane,
            int oldIndex,
            @NotNull InsertData insertData) {
        TabPane targetPane = insertData.insertPane();

        if (targetPane == null) {
            return;
        }

        if (oldTabPane == targetPane && oldTabPane.getTabs().size() == 1) {
            return;
        }

        int addIndex = insertData.index();

        oldTabPane.getTabs().remove(this);

        if (oldIndex < addIndex && oldTabPane == targetPane) {
            addIndex--;
        }

        addIndex = Math.max(0, Math.min(addIndex, targetPane.getTabs().size()));

        targetPane.getTabs().add(addIndex, this);
        targetPane.getSelectionModel().select(this);
    }

    private void detachToNewWindow(MouseEvent event, TabPane oldTabPane) {
        Stage newStage = new Stage(StageStyle.UTILITY);
        TabPane detachedPane = new TabPane();

        registerTabPane(detachedPane);

        oldTabPane.getTabs().remove(this);
        detachedPane.getTabs().add(this);
        detachedPane.getSelectionModel().select(this);

        detachedPane.getTabs().addListener((ListChangeListener<Tab>) change -> {
            if (detachedPane.getTabs().isEmpty()) {
                unregisterTabPane(detachedPane);
                newStage.hide();
            }
        });

        newStage.setTitle(getTitle());
        newStage.setScene(new Scene(detachedPane, 900, 520));
        newStage.setX(event.getScreenX());
        newStage.setY(event.getScreenY());

        newStage.setOnHiding(hidingEvent -> unregisterTabPane(detachedPane));

        newStage.show();
        detachedPane.requestLayout();
        detachedPane.requestFocus();
    }

    private @Nullable InsertData getInsertData(Point2D screenPoint) {
        if (screenPoint == null) {
            return null;
        }

        pruneDetachedTabPanes();

        for (TabPane tabPane : TAB_PANES) {
            if (tabPane == null || tabPane.getScene() == null || tabPane.getTabs().isEmpty()) {
                continue;
            }

            Rectangle2D tabPaneRect = getAbsoluteRect(tabPane);

            if (!tabPaneRect.contains(screenPoint)) {
                continue;
            }

            Rectangle2D firstTabRect = getAbsoluteRect(tabPane.getTabs().get(0));

            if (firstTabRect == null) {
                continue;
            }

            if (firstTabRect.getMaxY() + 60 < screenPoint.getY()
                    || firstTabRect.getMinY() > screenPoint.getY()) {
                return null;
            }

            int insertIndex = calculateInsertIndex(tabPane, screenPoint);
            return new InsertData(insertIndex, tabPane);
        }

        return null;
    }

    private int calculateInsertIndex(@NotNull TabPane tabPane, @NotNull Point2D screenPoint) {
        int tabCount = tabPane.getTabs().size();

        if (tabCount == 0) {
            return 0;
        }

        Rectangle2D firstTabRect = getAbsoluteRect(tabPane.getTabs().get(0));
        Rectangle2D lastTabRect = getAbsoluteRect(tabPane.getTabs().get(tabCount - 1));

        if (firstTabRect == null || lastTabRect == null) {
            return tabCount;
        }

        if (screenPoint.getX() < firstTabRect.getMinX() + firstTabRect.getWidth() / 2.0) {
            return 0;
        }

        if (screenPoint.getX() > lastTabRect.getMaxX() - lastTabRect.getWidth() / 2.0) {
            return tabCount;
        }

        for (int i = 0; i < tabCount - 1; i++) {
            Rectangle2D leftRect = getAbsoluteRect(tabPane.getTabs().get(i));
            Rectangle2D rightRect = getAbsoluteRect(tabPane.getTabs().get(i + 1));

            if (leftRect == null || rightRect == null) {
                continue;
            }

            if (betweenX(leftRect, rightRect, screenPoint.getX())) {
                return i + 1;
            }
        }

        return tabCount;
    }

    @Contract("_ -> new")
    private @Nullable Rectangle2D getAbsoluteRect(Control node) {
        if (node == null || node.getScene() == null || node.getScene().getWindow() == null) {
            return null;
        }

        Point2D localToScene = node.localToScene(
                node.getLayoutBounds().getMinX(),
                node.getLayoutBounds().getMinY());

        return new Rectangle2D(
                localToScene.getX() + node.getScene().getWindow().getX(),
                localToScene.getY() + node.getScene().getWindow().getY(),
                node.getWidth(),
                node.getHeight());
    }

    private @Nullable Rectangle2D getAbsoluteRect(Tab tab) {
        if (tab instanceof DraggableTab draggableTab) {
            return getAbsoluteRect(draggableTab.getLabel());
        }

        return null;
    }

    private Label getLabel() {
        return nameLabel;
    }

    private boolean betweenX(@NotNull Rectangle2D r1, @NotNull Rectangle2D r2, double xPoint) {
        double lowerBound = r1.getMinX() + r1.getWidth() / 2.0;
        double upperBound = r2.getMaxX() - r2.getWidth() / 2.0;
        return xPoint >= lowerBound && xPoint <= upperBound;
    }

    private static Stage createMarkerStage() {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);

        Rectangle marker = new Rectangle(3, 14, Color.web("#60a5fa"));
        StackPane markerStack = new StackPane(marker);
        markerStack.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(markerStack);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        return stage;
    }

    private static Stage createDragStage(String text) {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);

        Text dragText = new Text(text == null ? "Tab" : text);
        dragText.setFill(Color.WHITE);

        StackPane dragStagePane = new StackPane(dragText);
        dragStagePane.setAlignment(Pos.CENTER);
        dragStagePane.setStyle(
                "-fx-background-color: rgba(15, 23, 42, 0.92);"
                        + "-fx-background-radius: 6;"
                        + "-fx-border-color: #60a5fa;"
                        + "-fx-border-radius: 6;"
                        + "-fx-padding: 6 10;");

        Scene scene = new Scene(dragStagePane);
        scene.setFill(Color.TRANSPARENT);

        stage.setScene(scene);
        return stage;
    }

    record InsertData(int index, TabPane insertPane) {
    }
}
