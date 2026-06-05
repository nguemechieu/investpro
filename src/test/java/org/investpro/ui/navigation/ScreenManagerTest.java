package org.investpro.ui.navigation;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Parent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenManagerTest {

    @BeforeAll
    static void initJavaFxToolkit() {
        new JFXPanel();
    }

    @Test
    void switchesToRequestedScreenAndCallsOnShow() throws Exception {
        BorderPane host = fx(() -> new BorderPane());
        ScreenManager manager = fx(() -> new ScreenManager(host));
        TestScreen first = new TestScreen(new StackPane());

        fxRun(() -> manager.show(first));

        fxRun(() -> {
            assertCurrentCenter(host, first.getView());
            assertThat(first.showCount.get()).isEqualTo(1);
            assertThat(first.hideCount.get()).isZero();
        });
    }

    @Test
    void switchingScreensCallsOnHideForPreviousScreen() throws Exception {
        BorderPane host = fx(() -> new BorderPane());
        ScreenManager manager = fx(() -> new ScreenManager(host));
        TestScreen first = new TestScreen(new StackPane());
        TestScreen second = new TestScreen(new StackPane());

        fxRun(() -> {
            manager.show(first);
            manager.show(second);
        });

        fxRun(() -> {
            assertCurrentCenter(host, second.getView());
            assertThat(first.showCount.get()).isEqualTo(1);
            assertThat(first.hideCount.get()).isEqualTo(1);
            assertThat(second.showCount.get()).isEqualTo(1);
            assertThat(second.hideCount.get()).isZero();
        });
    }

    @Test
    void duplicateShowRequestForSameScreenIsIgnored() throws Exception {
        BorderPane host = fx(() -> new BorderPane());
        ScreenManager manager = fx(() -> new ScreenManager(host));
        TestScreen first = new TestScreen(new StackPane());

        fxRun(() -> {
            manager.show(first);
            manager.show(first);
        });

        fxRun(() -> {
            assertCurrentCenter(host, first.getView());
            assertThat(first.showCount.get()).isEqualTo(1);
            assertThat(first.hideCount.get()).isZero();
        });
    }

    @Test
    void showFromBackgroundThreadTransitionsOnFxThread() throws Exception {
        BorderPane host = fx(() -> new BorderPane());
        ScreenManager manager = fx(() -> new ScreenManager(host));
        TestScreen first = new TestScreen(new StackPane());

        CountDownLatch submitted = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            manager.show(first);
            submitted.countDown();
        });
        thread.start();

        assertThat(submitted.await(5, TimeUnit.SECONDS)).isTrue();

        fxRun(() -> {
            assertCurrentCenter(host, first.getView());
            assertThat(first.showCount.get()).isEqualTo(1);
        });
    }

    private static void assertCurrentCenter(BorderPane host, Parent expectedView) {
        assertThat(host.getCenter()).isInstanceOf(StackPane.class);
        StackPane slot = (StackPane) host.getCenter();
        assertThat(slot.getChildren()).containsExactly(expectedView);
    }

    private static void fxRun(ThrowingRunnable runnable) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];

        Platform.runLater(() -> {
            try {
                runnable.run();
            } catch (Throwable throwable) {
                error[0] = throwable;
            } finally {
                latch.countDown();
            }
        });

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        if (error[0] != null) {
            if (error[0] instanceof Exception exception) {
                throw exception;
            }
            throw new RuntimeException(error[0]);
        }
    }

    private static <T> T fx(FxSupplier<T> supplier) throws Exception {
        final Object[] value = new Object[1];
        fxRun(() -> value[0] = supplier.get());
        @SuppressWarnings("unchecked")
        T cast = (T) value[0];
        return cast;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface FxSupplier<T> {
        T get() throws Exception;
    }

    private static final class TestScreen implements Screen {
        private final Parent view;
        private final AtomicInteger showCount = new AtomicInteger();
        private final AtomicInteger hideCount = new AtomicInteger();

        private TestScreen(Parent view) {
            this.view = view;
        }

        @Override
        public Parent getView() {
            return view;
        }

        @Override
        public void onShow() {
            showCount.incrementAndGet();
        }

        @Override
        public void onHide() {
            hideCount.incrementAndGet();
        }
    }
}
