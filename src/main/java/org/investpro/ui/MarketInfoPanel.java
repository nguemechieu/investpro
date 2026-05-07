package org.investpro.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import org.investpro.models.trading.TradePair;

/**
 * Market information panel displaying cryptocurrency/asset stats and details
 */
public class MarketInfoPanel extends ScrollPane {
    
    private final VBox contentBox;

    public MarketInfoPanel() {
        contentBox = new VBox(12);
        contentBox.setPadding(new Insets(16));
        contentBox.setStyle("-fx-control-inner-background: #1e1e1e;");
        
        this.setContent(contentBox);
        this.setFitToWidth(true);
        this.setStyle("-fx-control-inner-background: #1e1e1e;");
        
        displayWelcome();
    }
    
    /**
     * Update market info for a specific trading pair
     */
    public void updateForPair(TradePair pair) {
        contentBox.getChildren().clear();
        
        if (pair == null) {
            displayWelcome();
        } else {
            displayMarketInfo(pair);
        }
    }
    
    private void displayWelcome() {
        Label welcome = new Label("Select a symbol to view market information");
        welcome.setStyle("-fx-text-fill: #888888; -fx-font-size: 14;");
        welcome.setWrapText(true);
        
        contentBox.getChildren().add(welcome);
        contentBox.setAlignment(Pos.TOP_CENTER);
    }
    
    private void displayMarketInfo(TradePair pair) {
        contentBox.setAlignment(Pos.TOP_LEFT);
        
        // Title with symbol
        Label titleLabel = createTitleLabel(pair.toString('/'));
        contentBox.getChildren().add(titleLabel);
        
        // Add divider
        contentBox.getChildren().add(createDivider());
        
        // Education section
        contentBox.getChildren().add(createEducationSection(pair));
        
        contentBox.getChildren().add(createDivider());
        
        // Market Stats Section
        contentBox.getChildren().add(createStatsSection(pair));
        
        contentBox.getChildren().add(createDivider());
        
        // Resources/Disclaimer Section
        contentBox.getChildren().add(createResourcesSection());
    }
    
    private Label createTitleLabel(String symbol) {
        Label label = new Label(symbol);
        label.setFont(Font.font("System", FontWeight.BOLD, 24));
        label.setStyle("-fx-text-fill: #ffffff;");
        return label;
    }
    
    private VBox createEducationSection(TradePair pair) {
        VBox section = new VBox(8);
        
        Label title = new Label("About " + pair.toString('/'));
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        title.setStyle("-fx-text-fill: #cccccc;");
        
        Label educationText = new Label();
        educationText.setWrapText(true);
        educationText.setStyle("-fx-text-fill: #999999; -fx-font-size: 12;");
        
        // Provide relevant education based on symbol
        String symbol = pair.getBaseCurrency().getSymbol();
        if ("BTC".equals(symbol)) {
            educationText.setText(
                    """
                            Bitcoin (BTC) is the world's first decentralized digital currency.
                            
                            Key Unit: Each Satoshi is worth 0.00000001 BTC (1 BTC = 100,000,000 Satoshis)
                            
                            Bitcoin transactions are secured by the Proof-of-Work consensus mechanism \
                            and recorded on a distributed ledger called the blockchain."""
            );
        } else if ("ETH".equals(symbol)) {
            educationText.setText(
                "Ethereum (ETH) is a decentralized platform for smart contracts and dApps.\n\n" +
                "Key Unit: 1 ETH = 1,000,000,000,000,000,000 Wei (1 ETH = 10^18 Wei)\n\n" +
                "Ethereum uses Proof-of-Stake consensus and is the leading platform " +
                "for decentralized finance (DeFi) and non-fungible tokens (NFTs)."
            );
        } else {
            educationText.setText(
                "Market information for " + symbol + ".\n\n" +
                "Price is displayed in the base currency (typically USD).\n\n" +
                "Monitor market trends, volume, and volatility to make informed trading decisions."
            );
        }
        
        section.getChildren().addAll(title, educationText);
        return section;
    }
    
    private VBox createStatsSection(TradePair pair) {
        VBox section = new VBox(12);
        
        Label title = new Label("Market Statistics");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        title.setStyle("-fx-text-fill: #cccccc;");
        
        section.getChildren().add(title);
        
        // Create a 2-column grid layout
        VBox col1 = new VBox(8);
        VBox col2 = new VBox(8);
        
        // Left column stats
        col1.getChildren().addAll(
            createStatRow("Market Cap", "$1.6T"),
            createStatRow("All Time High (Oct 2025)", "$126.2K"),
            createStatRow("Circulating Supply", "20M BTC")
        );
        
        // Right column stats
        col2.getChildren().addAll(
            createStatRow("Volume (24h)", "$52.0B (+202.54%)"),
            createStatRow("% Down from ATH", "-36.41%"),
            createStatRow("Fully Diluted Market Cap", "$1.7T")
        );
        
        HBox statsGrid = new HBox(24, col1, col2);
        statsGrid.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(col1, Priority.ALWAYS);
        HBox.setHgrow(col2, Priority.ALWAYS);
        
        section.getChildren().add(statsGrid);
        
        // Benchmarks subsection
        VBox benchmarks = new VBox(8);
        Label benchmarkTitle = new Label("Benchmarks");
        benchmarkTitle.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12;");
        benchmarks.getChildren().addAll(
            benchmarkTitle,
            createStatRow("Performance (Past Year)", "-14.02%"),
            createStatRow("Vs. ETH (Past Year)", "-35.63%"),
            createStatRow("Vs. Market (Past Year)", "-3.53%")
        );
        
        section.getChildren().add(benchmarks);
        
        // Popularity/Ranking
        VBox ranking = new VBox(4);
        Label rankingTitle = new Label("Rankings");
        rankingTitle.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12;");
        ranking.getChildren().addAll(
            rankingTitle,
            createStatRow("Popularity", "#1")
        );
        
        section.getChildren().add(ranking);
        
        return section;
    }
    
    private HBox createStatRow(String label, String value) {
        Label labelWidget = new Label(label);
        labelWidget.setStyle("-fx-text-fill: #888888; -fx-font-size: 11;");
        labelWidget.setMinWidth(160);
        
        Label valueWidget = new Label(value);
        valueWidget.setStyle("-fx-text-fill: #10b981; -fx-font-size: 12; -fx-font-weight: bold;");
        
        HBox row = new HBox(12, labelWidget, valueWidget);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
    
    private VBox createResourcesSection() {
        VBox section = new VBox(8);
        
        Label title = new Label("Resources & Disclaimers");
        title.setFont(Font.font("System", FontWeight.SEMI_BOLD, 14));
        title.setStyle("-fx-text-fill: #cccccc;");
        
        Label disclaimer = new Label();
        disclaimer.setWrapText(true);
        disclaimer.setStyle("-fx-text-fill: #999999; -fx-font-size: 11;");
        disclaimer.setText(
            "⚠ Important Information\n\n" +
            "Displayed prices exclude trading costs. Actual execution may incur additional " +
            "spread and fees.\n\n" +
            "Third-party and user-generated content is made available for informational purposes " +
            "only and should not be treated as investment advice.\n\n" +
            "InvestPro does not endorse or recommend such content and is not responsible for " +
            "its accuracy. Always conduct your own research before making trading decisions.\n\n" +
            "Past performance is not indicative of future results. Cryptocurrency markets are " +
            "highly volatile and risky. Only trade with capital you can afford to lose."
        );
        
        section.getChildren().addAll(title, disclaimer);
        return section;
    }
    
    private VBox createDivider() {
        VBox divider = new VBox();
        divider.setStyle("-fx-border-color: #333333; -fx-border-width: 0 0 1 0;");
        divider.setPrefHeight(1);
        return divider;
    }
}
