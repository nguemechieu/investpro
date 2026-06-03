package org.investpro.ui.capability;

import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import org.investpro.exchange.models.ExchangeCapability;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility that applies {@link ExchangeCapability} gates to JavaFX UI nodes.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 *   ExchangeCapabilityUiGate gate = new ExchangeCapabilityUiGate(capability);
 *
 *   // Hide depth chart if order book not supported
 *   gate.hideUnless(depthChartPanel, capability.isSupportsFullOrderBook(),
 *           "Order book depth not available on this exchange");
 *
 *   // Disable leverage spinner if margin not supported
 *   gate.disableUnless(leverageSpinner, capability.isSupportsMarginTrading(),
 *           "Margin trading not supported on this exchange");
 * }</pre>
 *
 * <p>Thread safety: must be called on the JavaFX Application Thread.
 */
public record ExchangeCapabilityUiGate(ExchangeCapability capability) {

    public ExchangeCapabilityUiGate(@NotNull ExchangeCapability capability) {
        this.capability = capability;
    }

    // ── Primary gate operations ───────────────────────────────────────────────

    /**
     * Hides the node (managed=false, visible=false) if {@code supported} is false.
     *
     * @param node      the UI node to gate
     * @param supported whether the capability is supported
     * @param reason    tooltip text explaining why the control is hidden
     */
    public void hideUnless(@NotNull Node node, boolean supported, @Nullable String reason) {
        if (!supported) {
            node.setVisible(false);
            node.setManaged(false);
            if (reason != null && !reason.isBlank()) {
                Tooltip.install(node, new Tooltip(reason));
            }
        }
    }

    /**
     * Disables the node and dims it if {@code supported} is false.
     *
     * @param node      the UI node to gate
     * @param supported whether the capability is supported
     * @param reason    tooltip text explaining why the control is disabled
     */
    public void disableUnless(@NotNull Node node, boolean supported, @Nullable String reason) {
        if (!supported) {
            node.setDisable(true);
            node.setOpacity(0.35);
            if (reason != null && !reason.isBlank()) {
                Tooltip.install(node, new Tooltip(reason));
            }
        }
    }

    /**
     * Sets the preferred height of a {@link Region} to zero and hides it
     * if {@code supported} is false. Useful for collapsing panels without
     * removing them from the scene graph.
     *
     * @param region    the region to collapse
     * @param supported whether to keep it expanded
     * @param reason    tooltip text
     */
    public void collapseUnless(@NotNull Region region, boolean supported, @Nullable String reason) {
        if (!supported) {
            region.setPrefHeight(0);
            region.setMaxHeight(0);
            region.setMinHeight(0);
            region.setVisible(false);
            region.setManaged(false);
            if (reason != null && !reason.isBlank()) {
                Tooltip.install(region, new Tooltip(reason));
            }
        }
    }

    // ── Pre-built capability gate methods ──────────────────────────────────────

    /**
     * Gates a full order-book depth panel.
     *
     * <p>Hides the panel if the exchange does not support
     * {@link ExchangeCapability#isSupportsFullOrderBook()}.
     *
     * @param depthPanel the depth chart or order-book region
     */
    public void gateOrderBookDepthPanel(@NotNull Region depthPanel) {
        hideUnless(depthPanel,
                capability.isSupportsFullOrderBook() || capability.isSupportsOrderBook(),
                "Order book depth data is not available on " + capability.getDisplayName());
    }

    /**
     * Gates leverage / margin controls.
     *
     * <p>Disables and dims the control if the exchange does not support
     * {@link ExchangeCapability#isSupportsMarginTrading()}.
     *
     * @param leverageControl any node representing leverage input
     */
    public void gateLeverageControl(@NotNull Node leverageControl) {
        disableUnless(leverageControl,
                capability.isSupportsMarginTrading() || capability.isSupportsLeverage(),
                "Margin / leverage trading is not supported on " + capability.getDisplayName());
    }

    /**
     * Gates stop-loss and take-profit controls.
     *
     * @param slControl stop-loss control
     * @param tpControl take-profit control
     */
    public void gateStopLossTakeProfit(@NotNull Node slControl, @NotNull Node tpControl) {
        String msg = "Stop-Loss / Take-Profit not supported on " + capability.getDisplayName();
        disableUnless(slControl, capability.isSupportsStopLossTakeProfit(), msg);
        disableUnless(tpControl, capability.isSupportsStopLossTakeProfit(), msg);
    }

    /**
     * Gates real-time streaming indicators or live-data widgets.
     *
     * @param streamingWidget the widget that requires WebSocket data
     */
    public void gateStreamingWidget(@NotNull Node streamingWidget) {
        disableUnless(streamingWidget,
                capability.isSupportsWebSocket() || capability.isSupportsNativeWebSocket()
                        || capability.isSupportsWebSocketStreaming(),
                "Real-time streaming not available on " + capability.getDisplayName()
                        + " — polling mode active");
    }

    /**
     * Gates any panel that requires live trading capability.
     *
     * @param tradingPanel the panel to gate
     */
    public void gateLiveTradingPanel(@NotNull Node tradingPanel) {
        disableUnless(tradingPanel,
                capability.isSupportsLiveTrading(),
                "Live trading is not enabled on " + capability.getDisplayName());
    }

    /**
     * Returns the capability this gate was constructed with.
     */
    @Override
    public @NotNull ExchangeCapability capability() {
        return capability;
    }
}
