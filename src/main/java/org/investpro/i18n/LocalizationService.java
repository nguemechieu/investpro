package org.investpro.i18n;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Labeled;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumnBase;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.prefs.Preferences;

public final class LocalizationService {
    private static final String BUNDLE_BASE_NAME = "i18n.messages";
    private static final String PREF_KEY_LANGUAGE = "app.language";
    private static final Preferences PREFS = Preferences.userNodeForPackage(LocalizationService.class);

    private static SupportedLanguage currentLanguage = SupportedLanguage.fromCode(
            PREFS.get(PREF_KEY_LANGUAGE, Locale.getDefault().getLanguage()));
    private static ResourceBundle bundle = loadBundle(currentLanguage);
    private static final ResourceBundle ENGLISH_BUNDLE = ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
    private static final Map<String, String> ENGLISH_TEXT_TO_KEY = buildEnglishTextIndex();

    private LocalizationService() {
    }

    public static SupportedLanguage getCurrentLanguage() {
        return currentLanguage;
    }

    public static void setCurrentLanguage(SupportedLanguage language) {
        currentLanguage = Objects.requireNonNullElse(language, SupportedLanguage.ENGLISH);
        bundle = loadBundle(currentLanguage);
        PREFS.put(PREF_KEY_LANGUAGE, currentLanguage.getCode());
    }

    public static String t(String key, Object... args) {
        String pattern = text(key);
        return args == null || args.length == 0
                ? pattern
                : MessageFormat.format(pattern, args);
    }

    public static String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            return bundle.getString(key);
        } catch (MissingResourceException ignored) {
            try {
                return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH).getString(key);
            } catch (MissingResourceException missingEnglish) {
                return key;
            }
        }
    }

    public static void applyTranslations(Node root) {
        if (root == null) {
            return;
        }

        root.setNodeOrientation(currentLanguage.isRightToLeft()
                ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
        applyToNode(root);
    }

    private static void applyToNode(Node node) {
        if (node instanceof Labeled labeled) {
            labeled.setText(localizedLiteral(labeled.getText()));
            Tooltip tooltip = labeled.getTooltip();
            if (tooltip != null) {
                tooltip.setText(localizedLiteral(tooltip.getText()));
            }
        }

        if (node instanceof TextInputControl input) {
            input.setPromptText(localizedLiteral(input.getPromptText()));
        }

        if (node instanceof ComboBoxBase<?> comboBoxBase) {
            comboBoxBase.setPromptText(localizedLiteral(comboBoxBase.getPromptText()));
        }

        if (node instanceof MenuButton menuButton) {
            menuButton.getItems().forEach(LocalizationService::applyToMenuItem);
        }

        if (node instanceof MenuBar menuBar) {
            menuBar.getMenus().forEach(LocalizationService::applyToMenu);
        }

        if (node instanceof TabPane tabPane) {
            tabPane.getTabs().forEach(LocalizationService::applyToTab);
        }

        if (node instanceof TableView<?> tableView) {
            if (tableView.getPlaceholder() != null) {
                applyToNode(tableView.getPlaceholder());
            }
            tableView.getColumns().forEach(LocalizationService::applyToColumn);
        }

        if (node instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(LocalizationService::applyToNode);
        } else if (node instanceof Pane pane) {
            pane.getChildren().forEach(LocalizationService::applyToNode);
        }
    }

    private static void applyToTab(Tab tab) {
        if (tab == null) {
            return;
        }
        tab.setText(localizedLiteral(tab.getText()));
        if (tab.getContent() != null) {
            applyToNode(tab.getContent());
        }
    }

    private static void applyToColumn(TableColumnBase<?, ?> column) {
        if (column == null) {
            return;
        }
        column.setText(localizedLiteral(column.getText()));
        column.getColumns().forEach(LocalizationService::applyToColumn);
    }

    private static void applyToMenu(Menu menu) {
        if (menu == null) {
            return;
        }
        menu.setText(localizedLiteral(menu.getText()));
        menu.getItems().forEach(LocalizationService::applyToMenuItem);
    }

    private static void applyToMenuItem(MenuItem item) {
        if (item == null) {
            return;
        }
        item.setText(localizedLiteral(item.getText()));
        if (item instanceof Menu menu) {
            applyToMenu(menu);
        }
    }

    public static String localizedLiteral(String literal) {
        if (literal == null || literal.isBlank()) {
            return literal;
        }
        String key = ENGLISH_TEXT_TO_KEY.get(literal);
        return key == null ? literal : text(key);
    }

    private static ResourceBundle loadBundle(SupportedLanguage language) {
        try {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, language.getLocale());
        } catch (MissingResourceException exception) {
            return ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);
        }
    }

    private static Map<String, String> buildEnglishTextIndex() {
        Map<String, String> index = new HashMap<>();
        for (String key : ENGLISH_BUNDLE.keySet()) {
            String value = ENGLISH_BUNDLE.getString(key);
            if (value != null && !value.isBlank() && !value.contains("{")) {
                index.putIfAbsent(value, key);
            }
        }
        return Map.copyOf(index);
    }
}
