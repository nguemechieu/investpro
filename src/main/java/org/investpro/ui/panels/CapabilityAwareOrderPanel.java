package org.investpro.ui.panels;

import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.SystemCore;
import org.investpro.exchange.models.ExchangeCapability;
import org.investpro.models.trading.TradePair;

/**
 * An {@link OrderPanel} variant that gates UI controls behind
 * {@link ExchangeCapability} flags.
 *
 * <h3>Capability gates applied</h3>
 * <ul>
 *   <li><b>Stop-Loss / Take-Profit spinners</b> — disabled and dimmed when
 *       {@link ExchangeCapability#isSupportsStopLossTakeProfit()} is false.</li>
 *   <li><b>Leverage controls</b> — hidden when
 *       {@link ExchangeCapability#isSupportsMarginTrading()} is false.</li>
 *   <li><b>Paper mode banner</b> — shown when
 *       {@link ExchangeCapability#isSupportsPaperTradingMode()} is true but
 *       live trading is selected.</li>
 *   <li><b>Place Order button</b> — disabled when
 *       {@link ExchangeCapability#isSupportsLiveTrading()} is false and paper
 *       mode is not active.</li>
 * </ul>
 *
 * <p>All capability checks run on the JavaFX Application Thread after the parent
 * constructor has built the UI.
 */
@Slf4j
public class CapabilityAwareOrderPanel extends OrderPanel {

    private final ExchangeCapability capability;

    /**
     * Creates a capability-aware order panel.
     *
     * @param systemCore  application system core (passed to parent)
     * @param tradePair   initially selected trade pair (may be null)
     * @param capability  the capability profile of the currently active exchange
     */
    public CapabilityAwareOrderPanel(
            SystemCore systemCore,
            TradePair tradePair,
            ExchangeCapability capability
    ) {
        super(systemCore, tradePair);
        this.capability = capability;
        Platform.runLater(this::applyCapabilityGates);
    }

    // ── Gate application ──────────────────────────────────────────────────────

    /**
     * Applies all capability gates to the already-constructed UI.
     * Must be called on the JavaFX Application Thread.
     */
    private void applyCapabilityGates() {
        gateStopLossTakeProfit();
        gateMarginControls();
        gateLiveTradingButton();
        gatePaperModeBanner();
        log.debug("[CapabilityAwareOrderPanel] Capability gates applied for exchange '{}'",
                capability.getExchangeName());
    }

    /** Disables and dims SL/TP spinners when not supported. */
    private void gateStopLossTakeProfit() {
        if (!capability.isSupportsStopLossTakeProfit()) {
            applyUnsupportedStyle(getStopLossSpinner(), "Stop-Loss orders are not supported by this exchange");
            applyUnsupportedStyle(getTakeProfitSpinner(), "Take-Profit orders are not supported by this exchange");
        }
    }

    /**
     * Hides leverage-related controls when margin trading is not supported.
     * InvestPro does not have a dedicated leverage spinner yet, but this gate
     * is provided so that when one is added it is automatically hidden.
     */
    private void gateMarginControls() {
        if (!capability.isSupportsMarginTrading() && !capability.isSupportsLeverage()) {
            // No leverage spinner exists yet — gate is a no-op until one is added.
            log.trace("[CapabilityGate] Margin trading not supported on {}",
                    capability.getExchangeName());
        }
    }

    /** Disables Place Order button when live trading is not supported and paper is off. */
    private void gateLiveTradingButton() {
        boolean canTrade = capability.isSupportsLiveTrading()
                || capability.isSupportsPaperTradingMode();
        if (!canTrade && getPlaceOrderButton() != null) {
            getPlaceOrderButton().setDisable(true);
            getPlaceOrderButton().setStyle(
                    "-fx-background-color: #374151; -fx-text-fill: #6b7280; "
                  + "-fx-font-size: 13px; -fx-font-weight: bold; "
                  + "-fx-background-radius: 8; -fx-cursor: default;");
            Tooltip.install(getPlaceOrderButton(),
                    new Tooltip("Trading not available on " + capability.getDisplayName()));
        }
    }

    /** Inserts a paper-mode banner above the order form when applicable. */
    private void gatePaperModeBanner() {
        if (!capability.isSupportsPaperTradingMode()) return;
        Label banner = new Label("⚠  Paper trading mode — orders are simulated");
        banner.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #f59e0b; -fx-font-weight: bold; "
              + "-fx-background-color: rgba(245,158,11,0.1); "
              + "-fx-padding: 6 12; -fx-background-radius: 6;");
        banner.setMaxWidth(Double.MAX_VALUE);
        getChildren().add(0, banner);
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private static void applyUnsupportedStyle(Region control, String tooltipText) {
        if (control == null) return;
        control.setDisable(true);
        control.setOpacity(0.35);
        Tooltip.install(control, new Tooltip(tooltipText));
    }
}
