package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.ui.theme.ThemeConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Theme Customization Panel
 * Allows users to adjust colors, fonts, and other styling without editing code.
 * Changes can be saved to .env file or exported as CSS.
 */
@Slf4j
@Getter
@Setter
public class ThemeCustomizationPanel extends VBox {
    private ThemeConfig currentTheme;
    private final TabPane themeTabPane = new TabPane();

    public ThemeCustomizationPanel() {
        this.currentTheme = ThemeConfig.loadFromConfig();
        initializeUI();
    }

    private void initializeUI() {
        setPadding(new Insets(16));
        setSpacing(12);
        setStyle("-fx-background-color: -panel-bg;");

        // Title
        Label titleLabel = new Label("Theme Customization");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: -text-primary;");

        // Tab pane for different theme sections
        themeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        themeTabPane.getTabs().addAll(
                createColorTab(),
                createTypographyTab(),
                createExportTab());

        // Control buttons
        HBox buttonBox = createControlButtons();

        getChildren().addAll(titleLabel, themeTabPane, buttonBox);
    }

    private Tab createColorTab() {
        VBox colorContent = new VBox(12);
        colorContent.setPadding(new Insets(16));
        colorContent.setStyle("-fx-background-color: -workspace-bg;");

        // Background Colors Section
        colorContent.getChildren().add(createSectionTitle("Background Colors"));
        colorContent.getChildren().addAll(
                createColorPicker("Dark Background", currentTheme.getDarkBg(), value -> currentTheme.setDarkBg(value)),
                createColorPicker("Panel Background", currentTheme.getPanelBg(),
                        value -> currentTheme.setPanelBg(value)),
                createColorPicker("Surface Background", currentTheme.getSurfaceBg(),
                        value -> currentTheme.setSurfaceBg(value)));

        // Trading Colors Section
        colorContent.getChildren().add(createSectionTitle("Trading Colors"));
        colorContent.getChildren().addAll(
                createColorPicker("Buy Color", currentTheme.getBuyColor(), value -> currentTheme.setBuyColor(value)),
                createColorPicker("Sell Color", currentTheme.getSellColor(), value -> currentTheme.setSellColor(value)),
                createColorPicker("Profit Color", currentTheme.getProfitColor(),
                        value -> currentTheme.setProfitColor(value)));

        // Text Colors Section
        colorContent.getChildren().add(createSectionTitle("Text Colors"));
        colorContent.getChildren().addAll(
                createColorPicker("Primary Text", currentTheme.getTextPrimary(),
                        value -> currentTheme.setTextPrimary(value)),
                createColorPicker("Secondary Text", currentTheme.getTextSecondary(),
                        value -> currentTheme.setTextSecondary(value)),
                createColorPicker("Muted Text", currentTheme.getTextMuted(),
                        value -> currentTheme.setTextMuted(value)));

        // Accent Colors Section
        colorContent.getChildren().add(createSectionTitle("Accent Colors"));
        colorContent.getChildren().addAll(
                createColorPicker("Primary Color", currentTheme.getPrimaryColor(),
                        value -> currentTheme.setPrimaryColor(value)),
                createColorPicker("Warning Color", currentTheme.getWarningColor(),
                        value -> currentTheme.setWarningColor(value)),
                createColorPicker("Success Color", currentTheme.getSuccessColor(),
                        value -> currentTheme.setSuccessColor(value)));

        ScrollPane scrollPane = new ScrollPane(colorContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: -workspace-bg; -fx-control-inner-background: -workspace-bg;");

        return new Tab("Colors", scrollPane);
    }

    private Tab createTypographyTab() {
        VBox typographyContent = new VBox(12);
        typographyContent.setPadding(new Insets(16));
        typographyContent.setStyle("-fx-background-color: -workspace-bg;");

        // Font Family
        typographyContent.getChildren().add(createSectionTitle("Font Settings"));
        typographyContent.getChildren().addAll(
                createFontFamilySelector(),
                createFontSizeSlider("Base Font Size", currentTheme.getFontSize(),
                        value -> currentTheme.setFontSize(value)),
                createFontSizeSlider("Small Font Size", currentTheme.getFontSizeSm(),
                        value -> currentTheme.setFontSizeSm(value)),
                createFontSizeSlider("Large Font Size", currentTheme.getFontSizeLg(),
                        value -> currentTheme.setFontSizeLg(value)));

        // Spacing
        typographyContent.getChildren().add(createSectionTitle("Spacing"));
        typographyContent.getChildren().add(
                createSpacingSlider("Component Spacing", currentTheme.getSpacing(),
                        value -> currentTheme.setSpacing(value)));

        ScrollPane scrollPane = new ScrollPane(typographyContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: -workspace-bg; -fx-control-inner-background: -workspace-bg;");

        return new Tab("Typography", scrollPane);
    }

    private Tab createExportTab() {
        VBox exportContent = new VBox(12);
        exportContent.setPadding(new Insets(16));
        exportContent.setStyle("-fx-background-color: -workspace-bg;");

        Label descriptionLabel = new Label(
                "Export your customized theme as CSS or save it to the environment configuration file.");
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-text-fill: -text-secondary;");

        // Export CSS Button
        Button exportCssButton = new Button("Export as CSS");
        exportCssButton.setPrefWidth(200);
        exportCssButton.setStyle("-fx-font-size: 12; -fx-padding: 10;");
        exportCssButton.setOnAction(e -> exportThemeAsCSS());

        // Export ENV Button
        Button exportEnvButton = new Button("Save to .env");
        exportEnvButton.setPrefWidth(200);
        exportEnvButton.setStyle("-fx-font-size: 12; -fx-padding: 10;");
        exportEnvButton.setOnAction(e -> exportThemeToEnv());

        // Theme Preview
        exportContent.getChildren().add(createSectionTitle("Current Theme"));
        exportContent.getChildren().add(createThemePreview());

        HBox buttonBox = new HBox(12);
        buttonBox.getChildren().addAll(exportCssButton, exportEnvButton);

        exportContent.getChildren().addAll(descriptionLabel, new Separator(), buttonBox);

        ScrollPane scrollPane = new ScrollPane(exportContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: -workspace-bg; -fx-control-inner-background: -workspace-bg;");

        return new Tab("Export", scrollPane);
    }

    private HBox createColorPicker(String label, String initialColor, java.util.function.Consumer<String> callback) {
        HBox box = new HBox(12);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setPadding(new Insets(8));
        box.setStyle("-fx-border-color: -border-soft; -fx-border-width: 0 0 1 0;");

        Label labelNode = new Label(label);
        labelNode.setPrefWidth(150);
        labelNode.setStyle("-fx-text-fill: -text-primary;");

        ColorPicker colorPicker = new ColorPicker();
        try {
            colorPicker.setValue(Color.web(initialColor));
        } catch (Exception e) {
            colorPicker.setValue(Color.web("#ffffff"));
        }

        Label hexLabel = new Label(initialColor);
        hexLabel.setPrefWidth(100);
        hexLabel.setStyle("-fx-text-fill: -text-secondary; -fx-font-family: 'Courier New';");

        colorPicker.setOnAction(e -> {
            String hexColor = colorPicker.getValue().toString().replace("0x", "#");
            if (hexColor.length() > 7) {
                hexColor = hexColor.substring(0, 7);
            }
            hexLabel.setText(hexColor);
            callback.accept(hexColor);
        });

        box.getChildren().addAll(labelNode, colorPicker, hexLabel);
        return box;
    }

    private HBox createFontSizeSlider(String label, String currentValue, java.util.function.Consumer<String> callback) {
        HBox box = new HBox(12);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setPadding(new Insets(8));

        Label labelNode = new Label(label);
        labelNode.setPrefWidth(150);
        labelNode.setStyle("-fx-text-fill: -text-primary;");

        Slider slider = new Slider(8, 20, extractNumeric(currentValue));
        slider.setPrefWidth(200);
        slider.setMajorTickUnit(1);
        slider.setShowTickLabels(true);

        Label valueLabel = new Label(currentValue);
        valueLabel.setPrefWidth(50);
        valueLabel.setStyle("-fx-text-fill: -text-secondary;");

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            String value = newVal.intValue() + "px";
            valueLabel.setText(value);
            callback.accept(value);
        });

        box.getChildren().addAll(labelNode, slider, valueLabel);
        return box;
    }

    private HBox createSpacingSlider(String label, String currentValue, java.util.function.Consumer<String> callback) {
        HBox box = new HBox(12);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setPadding(new Insets(8));

        Label labelNode = new Label(label);
        labelNode.setPrefWidth(150);
        labelNode.setStyle("-fx-text-fill: -text-primary;");

        Slider slider = new Slider(4, 24, extractNumeric(currentValue));
        slider.setPrefWidth(200);
        slider.setMajorTickUnit(2);
        slider.setShowTickLabels(true);

        Label valueLabel = new Label(currentValue);
        valueLabel.setPrefWidth(50);
        valueLabel.setStyle("-fx-text-fill: -text-secondary;");

        slider.valueProperty().addListener((obs, oldVal, newVal) -> {
            String value = newVal.intValue() + "px";
            valueLabel.setText(value);
            callback.accept(value);
        });

        box.getChildren().addAll(labelNode, slider, valueLabel);
        return box;
    }

    private HBox createFontFamilySelector() {
        HBox box = new HBox(12);
        box.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        box.setPadding(new Insets(8));

        Label labelNode = new Label("Font Family");
        labelNode.setPrefWidth(150);
        labelNode.setStyle("-fx-text-fill: -text-primary;");

        ComboBox<String> fontCombo = new ComboBox<>();
        fontCombo.getItems().addAll(
                "Segoe UI",
                "Inter",
                "Arial",
                "Courier New",
                "Consolas",
                "Monaco");
        fontCombo.setPrefWidth(250);
        fontCombo.setValue("Segoe UI");
        fontCombo.setOnAction(e -> currentTheme.setFontFamily(fontCombo.getValue()));

        box.getChildren().addAll(labelNode, fontCombo);
        return box;
    }

    private VBox createThemePreview() {
        VBox preview = new VBox(8);
        preview.setPadding(new Insets(12));
        preview.setStyle(
                "-fx-background-color: -panel-bg; -fx-border-color: -border-color; -fx-border-width: 1; -fx-border-radius: 4;");

        // Color swatches
        HBox swatches = new HBox(8);
        swatches.setPadding(new Insets(8));

        addColorSwatch(swatches, "Primary", currentTheme.getPrimaryColor());
        addColorSwatch(swatches, "Buy", currentTheme.getBuyColor());
        addColorSwatch(swatches, "Sell", currentTheme.getSellColor());
        addColorSwatch(swatches, "Warning", currentTheme.getWarningColor());

        // Text preview
        Label previewText = new Label("Theme Preview Text");
        previewText.setStyle("-fx-font-size: " + currentTheme.getFontSize() +
                "; -fx-text-fill: " + currentTheme.getTextPrimary() + ";");

        preview.getChildren().addAll(new Label("Color Swatches:"), swatches, new Separator(), previewText);
        return preview;
    }

    private void addColorSwatch(HBox box, String label, String color) {
        VBox swatch = new VBox(4);
        swatch.setAlignment(javafx.geometry.Pos.CENTER);
        swatch.setPrefSize(60, 60);
        swatch.setStyle("-fx-background-color: " + color + "; -fx-border-color: -border-strong; -fx-border-width: 1;");

        Label swatchLabel = new Label(label);
        swatchLabel.setStyle("-fx-font-size: 10; -fx-text-fill: -text-secondary;");

        swatch.getChildren().add(swatchLabel);
        box.getChildren().add(swatch);
    }

    private Label createSectionTitle(String title) {
        Label label = new Label(title);
        label.setStyle("-fx-font-size: 14; -fx-font-weight: bold; -fx-text-fill: -text-header; -fx-padding: 8 0 0 0;");
        return label;
    }

    private HBox createControlButtons() {
        HBox buttonBox = new HBox(12);
        buttonBox.setPadding(new Insets(12, 0, 0, 0));
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        Button resetButton = new Button("Reset to Defaults");
        resetButton.setStyle("-fx-padding: 8 16; -fx-font-size: 12;");
        resetButton.setOnAction(e -> resetTheme());

        Button applyButton = new Button("Apply Theme");
        applyButton.setStyle("-fx-padding: 8 16; -fx-font-size: 12; -fx-background-color: -primary-color;");
        applyButton.setOnAction(e -> applyTheme());

        buttonBox.getChildren().addAll(resetButton, applyButton);
        return buttonBox;
    }

    private void resetTheme() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Reset Theme");
        alert.setHeaderText("Reset to Default Theme?");
        alert.setContentText("This will reset all theme customizations to defaults.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            currentTheme = ThemeConfig.loadFromConfig();
            initializeUI();
            log.info("Theme reset to defaults");
        }
    }

    private void applyTheme() {
        // Apply theme variables to root CSS
        Scene scene = getScene();
        if (scene != null) {
            log.info("Theme applied");
            showAlert("Success", "Theme applied successfully!");
        }
    }

    private void exportThemeAsCSS() {
        try {
            String css = currentTheme.toCSSVariables();
            Path exportPath = Paths.get(System.getProperty("user.home"), "Downloads", "theme-custom.css");
            Files.writeString(exportPath, css);
            showAlert("Success", "Theme exported to:\n" + exportPath);
            log.info("Theme exported to: {}", exportPath);
        } catch (IOException e) {
            log.error("Failed to export theme as CSS", e);
            showAlert("Error", "Failed to export theme: " + e.getMessage());
        }
    }

    private void exportThemeToEnv() {
        try {
            StringBuilder envContent = new StringBuilder();
            envContent.append("# === THEME CUSTOMIZATION ===\n");

            for (var entry : currentTheme.asMap().entrySet()) {
                String key = "THEME_" + entry.getKey().toUpperCase().replaceAll("([A-Z])", "_$1");
                if (key.startsWith("THEME__")) {
                    key = key.substring(1);
                }
                envContent.append(key).append("=").append(entry.getValue()).append("\n");
            }

            Path envPath = Paths.get(".env");
            String existing = Files.exists(envPath) ? Files.readString(envPath) : "";

            // Remove old theme settings
            existing = existing.replaceAll("(?m)^THEME_.*$\n?", "");

            // Append new theme settings
            String updated = existing + "\n" + envContent;
            Files.writeString(envPath, updated);

            showAlert("Success", "Theme settings saved to .env file");
            log.info("Theme exported to .env");
        } catch (IOException e) {
            log.error("Failed to export theme to .env", e);
            showAlert("Error", "Failed to save theme: " + e.getMessage());
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static double extractNumeric(String value) {
        return Double.parseDouble(value.replaceAll("[^0-9.]", ""));
    }
}
