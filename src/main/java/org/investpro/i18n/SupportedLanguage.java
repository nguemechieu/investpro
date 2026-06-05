package org.investpro.i18n;

import lombok.Getter;

import java.util.Locale;
@Getter
public enum SupportedLanguage {
    ENGLISH("en", "English", Locale.ENGLISH, false),
    SPANISH("es", "Spanish", Locale.forLanguageTag("es"), false),
    FRENCH("fr", "French", Locale.FRENCH, false),
    GERMAN("de", "German", Locale.GERMAN, false),
    PORTUGUESE("pt", "Portuguese", Locale.forLanguageTag("pt"), false),
    ARABIC("ar", "Arabic", Locale.forLanguageTag("ar"), true),
    CHINESE("zh", "Chinese", Locale.SIMPLIFIED_CHINESE, false);

    private final String code;
    private final String displayName;
    private final Locale locale;
    private final boolean rightToLeft;

    SupportedLanguage(String code, String displayName, Locale locale, boolean rightToLeft) {
        this.code = code;
        this.displayName = displayName;
        this.locale = locale;
        this.rightToLeft = rightToLeft;
    }

    public static SupportedLanguage fromCode(String code) {
        if (code != null) {
            for (SupportedLanguage language : values()) {
                if (language.code.equalsIgnoreCase(code.trim())
                        || language.locale.toLanguageTag().equalsIgnoreCase(code.trim())) {
                    return language;
                }
            }
        }
        return ENGLISH;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
