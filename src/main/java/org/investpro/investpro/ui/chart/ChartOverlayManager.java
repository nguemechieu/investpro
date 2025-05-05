package org.investpro.investpro.ui.chart;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.ui.chart.overlay.ChartOverlay;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ChartOverlayManager {

    private final CandleStickChart chart;
    private final List<ChartOverlay> activeOverlays = new ArrayList<>();
    private boolean indicatorsVisible = true;

    public ChartOverlayManager(CandleStickChart chart) {
        this.chart = chart;
    }

    public void addOverlay(ChartOverlay overlay) {
        if (indicatorsVisible) {
            // overlay.apply(chart, chart.getCandleData());
            //activeOverlays.add(overlay);
        }
    }

    public void removeOverlayByName(String name) {
        activeOverlays.removeIf(overlay -> {
            if (overlay.getName().equalsIgnoreCase(name)) {
                overlay.clear(chart);
                return true;
            }
            return false;
        });
    }

    public void clearAll() {
        activeOverlays.forEach(overlay -> overlay.clear(chart));
        activeOverlays.clear();
    }

    public void toggleIndicators() {
        if (indicatorsVisible) {
            clearAll();
        } else {
            activeOverlays.forEach(overlay -> overlay.apply(chart, chart.getCandlesData()));
        }
        indicatorsVisible = !indicatorsVisible;
    }

    public void removeOverlayByPrefix(String prefix) {
        activeOverlays.removeIf(overlay -> {
            if (overlay.getName().startsWith(prefix)) {
                overlay.clear(chart);
                return true;
            }
            return false;
        });
    }

    public double priceToPixel(double price) {
        double chartHeight = chart.getCanvas().getHeight();
        double maxPrice = chart.getCandlesData().stream().mapToDouble(CandleData::getHighPrice).max().orElse(1);
        double minPrice = chart.getCandlesData().stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        double priceRange = maxPrice - minPrice;
        return (price - minPrice) / priceRange * chartHeight;
    }


}
