package org.investpro;

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
    private final StackPane thumb;
    private final StackPane thumbArea;
    private final Label label;
    private final StackPane labelContainer;
    private final TranslateTransition transition;
    private boolean firstLayout = true;

    protected static final CssMetaData<ToggleSwitch, Number> THUMB_MOVE_ANIMATION_TIME = new CssMetaData<>(
            "-thumb-move-animation-time", StyleConverter.getSizeConverter(), 300) {
        @Override
        public boolean isSettable(@NotNull ToggleSwitch toggleSwitch) {
            final ToggleSwitchSkin skin = (ToggleSwitchSkin) toggleSwitch.getSkin();


            return !skin.thumbMoveAnimationTime.isBound();
        }

        @Override
        public StyleableProperty<Number> getStyleableProperty(ToggleSwitch styleable) {
            final ToggleSwitchSkin skin = (ToggleSwitchSkin) styleable.getSkin();
            return (StyleableProperty<Number>) skin.thumbMoveAnimationTimeProperty();
        }


    };
    private final DoubleProperty thumbMoveAnimationTime = new SimpleDoubleProperty(300);

    private static final List<CssMetaData<? extends Styleable, ?>> STYLES;

    static {
        final List<CssMetaData<? extends Styleable, ?>> style = new ArrayList<>(SkinBase.getClassCssMetaData());
        style.add(THUMB_MOVE_ANIMATION_TIME);
        STYLES = Collections.unmodifiableList(style);
    }


    protected ToggleSwitchSkin(ToggleSwitch control) {
        super(control);

        thumb = new StackPane();
        thumbArea = new StackPane();
        label = new Label();
        labelContainer = new StackPane(label);
        transition = new TranslateTransition(Duration.millis(getThumbMoveAnimationTime()), thumb);

        label.setText(control.getText());
        getChildren().addAll(labelContainer, thumbArea, thumb);
        updateLabel(control);

        StackPane.setAlignment(label, Pos.CENTER_LEFT);

        control.textProperty().addListener((_, _, _) -> updateLabel(control));

        thumb.getStyleClass().setAll("thumb");
        thumbArea.getStyleClass().setAll("thumb-area");
        thumbArea.setOnMouseReleased(_ -> toggleSwitchState(control));
        thumb.setOnMouseReleased(_ -> toggleSwitchState(control));
        control.selectedProperty().addListener((_, _, _) -> selectedStateChanged());
    }


    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLES;
    }

    private void selectedStateChanged() {
        if (transition != null) {
            transition.stop();
        }
        updateLabel(getSkinnable());
        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double thumbWidth = snapSizeY(thumb.prefWidth(-1));
        double distance = thumbAreaWidth - thumbWidth;

        if (!getSkinnable().isOn()) {
            thumb.setLayoutX(thumbArea.getLayoutX());
            transition.setFromX(distance);
            transition.setToX(0);
        } else {
            thumb.setTranslateX(thumbArea.getLayoutX());
            transition.setFromX(0);
            transition.setToX(distance);
        }
        firstLayout = false;

        transition.setInterpolator(Interpolator.EASE_IN);
        transition.setCycleCount(1);
        transition.play();
    }

    private void updateLabel(@NotNull ToggleSwitch winnable) {
        label.setText(winnable.isOn() ? winnable.getTurnOnText() : winnable.getTurnOffText());
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    private void toggleSwitchState(@NotNull ToggleSwitch toggleSwitch) {
        toggleSwitch.setOn(!toggleSwitch.isOn());
    }

    private DoubleProperty thumbMoveAnimationTimeProperty() {
        return thumbMoveAnimationTime;
    }

    private double getThumbMoveAnimationTime() {
        return thumbMoveAnimationTime.get();
    }
}
