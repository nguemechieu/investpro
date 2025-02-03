package org.investpro;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.css.*;
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

    private DoubleProperty thumbMoveAnimationTime;

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

        transition.setInterpolator(Interpolator.EASE_IN);
        transition.setCycleCount(1);
        transition.play();
    }

    private void updateLabel(@NotNull ToggleSwitch skinnable) {
        label.setText(skinnable.isOn() ? skinnable.getTurnOnText() : skinnable.getTurnOffText());
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
        thumb.getStyleClass().setAll("thumb");
        thumbArea.getStyleClass().setAll("thumb-area");
        thumbArea.setOnMouseReleased(_ -> toggleSwitchState(control));
        thumb.setOnMouseReleased(event -> toggleSwitchState(control));
        control.selectedProperty().addListener((observable, oldValue, newValue) -> selectedStateChanged());
    }    protected static final CssMetaData<ToggleSwitch, Number> THUMB_MOVE_ANIMATION_TIME = new CssMetaData<>(
            "-thumb-move-animation-time", StyleConverter.getSizeConverter(), 300) {
        @Override
        public boolean isSettable(@NotNull ToggleSwitch toggleSwitch) {
            final ToggleSwitchSkin skin = (ToggleSwitchSkin) toggleSwitch.getSkin();
            return skin.thumbMoveAnimationTime == null ||
                    !skin.thumbMoveAnimationTime.isBound();
        }

        @Override
        public StyleableProperty<Number> getStyleableProperty(@NotNull ToggleSwitch toggleSwitch) {
            final ToggleSwitchSkin skin = (ToggleSwitchSkin) toggleSwitch.getSkin();
            return (StyleableProperty<Number>) skin.thumbMoveAnimationTimeProperty();
        }
    };

    private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

    static {
        final List<CssMetaData<? extends Styleable, ?>> style = new ArrayList<>(SkinBase.getClassCssMetaData());
        style.add(THUMB_MOVE_ANIMATION_TIME);
        STYLEABLES = Collections.unmodifiableList(style);
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    private void toggleSwitchState(@NotNull ToggleSwitch toggleSwitch) {
        toggleSwitch.setSelected(!toggleSwitch.isOn());
    }

    private DoubleProperty thumbMoveAnimationTimeProperty() {
        if (thumbMoveAnimationTime == null) {
            thumbMoveAnimationTime = new StyleableDoubleProperty(300) {
                @Override
                public Object getBean() {
                    return ToggleSwitchSkin.this;
                }

                @Override
                public String getName() {
                    return "thumbMoveAnimationTime";
                }

                @Override
                public CssMetaData<ToggleSwitch, Number> getCssMetaData() {
                    return THUMB_MOVE_ANIMATION_TIME;
                }
            };
        }
        return thumbMoveAnimationTime;
    }

    private double getThumbMoveAnimationTime() {
        return thumbMoveAnimationTime == null ? 300 : thumbMoveAnimationTime.get();
    }


}
