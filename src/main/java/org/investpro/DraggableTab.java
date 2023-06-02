package org.investpro;

import javafx.beans.property.DoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
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
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class DraggableTab extends Tab {


    private static final Set<TabPane> tabPanes = new HashSet<>();
    private static final Stage markerStage;

    static {
        markerStage = new Stage();
        markerStage.initStyle(StageStyle.UNDECORATED);
        Rectangle dummy = new Rectangle(3, 10, Color.web("#555555"));
        StackPane markerStack = new StackPane();
        markerStack.getChildren().add(dummy);
        markerStage.setScene(new Scene(markerStack));
    }

    Label nameLabel;
    Stage dragStage;
    boolean detachable;


    public DraggableTab(String text, Object icon) {
        nameLabel = new Label(text);
        setGraphic(nameLabel);
        detachable = true;
        dragStage = new Stage();
        dragStage.initStyle(StageStyle.UNDECORATED);
        StackPane dragStagePane = new StackPane();
        Text dragText = new Text(text);
        StackPane.setAlignment(dragText, Pos.CENTER);
        dragStagePane.getChildren().add(dragText);
        dragStage.setScene(new Scene(dragStagePane));
        nameLabel.setOnMouseDragged(new EventHandler<>() {

            @Override

            public void handle(MouseEvent t) {
                dragStage.setWidth(nameLabel.getWidth() + 10);
                dragStage.setHeight(nameLabel.getHeight() + 10);
                dragStage.setX(t.getScreenX());
                dragStage.setY(t.getScreenY());
                dragStage.show();
                Point2D screenPoint = new Point2D(t.getScreenX(), t.getScreenY());
                tabPanes.add(getTabPane());
                InsertData data = getInsertData(screenPoint);
                if (data == null || data.insertPane().getTabs().isEmpty()) {
                    markerStage.hide();
                } else {
                    int index = data.index();
                    boolean end = false;
                    if (index == data.insertPane().getTabs().size()) {
                        end = true;
                        index--;
                    }
                    Rectangle2D rect = getAbsoluteRect(data.insertPane().getTabs().get(index));
                    if (end) {
                        markerStage.setX(rect.getMaxX() + 13);
                    } else {
                        markerStage.setX(rect.getMinX());
                    }
                    markerStage.setY(rect.getMaxY() + 10);
                    markerStage.show();
                }
            }
        });
        nameLabel.setOnMouseReleased(new EventHandler<>() {

            @Override
            public void handle(MouseEvent t) {
                markerStage.hide();
                dragStage.hide();
                if (!t.isStillSincePress()) {
                    Point2D screenPoint = new Point2D(t.getScreenX(), t.getScreenY());
                    TabPane oldTabPane = getTabPane();
                    int oldIndex = oldTabPane.getTabs().indexOf(DraggableTab.this);
                    tabPanes.add(oldTabPane);
                    InsertData insertData = getInsertData(screenPoint);
                    if (insertData != null) {
                        int addIndex = insertData.index();
                        if (oldTabPane == insertData.insertPane() && oldTabPane.getTabs().size() == 1) {
                            return;
                        }
                        oldTabPane.getTabs().remove(DraggableTab.this);
                        if (oldIndex < addIndex && oldTabPane == insertData.insertPane()) {
                            addIndex--;
                        }
                        if (addIndex > insertData.insertPane().getTabs().size()) {
                            addIndex = insertData.insertPane().getTabs().size();
                        }
                        insertData.insertPane().getTabs().add(addIndex, DraggableTab.this);
                        insertData.insertPane().selectionModelProperty().get().select(addIndex);
                        return;
                    }
                    if (!detachable) {
                        return;
                    }
                    final Stage newStage = new Stage();
                    final TabPane pane = new TabPane();
                    tabPanes.add(pane);
                    newStage.setOnHiding(t1 -> tabPanes.remove(pane));
                    getTabPane().getTabs().remove(DraggableTab.this);
                    pane.getTabs().add(DraggableTab.this);
                    pane.getTabs().addListener((ListChangeListener<Tab>) change -> {
                        if (pane.getTabs().isEmpty()) {
                            newStage.hide();
                        }
                    });
                    newStage.setScene(new Scene(pane));
                    newStage.initStyle(StageStyle.UTILITY);
                    newStage.setX(t.getScreenX());
                    newStage.setY(t.getScreenY());
                    newStage.show();
                    pane.requestLayout();
                    pane.requestFocus();
                }
            }

        });
    }

    /**
     * Set whether it's possible to detach the tab from its pane and move it to
     * another pane or another window. Defaults to true.
     * <p>
     *
     * @param detachable true if the tab should be detachable, false otherwise.
     */
    public void setDetachable(boolean detachable) {
        this.detachable = detachable;
    }

    private @Nullable InsertData getInsertData(Point2D screenPoint) {
        for (TabPane tabPane : tabPanes) {
            Rectangle2D tabAbsolute = getAbsoluteRect(tabPane);
            if (tabAbsolute.contains(screenPoint)) {
                int tabInsertIndex = 0;
                if (!tabPane.getTabs().isEmpty()) {
                    Rectangle2D firstTabRect = getAbsoluteRect(tabPane.getTabs().get(0));
                    if (firstTabRect.getMaxY() + 60 < screenPoint.getY() || firstTabRect.getMinY() > screenPoint.getY()) {
                        return null;
                    }
                    Rectangle2D lastTabRect = getAbsoluteRect(tabPane.getTabs().get(tabPane.getTabs().size() - 1));
                    if (screenPoint.getX() < (firstTabRect.getMinX() + firstTabRect.getWidth() / 2)) {


                        for (int i = 0; i < tabPane.getTabs().size() - 1; i++) {
                            Tab leftTab = tabPane.getTabs().get(i);
                            Tab rightTab = tabPane.getTabs().get(i + 1);
                            if (leftTab instanceof DraggableTab && rightTab instanceof DraggableTab) {
                                Rectangle2D leftTabRect = getAbsoluteRect(leftTab);
                                Rectangle2D rightTabRect = getAbsoluteRect(rightTab);
                                if (betweenX(leftTabRect, rightTabRect, screenPoint.getX())) {
                                    tabInsertIndex = i + 1;
                                    break;
                                }
                            }
                        }

                    } else if (screenPoint.getX() > (lastTabRect.getMaxX() - lastTabRect.getWidth() / 2)) {
                        tabInsertIndex = tabPane.getTabs().size();
                    } else {
                        for (int i = 0; i < tabPane.getTabs().size() - 1; i++) {
                            Tab leftTab = tabPane.getTabs().get(i);
                            Tab rightTab = tabPane.getTabs().get(i + 1);
                            if (leftTab instanceof DraggableTab && rightTab instanceof DraggableTab) {
                                Rectangle2D leftTabRect = getAbsoluteRect(leftTab);
                                Rectangle2D rightTabRect = getAbsoluteRect(rightTab);
                                if (betweenX(leftTabRect, rightTabRect, screenPoint.getX())) {
                                    tabInsertIndex = i + 1;
                                    break;
                                }
                            }
                        }
                    }
                }
                return new InsertData(tabInsertIndex, tabPane);
            }
        }
        return null;
    }

    @Contract("_ -> new")
    private @NotNull Rectangle2D getAbsoluteRect(Control node) {
        return new Rectangle2D(node.localToScene(node.getLayoutBounds().getMinX(), node.getLayoutBounds().getMinY()).getX() + node.getScene().getWindow().getX(),
                node.localToScene(node.getLayoutBounds().getMinX(), node.getLayoutBounds().getMinY()).getY() + node.getScene().getWindow().getY(),
                node.getWidth(),
                node.getHeight());
    }

    private @NotNull Rectangle2D getAbsoluteRect(Tab tab) {
        Control node = ((DraggableTab) tab).getLabel();
        return getAbsoluteRect(node);
    }

    private Label getLabel() {
        return nameLabel;
    }

    private boolean betweenX(@NotNull Rectangle2D r1, @NotNull Rectangle2D r2, double xPoint) {
        double lowerBound = r1.getMinX() + r1.getWidth() / 2;
        double upperBound = r2.getMaxX() - r2.getWidth() / 2;
        return xPoint >= lowerBound && xPoint <= upperBound;
    }


    record InsertData(int index, TabPane insertPane) {

    }
}
