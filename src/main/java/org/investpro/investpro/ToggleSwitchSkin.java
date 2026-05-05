package org.investpro.investpro;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public class ToggleSwitchSkin extends SkinBase<ToggleSwitch> {

    private final StackPane thumb = new StackPane();
    private final StackPane thumbArea = new StackPane();
    private final Label label = new Label();
    private final StackPane labelContainer = new StackPane(label);
    private final TranslateTransition transition;
    private final DoubleProperty thumbMoveAnimationTime = new SimpleDoubleProperty(300);
    private boolean firstLayout = true;

    protected ToggleSwitchSkin(ToggleSwitch control) {
        super(control);

        transition = new TranslateTransition(Duration.millis(getThumbMoveAnimationTime()), thumb);
        thumbMoveAnimationTime.addListener((observable, oldValue, newValue) ->
                transition.setDuration(Duration.millis(newValue.doubleValue())));

        label.setText(control.getText());
        getChildren().addAll(labelContainer, thumbArea, thumb);
        updateLabel(control);

        StackPane.setAlignment(label, Pos.CENTER_LEFT);

        control.textProperty().addListener((observable, oldValue, newValue) -> updateLabel(control));
        control.selectedProperty().addListener((observable, oldValue, newValue) -> selectedStateChanged());

        thumb.getStyleClass().setAll("thumb");
        thumbArea.getStyleClass().setAll("thumb-area");

        thumbArea.setOnMouseReleased(e -> toggleSwitchState(control));
        thumb.setOnMouseReleased(e -> toggleSwitchState(control));
    }

    private void updateLabel(@NotNull ToggleSwitch toggleSwitch) {
        label.setText(toggleSwitch.isOn() ? toggleSwitch.getTurnOnText() : toggleSwitch.getTurnOffText());
    }

    private void toggleSwitchState(@NotNull ToggleSwitch toggleSwitch) {
        toggleSwitch.setOn(!toggleSwitch.isOn());
    }

    private void selectedStateChanged() {
        if (transition != null) {
            transition.stop();
        }

        updateLabel(getSkinnable());

        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double thumbWidth = snapSizeX(thumb.prefWidth(-1));
        double distance = thumbAreaWidth - thumbWidth;

        if (!getSkinnable().isOn()) {
            transition.setFromX(distance);
            transition.setToX(0);
        } else {
            transition.setFromX(0);
            transition.setToX(distance);
        }

        transition.setInterpolator(Interpolator.EASE_IN);
        transition.setCycleCount(1);
        transition.play();
    }

    private double getThumbMoveAnimationTime() {
        return thumbMoveAnimationTime.get();
    }
}
