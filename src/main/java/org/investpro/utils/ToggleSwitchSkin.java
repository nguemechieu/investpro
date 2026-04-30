/*
 * Copyright (c) 2015, 2016 ControlsFX
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *     * Neither the name of ControlsFX, any associated website, nor the
 * names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL CONTROLSFX BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.investpro.utils;

import javafx.animation.Interpolator;
import javafx.animation.TranslateTransition;
import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
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


public class ToggleSwitchSkin extends SkinBase<ToggleSwitch> {

    private static final double DEFAULT_THUMB_MOVE_ANIMATION_TIME = 300.0;

    private final StackPane thumb;
    private final StackPane thumbArea;
    private final Label label;
    private final StackPane labelContainer;
    private final TranslateTransition transition;



    private final StyleableDoubleProperty thumbMoveAnimationTime =
            new StyleableDoubleProperty(DEFAULT_THUMB_MOVE_ANIMATION_TIME) {
                @Override
                public Object getBean() {
                    return ToggleSwitchSkin.this;
                }

                @Override
                public String getName() {
                    return "thumbMoveAnimationTime";
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.THUMB_MOVE_ANIMATION_TIME;
                }
            };
    @Getter
    @Setter
    private boolean firstLayout;

    public ToggleSwitchSkin(ToggleSwitch control) {
        super(control);

        this.thumb = new StackPane();
        this.thumbArea = new StackPane();
        this.label = new Label();
        this.labelContainer = new StackPane(label);
        this.transition = new TranslateTransition(
                Duration.millis(getThumbMoveAnimationTime()),
                thumb
        );

        label.setText(control.getText());
        label.getStyleClass().setAll("label");

        thumb.getStyleClass().setAll("thumb");
        thumbArea.getStyleClass().setAll("thumb-area");
        labelContainer.getStyleClass().setAll("label-container");

        StackPane.setAlignment(label, Pos.CENTER_LEFT);

        getChildren().addAll(labelContainer, thumbArea, thumb);

        updateLabel(control);

        thumbArea.setOnMouseReleased(event -> toggle(control));
        thumb.setOnMouseReleased(event -> toggle(control));

        control.selectedProperty().addListener((observable, oldValue, newValue) -> selectedStateChanged());

        thumbMoveAnimationTime.addListener((observable, oldValue, newValue) ->
                transition.setDuration(Duration.millis(Math.max(0.0, newValue.doubleValue())))
        );
    }

    private void toggle(@NotNull ToggleSwitch toggleSwitch) {
        toggleSwitch.setSelected(!toggleSwitch.isOn());
    }

    private void selectedStateChanged() {
        transition.stop();

        ToggleSwitch toggleSwitch = getSkinnable();
        updateLabel(toggleSwitch);

        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double thumbWidth = snapSizeX(thumb.prefWidth(-1));
        double distance = Math.max(0.0, thumbAreaWidth - thumbWidth);

        /*
         * The thumb layoutX stays anchored at the left edge of thumbArea.
         * The selected state is represented by translateX.
         */
        thumb.setLayoutX(thumbArea.getLayoutX());
        thumb.setLayoutY(
                thumbArea.getLayoutY()
                        + (thumbArea.getHeight() - thumb.getHeight()) / 2.0
        );

        if (toggleSwitch.isOn()) {
            transition.setFromX(0.0);
            transition.setToX(distance);
        } else {
            transition.setFromX(distance);
            transition.setToX(0.0);
        }

        transition.setInterpolator(Interpolator.EASE_BOTH);
        transition.setCycleCount(1);
        transition.playFromStart();
    }

    private void updateLabel(@NotNull ToggleSwitch skinnable) {
        label.setText(skinnable.isOn()
                ? skinnable.getTurnOnText()
                : skinnable.getTurnOffText());
    }

    public final StyleableDoubleProperty thumbMoveAnimationTimeProperty() {
        return thumbMoveAnimationTime;
    }

    public final double getThumbMoveAnimationTime() {
        return thumbMoveAnimationTime.get();
    }

    public final void setThumbMoveAnimationTime(double value) {
        thumbMoveAnimationTime.set(Math.max(0.0, value));
    }

    @Override
    protected void layoutChildren(
            double contentX,
            double contentY,
            double contentWidth,
            double contentHeight
    ) {
        ToggleSwitch toggleSwitch = getSkinnable();

        double thumbWidth = snapSizeX(thumb.prefWidth(-1));
        double thumbHeight = snapSizeY(thumb.prefHeight(-1));
        double thumbAreaWidth = snapSizeX(thumbArea.prefWidth(-1));
        double labelHeight = snapSizeY(thumbArea.prefHeight(-1));

        double thumbAreaX = snapPositionX(contentX + contentWidth - thumbAreaWidth);
        double thumbAreaY = snapPositionY(contentY + Math.max(0.0, contentHeight - labelHeight) / 2.0);

        double labelWidth = Math.max(0.0, contentWidth - thumbAreaWidth);

        thumb.resize(thumbWidth, thumbHeight);
        thumbArea.resize(thumbAreaWidth, labelHeight);
        labelContainer.resize(labelWidth, labelHeight);

        thumbArea.setLayoutX(thumbAreaX);
        thumbArea.setLayoutY(thumbAreaY);

        labelContainer.setLayoutX(contentX);
        labelContainer.setLayoutY(thumbAreaY);

        double distance = Math.max(0.0, thumbAreaWidth - thumbWidth);

        transition.stop();

        thumb.setLayoutX(thumbAreaX);
        thumb.setLayoutY(thumbAreaY + (labelHeight - thumbHeight) / 2.0);
        thumb.setTranslateX(toggleSwitch.isOn() ? distance : 0.0);

        firstLayout = false;
    }

    @Override
    protected double computeMinWidth(
            double height,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return leftInset
                + label.prefWidth(-1)
                + thumbArea.prefWidth(-1)
                + rightInset;
    }

    @Override
    protected double computeMinHeight(
            double width,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return topInset
                + Math.max(thumb.prefHeight(-1), label.prefHeight(-1))
                + bottomInset;
    }

    @Override
    protected double computePrefWidth(
            double height,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        final String labelText;

        if (getSkinnable().turnOnTextLonger.get()) {
            labelText = getSkinnable().getTurnOnText();
        } else {
            labelText = getSkinnable().getTurnOffText();
        }

        double textWidth = FXUtils.computeTextDimensions(labelText, label.getFont()).getWidth();

        return leftInset
                + textWidth
                + 20
                + thumbArea.prefWidth(-1)
                + rightInset;
    }

    @Override
    protected double computePrefHeight(
            double width,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return topInset
                + Math.max(thumb.prefHeight(-1), label.prefHeight(-1))
                + bottomInset;
    }

    @Override
    protected double computeMaxWidth(
            double height,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return getSkinnable().prefWidth(height);
    }

    @Override
    protected double computeMaxHeight(
            double width,
            double topInset,
            double rightInset,
            double bottomInset,
            double leftInset
    ) {
        return getSkinnable().prefHeight(width);
    }


    private static final class StyleableProperties {
        private static final CssMetaData<ToggleSwitch, Number> THUMB_MOVE_ANIMATION_TIME =
                new CssMetaData<>(
                        "-thumb-move-animation-time",
                        SizeConverter.getInstance(),
                        DEFAULT_THUMB_MOVE_ANIMATION_TIME
                ) {
                    @Override
                    public boolean isSettable(@NotNull ToggleSwitch toggleSwitch) {
                        ToggleSwitchSkin skin = (ToggleSwitchSkin) toggleSwitch.getSkin();
                        return skin != null
                                && !skin.thumbMoveAnimationTimeProperty().isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(
                            @NotNull ToggleSwitch toggleSwitch
                    ) {
                        ToggleSwitchSkin skin = (ToggleSwitchSkin) toggleSwitch.getSkin();

                        if (skin == null) {
                            throw new IllegalStateException(
                                    "ToggleSwitch skin has not been created yet."
                            );
                        }

                        return skin.thumbMoveAnimationTimeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            List<CssMetaData<? extends Styleable, ?>> styleables =
                    new ArrayList<>(SkinBase.getClassCssMetaData());

            styleables.add(THUMB_MOVE_ANIMATION_TIME);

            STYLEABLES = Collections.unmodifiableList(styleables);
        }
    }

    /**
     * @return CSS metadata associated with this skin class, including inherited metadata.
     */
    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }
}