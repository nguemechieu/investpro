package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.news.CryptoNewsIntelligence;
import org.investpro.news.CryptoNewsItem;
import org.investpro.news.NewsAggregatorService;
import org.investpro.news.NewsCategory;
import org.investpro.news.NewsImpact;
import org.investpro.news.NewsUrgency;

import java.awt.Desktop;
import java.net.URI;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@Slf4j
public class NewsPanel extends BorderPane {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final NewsAggregatorService newsService;
    private final ObservableList<CryptoNewsItem> newsItems = FXCollections.observableArrayList();
    private final FilteredList<CryptoNewsItem> filteredItems = new FilteredList<>(newsItems, ignored -> true);
    private final TableView<CryptoNewsItem> table = new TableView<>();
    private final ComboBox<String> sourceFilter = new ComboBox<>();
    private final TextField symbolFilter = new TextField();
    private final ComboBox<NewsCategory> categoryFilter = new ComboBox<>();
    private final ComboBox<NewsUrgency> urgencyFilter = new ComboBox<>();
    private final TextField searchField = new TextField();
    private final Label statusLabel = new Label("News idle");
    private final Label detailTitle = new Label("Select a headline");
    private final TextArea detailSummary = new TextArea();
    private CryptoNewsItem selectedItem;

    public NewsPanel() {
        this(CryptoNewsIntelligence.getInstance());
    }

    public NewsPanel(NewsAggregatorService newsService) {
        this.newsService = newsService;
        getStyleClass().add("news-panel");
        setPadding(new Insets(12));
        setTop(createToolbar());
        setCenter(createTable());
        setRight(createDetailPanel());
        setBottom(statusLabel);
        refreshFromRepository();
    }

    private HBox createToolbar() {
        Button refresh = new Button("Refresh News");
        refresh.setOnAction(event -> refreshNews());

        sourceFilter.setPromptText("Source");
        sourceFilter.getItems().add("All Sources");
        sourceFilter.setValue("All Sources");

        symbolFilter.setPromptText("Symbol");
        symbolFilter.setPrefWidth(90);

        categoryFilter.setPromptText("Category");
        categoryFilter.getItems().add(null);
        categoryFilter.getItems().addAll(NewsCategory.values());

        urgencyFilter.setPromptText("Urgency");
        urgencyFilter.getItems().add(null);
        urgencyFilter.getItems().addAll(NewsUrgency.values());

        searchField.setPromptText("Search headlines");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        sourceFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        symbolFilter.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        urgencyFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());

        HBox toolbar = new HBox(8, refresh, sourceFilter, symbolFilter, categoryFilter, urgencyFilter, searchField);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("news-toolbar");
        return toolbar;
    }

    private TableView<CryptoNewsItem> createTable() {
        table.setItems(filteredItems);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        TableColumn<CryptoNewsItem, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().publishedAt() == null
                ? ""
                : TIME_FORMAT.format(data.getValue().publishedAt())));

        TableColumn<CryptoNewsItem, String> source = new TableColumn<>("Source");
        source.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sourceName()));

        TableColumn<CryptoNewsItem, String> title = new TableColumn<>("Title");
        title.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().title()));

        TableColumn<CryptoNewsItem, String> symbols = new TableColumn<>("Symbols");
        symbols.setCellValueFactory(data -> new SimpleStringProperty(String.join(", ", data.getValue().mentionedSymbols())));

        TableColumn<CryptoNewsItem, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().category().name()));

        TableColumn<CryptoNewsItem, String> impact = new TableColumn<>("Impact");
        impact.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().impact().name()));

        TableColumn<CryptoNewsItem, String> urgency = new TableColumn<>("Urgency");
        urgency.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().urgency().name()));

        table.getColumns().addAll(time, source, title, symbols, category, impact, urgency);
        table.setRowFactory(view -> new TableRow<>() {
            @Override
            protected void updateItem(CryptoNewsItem item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("news-critical", "news-positive", "news-neutral");
                if (!empty && item != null) {
                    if (item.urgency() == NewsUrgency.CRITICAL || item.impact() == NewsImpact.VERY_NEGATIVE) {
                        getStyleClass().add("news-critical");
                    } else if (item.impact() == NewsImpact.POSITIVE || item.impact() == NewsImpact.VERY_POSITIVE) {
                        getStyleClass().add("news-positive");
                    } else {
                        getStyleClass().add("news-neutral");
                    }
                }
            }
        });
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> showDetail(newValue));
        return table;
    }

    private VBox createDetailPanel() {
        detailTitle.setWrapText(true);
        detailTitle.getStyleClass().add("news-detail-title");
        detailSummary.setEditable(false);
        detailSummary.setWrapText(true);
        detailSummary.setPrefWidth(340);

        Button openLink = new Button("Open Link");
        openLink.setOnAction(event -> openSelectedLink());
        Button markImportant = new Button("Mark Important");
        markImportant.setOnAction(event -> {
            if (selectedItem != null) {
                newsService.repository().markImportant(selectedItem.id(), true);
                statusLabel.setText("Marked important: " + selectedItem.title());
            }
        });

        VBox detail = new VBox(10, detailTitle, detailSummary, new HBox(8, openLink, markImportant));
        detail.setPadding(new Insets(0, 0, 0, 12));
        detail.setPrefWidth(360);
        return detail;
    }

    private void refreshNews() {
        statusLabel.setText("Refreshing crypto news...");
        newsService.refreshAsync().whenComplete((summary, throwable) -> Platform.runLater(() -> {
            if (throwable != null) {
                statusLabel.setText("News refresh failed: " + throwable.getMessage());
                return;
            }
            refreshFromRepository();
            statusLabel.setText("Last refresh: " + summary.refreshedAt()
                    + " | sources ok=" + summary.sourcesOk()
                    + " failed=" + summary.sourcesFailed()
                    + " new=" + summary.newItems());
        }));
    }

    private void refreshFromRepository() {
        newsItems.setAll(newsService.latest(500));
        sourceFilter.getItems().setAll("All Sources");
        sourceFilter.getItems().addAll(newsItems.stream()
                .map(CryptoNewsItem::sourceName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .sorted()
                .collect(Collectors.toList()));
        sourceFilter.setValue("All Sources");
        applyFilters();
    }

    private void applyFilters() {
        String source = sourceFilter.getValue();
        String symbol = symbolFilter.getText() == null ? "" : symbolFilter.getText().trim().toUpperCase(Locale.ROOT);
        NewsCategory category = categoryFilter.getValue();
        NewsUrgency urgency = urgencyFilter.getValue();
        String query = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        filteredItems.setPredicate(item -> {
            if (source != null && !"All Sources".equals(source) && !source.equals(item.sourceName())) return false;
            if (!symbol.isBlank() && !item.mentionedSymbols().contains(symbol)) return false;
            if (category != null && item.category() != category) return false;
            if (urgency != null && item.urgency() != urgency) return false;
            return query.isBlank()
                    || (item.title() != null && item.title().toLowerCase(Locale.ROOT).contains(query))
                    || (item.summary() != null && item.summary().toLowerCase(Locale.ROOT).contains(query));
        });
    }

    private void showDetail(CryptoNewsItem item) {
        selectedItem = item;
        if (item == null) {
            detailTitle.setText("Select a headline");
            detailSummary.clear();
            return;
        }
        detailTitle.setText(item.title());
        detailSummary.setText("""
                %s

                Source: %s
                Published: %s
                Symbols: %s
                Category: %s
                Impact: %s
                Urgency: %s
                Sentiment: %.2f
                """.formatted(
                item.summary() == null ? "" : item.summary(),
                item.sourceName(),
                item.publishedAt(),
                String.join(", ", item.mentionedSymbols()),
                item.category(),
                item.impact(),
                item.urgency(),
                item.sentimentScore()));
    }

    private void openSelectedLink() {
        if (selectedItem == null || selectedItem.url() == null || selectedItem.url().isBlank()) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(selectedItem.url()));
            }
        } catch (Exception exception) {
            log.warn("Unable to open news link {}", selectedItem.url(), exception);
        }
    }
}
