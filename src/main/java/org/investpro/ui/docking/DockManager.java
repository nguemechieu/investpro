package org.investpro.ui.docking;

import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.geometry.Orientation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Central docking controller. Single source of truth for panel placement.
 */
public final class DockManager {
    private static final String HORIZONTAL_DIVIDERS_SUFFIX = "dividers.horizontal";
    private static final String VERTICAL_DIVIDER_SUFFIX = "dividers.vertical";

    private final SplitPane horizontalSplit = new SplitPane();
    private final SplitPane rootSplit = new SplitPane();
    private final Map<DockRegionType, DockRegion> regions = new EnumMap<>(DockRegionType.class);
    private final Map<String, DockablePane> panesById = new LinkedHashMap<>();
    private final Map<String, DockRegionType> paneRegion = new LinkedHashMap<>();
    private final Map<String, DockRegionType> lastDockedRegion = new LinkedHashMap<>();
    private final Map<String, FloatingWindow> floatingById = new LinkedHashMap<>();

    public DockManager() {
        for (DockRegionType regionType : DockRegionType.values()) {
            DockRegion region = new DockRegion(regionType);
            region.setPaneMoveHandler((paneId, targetRegion) -> movePane(paneId, targetRegion));
            regions.put(regionType, region);
        }

        horizontalSplit.setOrientation(Orientation.HORIZONTAL);
        horizontalSplit.getItems().setAll(
                regions.get(DockRegionType.LEFT),
                regions.get(DockRegionType.CENTER),
                regions.get(DockRegionType.RIGHT));
        horizontalSplit.setDividerPositions(0.22, 0.78);

        rootSplit.setOrientation(Orientation.VERTICAL);
        rootSplit.getItems().setAll(horizontalSplit, regions.get(DockRegionType.BOTTOM));
        rootSplit.setDividerPositions(0.72);

        DockRegion leftRegion = regions.get(DockRegionType.LEFT);
        DockRegion rightRegion = regions.get(DockRegionType.RIGHT);
        DockRegion bottomRegion = regions.get(DockRegionType.BOTTOM);
        if (leftRegion != null) {
            leftRegion.setMinWidth(220);
        }
        if (rightRegion != null) {
            rightRegion.setMinWidth(220);
        }
        if (bottomRegion != null) {
            bottomRegion.setMinHeight(140);
        }
    }

    public Node getView() {
        return rootSplit;
    }

    public void registerPane(DockablePane pane, DockRegionType initialRegion) {
        Objects.requireNonNull(pane, "pane must not be null");
        Objects.requireNonNull(initialRegion, "initialRegion must not be null");

        if (panesById.containsKey(pane.getId())) {
            return;
        }

        panesById.put(pane.getId(), pane);
        attachPane(pane.getId(), initialRegion);
    }

    public Optional<DockablePane> findPane(String paneId) {
        return Optional.ofNullable(panesById.get(paneId));
    }

    public Optional<DockRegionType> getPaneRegion(String paneId) {
        return Optional.ofNullable(paneRegion.get(paneId));
    }

    public void movePane(String paneId, DockRegionType fromRegion, DockRegionType toRegion) {
        if (paneId == null || fromRegion == null || toRegion == null || fromRegion == toRegion) {
            return;
        }
        DockRegion from = regions.get(fromRegion);
        DockRegion to = regions.get(toRegion);
        if (from == null || to == null) {
            return;
        }

        DockablePane pane = from.removePane(paneId);
        if (pane == null) {
            return;
        }

        to.addPane(pane);
        paneRegion.put(paneId, toRegion);
        lastDockedRegion.put(paneId, toRegion);
    }

    public void movePane(String paneId, DockRegionType toRegion) {
        if (paneId == null || toRegion == null) {
            return;
        }
        DockRegionType fromRegion = paneRegion.get(paneId);
        if (fromRegion == null || fromRegion == toRegion) {
            return;
        }
        movePane(paneId, fromRegion, toRegion);
    }

    public void detachPane(String paneId) {
        DockablePane pane = panesById.get(paneId);
        DockRegionType regionType = paneRegion.get(paneId);
        if (pane == null || regionType == null) {
            return;
        }

        DockRegion region = regions.get(regionType);
        if (region != null) {
            region.removePane(paneId);
        }
        paneRegion.remove(paneId);
        lastDockedRegion.put(paneId, regionType);

        FloatingWindow floatingWindow = new FloatingWindow(pane,
                () -> attachPane(paneId, lastDockedRegion.getOrDefault(paneId, DockRegionType.CENTER)));
        floatingById.put(paneId, floatingWindow);
        floatingWindow.show();
    }

    public void attachPane(String paneId, DockRegionType regionType) {
        DockablePane pane = panesById.get(paneId);
        DockRegion region = regions.get(regionType);
        if (pane == null || region == null) {
            return;
        }

        FloatingWindow floatingWindow = floatingById.remove(paneId);
        if (floatingWindow != null) {
            floatingWindow.close();
        }

        for (DockRegion existingRegion : regions.values()) {
            if (existingRegion.containsPane(paneId)) {
                existingRegion.removePane(paneId);
                break;
            }
        }

        region.addPane(pane);
        paneRegion.put(paneId, regionType);
        lastDockedRegion.put(paneId, regionType);
    }

    public boolean isDocked(String paneId) {
        return paneRegion.containsKey(paneId);
    }

    public void hidePane(String paneId) {
        DockRegionType regionType = paneRegion.remove(paneId);
        if (regionType == null) {
            return;
        }
        DockRegion region = regions.get(regionType);
        if (region != null) {
            region.removePane(paneId);
        }
        lastDockedRegion.put(paneId, regionType);
    }

    public void showPane(String paneId, DockRegionType fallbackRegion) {
        DockRegionType target = lastDockedRegion.getOrDefault(paneId, fallbackRegion);
        attachPane(paneId, target);
    }

    public void saveLayout(java.util.prefs.Preferences preferences, String keyPrefix) {
        for (DockRegionType regionType : DockRegionType.values()) {
            List<String> paneIds = new ArrayList<>();
            for (Map.Entry<String, DockRegionType> entry : paneRegion.entrySet()) {
                if (entry.getValue() == regionType) {
                    paneIds.add(entry.getKey());
                }
            }
            preferences.put(keyPrefix + regionType.name().toLowerCase(), String.join(",", paneIds));
        }

        double[] horizontalDividers = horizontalSplit.getDividerPositions();
        if (horizontalDividers.length >= 2) {
            preferences.put(keyPrefix + HORIZONTAL_DIVIDERS_SUFFIX,
                    horizontalDividers[0] + "," + horizontalDividers[1]);
        }

        double[] verticalDividers = rootSplit.getDividerPositions();
        if (verticalDividers.length >= 1) {
            preferences.put(keyPrefix + VERTICAL_DIVIDER_SUFFIX, Double.toString(verticalDividers[0]));
        }
    }

    public Map<String, List<String>> saveLayout() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (DockRegionType regionType : DockRegionType.values()) {
            List<String> paneIds = new ArrayList<>();
            for (Map.Entry<String, DockRegionType> entry : paneRegion.entrySet()) {
                if (entry.getValue() == regionType) {
                    paneIds.add(entry.getKey());
                }
            }
            snapshot.put(regionType.name().toLowerCase(), paneIds);
        }
        return snapshot;
    }

    public void loadLayout(Map<String, List<String>> layout) {
        if (layout == null || layout.isEmpty()) {
            return;
        }
        for (Map.Entry<String, List<String>> entry : layout.entrySet()) {
            DockRegionType regionType;
            try {
                regionType = DockRegionType.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException ex) {
                continue;
            }

            List<String> paneIds = entry.getValue();
            if (paneIds == null) {
                continue;
            }

            for (String paneId : paneIds) {
                if (panesById.containsKey(paneId)) {
                    attachPane(paneId, regionType);
                }
            }
        }
    }

    public void loadLayout(Preferences preferences, String keyPrefix) {
        if (preferences == null || keyPrefix == null) {
            return;
        }

        Map<String, List<String>> layout = new LinkedHashMap<>();
        for (DockRegionType regionType : DockRegionType.values()) {
            String csv = preferences.get(keyPrefix + regionType.name().toLowerCase(), "");
            if (csv == null || csv.isBlank()) {
                continue;
            }
            List<String> paneIds = new ArrayList<>();
            for (String token : csv.split(",")) {
                String paneId = token == null ? "" : token.trim();
                if (!paneId.isBlank()) {
                    paneIds.add(paneId);
                }
            }
            if (!paneIds.isEmpty()) {
                layout.put(regionType.name().toLowerCase(), paneIds);
            }
        }

        loadLayout(layout);

        String horizontalCsv = preferences.get(keyPrefix + HORIZONTAL_DIVIDERS_SUFFIX, "");
        if (!horizontalCsv.isBlank()) {
            String[] tokens = horizontalCsv.split(",");
            if (tokens.length >= 2) {
                try {
                    double leftDivider = Double.parseDouble(tokens[0].trim());
                    double rightDivider = Double.parseDouble(tokens[1].trim());
                    horizontalSplit.setDividerPositions(leftDivider, rightDivider);
                } catch (NumberFormatException ignored) {
                    // Keep defaults when persisted divider values are invalid.
                }
            }
        }

        String verticalValue = preferences.get(keyPrefix + VERTICAL_DIVIDER_SUFFIX, "");
        if (!verticalValue.isBlank()) {
            try {
                rootSplit.setDividerPositions(Double.parseDouble(verticalValue.trim()));
            } catch (NumberFormatException ignored) {
                // Keep defaults when persisted divider value is invalid.
            }
        }
    }
}
