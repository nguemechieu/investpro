package org.investpro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.beans.property.DoubleProperty;
import javafx.css.CssMetaData;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

public class ToggleSwitchSkin extends SkinBase<ToggleSwitch> {
    private final StackPane thumb;
    private final StackPane thumbArea;
    private final Label label;
    private final StackPane labelContainer;
    private final TranslateTransition transition;
    private boolean firstLayout = true;

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
        thumbArea.setOnMouseReleased(event -> mousePressedOnToggleSwitch(control));
        thumb.setOnMouseReleased(event -> mousePressedOnToggleSwitch(control));
        control.selectedProperty().addListener((observable, oldValue, newValue) -> selectedStateChanged());
    }

    private void selectedStateChanged() {
        if (transition != null) {
            transition.stop();
        }
        updateLabel(getSkinnable());

        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double thumbWidth = snapSizeY(thumb.prefWidth(-1));
        double distance = thumbAreaWidth - thumbWidth;

        // If we are not "ON" we must go from right to left.
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

    private void mousePressedOnToggleSwitch(@NotNull ToggleSwitch toggleSwitch) {
        toggleSwitch.setSelected(!toggleSwitch.isOn());
    }

    /**
     * How many milliseconds it should take for the thumb to go from
     * one edge to the other
     */
    private DoubleProperty thumbMoveAnimationTime;

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

    @Override
    protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
        ToggleSwitch toggleSwitch = getSkinnable();

        double thumbWidth = snapSizeX(thumb.prefWidth(-1));
        double thumbHeight = snapSizeY(thumb.prefHeight(-1));
        thumb.resize(thumbWidth, thumbHeight);

        // We must reset the translateX otherwise the thumb is misaligned when window is resized
        if (transition != null) {
            transition.stop();
        }
        thumb.setTranslateX(0);
        double thumbAreaY = snapPositionY(contentY);
        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double thumbAreaHeight = snapSizeY(thumbArea.prefHeight(-1));

        thumbArea.resize(thumbAreaWidth, thumbAreaHeight);
        thumbArea.setLayoutX(contentWidth - thumbAreaWidth);
        thumbArea.setLayoutY(thumbAreaY);

        labelContainer.resize(contentWidth - thumbAreaWidth, thumbAreaHeight);
        labelContainer.setLayoutY(thumbAreaY);

        // we only want to set the thumb position one time on the initial layout -
        // from then on its position is animated via selectedStateChanged() otherwise
        // the thumb moves twice the distance it should
        if (firstLayout) {
            if (!toggleSwitch.isOn()) {
                thumb.setLayoutX(thumbArea.getLayoutX());
            } else {
                thumb.setLayoutX(thumbArea.getLayoutX() + thumbAreaWidth - thumbWidth);
            }
            thumb.setLayoutY(thumbAreaY + (thumbAreaHeight - thumbHeight) / 2);
            firstLayout = false;
        }
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {
        return leftInset + label.prefWidth(-1) + thumbArea.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return topInset + Math.max(thumb.prefHeight(-1), label.prefHeight(-1)) + bottomInset;
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        final String labelText;
        if (getSkinnable().turnOnTextLonger.get()) {
            labelText = getSkinnable().getTurnOnText();
        } else {
            labelText = getSkinnable().getTurnOffText();
        }
        double textWidth = FXUtils.computeTextDimensions(labelText, label.getFont()).getWidth();

        return leftInset + textWidth + 20 + thumbArea.prefWidth(-1) + rightInset;
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset,
                                       double leftInset) {
        return topInset + Math.max(thumb.prefHeight(-1), label.prefHeight(-1)) + bottomInset;
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset,
                                     double leftInset) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset,
                                      double leftInset) {
        return getSkinnable().prefHeight(width);
    }

    private static final CssMetaData<ToggleSwitch, Number> THUMB_MOVE_ANIMATION_TIME = new CssMetaData<>(
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

    /**
     * @return The CssMetaData associated with this class, which may include the
     * CssMetaData of its superclasses.
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}

