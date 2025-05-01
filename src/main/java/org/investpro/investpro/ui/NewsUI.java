package org.investpro.investpro.ui;

import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Background;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.News;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class NewsUI extends Region {
    private static final Logger logger = LoggerFactory.getLogger(NewsUI.class);
    private final Exchange exchange;
    private final ScrollPane scrollPane;
    private final Canvas canvas;
    private final ScheduledExecutorService scheduler;
    private List<News> newsList;

    public NewsUI(Exchange exchange) {
        this.exchange = exchange;
        this.canvas = new Canvas(1200, 800);

        canvas.getGraphicsContext2D().setFill(
                Color.BLACK
        );

        this.scrollPane = createScrollPane();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        getChildren().add(scrollPane);
        startUpdating();
    }


    private @NotNull ScrollPane createScrollPane() {
        ScrollPane scrollPane = new ScrollPane(canvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setPannable(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.ALWAYS);
        scrollPane.setBackground(Background.fill(Color.DARKBLUE));  // ScrollPane background
        return scrollPane;
    }

    private void startUpdating() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<News> updatedNews = exchange.getLatestNews();
                Platform.runLater(() -> {
                    newsList = updatedNews;
                    drawNews(canvas.getGraphicsContext2D(), newsList);
                });
            } catch (Exception e) {
                logger.error("Error fetching news", e);
            }
        }, 5, 1000, TimeUnit.SECONDS);  // Fetch news every 60 seconds
    }

    private synchronized void drawNews(GraphicsContext gc, List<News> news) {
        if (gc == null) {
            logger.error("GraphicsContext is null!");
            return;
        }

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.YELLOWGREEN);
        gc.setFont(new Font("Arial", 18));

        int yOffset = 50;
        gc.fillText("📰 Latest News", 20, yOffset);
        gc.setFill(Color.YELLOW);
        gc.fillRect(20, yOffset + 5, 300, 3);
        yOffset += 40;

        LocalDate oneWeekAgo = LocalDate.now().minusDays(7);

        for (News n : news) {
            // Convert Date to LocalDate
            LocalDate newsDate = n.getDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            if (newsDate.isBefore(oneWeekAgo)) {
                continue;  // Skip older news
            }

            // Assign color based on impact
            Color impactColor = switch (n.getImpact()) {
                case "High" -> Color.RED;
                case "Medium" -> Color.ORANGE;
                case "Low" -> Color.GREEN;
                default -> Color.LIGHTGRAY;
            };

            gc.setFill(Color.LIGHTBLUE);
            gc.strokeText("📍 " + n.getTitle(), 20, yOffset);
            gc.setFill(impactColor);
            gc.fillRect(20, yOffset - 30, canvas.getWidth() - 40, 50);  // Highlight Box
            gc.setFill(Color.BLACK);
            gc.fillText(n.getDate() + " | " + n.getTitle() + " | " + n.getCountry() + " | Impact: " + n.getImpact() +
                            ", Forecast: " + n.getForecast() +
                            ", Previous: " + n.getPrevious()


                    , 30, yOffset + 20);

            // Draw border for each news entry
            gc.setStroke(Color.BLACK);
            gc.strokeRect(20, yOffset - 30, canvas.getWidth() - 40, 50);

            yOffset += 70;  // Space between news items
        }
    }
}
