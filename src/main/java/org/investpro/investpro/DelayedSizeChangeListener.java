package org.investpro.investpro;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;


public abstract class DelayedSizeChangeListener implements ChangeListener<Number> {
    private final double subsequentDelay;
    private final BooleanProperty gotFirstSize;
    private final Timeline timeline;
    protected final ObservableValue<Number> containerWidth;
    protected final ObservableValue<Number> containerHeight;

    public DelayedSizeChangeListener(double initialDelay, double subsequentDelay, BooleanProperty gotFirstSize,
                                     ObservableValue<Number> containerWidth, ObservableValue<Number> containerHeight) {
        this.subsequentDelay = subsequentDelay;
        this.gotFirstSize = gotFirstSize;
        this.containerWidth = containerWidth;
        this.containerHeight = containerHeight;
        timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(initialDelay), event -> {
            gotFirstSize.setValue(true);
            timeline.stop();
        }));
        timeline.play();
    }

    public abstract void resize();

    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, final Number newValue) {
        if (timeline.getStatus() == Animation.Status.RUNNING) {
            timeline.stop();
        }

        if (gotFirstSize.get()) {
            timeline.getKeyFrames().clear();
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(subsequentDelay), event -> {
                resize();
                timeline.stop();
            }));
        }

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }
}
