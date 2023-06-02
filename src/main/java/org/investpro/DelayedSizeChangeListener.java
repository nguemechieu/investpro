package org.investpro;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

public abstract class DelayedSizeChangeListener implements ChangeListener<Number> {
    protected final ObservableValue<Number> containerWidth;
    protected final ObservableValue<Number> containerHeight;
    private final double subsequentDelay;
    private final BooleanProperty gotFirstSize;
    private final Timeline timeline;

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

    public abstract void resize() throws TelegramApiException, ParseException, IOException, InterruptedException, URISyntaxException;

    @Override
    public void changed(ObservableValue<? extends Number> observable, Number oldValue, final Number newValue) {
        if (timeline.getStatus() == Animation.Status.RUNNING) {
            timeline.stop();
        }

        if (gotFirstSize.get()) {
            timeline.getKeyFrames().clear();
            timeline.getKeyFrames().add(new KeyFrame(Duration.millis(subsequentDelay), event -> {
                try {
                    resize();
                } catch (TelegramApiException | ParseException | IOException | InterruptedException |
                         URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                timeline.stop();
            }));
        }

        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }
}
