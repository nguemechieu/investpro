package org.investpro.ui.navigation;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

@Slf4j
public class ScreenManager {

    private final BorderPane host;
    private StackPane centerSlot;
    private final Object lock = new Object();
    private final Deque<Screen> navigationQueue = new ArrayDeque<>();
    private Screen activeScreen;
    private boolean transitionInProgress;
    private boolean shuttingDown;

    public ScreenManager(BorderPane host) {
        this.host = Objects.requireNonNull(host, "host must not be null");
        this.centerSlot = new StackPane();
        this.host.setCenter(centerSlot);
    }

    public void show(Screen nextScreen) {
        Objects.requireNonNull(nextScreen, "nextScreen must not be null");

        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(nextScreen));
            return;
        }

        synchronized (lock) {
            if (shuttingDown || isRedundantRequest(nextScreen)) {
                return;
            }

            navigationQueue.offerLast(nextScreen);
            if (transitionInProgress) {
                return;
            }
            transitionInProgress = true;
        }

        while (true) {
            Screen next;
            synchronized (lock) {
                next = navigationQueue.pollFirst();
                if (next == null) {
                    transitionInProgress = false;
                    break;
                }
            }

            try {
                switchTo(next);
            } catch (Exception exception) {
                log.error("Screen transition failed: {}", next.getClass().getSimpleName(), exception);
                synchronized (lock) {
                    navigationQueue.clear();
                    transitionInProgress = false;
                }
                throw exception instanceof RuntimeException runtimeException
                        ? runtimeException
                        : new IllegalStateException("Screen transition failed", exception);
            }
        }
    }

    public void shutdown() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::shutdown);
            return;
        }

        synchronized (lock) {
            shuttingDown = true;
            navigationQueue.clear();
        }

        Screen previous = activeScreen;
        activeScreen = null;
        centerSlot = new StackPane();
        host.setCenter(centerSlot);
        if (previous != null) {
            safeOnHide(previous);
        }
    }

    private boolean isRedundantRequest(Screen candidate) {
        if (activeScreen == candidate) {
            return true;
        }

        Parent candidateView = candidate.getView();
        if (centerSlot.getChildren().size() == 1 && centerSlot.getChildren().getFirst() == candidateView) {
            return true;
        }

        Screen tail = navigationQueue.peekLast();
        return tail == candidate || (tail != null && tail.getView() == candidateView);
    }

    private void switchTo(@NonNull Screen nextScreen) {
        Parent nextView = Objects.requireNonNull(nextScreen.getView(), "screen view must not be null");

        if (centerSlot.getChildren().size() == 1 && centerSlot.getChildren().getFirst() == nextView) {
            activeScreen = nextScreen;
            return;
        }

        Screen previousScreen = activeScreen;
        if (previousScreen != null && previousScreen != nextScreen) {
            safeOnHide(previousScreen);
        }

        detachFromParent(nextView);

        StackPane nextSlot = new StackPane();
        nextSlot.getChildren().add(nextView);
        centerSlot = nextSlot;
        host.setCenter(nextSlot);

        activeScreen = nextScreen;
        safeOnShow(nextScreen);
    }

    private static void detachFromParent(@NonNull Parent view) {
        if (view.getParent() == null) {
            return;
        }

        if (view.getParent() instanceof BorderPane borderPane) {
            if (borderPane.getCenter() == view) {
                borderPane.setCenter(null);
                return;
            }
            if (borderPane.getTop() == view) {
                borderPane.setTop(null);
                return;
            }
            if (borderPane.getBottom() == view) {
                borderPane.setBottom(null);
                return;
            }
            if (borderPane.getLeft() == view) {
                borderPane.setLeft(null);
                return;
            }
            if (borderPane.getRight() == view) {
                borderPane.setRight(null);
                return;
            }
        }

        if (view.getParent() instanceof Pane pane) {
            while (pane.getChildren().remove(view)) {
                // Remove all stale references in case prior state left duplicates.
            }
            return;
        }

        if (view.getParent() instanceof ScrollPane scrollPane && scrollPane.getContent() == view) {
            scrollPane.setContent(null);
        }
    }

    private static void safeOnShow(Screen screen) {
        try {
            screen.onShow();
        } catch (Exception exception) {
            log.error("Screen onShow failed: {}", screen.getClass().getSimpleName(), exception);
        }
    }

    private static void safeOnHide(Screen screen) {
        try {
            screen.onHide();
        } catch (Exception exception) {
            log.error("Screen onHide failed: {}", screen.getClass().getSimpleName(), exception);
        }
    }
}
