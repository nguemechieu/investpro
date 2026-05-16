package org.investpro.ui.utils;

import javafx.scene.image.Image;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads currency flag / icon images for display in market watch tables.
 * <p>
 * Looks up icons from the classpath at {@code /icons/currencies/<CODE>.png}.
 * Falls back to a generic placeholder if the icon is not found.
 */
@Slf4j
public final class CurrencyIconLoader {

    private static final String ICON_PATH_TEMPLATE = "/icons/currencies/%s.png";
    private static final String FALLBACK_PATH = "/icons/currencies/unknown.png";

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private CurrencyIconLoader() {
    }

    /**
     * Load the icon for the given currency code (e.g. "BTC", "USD").
     *
     * @param currencyCode ISO or crypto currency code
     * @return Image, or {@code null} if neither the icon nor the fallback is found
     */
    public static Image loadCurrencyIcon(String currencyCode) {
        if (currencyCode == null || currencyCode.isBlank()) {
            return loadFallback();
        }

        String code = currencyCode.trim().toUpperCase();
        return CACHE.computeIfAbsent(code, key -> {
            Image icon = tryLoad(String.format(ICON_PATH_TEMPLATE, key));
            if (icon != null) {
                return icon;
            }
            log.debug("Currency icon not found for {}, using fallback.", key);
            return loadFallback();
        });
    }

    private static Image loadFallback() {
        return CACHE.computeIfAbsent("__fallback__", ignored -> tryLoad(FALLBACK_PATH));
    }

    private static Image tryLoad(String resourcePath) {
        try {
            InputStream stream = CurrencyIconLoader.class.getResourceAsStream(resourcePath);
            if (stream == null) {
                return null;
            }
            return new Image(stream);
        } catch (Exception e) {
            log.debug("Failed to load icon from {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }
}
