package org.investpro.ui.panels;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import org.investpro.licensing.LicenseManager;
import org.investpro.licensing.LicenseStatus;

/**
 * UI Panel for displaying and managing application license.
 * Shows license status, expiration, and allows activation of new licenses.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
public final class LicensePanel extends VBox {
    private static final String PRIMARY_BG = "#0f172a";
    private static final String CARD_BG = "#1e293b";
    private static final String TEXT_PRIMARY = "#f1f5f9";
    private static final String TEXT_SECONDARY = "#cbd5e1";
    private static final String ACCENT_COLOR = "#3b82f6";

    private final LicenseManager licenseManager;
    private final Label statusLabel = new Label();
    private final Label licenseeLabel = new Label();
    private final Label typeLabel = new Label();
    private final Label expirationLabel = new Label();
    private final Label limitationsLabel = new Label();

    public LicensePanel(LicenseManager licenseManager) {
        this.licenseManager = licenseManager;
        initializeUI();
        updateDisplay();
    }

    private void initializeUI() {
        setPrefHeight(400);
        setSpacing(16);
        setPadding(new Insets(20));
        setStyle("-fx-background-color: " + PRIMARY_BG + ";");

        // Title
        Label titleLabel = new Label("📜 License Information");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + ACCENT_COLOR + ";");

        // Status card
        VBox statusCard = createStatusCard();

        // Details section
        VBox detailsSection = createDetailsSection();

        // Action buttons
        HBox actionsBox = createActionsBox();

        getChildren().addAll(titleLabel, statusCard, detailsSection, actionsBox);
    }

    private VBox createStatusCard() {
        VBox card = new VBox(12);
        card.setStyle("-fx-background-color: " + CARD_BG + "; -fx-padding: 16; -fx-border-radius: 8;");
        card.setPadding(new Insets(16));

        statusLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #10b981;");
        statusLabel.setText("Status: VALID");

        licenseeLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: " + TEXT_PRIMARY + ";");
        licenseeLabel.setText("Licensee: Trial User");

        card.getChildren().addAll(statusLabel, licenseeLabel);
        return card;
    }

    private VBox createDetailsSection() {
        VBox section = new VBox(10);
        section.setStyle("-fx-padding: 16; -fx-border-color: " + CARD_BG + "; -fx-border-width: 1;");

        Label detailsTitle = new Label("License Details");
        detailsTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: " + TEXT_PRIMARY + ";");

        typeLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + ";");
        typeLabel.setText("Type: Trial");

        expirationLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + ";");
        expirationLabel.setText("Expiration: 7 days");

        limitationsLabel.setStyle("-fx-text-fill: " + TEXT_SECONDARY + ";");
        limitationsLabel.setText("Max Connections: 1 | Max Strategies: 1");

        section.getChildren().addAll(detailsTitle, typeLabel, expirationLabel, limitationsLabel);
        return section;
    }

    private HBox createActionsBox() {
        HBox box = new HBox(12);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(16, 0, 0, 0));

        Button activateButton = new Button("🔑 Activate License");
        activateButton.setStyle(
                "-fx-padding: 8 16; -fx-background-color: " + ACCENT_COLOR
                        + "; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        activateButton.setOnAction(event -> showActivationDialog());

        Button viewButton = new Button("📋 View Details");
        viewButton.setStyle(
                "-fx-padding: 8 16; -fx-background-color: #475569; -fx-text-fill: white; -fx-font-weight: bold; -fx-cursor: hand;");
        viewButton.setOnAction(event -> showLicenseDetails());

        box.getChildren().addAll(activateButton, viewButton);
        return box;
    }

    private void showActivationDialog() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Activate License");
        dialog.setHeaderText("Enter your license key to activate");

        TextField licenseKeyField = new TextField();
        licenseKeyField.setPromptText("Enter license key...");
        licenseKeyField.setStyle(
                "-fx-control-inner-background: " + CARD_BG + "; -fx-text-fill: " + TEXT_PRIMARY
                        + "; -fx-prompt-text-fill: " + TEXT_SECONDARY + ";");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.getChildren().add(licenseKeyField);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane()
                .setStyle("-fx-background-color: " + PRIMARY_BG + "; -fx-text-fill: " + TEXT_PRIMARY + ";");

        Button activateBtn = new Button("Activate");
        activateBtn.setOnAction(e -> {
            String licenseKey = licenseKeyField.getText().trim();
            if (!licenseKey.isBlank()) {
                if (licenseManager.activateLicense(licenseKey)) {
                    updateDisplay();
                    dialog.close();
                    log.info("License activated successfully");
                } else {
                    licenseKeyField.setStyle(
                            "-fx-control-inner-background: " + CARD_BG
                                    + "; -fx-text-fill: #ef4444; -fx-border-color: #ef4444;");
                }
            }
        });

        dialog.getDialogPane().getButtonTypes().add(
                new javafx.scene.control.ButtonType("Activate", javafx.scene.control.ButtonBar.ButtonData.OK_DONE));
    }

    private void showLicenseDetails() {
        LicenseStatus status = licenseManager.getStatus();
        String details = "License Type: " + status.getLicenseType().getDisplayName() + "\n" +
                "Licensee: " + status.getLicenseeName() + "\n" +
                "Status: " + status.getStatusText() + "\n" +
                "Days Until Expiration: " + status.getDaysUntilExpiration() + "\n" +
                "Max Connections: " + status.getMaxConnections() + "\n" +
                "Max Strategies: " + status.getMaxStrategies() + "\n";

        Label detailsLabel = new Label(details);
        detailsLabel.setStyle("-fx-text-fill: " + TEXT_PRIMARY + "; -fx-font-family: monospace;");

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("License Details");
        dialog.getDialogPane().setContent(detailsLabel);
        dialog.getDialogPane().getButtonTypes().add(javafx.scene.control.ButtonType.CLOSE);
        dialog.showAndWait();
    }

    public void updateDisplay() {
        LicenseStatus status = licenseManager.getStatus();

        // Update status
        statusLabel.setText("Status: " + status.getStatusText());
        statusLabel.setStyle(
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: " + status.getStatusColor() + ";");

        licenseeLabel.setText("Licensee: " + status.getLicenseeName());
        typeLabel.setText("Type: " + status.getLicenseType().getDisplayName());

        if (status.isExpired()) {
            expirationLabel.setText("Expiration: EXPIRED");
            expirationLabel.setStyle("-fx-text-fill: #ef4444;");
        } else if (status.getDaysUntilExpiration() == Long.MAX_VALUE) {
            expirationLabel.setText("Expiration: Perpetual");
            expirationLabel.setStyle("-fx-text-fill: #10b981;");
        } else {
            expirationLabel.setText("Expiration: " + status.getDaysUntilExpiration() + " days remaining");
        }

        limitationsLabel.setText("Max Connections: " + status.getMaxConnections() + " | Max Strategies: "
                + status.getMaxStrategies());
    }
}
