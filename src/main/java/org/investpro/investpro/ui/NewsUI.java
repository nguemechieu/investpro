package org.investpro.investpro.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.FxLifecycle;
import org.investpro.investpro.News;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class NewsUI extends VBox {
    private static final Logger logger = LoggerFactory.getLogger(NewsUI.class);

    private final Exchange exchange;
    private final ListView<News> newsListView;
    private final Label statusLabel;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public NewsUI(Exchange exchange) {
        this.exchange = exchange;
        this.newsListView = new ListView<>();
        this.statusLabel = new Label("Loading news...");
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        setSpacing(12);
        setPadding(new Insets(12));
        getStyleClass().add("desk-table-panel");

        Label titleLabel = new Label("Latest News");
        titleLabel.getStyleClass().add("desk-section-title");
        statusLabel.getStyleClass().add("desk-section-status");

        newsListView.setPlaceholder(new Label("No recent news available"));
        newsListView.setCellFactory(_ -> new NewsCell());

        getChildren().addAll(titleLabel, statusLabel, newsListView);
        VBox.setVgrow(newsListView, Priority.ALWAYS);

        startUpdating();
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                shutdown();
            }
        });
    }

    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            if (disposed.get() || !FxLifecycle.isShowing(this)) {
                return;
            }
            try {
                LocalDate oneWeekAgo = LocalDate.now().minusDays(7);
                List<News> updatedNews = exchange.getLatestNews()
                        .stream()
                        .filter(news -> news.getDate() != null)
                        .filter(news -> !news.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(oneWeekAgo))
                        .toList();

                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this), () -> {
                    newsListView.getItems().setAll(updatedNews);
                    statusLabel.setText(updatedNews.isEmpty()
                            ? "No recent news returned."
                            : "Updated " + LocalTime.now().withNano(0));
                });
            } catch (Exception e) {
                logger.error("Error fetching news", e);
                FxLifecycle.runLaterIf(() -> !disposed.get() && FxLifecycle.isShowing(this),
                        () -> statusLabel.setText("News unavailable right now."));
            }
        }, 5, 60, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        scheduler.shutdownNow();
    }

    private static final class NewsCell extends ListCell<News> {
        @Override
        protected void updateItem(News news, boolean empty) {
            super.updateItem(news, empty);
            if (empty || news == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            Label title = new Label(news.getTitle() == null ? "Untitled event" : news.getTitle());
            title.setWrapText(true);
            title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");

            Label meta = new Label(String.format(
                    "%s | %s | Forecast: %s | Previous: %s",
                    news.getDate(),
                    news.getCountry() == null ? "-" : news.getCountry(),
                    news.getForecast() == null ? "-" : news.getForecast(),
                    news.getPrevious() == null ? "-" : news.getPrevious()
            ));
            meta.setWrapText(true);
            meta.setTextFill(Color.GRAY);

            Label impact = new Label("Impact: " + (news.getImpact() == null ? "Unknown" : news.getImpact()));
            impact.setTextFill(switch (news.getImpact() == null ? "" : news.getImpact()) {
                case "High" -> Color.RED;
                case "Medium" -> Color.DARKORANGE;
                case "Low" -> Color.GREEN;
                default -> Color.LIGHTGRAY;
            });

            VBox content = new VBox(6, title, meta, impact);
            content.setPadding(new Insets(10));
            setGraphic(content);
        }
    }
}
