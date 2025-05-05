package org.investpro.investpro;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.css.CssMetaData;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class ToggleSwitchSkin extends SkinBase<ToggleSwitch> {

    protected static final CssMetaData<ToggleSwitch, Number> THUMB_MOVE_ANIMATION_TIME =
            new CssMetaData<>("-thumb-move-animation-time", StyleConverter.getSizeConverter(), 300.0) {

                @Override
                public boolean isSettable(@NotNull ToggleSwitch control) {
                    final ToggleSwitchSkin skin = (ToggleSwitchSkin) control.getSkin();
                    return !skin.thumbMoveAnimationTime.isBound();
                }

                @Override
                public StyleableProperty<Number> getStyleableProperty(ToggleSwitch control) {
                    final ToggleSwitchSkin skin = (ToggleSwitchSkin) control.getSkin();
                    return null;//(StyleableProperty<Number>) skin.thumbMoveAnimationTimeProperty();
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> STYLES;
    static {
        List<CssMetaData<? extends Styleable, ?>> styles = new ArrayList<>(SkinBase.getClassCssMetaData());
        styles.add(THUMB_MOVE_ANIMATION_TIME);
        STYLES = Collections.unmodifiableList(styles);
    }

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

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
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

    private DoubleProperty thumbMoveAnimationTimeProperty() {
        return thumbMoveAnimationTime;
    }

    private double getThumbMoveAnimationTime() {
        return thumbMoveAnimationTime.get();
    }
}
