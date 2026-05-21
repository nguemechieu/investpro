package org.investpro.ui.icons;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import org.investpro.models.currency.CurrencyRegistry;
import org.investpro.models.trading.TradePair;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight icon loader with in-memory cache and safe fallbacks.
 */
public class CurrencyIconLoader {

    private final CurrencyRegistry registry;
    private final Map<String, Optional<Image>> imageCache = new ConcurrentHashMap<>();

    public CurrencyIconLoader() {
        this(CurrencyRegistry.global());
    }

    public CurrencyIconLoader(CurrencyRegistry registry) {
        this.registry = registry == null ? CurrencyRegistry.global() : registry;
    }

    public Optional<Image> loadCurrencyIcon(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        return imageCache.computeIfAbsent(normalized, key -> {
            String path = registry.iconPathOrDefault(key);
            Optional<Image> image = loadClasspathImage(path);
            if (image.isPresent()) {
                return image;
            }

            String pngPath = path.endsWith(".svg") ? path.substring(0, path.length() - 4) + ".png" : path;
            image = loadClasspathImage(pngPath);
            if (image.isPresent()) {
                return image;
            }

            return loadClasspathImage("/icons/currencies/default.png");
        });
    }

    public Optional<Image> loadPairIcon(TradePair pair) {
        if (pair == null || pair.getBaseCurrency() == null) {
            return Optional.empty();
        }
        return loadCurrencyIcon(pair.getBaseCurrency().getCode());
    }

    public Node createCurrencyIconNode(String code, double size) {
        double iconSize = size <= 0 ? 14.0 : size;
        Optional<Image> image = loadCurrencyIcon(code);

        if (image.isPresent()) {
            ImageView view = new ImageView(image.get());
            view.setFitWidth(iconSize);
            view.setFitHeight(iconSize);
            view.setPreserveRatio(true);
            view.getStyleClass().add("currency-icon");
            return view;
        }

        Label fallback = new Label(shortCode(code));
        fallback.getStyleClass().addAll("currency-code-badge", "currency-icon");
        return fallback;
    }

    public Node createPairIconNode(TradePair pair, double size) {
        if (pair == null) {
            return createCurrencyIconNode("UNK", size);
        }

        Node base = createCurrencyIconNode(pair.getBaseCode(), size);
        Node quote = createCurrencyIconNode(pair.getCounterCode(), size);

        HBox box = new HBox(4, base, quote);
        box.getStyleClass().add("currency-icon-pair");
        return box;
    }

    private Optional<Image> loadClasspathImage(String classpathPath) {
        String normalized = classpathPath == null ? "" : classpathPath.trim();
        if (normalized.isBlank()) {
            return Optional.empty();
        }

        String resourcePath = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        try (InputStream inputStream = CurrencyIconLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                return Optional.empty();
            }
            Image image = new Image(inputStream);
            if (image.isError()) {
                return Optional.empty();
            }
            return Optional.of(image);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String shortCode(String code) {
        String normalized = normalize(code);
        if (normalized.isBlank()) {
            return "UNK";
        }
        return normalized.length() <= 4 ? normalized : normalized.substring(0, 4);
    }
}
