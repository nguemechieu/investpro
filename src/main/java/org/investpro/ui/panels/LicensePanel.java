package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.licensing.LicenseManager;
import org.investpro.licensing.LicenseStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * UI panel for displaying and managing the InvestPro application license.
 *
 * <p>Shows license status, licensee, expiration, limits, and allows activation
 * of a new license key.</p>
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
public final class LicensePanel extends VBox {

    private static final String PRIMARY_BG = "#0f172a";
    private static final String CARD_BG = "#1e293b";
    private static final String CARD_BG_DARK = "#111827";
    private static final String BORDER = "#334155";
    private static final String TEXT_PRIMARY = "#f1f5f9";
    private static final String TEXT_SECONDARY = "#cbd5e1";
    private static final String TEXT_MUTED = "#94a3b8";
    private static final String ACCENT_COLOR = "#3b82f6";
    private static final String SUCCESS = "#10b981";
    private static final String DANGER = "#ef4444";
    private static final String WARNING = "#f59e0b";

    private final LicenseManager licenseManager;

    private final Label statusLabel = new Label();
    private final Label licenseeLabel = new Label();
    private final Label typeLabel = new Label();
    private final Label expirationLabel = new Label();
    private final Label limitationsLabel = new Label();
    private final Label summaryLabel = new Label();

    public LicensePanel(@NotNull LicenseManager licenseManager) {
        this.licenseManager = Objects.requireNonNull(licenseManager, "licenseManager must not be null");
        initializeUI();
        updateDisplay();
    }

    private void initializeUI() {
        getStyleClass().add("license-panel");
        setPrefHeight(400);
        setMinHeight(320);
        setSpacing(16);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: " + PRIMARY_BG + ";");

        Label titleLabel = new Label("📜 License Information");
        titleLabel.getStyleClass().add("license-title");
        titleLabel.setStyle("""
                -fx-font-size: 18px;
                -fx-font-weight: 900;
                -fx-text-fill: %s;
                """.formatted(ACCENT_COLOR));

        summaryLabel.setWrapText(true);
        summaryLabel.setStyle("""
                -fx-font-size: 12px;
                -fx-text-fill: %s;
                """.formatted(TEXT_MUTED));

        VBox statusCard = createStatusCard();
        VBox detailsSection = createDetailsSection();
        HBox actionsBox = createActionsBox();

        getChildren().setAll(
                titleLabel,
                summaryLabel,
                statusCard,
                detailsSection,
                actionsBox
        );
    }

    private VBox createStatusCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("license-status-card");
        card.setPadding(new Insets(16));
        card.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                """.formatted(CARD_BG, BORDER));

        statusLabel.setStyle("""
                -fx-font-size: 16px;
                -fx-font-weight: 900;
                -fx-text-fill: %s;
                """.formatted(SUCCESS));

        licenseeLabel.setStyle("""
                -fx-font-size: 13px;
                -fx-text-fill: %s;
                """.formatted(TEXT_PRIMARY));

        card.getChildren().addAll(statusLabel, licenseeLabel);
        return card;
    }

    private VBox createDetailsSection() {
        VBox section = new VBox(10);
        section.getStyleClass().add("license-details-section");
        section.setPadding(new Insets(16));
        section.setStyle("""
                -fx-background-color: %s;
                -fx-border-color: %s;
                -fx-border-width: 1;
                -fx-background-radius: 8;
                -fx-border-radius: 8;
                """.formatted(CARD_BG_DARK, BORDER));

        Label detailsTitle = new Label("License Details");
        detailsTitle.setStyle("""
                -fx-font-size: 14px;
                -fx-font-weight: 900;
                -fx-text-fill: %s;
                """.formatted(TEXT_PRIMARY));

        typeLabel.setStyle(labelStyle());
        expirationLabel.setStyle(labelStyle());
        limitationsLabel.setStyle(labelStyle());
        limitationsLabel.setWrapText(true);

        section.getChildren().addAll(
                detailsTitle,
                new Separator(),
                typeLabel,
                expirationLabel,
                limitationsLabel
        );

        return section;
    }

    private HBox createActionsBox() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 0, 0, 0));

        Button activateButton = new Button("🔑 Activate License");
        activateButton.getStyleClass().addAll("primary-button", "license-activate-button");
        activateButton.setMaxWidth(Double.MAX_VALUE);
        activateButton.setStyle("""
                -fx-padding: 8 16;
                -fx-background-color: %s;
                -fx-text-fill: white;
                -fx-font-weight: 900;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-cursor: hand;
                """.formatted(ACCENT_COLOR));
        activateButton.setOnAction(event -> showActivationDialog());

        Button viewButton = new Button("📋 View Details");
        viewButton.getStyleClass().addAll("terminal-button", "license-details-button");
        viewButton.setMaxWidth(Double.MAX_VALUE);
        viewButton.setStyle("""
                -fx-padding: 8 16;
                -fx-background-color: #475569;
                -fx-text-fill: white;
                -fx-font-weight: 900;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-cursor: hand;
                """);
        viewButton.setOnAction(event -> showLicenseDetails());

        Button refreshButton = new Button("↻ Refresh");
        refreshButton.getStyleClass().addAll("terminal-button", "license-refresh-button");
        refreshButton.setStyle("""
                -fx-padding: 8 14;
                -fx-background-color: #1e293b;
                -fx-border-color: #334155;
                -fx-border-width: 1;
                -fx-text-fill: #e2e8f0;
                -fx-font-weight: 900;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-cursor: hand;
                """);
        refreshButton.setOnAction(event -> updateDisplay());

        HBox.setHgrow(activateButton, Priority.ALWAYS);
        HBox.setHgrow(viewButton, Priority.ALWAYS);

        box.getChildren().addAll(activateButton, viewButton, refreshButton);
        return box;
    }

    private void showActivationDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Activate License");
        dialog.setHeaderText("Enter your license key to activate");

        ButtonType activateType = new ButtonType("Activate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(activateType, ButtonType.CANCEL);

        TextField licenseKeyField = new TextField();
        licenseKeyField.setPromptText("Enter license key...");
        licenseKeyField.setMaxWidth(Double.MAX_VALUE);
        licenseKeyField.setStyle("""
                -fx-control-inner-background: %s;
                -fx-background-color: %s;
                -fx-text-fill: %s;
                -fx-prompt-text-fill: %s;
                -fx-border-color: %s;
                -fx-border-width: 1;
                -fx-background-radius: 6;
                -fx-border-radius: 6;
                -fx-padding: 8;
                """.formatted(CARD_BG, CARD_BG, TEXT_PRIMARY, TEXT_SECONDARY, BORDER));

        Label messageLabel = new Label();
        messageLabel.setWrapText(true);
        messageLabel.setStyle("-fx-text-fill: " + TEXT_MUTED + ";");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color: " + PRIMARY_BG + ";");
        content.getChildren().addAll(
                new Label("License Key"),
                licenseKeyField,
                messageLabel
        );

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.setContent(content);
        dialogPane.setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                """.formatted(PRIMARY_BG, TEXT_PRIMARY));

        Button activateButton = (Button) dialogPane.lookupButton(activateType);
        activateButton.disableProperty().bind(licenseKeyField.textProperty().isEmpty());

        dialog.setResultConverter(buttonType -> {
            if (buttonType == activateType) {
                return licenseKeyField.getText() == null ? "" : licenseKeyField.getText().trim();
            }
            return null;
        });

        dialog.setOnShown(event -> Platform.runLater(licenseKeyField::requestFocus));

        dialog.showAndWait().ifPresent(licenseKey -> {
            if (licenseKey.isBlank()) {
                return;
            }

            try {
                boolean activated = licenseManager.activateLicense(licenseKey);

                if (activated) {
                    updateDisplay();
                    log.info("License activated successfully");
                    showInfoDialog("License Activated", "Your InvestPro license was activated successfully.");
                } else {
                    log.warn("License activation failed");
                    showInfoDialog("Activation Failed", "The license key could not be activated. Please verify the key and try again.");
                }

            } catch (Exception exception) {
                log.error("License activation failed", exception);
                showInfoDialog("Activation Error", "License activation failed: " + rootMessage(exception));
            }
        });
    }

    private void showLicenseDetails() {
        LicenseStatus status = licenseManager.getStatus();

        TextArea detailsArea = new TextArea(buildLicenseDetails(status));
        detailsArea.setEditable(false);
        detailsArea.setWrapText(false);
        detailsArea.setPrefColumnCount(52);
        detailsArea.setPrefRowCount(10);
        detailsArea.setStyle("""
                -fx-control-inner-background: #020617;
                -fx-background-color: #020617;
                -fx-text-fill: %s;
                -fx-font-family: "Consolas", "Cascadia Mono", monospace;
                -fx-font-size: 12px;
                -fx-border-color: %s;
                -fx-border-width: 1;
                """.formatted(TEXT_PRIMARY, BORDER));

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("License Details");
        dialog.setHeaderText("Current License Details");
        dialog.getDialogPane().setContent(detailsArea);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.CLOSE);
        dialog.getDialogPane().setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                """.formatted(PRIMARY_BG, TEXT_PRIMARY));
        dialog.showAndWait();
    }

    private void showInfoDialog(String title, String message) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(title);

        Label label = new Label(message == null ? "" : message);
        label.setWrapText(true);
        label.setPadding(new Insets(16));
        label.setStyle("-fx-text-fill: " + TEXT_PRIMARY + ";");

        dialog.getDialogPane().setContent(label);
        dialog.getDialogPane().getButtonTypes().setAll(ButtonType.OK);
        dialog.getDialogPane().setStyle("""
                -fx-background-color: %s;
                -fx-text-fill: %s;
                """.formatted(PRIMARY_BG, TEXT_PRIMARY));
        dialog.showAndWait();
    }

    public void updateDisplay() {
        Runnable update = () -> {
            LicenseStatus status = licenseManager.getStatus();

            statusLabel.setText("Status: " + safe(status.getStatusText()));
            statusLabel.setStyle("""
                    -fx-font-size: 16px;
                    -fx-font-weight: 900;
                    -fx-text-fill: %s;
                    """.formatted(safeColor(status.getStatusColor())));

            licenseeLabel.setText("Licensee: " + safe(status.getLicenseeName()));

            typeLabel.setText("Type: " + safe(status.getLicenseType().getDisplayName()));

            if (status.isExpired()) {
                expirationLabel.setText("Expiration: EXPIRED");
                expirationLabel.setStyle("-fx-text-fill: " + DANGER + "; -fx-font-weight: 900;");
                summaryLabel.setText("Your license is expired. Activate or renew to unlock licensed features.");

            } else if (status.getDaysUntilExpiration() == Long.MAX_VALUE) {
                expirationLabel.setText("Expiration: Perpetual");
                expirationLabel.setStyle("-fx-text-fill: " + SUCCESS + "; -fx-font-weight: 900;");
                summaryLabel.setText("Your perpetual license is active.");

            } else {
                long days = status.getDaysUntilExpiration();
                expirationLabel.setText("Expiration: " + days + " days remaining");
                expirationLabel.setStyle("-fx-text-fill: " + expirationColor(days) + "; -fx-font-weight: 900;");
                summaryLabel.setText(days <= 7
                        ? "Your license is active, but it expires soon."
                        : "Your license is active.");
            }

            limitationsLabel.setText("Max Connections: " + status.getMaxConnections()
                    + " | Max Strategies: " + status.getMaxStrategies());
        };

        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            Platform.runLater(update);
        }
    }

    private String buildLicenseDetails(@NotNull LicenseStatus status) {
        return """
                License Type:           %s
                Licensee:               %s
                Status:                 %s
                Expired:                %s
                Days Until Expiration:  %s
                Max Connections:        %s
                Max Strategies:         %s
                """.formatted(
                safe(status.getLicenseType().getDisplayName()),
                safe(status.getLicenseeName()),
                safe(status.getStatusText()),
                status.isExpired(),
                status.getDaysUntilExpiration() == Long.MAX_VALUE
                        ? "Perpetual"
                        : status.getDaysUntilExpiration(),
                status.getMaxConnections(),
                status.getMaxStrategies()
        );
    }

    private String labelStyle() {
        return """
                -fx-text-fill: %s;
                -fx-font-size: 12px;
                """.formatted(TEXT_SECONDARY);
    }

    private String expirationColor(long days) {
        if (days <= 0) {
            return DANGER;
        }

        if (days <= 7) {
            return WARNING;
        }

        return SUCCESS;
    }

    private String safeColor(String color) {
        if (color == null || color.isBlank()) {
            return TEXT_PRIMARY;
        }

        return color;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current = throwable;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}